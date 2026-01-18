# Catalytic 决策记录与逻辑字典

## 关键决策记录 (ADR)

### ADR-001: 程序与数据分离架构 (2026-01-07, 已更新)

> ⚠️ **重要**: 本 ADR 已于 2026-01-07 完全重写，废弃了原有的"两级配置"设计。

**背景**: 
- 需要像移动 App 一样简单：用户双击就能用，升级时整包替换。
- 用户不应该接触程序文件，只管理自己的数据。
- 必须考虑权限问题：程序目录可能只读，用户目录必须可写。

---

#### 最终架构：两个完全独立的区域

```
┌─────────────────────────────────────────────────────────────────┐
│ 程序包 (App Bundle) - 只读，用户不碰                              │
│ ================================================================ │
│ 位置: 系统应用目录 (如 /Applications/Catalytic.app 或 Program Files) │
│                                                                   │
│ 内容:                                                            │
│   ├── CatalyticUI (主程序入口)                                    │
│   ├── host/                                                       │
│   │   └── Catalytic.Host (后台服务可执行文件)                      │
│   └── lib/                                                       │
│       └── libcatalytic.dylib (Engine 动态库)                      │
│                                                                   │
│ 注意: 没有任何 JSON 配置文件在这里！                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 用户目录 (User Directory) - 系统标准应用数据目录                   │
│ ================================================================ │
│ 位置:                                                            │
│   - macOS:   ~/Library/Application Support/Catalytic/            │
│   - Windows: %APPDATA%\Catalytic\                                │
│   - Linux:   ~/.config/Catalytic/                                │
│                                                                   │
│ 内容:                                                            │
│   └── config.json (唯一的配置文件，详见下方)                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 工作目录 (Working Directory) - 用户选择的任意位置                  │
│ ================================================================ │
│ 位置: 由用户在首次启动时选择（路径记录在 config.json 中）           │
│                                                                   │
│ 内容:                                                            │
│   ├── plugins/    (用户/第三方插件 DLL)                           │
│   ├── data/       (测试数据、engine.db)                          │
│   ├── reports/    (测试报告)                                      │
│   └── logs/       (日志文件)                                      │
│                                                                   │
│ 注意: 这里没有 config.json！配置文件在用户目录，不在这里。          │
└─────────────────────────────────────────────────────────────────┘
```

---

#### config.json 的内容与位置

**位置**: 用户目录（不是工作目录！不是程序目录！）

**内容示例**:
```json
{
  "working_directory": "/Users/liuzhe/Documents/MyCatalyticData",
  "language": "zh",
  "grpc_port": 5000
}
```

**生成方式**: 
- **没有** `default_config.json` 文件。
- **没有** `workspace.json` 文件。
- 默认值来自 **Kotlin Data Class** 的默认参数。
- UI 首次启动时，如果 config.json 不存在，则实例化 Data Class -> 序列化 -> 写入文件。

---

#### UI 启动流程 (必读)

```
1. 用户双击启动 Catalytic UI

2. UI 检查用户目录下是否存在 config.json
   │
   ├─ 不存在 (首次启动):
   │   ├─ 弹出原生文件夹选择器，强制用户选择一个工作目录
   │   ├─ 检查该目录结构，如果缺少 plugins/, data/ 等，自动创建
   │   ├─ 实例化 ConfigModel(workingDirectory = 用户选择的路径, language = "en", ...)
   │   └─ 序列化并写入 config.json 到用户目录
   │
   └─ 存在:
       └─ 读取并反序列化为 ConfigModel

3. UI 定位程序包内的 Host 可执行文件
   位置: <App Bundle>/host/Catalytic.Host
   
   如果找不到 -> 显示错误: "Core component missing" / "核心组件缺失"

4. UI 寻找一个可用的 TCP 端口 (从 config 中读取或自动探测)

5. UI 启动 Host 进程，传递命令行参数:
   ./Catalytic.Host --port 5000 --work-dir "/Users/liuzhe/Documents/MyCatalyticData"

6. UI 连接 localhost:<port>，进入主界面
```

---

#### 实现要点

| 组件 | 职责 |
|------|------|
| **UI (Kotlin)** | 管理 config.json、启动/停止 Host、连接 gRPC |
| **Host (C#)** | 接收命令行参数、加载 plugins/、调用 Engine |
| **Engine (Rust)** | 纯库，由 Host 加载，不涉及任何配置文件 |

---

### ADR-002: Engine 控制 vs Host 控制

**决策**: 测试步骤分两种执行模式
- `engine_controlled`: Engine 驱动，调用协议驱动发送命令、解析响应
- `host_controlled`: Host 执行自定义任务，需实现 `ITaskHandler` 接口

---

### ADR-003: UI 自动化测试策略 (2026-01-07)

**背景**:
- 需要自动化验证 UI 功能，减少人工回归测试成本
- 目标应用为 Compose Multiplatform Desktop (JVM)
- 需支持"可观测"的测试结果

**决策**: 采用 **Compose Multiplatform Testing (JUnit)** + **截图验证**
- **框架**: 使用 `compose.desktop.uiTestJUnit4` (基于 JUnit 4)
- **范围**: 
  - 组件级测试 (SlotCard, DeviceList)
  - 窗口级集成测试 (MainScreen E2E)
- **验证手段**:
  - `SemanticsMatcher`: 验证文本、状态、存在性
  - `captureToImage()`: 捕获组件或全屏截图，用于视觉回顾或对比
- **理由**: 原生支持最好，运行速度快，能够访问组件内部状态，比图像识别(Sikuli)或坐标点击更稳定。

---

### ADR-004: 多语言支持策略 (2026-01-07, 已更新)

**背景**:
- 目标用户群首先是中国用户（简体+繁体），未来扩展全球。
- 代码开发规范要求英文优先。
- 需要翻译人员可以只改文件不改代码。
- 保持 Pure Kotlin，不依赖 Java 资源系统。

---

#### 实现方案：JSON 资源文件 + kotlinx.serialization

**文件结构**:
```
commonMain/resources/
├── strings_en.json      ← 英文 (开发默认)
├── strings_zh_CN.json   ← 简体中文
└── strings_zh_TW.json   ← 繁体中文
```

**JSON 文件示例** (strings_en.json):
```json
{
  "app_title": "Catalytic",
  "start_test": "Start",
  "stop_test": "Stop",
  "core_component_missing": "Core component missing",
  "select_working_directory": "Select Working Directory"
}
```

**Data Class**:
```kotlin
@Serializable
data class AppStrings(
    val app_title: String,
    val start_test: String,
    val stop_test: String,
    val core_component_missing: String,
    val select_working_directory: String
)
```

**加载逻辑**:
```kotlin
object StringsLoader {
    fun load(lang: String): AppStrings {
        val fileName = "strings_$lang.json"
        val json = readResourceAsText(fileName)
        return Json.decodeFromString(json)
    }
}
```

**CompositionLocal 注入**:
```kotlin
val LocalStrings = staticCompositionLocalOf { StringsLoader.load("en") }

@Composable
fun App() {
    val config = ... // 从 config.json 读取
    val strings = remember(config.language) { StringsLoader.load(config.language) }
    
    CompositionLocalProvider(LocalStrings provides strings) {
        MainScreen()
    }
}
```

**使用**:
```kotlin
val strings = LocalStrings.current
Text(strings.start_test)
```

---

#### 支持的语言

| 代码 | 语言 | 文件 |
|------|------|------|
| `en` | English | strings_en.json |
| `zh_CN` | 简体中文 | strings_zh_CN.json |
| `zh_TW` | 繁體中文 | strings_zh_TW.json |

---

#### 翻译工作流

1. 开发者在 `AppStrings` Data Class 中添加新字段（英文 key）
2. 更新 `strings_en.json`（英文默认值）
3. 翻译人员复制到 `strings_zh_CN.json` 和 `strings_zh_TW.json`，替换为对应翻译
4. 编译时自动验证 JSON 结构与 Data Class 一致

---

### ADR-005: Host 透明化与生命周期管理 (2026-01-07, 已更新)

> ⚠️ **重要**: 本 ADR 已于 2026-01-07 完全重写。

**背景**:
- "Host" 是内部技术术语，对外不应暴露。
- 用户只需要知道"Catalytic"这一个应用。

---

#### 核心原则

1. **用户感知**: 用户只看到一个 "Catalytic" 应用。Host 是后台服务，完全透明。
2. **错误提示**: 如果 Host 启动失败或找不到，提示 "Core component missing" / "核心组件缺失"。
3. **生命周期**: UI 负责 Host 的启动和关闭。UI 退出时，Host 也应该终止。

---

#### Host 定位逻辑

```kotlin
// 伪代码
val appDir = System.getProperty("compose.application.resources.dir")
    ?: System.getProperty("user.dir")

val hostPath = Paths.get(appDir, "host", "Catalytic.Host")

if (!hostPath.exists()) {
    showError("Core component missing")
    exitProcess(1)
}
```

---

#### Host 启动参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `--port` | gRPC 监听端口 | `--port 5000` |
| `--work-dir` | 工作目录路径 | `--work-dir "/Users/liuzhe/Data"` |

Host 不再自己读取任何配置文件。一切配置由 UI 通过命令行传递。

---

### ADR-006: LogManager 独立模块 (2026-01-08)

**背景**:
- 日志系统应该是独立模块，不依赖 UI 或 ViewModel
- 方便未来 UI 改造时灵活对接
- 需要区分系统日志和操作日志

---

#### 架构设计

```kotlin
object LogManager {
    // 系统日志 (Host 启动、gRPC 请求等)
    val systemLogs: StateFlow<List<String>>
    
    // 操作日志 (开始/停止/PASS/FAIL)
    val operationLogs: StateFlow<List<String>>
    
    fun addLog(message: String)    // 系统日志
    fun addOpLog(message: String)  // 操作日志
}
```

#### 设计要点

| 方面 | 设计 |
|------|------|
| UI 显示限制 | 500 条（节省内存） |
| 持久化 | 全量保存到 `logs/` 目录 |
| 订阅方式 | Flow |
| 模块依赖 | 无（可被任意组件使用） |

---

#### 开发规范 (重要)

1. **Pure Kotlin**: 尽可能避免 Java 生态库，保持 Kotlin Multiplatform 兼容性。
2. **例外**: `Wire` (gRPC) 等必要依赖可以使用，但需明确标注。
3. **心态**: 像开发 Android App 一样开发这个 Desktop 应用。

## 避坑指南

### 1. Compose 程序目录获取
```kotlin
// ❌ 错误: 返回 JVM 目录
System.getProperty("user.dir")

// ✅ 正确: 返回打包后的程序目录
System.getProperty("compose.application.resources.dir")
    ?: System.getProperty("user.dir")
```

### 2. config.json 不生效
**原因**: 旧的 `GetRootDirectory()` 返回上一级目录
**解决**: ADR-001 两级配置机制

### 3. gRPC 连接失败
**检查项**:
- Host 是否在运行？ `lsof -i :5000`
- 防火墙是否阻止？
- UI 是否连接正确端口？(默认 5000)

### 4. Engine 库加载失败
**现象**: `libcatalytic.dylib not found`
**解决**: 
- 确保库在程序目录或系统路径
- macOS 需要 `@rpath` 或绝对路径

---

### ADR-007: Role 概念废弃与 UI 优先开发策略 (2026-01-08)

**背景**:
- 原有 `Role` 概念（设备角色）被发现**毫无价值**，徒增复杂度。
- 槽位绑定需要支持：一个类型可绑定多个设备。
- 全栈联调发现问题定位困难，需要分层验证。

**决策**:
1. **删除 Role**: 从 Model、ViewModel、Repository、UI 全部移除。
2. **开发策略调整**: 采用 **UI 优先** 模式。
   - Phase A: UI 数据正确性 (MockRepository 验证)
   - Phase B: Host 数据正确性 (Host 日志验证)
   - Phase C: Engine 数据正确性 (硬件联调)

**影响**:
- `FlowDefinitionTab` 不再有"角色管理"面板。
- 测试步骤 `targetDevice` 应改为引用 `deviceTypeId` + `commandId`。

---

## 核心业务逻辑

### 槽位状态机
```
idle ──start──► running ──complete──► completed (pass/fail)
  ▲                │
  │              pause
  │                ▼
  └──stop───── paused ◄──resume
```

### 设备绑定 (2026-01-08 更新)
- 每个槽位可绑定**多个设备类型**
- 每个类型可绑定**多个设备实例** (新增)
- 数据结构:
  ```kotlin
  Map<SlotId, Map<DeviceTypeId, List<DeviceId>>>
  ```
- UI: 点击"添加" → 弹窗选择类型 → 选择设备 → 确认

### DeviceType 结构 (2026-01-08 更新)
```kotlin
data class DeviceTypeUiState(
    val id: String,
    val name: String,
    val transport: String,
    val protocol: String,
    val devices: List<DeviceUiState>,
    val commands: List<CommandUiState>  // 新增
)

data class CommandUiState(
    val id: String,
    val name: String,       // "读取电压"
    val payload: String,    // "MEAS:VOLT?"
    val parseRule: String?, // 解析规则
    val timeoutMs: Int
)
```

### 测试步骤结构 (2026-01-09 更新)

> 已通过 MockRepository 和单元测试验证

```json
{
  "step_id": 1,
  "step_name": "电压检测",
  "execution_mode": "engine_controlled",  // 或 "host_controlled" / "calculation"
  "engine_task": {
    "device_type_id": "DMM",
    "command_id": "read_voltage"
  },
  "input_variables": [],      // 计算步骤使用
  "variables": {"voltage": "NUMBER"},
  "check_type": "builtin",
  "check_rule": {}
}
```

### SlotStatus Proto (2026-01-09 更新)

```protobuf
message SlotStatus {
    uint32 slot_id = 1;
    string status = 2;
    uint32 current_step_index = 3;
    uint32 total_steps = 4;
    uint64 elapsed_ms = 5;
    string sn = 6;
    // 新增字段
    string current_step_name = 7;
    string current_step_desc = 8;
    repeated SlotVariable variables = 9;
}

message SlotVariable {
    string name = 1;
    string value = 2;
    string unit = 3;
    bool is_passing = 4;
}
```

### Phase A 完成总结 (2026-01-09)

| 完成项 | 说明 |
|--------|------|
| UI 输出数据契约 | DeviceType, Step, SlotBinding JSON 格式已定义 |
| UI 接收数据契约 | SlotStatus Proto 已扩展 |
| 自动化测试 | MappersTest 13/13 通过 |
| 移除字段 | deviceIndex, ActionType, Role |
| 新增字段 | inputVariables, CALCULATION mode |

---

## Proto API 速查

### 测试控制
| 方法 | 功能 |
|------|------|
| `StartTest(slot_id)` | 启动指定槽位测试 |
| `StopTest(slot_id)` | 停止测试 |
| `PauseTest(slot_id)` | 暂停测试 |
| `ResumeTest(slot_id)` | 继续测试 |

### ADR-008: Command RPC 独立设计 (2026-01-09) [已废弃]

> **状态更新 (2026-01-10)**: 被 ADR-009 取代。虽然独立的 `CreateCommand`/`CreateDevice` RPC 仍然保留用于特殊情况，但它们不再是 UI 保存数据的主要方式。

~~**决策**:~~
~~- 添加独立的 `Command` 消息和 `CreateCommand` RPC~~

---

### ADR-009: 嵌套 Proto 与批量保存 (2026-01-10)

**背景**:
- 之前的独立设计导致 UI 必须进行 N+M 次 RPC 调用才能保存一套配置，效率极低且容易出现部分失败。
- UI 数据模型 (`DeviceTypeUiState`) 本身是嵌套结构，独立 RPC 需要手动拆分和重组，容易产生数据不一致 Bug（例如 `ListDeviceTypes` 忘记加载 Commands）。

**决策**:
1. **Proto 嵌套化**: 修改 `DeviceType` 消息，直接包含 `repeated Device devices` 和 `repeated Command commands`。
2. **批量保存 (Batch Save)**: UI 通过单次 `UpdateDeviceType` RPC 发送完整的 DeviceType 树（含所有子设备和命令）。
3. **Rust 引擎对接**: Host 负责将这个嵌套 Proto 序列化为完整的 JSON，调用 Engine 的 `AddDeviceType` 一次性存储，利用 Engine 内部的事务机制。

**优势**:
- **原子性**: 整个 Type 的更新要么全成功，要么全失败。
- **性能**: RPC 调用次数从 O(N) 降为 O(1)。
- **一致性**: Proto 结构与 UI 模型和业务逻辑天然对齐。

---

## Proto API 速查 (Updated 2026-01-10)

### 测试控制
| 方法 | 功能 |
|------|------|
| `StartTest(slot_id)` | 启动指定槽位测试 |
| `StopTest(slot_id)` | 停止测试 |
| `PauseTest(slot_id)` | 暂停测试 |
| `ResumeTest(slot_id)` | 继续测试 |

### 配置管理
| 方法 | 功能 | 说明 |
|------|------|------|
| `UpdateDeviceType(type)` | **更新设备类型 (Batch)** | **推荐**。同时保存 Type 及其下属 Devices/Commands |
| `CreateDeviceType(type)` | 创建设备类型 | 同 Update，支持嵌套创建 |
| `CreateDevice(device)` | 创建设备实例 | **Legacy**。仅用于单独添加设备 |
| `CreateCommand(command)` | 创建命令定义 | **Legacy**。仅用于单独添加命令 |
| `SetSlotBinding(binding)` | 设置槽位绑定 | |
| `AddTestStep(json)` | 添加测试步骤 | |

### 状态查询
| 方法 | 功能 | 说明 |
|------|------|------|
| `ListDeviceTypes()` | **列出设备类型 (Tree)** | 返回所有 Types 及其嵌套的 Devices/Commands |
| `GetSlotStatus(slot_id)` | 获取槽位状态 | |
| `Subscribe(topics)` | 订阅事件流 | |

---

> 已验证通过的 UI 功能：
> - DeviceType/Device/Command 创建和发送
> - SlotBinding 多设备绑定
> - Step 序列化（待 Engine 修复后验证）

---

### ADR-010: Host 事件驱动架构 (2026-01-12)

**背景**:
- 原始设计中使用 gRPC Server Streaming + Polling (`while(!cancelled) { delay(500) }`) 获取 Engine 状态。
- 问题：CPU 浪费，实时性差（500ms 延迟），Engine 内部变化无法及时推送。

**决策**:
1. **Engine Push**: Rust Engine 实现 `push_ui_update`，通过 callback (`OnUIUpdate`) 推送 JSON 快照。
2. **Callbacks**: C# `Engine.cs` 暴露 `SnapshotReceived` 事件。
3. **Channel Bridge**: `HostGrpcService` 使用 `System.Threading.Channels.Channel<SlotStatus>` 作为缓冲。
    - `Event Handler` (Producer): 收到 Engine 事件 -> 解析 JSON -> `channel.Writer.WriteAsync`
    - `gRPC Method` (Consumer): `await channel.Reader.ReadAllAsync()` -> `responseStream.WriteAsync`

**收益**:
- **Zero Latency**: 状态变化即时推送。
- **Zero Polling**: 空闲时 CPU 占用几乎为零。
- **Backpressure**: Channel 天然支持背压，防止 UI 处理不过来。

---

### ADR-011: 严格类型安全与 FFI 错误传播 (2026-01-12)

**背景**:
- C# `long` (signed 64) 与 Rust `u64` (unsigned 64) 在 `TaskId` 上存在隐式不匹配。
- 早期代码直接忽略了 Rust FFI 函数 (`NativeMethods.SubmitResult`) 的返回值 (`int`)。

**决策**:
1. **统一类型**: Host 端全线（EventArgs, Delegates, Methods）使用 `ulong` 对接 `TaskId`。
2. **Explicit Marshalling**: `NativeMethods` 显式声明 `StringMarshalling = StringMarshalling.Utf8` ( .NET 7+ LibraryImport)。
3. **Error Propagation**: 所有调用的 FFI 方法必须检查返回值：
    ```csharp
    var result = NativeMethods.SubmitResult(...);
    if (result != 0) throw new InvalidOperationException(...);
    ```

**原则**:
- **Fail Fast**: 如果 Host 传递了错误数据给 Engine，或者 Engine 处于非法状态，Host 必须立即抛出异常，而不是让错误静默发生导致后续逻辑诡异。

---

### ADR-012: MVP 完成与代码质量优化 (2026-01-13)

**背景**:
- 完成 UI (Phase A)、Host (Phase B)、Engine (Phase C) 三阶段验证。
- 发现测试代码质量问题：Engine 的 `e2e_test.rs` 和 `integration_test.rs` 无法编译（import 路径错误）。
- Host 和 Engine 代码存在大量重复模式。

**决策**:

1. **清理无效测试**: 删除无法编译的 Engine 测试文件，保留唯一能运行的 `integration_full_flow.rs`。

2. **Engine FFI 优化**:
   - 提取 `send_slot_control()` 和 `for_all_slots()` 内部辅助函数
   - 统一使用 `str_from_ptr()` 和 `parse_json_from_ptr()` 替代手写 CStr 解析
   - **收益**: 减少 ~67 行代码，提高可维护性

3. **Host gRPC 优化**:
   - 添加 `ExecuteEngineAction()` 辅助方法，封装 try-catch-log 模式
   - 8 个函数 (Start/Pause/Resume/StopTest + 4 个 TestStep 函数) 简化为单行调用
   - **收益**: 减少 ~59 行代码，消除 CS0168 警告

**测试验证**:
```bash
# Engine: 1 passed, 0 warnings
cargo test --test integration_full_flow

# Host: 4 passed, 0 warnings
dotnet test
```

**MVP 定义**:
- UI 可配置设备类型、设备实例、测试步骤
- UI 可绑定槽位
- 点击 Start Test → Engine 执行测试 → UI 显示进度和结果
- 所有自动化测试全部通过

---

### MVP 完成清单 (2026-01-13)

| 组件 | 功能 | 状态 |
|------|------|------|
| **UI** | 设备类型/实例/命令配置 | ✅ |
| | 测试步骤编辑 | ✅ |
| | 槽位绑定 | ✅ |
| | 测试控制 (Start/Stop/Pause/Resume) | ✅ |
| | 状态实时显示 | ✅ |
| **Host** | gRPC 服务 | ✅ |
| | Engine FFI 封装 | ✅ |
| | Event-Driven 架构 | ✅ |
| **Engine** | 测试执行引擎 | ✅ |
| | 变量存储与检查 | ✅ |
| | 槽位状态管理 | ✅ |

### KMP 开发避坑指南 (2026-01-13)

#### 1. 字符串格式化
**问题**: `String.format()` 在 kotlin-common 中不存在。
**解决**:
- 使用 `.replace("%s", value)` (简单场景)
- 使用 Kotlin String Templates `"$value"`
- 避免在 commonMain 中使用 `String.format`

#### 2. FileKit 路径访问
**问题**: `FileKit` 的 `PlatformDirectory` 属性在不同版本/平台由于 expect/actual 差异极其混乱 (`file`, `path` 等属性可能无法解析)。
**解决**: 使用 `it.toString()` 作为防御性回退，通常能获取正确路径字符串。

#### 3. Compose Resources for KMP
**问题**: `expect/actual` 资源读取手动实现繁琐且易错。
**建议**: 迁移到 `compose.components.resources` 官方库（Compose 1.6+），支持异步 `readBytes()`。但在同步加载场景（如 `StringsLoader`）仍需谨慎处理。

---

### ADR-013: 插件架构增强与设备类型简化 (2026-01-15)

**背景**:
- 旧架构中 DeviceType 必须选择 `Transport`(Enum) 和 `Protocol`(Enum)，导致扩展新协议必须修改 Engine/Proto 核心代码。
- 业务插件 (TaskHandler) 无法直接调用通讯插件 (ProtocolDriver)，导致自动化流程断裂。
- SDK 命名 `Catalytic.Contracts` 略显冗长，且部分功能缺失。

**决策**:
1. **统一标识**: 废弃 `Transport/Protocol` 枚举，DeviceType 仅保留 `plugin_id`。所有通讯能力由插件定义，Engine 仅透传 ID。
2. **SDK 重构**: 
   - 重命名为 `CatalyticKit`。
   - 新增 `IPluginContext.GetProtocolDriver()` 实现插件间互调。
   - 新增 `IPluginContext.PushEvent()` 实现设备主动推数。
3. **标准化动作**: 引入 `CommAction` 枚举 (Connect, Send, Read...) 和 `CommunicatorExtensions`，降低插件开发门槛。

**收益**:
- **解耦**: 新增协议（如 Bluetooth, CANRequest）无需修改 Engine/Host 核心代码。
- **灵活**: 业务插件可复用现有的通讯驱动（如烧录插件调用 Serial 驱动）。
- **简洁**: UI 只有一个下拉框选择插件，不再需要两级选择。

---

### ADR-014: FFI Submit Gap 与 AI 污染清理 (2026-01-15)

**背景**:
- 审计 FFI 发现 C# Host 声明了 `cat_engine_submit_result/timeout/error`，但 Rust Engine **完全没有实现**。
- 这导致 Engine 发起任务回调后，Host 无法将结果返回给 Engine，测试永远无法完成。
- Rust 集成测试绕过了 FFI（直接调用 `TaskRegistry.submit()`），因此未能发现此缺陷。
- 发现 Gemini 添加的无用代码：`cat_engine_get_variable_json` (Rust)、`param_schema` 字段 (Rust)。

**问题根源**:
1. **测试策略缺陷**: Rust 测试用 mock 回调直接调用内部方法，C# 测试只测配置 CRUD，从未触发完整执行流程。
2. **AI 代码审核不足**: Gemini 添加的代码未经人工确认即合并。

**决策**:
1. **实现缺失 FFI**: 创建 `src/ffi/submit.rs`，实现三个 submit 函数。
2. **清理污染代码**: 删除 `cat_engine_get_variable_json` (C#) 和 `param_schema` (Rust)。
3. **新增真正的 E2E 测试**: `tests/e2e_test.rs` 验证 Engine-Host 回调往返。
4. **完善 UI**: 实现 CheckRuleSection，替换"开发中"占位符。

**收益**:
- FFI 现在 100% 匹配：37 个 C# 声明 ↔ Rust 导出。
- 所有测试通过：Engine 33/33, Host 12/12。
- CheckRule UI 支持 4 种检查类型（范围、阈值、包含、表达式）。

**教训**:
- **AI 生成代码必须审计**: 尤其关注 FFI 边界一致性。
- **测试必须覆盖完整流程**: 不能依赖绕过关键层的 mock。

---

### ADR-015: 全局忽略构建产物 (2026-01-18)

**背景**:
- C# 构建会产生巨大的 `.pdb` (调试符号) 文件。
- `publish/` 和 `publish_output/` 目录包含已编译的二进制产物，不应进入版本控制。
- 发现即便在子目录中有 `.gitignore`，部分深度嵌套的产物仍被 Git 监控。

**决策**:
1. **全局忽略**: 在根目录 `.gitignore` 中添加 `*.pdb`、`publish/`、`publish_output/`。
2. **强制清理**: 修改后必须执行 `git rm -r --cached .` 以确保已追踪的存量文件被正确剔除。

**收益**:
- 减小代码库体积。
- 避免开发者的更改列表中出现无关的构建中间件产物。
- 保持 Git 状态整洁。

