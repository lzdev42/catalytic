# Catalytic Engine 业务逻辑深度分析报告

本报告旨在详尽分析 Catalytic Engine 的核心业务逻辑、数据架构及执行流，确保理解与代码实现高度一致。

---

## 1. 核心架构概览

Catalytic Engine 采用 **Rust 核心 + C# Host** 的分层架构。

- **Rust 层 (Brain)**: 负责状态机维护、测试流程调度、数据解析与规则判定。
- **C# 层 (Hands)**: 负责具体物理设备的 I/O 操作（通过插件系统）以及 UI 展示（通过 gRPC）。
- **FFI 桥接**: 通过异步回调（Callback）与任务 ID（TaskID）机制，将 Rust 的异步逻辑与 C# 的同步插件库无缝对接。

---

## 2. 配置与持久化逻辑

### 2.1 数据模型
- **DeviceType**: 定义一类设备（如 DMM、Source），包含协议（SCPI, Serial 等）和对应的设备实例。
- **DeviceInstance**: 具体的设备连接信息（如 IP、COM 口、地址）。
- **TestStep**: 最小执行单元。包含执行模式、任务载荷、解析规则、检查规则及跳转逻辑。
- **SlotBinding**: 静态配置到运行时的映射，定义哪个“槽位”使用哪个“设备实例”。

### 2.2 持久化机制 (redb)
- **存储路径**: 由 `cat_engine_set_data_path` 指定，默认为 `engine.db`。
- **保存策略**: 每当修改槽位数量、添加/修改/删除步骤、更改设备配置或绑定时，会自动触发 `save_to_storage`。
- **加载逻辑**: 引擎启动时自动读取 `full_config` 键，恢复所有设备类型、步骤列表及槽位绑定。

---

## 3. 槽位 (Slot) 运行时逻辑

### 3.1 槽位上下文 (SlotContext)
每个槽位是一个独立的运行环境，维护：
- **SN (序列号)**: 当前被测物的唯一标识。
- **VariablePool**: 运行时变量池，支持跨步骤的数据共享（如：第一步读取的电压存入变量，第三步进行比较）。
- **StepResults**: 历史步骤执行结果的累积，用于最终生成测试报告。
- **ControlChannel**: 一个 MPSC 异步通道，接收来自主界面的“暂停/恢复/停止/跳过”指令。

### 3.2 状态机与转换规则 (StateMachine)
槽位状态流转逻辑严格遵循预定义的转换矩阵（见 `status.rs`），任何非法转换都会被引擎拦截：

| 源状态 | 目标状态 | 触发行为 / 条件 |
| :--- | :--- | :--- |
| `Idle` | `Running` | 调用 `cat_engine_start_slot()` |
| `Running` | `Paused` | 调用 `cat_engine_pause_slot()` 或脚本指令 |
| `Paused` | `Running` | 调用 `cat_engine_resume_slot()` |
| `Running` | `Idle` | 调用 `cat_engine_stop_slot()`（强制中止） |
| `Running` | `Completed`| 所有测试步骤正常执行完毕 |
| `Running` | `Error` | 发生不可恢复的系统错误或执行异常 |
| `Completed/Error` | `Idle` | 调用 `cat_engine_reset_slot()` |

---

## 4. 测试流执行逻辑 (Executor)

这是引擎的核心“主循环”，位于 `executor.rs` 的 `run_slot_async` 中。其本质上是一个 **可调度的异步迭代器**。

### 4.1 主循环流 (Wait-Execute-Transition)
1. **信号检查**: 每一轮循环开始，非阻塞检查 `control_rx`。
    - 若收到 `Stop`：立即退出循环。
    - 若收到 `Pause`：进入阻塞等待模式，直到收到 `Resume` 或 `Stop`。
    - 若收到 `SkipCurrent`：直接跳过当前步骤索引。
2. **步骤提取**: 根据当前 `current_step_index` 从内存缓存中提取 `TestStep` 模型。
3. **执行模式分发**:
    - **EngineControlled**: 引擎控制模式。
        - **设备映射**: 查找 `device_bindings` 确认目标设备实例的地址（IP/COM）。
        - **生命周期**: 生成 `TaskID` -> 注册到 `TaskRegistry` -> 发起 FFI 回调 -> **异步等待** 返回数据。
        - **超时管理**: 利用 `tokio::select!` 监控 `TaskID` 的结果回传与 `timeout_ms` 定时器。
        - **重试逻辑**: 支持按 `loop_max_iterations` 进行应用层重试。
    - **HostControlled**: 脚本控制模式。
        - 将控制权完全移交给 C# Host，支持自定义复杂业务（如弹窗交互）。
        - 同样通过 `TaskID` 机制实现异步同步。
4. **结果处理 & UI 推送**:
    - 详见第 5 节“数据流水线”。
    - 每一步结束都会通过 `ui_update_callback` 推送包含进度、变量快照、SN 在内的全量模型。
5. **跳转逻辑 (Jump Logic)**:
    - 引擎根据 `StepResult` 的状态（Passed/Failed/Timeout/Error）在 `steps` 数组中移动指针。
    - 这实现了 **非线性的测试路径**。

---

## 5. 数据处理流水线

数据的转化逻辑遵循以下严格顺序，确保“从二进制到业务判断”的透明性：

### 5.1 解析阶段 (Parser)
- **输入**: 原始字节数组 (`Vec<u8>`)，通常是 SCPI 响应或十六进制报文。
- **逻辑**: 根据 `ParseRule` 执行：
    - `Number`: 自动清理非数字字符并尝试 parse 为 f64。
    - `Regex`: 使用 PCRE 风格正则配合捕获组提取（Capture Group 1）。
    - `JsonPath`: 针对复杂 JSON 响应的字段值提取。
- **输出**: `Variable` 对象（支持 String, Number, Boolean, Null）。

### 5.2 存储阶段 (VariablePool)
- 解析出的 `Variable` 若配置了 `save_to`，则存入 `SlotContext` 的变量池。
- 变量池采用读写锁保护，支持在后续步骤中通过模板引用。

### 5.3 检查阶段 (Checker)
- **Builtin 检查**:
    - `RangeCheck`: 判断 `Variable` 是否在数值区间内。
    - `Threshold`: 对标运算符（如电流是否小于 100mA）。
    - `Compare`: A 变量与 B 变量的逻辑判定。
- **判定结果**: 生成 `CheckOutput`（包含 Passed 标志、Actual 实际值、Summary 文字摘要）。

---

## 6. 异常处理与日志流

### 6.1 错误分级机制
- **FFI 层**: 通过返回值（0-成功，其他-错误码）进行初步判定。
- **Executor 层**: 捕捉 `ExecuteError`，并将其持久化到槽位的 `last_error` 字段。
- **UI 层**: 持续轮询 `GetSlotStatusJSON` 以获取最新的 `last_error`。

### 6.2 实时日志 (LogCallback)
- **实时性**: 与状态轮询不同，日志通过 `LogCallback` 实时主动推送到 Host 侧。
- **包含信息**: 时间戳、日志级别（info/warn/error）、来源（executor/check/ffi）、消息正文。

---

## 7. 总结与合规性评估

目前的业务逻辑设计具备以下优势：
1. **职责分离**: 引擎不关心具体的 I/O 实现（如 VISA 库或串口配置），只下达“命令”并等待“结果”。
2. **异步非阻塞**: 槽位之间相互独立，利用 Tokio 运行时实现高并发。
3. **确定性**: 通过状态机和跳转逻辑，保证了测试路径的可预测性。

**目前潜在的改进点**:
- **循环嵌套**: 当前只支持单步骤内的 Loop，不支持多步骤组成的 Block 循环。
- **条件分支**: 跳转逻辑虽然灵活，但缺乏高级 `If-Else` 可视化表达。
