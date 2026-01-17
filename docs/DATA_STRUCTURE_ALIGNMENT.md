# Catalyst 数据结构全栈对齐规范

**生效日期**: 2026-01-13
**版本**: 3.0 (MVP Complete)
**目标**: 统一 UI (Kotlin), Host (Proto, C#) 与 Engine (FFI JSON, Rust) 之间的数据字段映射，消除歧义。
**权威性**: 此文档为**唯一真理源**。所有代码实现必须严格遵守此文档定义。

---

## 1. 设备类型 (DeviceType)

**功能**: 定义一种设备的通用属性和指令集。

| 字段含义 | 必选 | UI Layer (Kotlin) | Host Layer (Proto) | Engine FFI JSON | 数据类型/说明 |
|----------|-----|-------------------|--------------------|-----------------|---------------|
| 唯一标识 | ✅ | `id: String` | `string id = 1` | `id` | 字符串 (如 `type_123`)。Engine 内部作为 DeviceTypeID |
| 显示名称 | ✅ | `name: String` | `string name = 2` | `name` | 字符串 (如 `Keysight 34401A`)。仅用于显示/日志 |
| 传输层 | ✅ | `transport: String` | `string transport = 3` | `transport` | 枚举字符串: `serial`, `tcp`, `usb` |
| 协议层 | ✅ | `protocol: String` | `string protocol = 4` | `protocol` | 枚举字符串: `scpi`, `modbus` |
| 插件ID | ⚪ | `plugin_id: String?` | `string plugin_id = 5` | `plugin_id` | 字符串 (可选)。若指定，强制使用该插件 |
| **设备列表** | ✅ | `devices: List` | **`repeated Device devices = 10`** | `devices` (Array) | **变更**: 嵌套于 DeviceType 中传输 |
| **命令列表** | ✅ | `commands: List` | **`repeated Command commands = 11`** | `commands` (Array) | **变更**: 嵌套于 DeviceType 中传输 |

> **注意 (Phase C 重构)**: Proto 已重构为嵌套结构。`ListDeviceTypes` 返回完整树，`UpdateDeviceType` 进行全量保存（Batch Save）。独立的 `CreateDevice`/`CreateCommand` 仅作辅助或 Legacy 用途。

---

## 2. 设备实例 (Device)

**功能**: 代表一个物理连接的设备。

| 字段含义 | 必选 | UI Layer (Kotlin) | Host Layer (Proto) | Engine FFI JSON | 数据类型/说明 |
|----------|-----|-------------------|--------------------|-----------------|---------------|
| 唯一标识 | ✅ | `id: String` | `string id = 1` | `id` | **Engine新增**: 必须存储 UI 生成的 UUID (如 `device_abc`) |
| 所属类型 | ✅ | (Parent Context) | `string device_type_id = 2` | (FFI 参数) | 关联的 DeviceType ID |
| 显示名称 | ✅ | `name: String` | `string name = 3` | `name` | **关键映射**: UI `name` -> Host `Name` -> Engine `name` (如 "万用表1") |
| 通讯地址 | ✅ | `address: String` | `string address = 4` | `address` | 字符串 (如 `COM3`, `192.168.1.10`) |

---

## 3. 命令 (Command)

**功能**: 定义一条可发送给设备的指令。Phase C 重点修复对象。

| 字段含义 | 必选 | UI Layer (Kotlin) | Host Layer (Proto) | Engine FFI JSON | 数据类型/说明 |
|----------|-----|-------------------|--------------------|-----------------|---------------|
| 唯一标识 | ✅ | `id: String` | `string id = 1` | `id` | 字符串 (如 `cmd_read_volt`) |
| 所属类型 | ✅ | (Parent Context) | `string device_type_id = 2` | (FFI 参数) | 关联的 DeviceType ID |
| 显示名称 | ✅ | `name: String` | `string name = 3` | `name` | 字符串 (如 `读取电压`) |
| 指令载荷 | ✅ | `payload: String` | `string payload = 4` | `payload` | 字符串。实际发送给设备的指令 (如 `MEAS:VOLT?`) |
| 解析规则 | ⚪ | `parseRule: String?`| `string parse_rule = 5`| `parse_rule` | 字符串 (可选)。Regex 或特定格式，用于从返回值提取数据 |
| 超时时间 | ✅ | `timeoutMs: Int` | `uint32 timeout_ms = 6`| `timeout_ms` | 整数 (ms)。默认 1000 |

---

## 4. 测试步骤 (TestStep)

**功能**: 定义测试流程中的一个原子操作。数据结构较为复杂，采用 JSON 透传模式。

### 顶层结构

| 字段含义 | 必选 | Engine FFI JSON | 数据类型/说明 |
|----------|-----|-----------------|---------------|
| 步骤ID | ✅ | `step_id` | u32 (1, 2, 3...) |
| 步骤名称 | ✅ | `step_name` | String |
| 执行模式 | ✅ | `execution_mode` | 枚举: `engine_controlled`, `host_controlled` |
| 引擎任务 | ⚪ | `engine_task` | EngineTask 对象 (execution_mode=engine_controlled 时必选) |
| Host任务 | ⚪ | `host_task` | HostTask 对象 (execution_mode=host_controlled 时必选) |
| 保存变量 | ⚪ | `save_to` | String - 将结果存储到此变量名 |
| 检查类型 | ✅ | `check_type` | 枚举: `none`, `builtin`, `external` |
| 检查规则 | ⚪ | `check_rule` | CheckRule 对象 (见下文) |
| 成功跳转 | ⚪ | `next_on_pass` | u32 - 成功后跳转到的步骤 ID |
| 失败跳转 | ⚪ | `next_on_fail` | u32 - 失败后跳转到的步骤 ID |
| 超时跳转 | ⚪ | `next_on_timeout` | u32 - 超时后跳转到的步骤 ID |
| 异常跳转 | ⚪ | `next_on_error` | u32 - 异常后跳转到的步骤 ID |
| 跳过 | ⚪ | `skip` | bool - true 表示始终跳过此步骤 |

### 4.1 engine_task (Engine Controlled 模式)

> **2026-01-13 更新**: 字段与代码对齐

```json
{
  "target_device": "DMM",        // 必选。目标设备类型名
  "action_type": "query",        // 必选。枚举: send, query, wait, loop
  "payload": "MEAS:VOLT?",       // 必选。发送给设备的指令 (字符串或字节数组)
  "timeout_ms": 3000,            // 必选。超时时间 (ms)
  "parse_rule": {                // 可选。解析规则
    "type": "number"             // 或 "regex" 带 pattern/group
  },
  "loop_max_iterations": 5,      // 可选。最大循环次数
  "loop_delay_ms": 100           // 可选。循环间隔 (ms)
}
```

**action_type 枚举值**:
| 值 | 说明 |
|----|------|
| `send` | 仅发送，不等待响应 |
| `query` | 发送并等待响应 |
| `wait` | 等待事件 |
| `loop` | 循环执行 |

### 4.2 host_task (Host Controlled 模式)

```json
{
  "task_name": "MyCustomTask",   // 必选。Host 端 Plugin 对应的方法名
  "timeout_ms": 5000,            // 必选。超时时间 (ms)
  "params": {"a": 1, "b": 2},    // 可选。传给 Plugin 的参数 (JSON 对象)
  "param_schema": {...}          // 可选。参数 Schema (用于 UI 表单生成)
}
```

### 4.3 parse_rule (解析规则)

**Number (数字提取)**:
```json
{"type": "number"}
```

**Regex (正则提取)**:
```json
{
  "type": "regex",
  "pattern": "([0-9.]+)V",
  "group": 1
}
```

### 4.4 check_rule (检查规则)

**RangeCheck (范围检查)**:
```json
{
  "template": "range_check",
  "min": 4.5,
  "max": 5.5,
  "include_min": true,
  "include_max": true
}
```

**Threshold (阈值比较)**:
```json
{
  "template": "threshold",
  "variable": "voltage",
  "operator": ">=",    // >, <, >=, <=, ==, !=
  "value": 3.3
}
```

**Contains (字符串包含)**:
```json
{
  "template": "contains",
  "variable": "status",
  "substring": "PASS"
}
```

---

## 5. 槽位绑定 (SlotBinding)

**功能**: 定义某个槽位上，特定设备类型对应的具体设备实例。

| 字段含义 | 必选 | UI Layer (Kotlin) | Host Layer (Proto) | Engine FFI JSON | 数据类型/说明 |
|----------|-----|-------------------|--------------------|-----------------|---------------|
| 槽位ID | ✅ | (Slot上下文) | `uint32 slot_id = 1` | (FFI 参数) | 整数 (0-indexed) |
| 绑定映射 | ✅ | `Map<TypeId, List<DevId>>` | `device_bindings` | Map<String, List<String>> | 键: DeviceTypeId, 值: DeviceId 列表 |

**Engine FFI JSON 示例**:
```json
{
  "type_multimeter": ["dev_agilent_1", "dev_fluke_2"],
  "type_powersupply": ["dev_ps_1"]
}
```

---

## 6. 槽位状态 (SlotStatus)

**功能**: 运行时反馈的状态信息。

| 字段含义 | Source | Proto Field | UI Model Field | 说明 |
|----------|--------|-------------|----------------|------|
| 槽位ID | Engine | `slot_id` | `slotId` | |
| 状态 | Engine | `status` | `status` | `idle`, `running`, `paused`, `error`, `completed` |
| 当前步骤序号 | Engine | `current_step_index` | `currentStepIndex` | 0-indexed |
| 当前步骤名 | Engine | `current_step_name` | `currentStepName` | **新字段**, 显示用 |
| 当前步骤描述 | Engine | `current_step_desc` | `currentStepDesc` | **新字段** |
| 总步骤数 | Engine | `total_steps` | `totalSteps` | |
| 耗时 | Engine | `elapsed_ms` | `elapsedMs` | |
| 序列号 | Engine | `sn` | `sn` | DUT 序列号 |
| 变量列表 | Engine | `variables` (List) | `variables: List` | 运行时变量值 |

**SlotVariable 结构**:
| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 变量名 |
| `value` | string | 值 (统一转为字符串) |
| `unit` | string | 单位 (可选) |
| `is_passing`| bool | 当前检查结果 |
