# Catalytic 实施路线图

## 项目总览
Catalytic 是低代码自动化测试平台，目标是让测试工程师无需写代码即可配置和执行测试。

---

## 🎉 MVP 完成 (2026-01-13)

> **状态**: MVP 理论上已完成，所有组件测试通过，代码已优化。

### 验证三阶段 - 全部完成
| 阶段 | 目标 | 验证方式 | 状态 |
|------|------|----------|------|
| **Phase A: UI** | UI 正确输出/解析数据 | MockRepository + 单元测试 | ✅ 已完成 |
| **Phase B: Host** | Host 正确解析 UI 数据，返回正确数据 | Host 日志 + gRPC 测试 | ✅ 已完成 |
| **Phase C: Engine** | Host ↔ Engine 数据正确 | 集成测试 | ✅ 已完成 |
| **Phase D: Plugin** | 插件系统增强 (Protocol/SDK) | 编译 + 单元测试 | ✅ 已完成 |

---

## ✅ Phase A 完成内容 (2026-01-09)

### UI → Host 数据输出
- [x] DeviceType JSON 序列化 (含 commands, instances)
- [x] Step JSON 序列化 (ENGINE_CONTROLLED / HOST_CONTROLLED / CALCULATION)
- [x] SlotBinding JSON 序列化 (多设备类型、多实例)
- [x] 移除 `deviceIndex` 字段 (Engine 默认使用第一个)
- [x] 添加 `inputVariables` 字段 (计算步骤选择前置变量)

### Host → UI 数据接收
- [x] Proto 扩展: `current_step_name`, `current_step_desc`, `variables`
- [x] 新增 `SlotVariable` 消息类型
- [x] `Mappers.mapSlotStatus()` 映射新字段
- [x] 单元测试 13/13 通过

---

## ✅ Phase B: Host-UI 交互验证 (2026-01-12 完成)

### 完成修复 (关键项)
- [x] **Event-Driven UI Refactor**: 废弃 Polling，改为 Engine Push -> Host Channel -> gRPC Stream 模式。
- [x] **Code Quality Optimization**:
    - [x] `TaskId` 全线统一为 `ulong`，与 Engine FFI 保持一致。
    - [x] FFI 调用增加返回值检查，杜绝静默失败。
    - [x] 消除 Host 侧 Magic Numbers。
- [x] **Host Self-Test Passed**: `MockIntegrationTest` 验证了配置下发、FFI 存取和数据回显。

---

## ✅ Phase C: Engine 联调 (2026-01-13 完成)

### 完成项
- [x] Engine 集成测试 (`integration_full_flow.rs`) 通过
- [x] Host 集成测试 (4/4 通过)
- [x] 删除无法编译的无效测试文件 (`e2e_test.rs`, `integration_test.rs`)

- [x] Host 集成测试 (4/4 通过)
- [x] 删除无法编译的无效测试文件 (`e2e_test.rs`, `integration_test.rs`)

---

## ✅ Phase D: 插件系统增强 (2026-01-15 完成)

### 完成项
- [x] **SDK 重命名**: `Catalytic.Contracts` → `CatalyticKit`
- [x] **设备类型简化**:
    - [x] 移除 `Transport`/`Protocol` 枚举，统一使用 `plugin_id`
    - [x] UI 实现插件下拉选择器
- [x] **协议增强**: `IPluginContext` 新增 `GetProtocolDriver` 和 `PushEvent`
- [x] **SDK 完善**: 新增 `CommAction` 和 `CommunicatorExtensions`

---

## ✅ 代码优化 (2026-01-13 完成)

### Engine (Rust)
| 文件 | 优化内容 | 减少行数 |
|------|----------|----------|
| `ffi/control.rs` | 提取 `send_slot_control` 和 `for_all_slots` 辅助函数 | -34 |
| `ffi/device.rs` | 使用 `str_from_ptr` 和 `parse_json_from_ptr` | -15 |
| `ffi/step.rs` | 使用 `parse_json_from_ptr` | -18 |
| **合计** | | **-67** |

### Host (C#)
| 文件 | 优化内容 | 减少行数 |
|------|----------|----------|
| `HostGrpcService.cs` | 添加 `ExecuteEngineAction` 辅助方法，简化 8 个函数 | -59 |
| | 消除 CS0168 编译警告 | |

---

## ✅ Phase E: FFI 修复 & UI 完善 (2026-01-15 完成)

### FFI 修复 (关键缺陷修复)
- [x] **实现缺失的 Submit FFI**: `cat_engine_submit_result`, `cat_engine_submit_timeout`, `cat_engine_submit_error`
- [x] **清理 Gemini 污染代码**:
    - [x] 删除未使用的 `cat_engine_get_variable_json` (C#)
    - [x] 删除未使用的 `param_schema` 字段 (Rust)
- [x] **FFI 一致性审计**: 37 个 C# 声明与 Rust 导出完全匹配

### UI CheckRule 完善
- [x] **CheckRuleSection 功能化**: 替换"开发中"占位符
- [x] **支持 4 种检查类型**:
    - [x] 范围检查 (RangeCheck): min ≤ value ≤ max
    - [x] 阈值检查 (Threshold): value `op` threshold
    - [x] 包含检查 (Contains): 字符串包含
    - [x] 自定义表达式 (Expression): 自由输入表达式

### E2E 测试
- [x] **新增 `e2e_test.rs`**: 验证完整测试执行流程
- [x] **测试通过**: Engine 33/33, Host 12/12

---

## 📋 下一步 (MVP 后)

- [ ] **硬件联调**: 真实仪器通讯测试
- [ ] **UI 真实联调**: Host + Engine + UI 完整流程
- [ ] **Release 打包**: 创建可分发的应用包
- [x] **Git 环境清理**: 全局忽略 `.pdb` 及 `publish` 产物 (2026-01-18)

---

## 验收测试

```bash
# Engine 测试
cd catalytic-engine && cargo test --test integration_full_flow

# Host 测试
cd catalytic/Tests && dotnet test

# UI 测试 (需先启动 Host)
cd catalyticui && ./gradlew :composeApp:testGrpcConnection
```

