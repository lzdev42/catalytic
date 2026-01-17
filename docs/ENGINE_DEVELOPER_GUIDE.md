# Catalytic Engine 开发者指南

> **版本**: 0.1.0  
> **更新日期**: 2024-12-28  
> **状态**: 核心功能完成，已通过端到端测试

---

## 目录

1. [架构概述](#1-架构概述)
2. [快速开始](#2-快速开始)
3. [核心概念](#3-核心概念)
4. [FFI 接口详解](#4-ffi-接口详解)
5. [数据结构](#5-数据结构)
6. [回调机制](#6-回调机制)
7. [执行流程](#7-执行流程)
8. [C# 集成示例](#8-c-集成示例)
9. [错误处理](#9-错误处理)

---

## 1. 架构概述

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         Host 应用程序                            │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────────┐ │
│  │ VISA 通讯   │  │   UI 界面   │  │ 设备驱动 (SCPI/Modbus/…) │ │
│  └──────┬──────┘  └──────┬──────┘  └────────────┬─────────────┘ │
└─────────┼────────────────┼──────────────────────┼───────────────┘
          │                │                      │
          │    FFI 接口    │                      │
┌─────────▼────────────────▼──────────────────────▼───────────────┐
│                    Catalytic Engine (Rust)                       │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                        CatEngine                            │ │
│  │   ┌─────────┐  ┌─────────────┐  ┌────────────────────────┐  │ │
│  │   │ Slots   │  │ TestSteps   │  │ DeviceTypes/Instances  │  │ │
│  │   │ 0..N    │  │             │  │                        │  │ │
│  │   └────┬────┘  └──────┬──────┘  └────────────────────────┘  │ │
│  └────────┼──────────────┼─────────────────────────────────────┘ │
│           │              │                                       │
│  ┌────────▼──────────────▼─────────────────────────────────────┐ │
│  │                      Executor                               │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │ │
│  │  │ 任务分发 │→ │ 数据解析 │→ │ 检查规则 │→ │ 状态机更新  │ │ │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 核心设计原则

| 原则 | 说明 |
|------|------|
| **引擎是大脑** | Engine 负责决策、校验、流程控制 |
| **Host 是四肢** | Host 负责设备通讯、物理操作 |
| **槽位隔离** | 每个 Slot 独立执行，互不干扰 |
| **异步非阻塞** | 使用 Tokio 运行时，UI 不卡顿 |

---

## 2. 快速开始

### 最小示例 (C 伪代码)

```c
// 1. 创建引擎（4 槽位）
CatEngine* engine = cat_engine_create(4);

// 2. 配置设备类型（告诉引擎有哪些仪器）
const char* dmm_type = "{\"name\": \"万用表\", \"transport\": \"tcp\", \"protocol\": \"scpi\"}";
cat_engine_add_device_type_json(engine, "dmm", dmm_type);

// 3. 添加设备实例（告诉引擎每种仪器有几台，地址是什么）
const char* dmm_instance = "{\"name\": \"DMM_1\", \"address\": \"TCPIP0::192.168.1.101::INSTR\"}";
cat_engine_add_device_instance_json(engine, "dmm", dmm_instance);

// 4. 配置测试步骤
const char* step = "{\"step_id\": 1, \"step_name\": \"测电压\", \"execution_mode\": \"engine_controlled\", "
                   "\"engine_task\": {\"target_device\": \"dmm\", \"action_type\": \"query\", "
                   "\"payload\": \"MEAS:VOLT:DC?\", \"timeout_ms\": 3000}}";
cat_engine_add_step_json(engine, step);

// 5. 绑定槽位设备（告诉 Slot 0 使用 DMM_1 这台万用表）
cat_engine_set_slot_binding_json(engine, 0, "{\"dmm\": \"DMM_1\"}");

// 6. 注册回调（引擎通过回调告诉你要发什么指令）
cat_engine_register_engine_task_callback(engine, my_callback, user_data);
cat_engine_register_ui_callback(engine, ui_callback, user_data);

// 7. 启动测试
cat_engine_start_slot(engine, 0);

// 8. 清理
cat_engine_destroy(engine);
```

> **重要**：`my_callback` 是你自己实现的函数，引擎会调用它来发送设备指令。

---

## 3. 核心概念

### 3.1 执行模式

| 模式 | 说明 | 使用场景 |
|------|------|----------|
| **EngineControlled** | 引擎发 SCPI 指令，Host 转发 | 标准仪器控制 |
| **HostControlled** | 引擎只给任务名，Host 自行处理 | 复杂业务逻辑、烧录 |

### 3.2 槽位 (Slot)

每个槽位代表一个独立的测试工位，拥有：
- 独立的设备绑定
- 独立的变量池
- 独立的状态机
- 独立的执行线程

### 3.3 测试步骤 (TestStep)

测试步骤定义了：
- 执行什么动作 (Send/Query/Wait)
- 对哪个设备操作 (target_device)
- 如何解析响应 (ParseRule)
- 如何检查结果 (CheckRule)
- 通过/失败后跳转到哪 (next_on_pass/fail)

---

## 4. FFI 接口详解

### 4.1 生命周期

| 函数 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `cat_engine_create` | `slot_count: u32` | `*mut CatEngine` | 创建引擎 |
| `cat_engine_destroy` | `engine` | - | 销毁引擎 |
| `cat_engine_free_json` | `json: *mut c_char` | - | 释放 JSON 字符串 |
| `cat_engine_get_slot_count` | `engine` | `i32` | 获取槽位数 |

### 4.2 控制

| 函数 | 说明 |
|------|------|
| `cat_engine_start_slot(engine, slot_id)` | 启动单槽位（非阻塞） |
| `cat_engine_start_all_slots(engine)` | 启动所有槽位 |
| `cat_engine_pause_slot(engine, slot_id)` | 暂停 |
| `cat_engine_resume_slot(engine, slot_id)` | 恢复 |
| `cat_engine_stop_slot(engine, slot_id)` | 停止 |
| `cat_engine_skip_current_step(engine, slot_id)` | 跳过当前步骤 |

### 4.3 回调注册

| 函数 | 回调类型 |
|------|----------|
| `cat_engine_register_engine_task_callback` | 设备指令回调 |
| `cat_engine_register_host_task_callback` | Host 任务回调 |
| `cat_engine_register_ui_callback` | UI 更新回调 |

### 4.4 结果提交

| 函数 | 说明 |
|------|------|
| `cat_engine_submit_result(engine, slot_id, task_id, data, len)` | 提交成功结果 |
| `cat_engine_submit_timeout(engine, slot_id, task_id)` | 提交超时 |
| `cat_engine_submit_error(engine, slot_id, task_id, msg)` | 提交错误 |

---

## 5. 数据结构

### 5.1 TestStep (测试步骤)

```json
{
  "step_id": 1,
  "step_name": "检测供电电压",
  "execution_mode": "engine_controlled",
  "skip": false,  // 预设跳过（true 表示始终跳过此步骤）
  "engine_task": {
    "target_device": "dmm",
    "action_type": "query",
    "payload": "MEAS:VOLT:DC?",
    "timeout_ms": 3000,
    "parse_rule": { "type": "number" }
  },
  "check_type": "builtin",
  "check_rule": {
    "template": "range_check",
    "min": 3.2,
    "max": 3.4,
    "include_min": true,
    "include_max": true
  },
  "save_to": "voltage",
  "next_on_pass": 2,
  "next_on_fail": null
}
```

> **预设跳过**：当 `skip: true` 时，该步骤不会执行，直接标记为 `Skipped` 并按 `next_on_pass` 继续。适用于 DUT 有问题时临时禁用某个测试项。

### 5.2 CheckRule (检查规则)

| 模板 | 参数 | 示例 |
|------|------|------|
| `range_check` | `min`, `max`, `include_min`, `include_max` | `3.2 <= x <= 3.4` |
| `threshold` | `variable`, `operator`, `value` | `x > 3.0` |
| `compare` | `var_a`, `operator`, `var_b` | `a == b` |
| `contains` | `variable`, `substring` | `"OK" in response` |
| `expression` | `expr` | 自定义表达式 |

**operator 取值**: `>`, `<`, `>=`, `<=`, `==`, `!=`

### 5.3 ParseRule (解析规则)

| 类型 | 说明 | 示例 |
|------|------|------|
| `number` | 提取数字（含科学计数法） | `"3.32V"` → `3.32` |
| `regex` | 正则提取 | `pattern: "(\d+)"` |
| `json` | JSON 路径 | `path: "$.data.value"` |

### 5.4 SlotStatus (槽位状态)

```
Idle → Running → Completed
         ↓ ↑
       Paused
         ↓
       Error
```

---

## 6. 回调机制

### 6.0 关键概念澄清

**device_address 从哪来？**

Engine 回调中给出的 `device_address` 是**已解析的物理地址**，例如 `"TCPIP0::192.168.1.101::INSTR"` 或 `"COM3"`。这个地址的来源流程是：

```
1. 设备扫描（Host 负责）
   └─ 使用 VISA Find Resources 或其他方式发现设备
   └─ 获得物理地址列表，如 "TCPIP0::192.168.1.101::INSTR"

2. 设备配置（用户在 UI 操作）
   └─ 创建设备实例：DMM_1 → "TCPIP0::192.168.1.101::INSTR"
   └─ 通过 FFI 告诉 Engine

3. 槽位绑定（用户在 UI 操作）
   └─ 槽位 0 的 "dmm" 使用 "DMM_1" 这台设备

4. 测试运行（Engine 负责映射）
   └─ 步骤配置写 target_device = "dmm"
   └─ Engine 查表：dmm → DMM_1 → "TCPIP0::192.168.1.101::INSTR"
   └─ 回调时直接给出物理地址
```

**职责边界**

| 谁 | 做什么 | 不做什么 |
|----|--------|----------|
| **Engine** | 存储设备配置、执行逻辑名→物理地址映射、管理测试流程 | 设备扫描、设备通讯 |
| **Host** | 设备扫描（VISA）、设备通讯、执行回调指令 | 理解测试逻辑、判断 PASS/FAIL |
| **插件** | 实现具体协议的通讯（SCPI/Modbus/...） | 管理设备地址映射 |

> **简单理解**：Engine 是调度员，告诉 Host "往 192.168.1.101 发这条指令"；Host/插件只管照做，不问为什么。

---

### 6.1 EngineTaskCallback

引擎发送设备指令时调用。

```c
typedef int (*EngineTaskCallback)(
    uint32_t slot_id,       // 槽位 ID
    uint64_t task_id,       // 任务 ID（用于 submit_result）
    const char* device_type,    // 设备类型 ("dmm")
    const char* device_address, // 实际地址 ("TCPIP0::192.168.1.101::INSTR")
    const char* protocol,       // 协议 ("scpi")
    const char* action_type,    // 动作 ("query")
    const uint8_t* payload,     // 指令内容
    uint32_t payload_len,
    uint32_t timeout_ms,
    void* user_data
);
```

**Host 职责**：
1. 根据 `device_address` 和 `protocol` 发送指令
2. 等待响应（不超过 `timeout_ms`）
3. 调用 `cat_engine_submit_result` 返回数据

**返回值**：返回 `0` 表示成功接收任务，非 `0` 表示拒绝（引擎会记录错误）。

> **注意**：`payload` 是原始字节数组，通常是 UTF-8 编码的文本指令。JSON 配置中写成字符串形式，FFI 传递时是 `uint8_t*`。

### 6.2 时序图

```
  Engine                    Host                    Device
    │                        │                        │
    │──EngineTaskCallback───→│                        │
    │  (slot_id, task_id,    │                        │
    │   device_address,      │──SCPI Command─────────→│
    │   payload)             │                        │
    │                        │                        │
    │                        │←──Response─────────────│
    │                        │                        │
    │←─submit_result─────────│                        │
    │  (task_id, data)       │                        │
    │                        │                        │
    │  处理数据              │                        │
    │  更新状态              │                        │
    │                        │                        │
    │──UICallback───────────→│                        │
    │  (json snapshot)       │                        │
```

---

## 7. 执行流程

### 7.1 步骤执行流程

```
TestStep 配置
     │
     ▼
┌───────────────┐
│ 解析设备绑定 │  → 获取真实地址 (device_bindings)
└───────────────┘
     │
     ▼
┌───────────────┐
│ 调用 Callback │  → Host 执行设备操作
└───────────────┘
     │
     ▼
┌───────────────┐
│ 等待 Result   │  → 带超时保护
└───────────────┘
     │
     ▼
┌───────────────┐
│ 解析响应     │  → ParseRule (Number/Regex/JSON)
└───────────────┘
     │
     ▼
┌───────────────┐
│ 存储变量     │  → save_to → VariablePool
└───────────────┘
     │
     ▼
┌───────────────┐
│ 执行检查     │  → CheckRule → PASS/FAIL
└───────────────┘
     │
     ▼
┌───────────────┐
│ 决定下一步   │  → next_on_pass / next_on_fail
└───────────────┘
```

### 7.2 控制信号

| 信号 | 效果 |
|------|------|
| **Stop** | 立即退出，标记 Completed |
| **Pause** | 暂停执行（当前步骤完成后生效），等待 Resume |
| **Resume** | 恢复执行 |
| **SkipCurrent** | 跳过当前步骤（运行时跳过） |

### 7.3 两种跳过方式

| 类型 | 触发方式 | 使用场景 |
|------|----------|----------|
| **运行时跳过** | 暂停后发送 `SkipCurrent` 信号 | 临时跳过当前正在执行的步骤 |
| **预设跳过** | 配置 `skip: true` | DUT 有问题，某个测试项始终失败，供应商需要跳过该项继续测试 |

---

## 8. C# 集成示例

```csharp
public class CatalyticEngine : IDisposable
{
    // FFI 导入
    [DllImport("catalytic_engine")]
    private static extern IntPtr cat_engine_create(uint slotCount);
    
    [DllImport("catalytic_engine")]
    private static extern void cat_engine_destroy(IntPtr engine);
    
    [DllImport("catalytic_engine")]
    private static extern int cat_engine_start_slot(IntPtr engine, uint slotId);
    
    [DllImport("catalytic_engine")]
    private static extern void cat_engine_register_engine_task_callback(
        IntPtr engine, EngineTaskDelegate callback, IntPtr userData);
    
    [DllImport("catalytic_engine")]
    private static extern int cat_engine_submit_result(
        IntPtr engine, uint slotId, ulong taskId, byte[] data, uint len);

    // 回调委托
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    public delegate int EngineTaskDelegate(
        uint slotId, ulong taskId,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string deviceType,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string deviceAddress,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string protocol,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string actionType,
        IntPtr payload, uint payloadLen,
        uint timeoutMs, IntPtr userData);

    private IntPtr _engine;
    private EngineTaskDelegate _taskCallback;

    public void Initialize(uint slotCount)
    {
        _engine = cat_engine_create(slotCount);
        _taskCallback = OnEngineTask;
        cat_engine_register_engine_task_callback(_engine, _taskCallback, IntPtr.Zero);
    }

    private int OnEngineTask(uint slotId, ulong taskId, string deviceType,
        string deviceAddress, string protocol, string actionType,
        IntPtr payload, uint payloadLen, uint timeoutMs, IntPtr userData)
    {
        // 1. 读取 payload
        byte[] payloadBytes = new byte[payloadLen];
        Marshal.Copy(payload, payloadBytes, 0, (int)payloadLen);
        string command = Encoding.UTF8.GetString(payloadBytes);

        // 2. 发送 SCPI 指令（这是你自己实现的函数！使用 NI-VISA 或其他库）
        // 例如: visa.Write(deviceAddress, command); var response = visa.Read();
        byte[] response = SendScpiCommand(deviceAddress, command, timeoutMs);

        // 3. 提交结果
        cat_engine_submit_result(_engine, slotId, taskId, response, (uint)response.Length);
        return 0;
    }

    public void StartTest(uint slotId) => cat_engine_start_slot(_engine, slotId);

    public void Dispose() => cat_engine_destroy(_engine);
}
```

---

## 9. 错误处理

### 9.1 错误码

| 常量 | 值 | 说明 |
|------|-----|------|
| `SUCCESS` | 0 | 成功 |
| `ERR_INVALID_PARAM` | -1 | 参数无效 |
| `ERR_INVALID_STATE` | -2 | 状态无效 |
| `ERR_INTERNAL` | -100 | 内部错误 |

### 9.2 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `submit_result` 无效 | task_id 不匹配 | 使用 callback 传入的 task_id |
| 槽位启动失败 | 状态不是 Idle | 调用 reset 或检查状态 |
| 检查失败 | 值超出范围 | 检查 min/max 配置 |
| 超时 | Host 未及时响应 | 增加 timeout_ms 或检查通讯 |

---

## 附录：已验证功能清单

| 功能 | 测试覆盖 | 状态 |
|------|----------|------|
| 多槽位并行 | e2e_test | ✅ |
| 设备地址解析 | e2e_test | ✅ |
| 协议识别 | e2e_test | ✅ |
| 科学计数法解析 | test_b1 | ✅ |
| 范围检查（含边界配置） | test_b1, test_b2 | ✅ |
| 超时处理 | test_b3 | ✅ |
| 槽位隔离 | test_b4 | ✅ |
| 停止控制 | test_b5 | ✅ |
