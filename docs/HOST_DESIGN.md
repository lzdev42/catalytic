# Catalytic Host 设计文档

> **项目名称**: Catalytic (Host)  
> **版本**: 1.0.0 (MVP)  
> **更新日期**: 2026-01-13  
> **状态**: ✅ MVP 完成

---

## 1. 概述

### 核心理念：Host 是桥梁，不是大脑

**Host 自身没有业务逻辑**，它只是三个组件之间的胶水：

| 角色 | 说明 |
|------|------|
| **插件加载器** | 扫描 DLL、反射加载、按标识注册 |
| **FFI 桥** | 封装 Core 调用、把 Core 回调分发给插件 |
| **IPC 桥** | 转发 UI 命令给 Core、推送状态给 UI |

```
         ┌─────────────────────────────────────────────────┐
         │                     Host                         │
         │                                                  │
UI ←────────→ [IPC 桥] ←───→ [插件路由] ←───→ Plugins       │
         │        ↑                ↑                        │
         │        └────────────────┴───→ [FFI 桥] ←→ Core   │
         └─────────────────────────────────────────────────┘
```

**代码量预期很小**：核心逻辑约几百行。业务逻辑全在 Core 和 Plugins 中。

---

## 2. 需要开发的内容

| 组件 | 说明 |
|------|------|
| **Catalytic.Host** | Host 主程序 |
| **Catalytic.Contracts** | 插件接口定义（给插件开发者的 SDK） |

---

## 3. 插件接口设计

### 3.1 ICommunicator（通讯器）

处理 Engine 的 `EngineTaskCallback`，用于设备通讯。

```csharp
namespace Catalytic.Contracts;

/// <summary>
/// 通讯器接口 - 处理设备通讯
/// </summary>
public interface ICommunicator
{
    /// <summary>
    /// 协议标识，对应 Engine 回调的 protocol 参数
    /// 例如："scpi", "modbus", "uds", "serial"
    /// </summary>
    string Protocol { get; }

    /// <summary>
    /// 执行设备操作
    /// </summary>
    /// <param name="address">物理地址（Engine 已解析），如 "TCPIP0::192.168.1.101::INSTR"</param>
    /// <param name="action">动作类型："send"=只发不读, "query"=发并读, "wait"=等待事件</param>
    /// <param name="payload">指令内容（字节数组）</param>
    /// <param name="timeoutMs">超时时间（毫秒）</param>
    /// <param name="ct">取消令牌</param>
    /// <returns>响应数据（send 返回空数组）</returns>
    Task<byte[]> ExecuteAsync(
        string address, 
        string action, 
        byte[] payload, 
        int timeoutMs, 
        CancellationToken ct);
}
```

### 3.2 IProcessor（处理器）

处理 Engine 的 `HostTaskCallback`，用于复杂业务逻辑。

```csharp
namespace Catalytic.Contracts;

/// <summary>
/// Host 任务处理器接口 - 处理复杂业务逻辑
/// </summary>
public interface IProcessor
{
    /// <summary>
    /// 任务名称，对应 TestStep 中 host_task.task_name
    /// 例如："burn_firmware", "calibrate", "self_test"
    /// </summary>
    string TaskName { get; }

    /// <summary>
    /// 执行任务
    /// </summary>
    /// <param name="parametersJson">任务参数（JSON 格式）</param>
    /// <param name="ct">取消令牌</param>
    /// <returns>执行结果（会提交给 Engine）</returns>
    Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct);
}
```

---

## 4. Host 主程序设计

### 4.1 模块划分

```
Catalytic.Host/
├── PluginManager/        # 插件加载与管理
│   ├── PluginLoader.cs   # 反射加载 DLL
│   └── PluginRegistry.cs # 注册表（按 Protocol/TaskName 索引）
├── EngineAdapter/        # FFI 封装
│   ├── NativeMethods.cs  # P/Invoke 声明
│   ├── EngineWrapper.cs  # 高层 API
│   └── CallbackHandler.cs# 回调分发
├── IpcServer/            # UI 通信
│   └── (待定：gRPC / Socket / 命名管道)
└── Program.cs            # 入口
```

### 4.2 插件加载流程

```
Host 启动
    │
    ▼
扫描 plugins/ 目录下所有 .dll
    │
    ▼
反射加载每个 DLL
    │
    ▼
查找实现 ICommunicator 的类 → 按 Protocol 注册
查找实现 IProcessor 的类 → 按 TaskName 注册
    │
    ▼
准备就绪，等待 Engine 回调
```

### 4.3 回调处理流程

```
Engine 发起 EngineTaskCallback
    │
    ├─ protocol = "scpi"
    │
    ▼
Host 查找 _drivers["scpi"]
    │
    ▼
调用 driver.ExecuteAsync(address, action, payload, timeout, ct)
    │
    ▼
await 结果
    │
    ├─ 成功 → cat_engine_submit_result(slotId, taskId, result)
    ├─ 超时 → cat_engine_submit_timeout(slotId, taskId)
    └─ 异常 → cat_engine_submit_error(slotId, taskId, message)
```

---

## 5. UI 通信 ✅ 已确定

> **2026-01-13 更新**: IPC 方案已确定为 gRPC。

| 方案 | 状态 |
|------|------|
| **gRPC (HTTP/2)** | ✅ **已采用** |
| TCP Socket + JSON | ❌ 未采用 |
| 命名管道 + JSON | ❌ 未采用 |

**选择理由**:
- 强类型 Proto 定义，自动生成 C# 和 Kotlin 代码
- 支持 Server Streaming (Event-Driven 推送)
- 跨平台原生支持

**实现位置**:
- Proto 定义: `catalytic/protos/host.proto`
- Host 服务端: `catalytic/Catalytic/Grpc/HostGrpcService.cs`
- UI 客户端: `catalyticui/.../data/grpc/GrpcRepository.kt`

---

## 6. 当前状态 (2026-01-13)

| 模块 | 状态 |
|------|------|
| **PluginManager** | ✅ 已完成 |
| **Engine FFI 适配器** | ✅ 已完成 |
| **gRPC 服务** | ✅ 已完成 |
| **插件接口 (Contracts)** | ✅ 已完成 |

> **MVP 已完成**: Host 可正常加载插件、接收 UI 请求、调用 Engine、推送状态更新。

---

## 附录：命名规则

| 实际名称 | 说明 |
|--------|--------|
| `catalytic-engine` | Rust 引擎 (libcatalytic.dylib) |
| `Catalytic` | C# Host 程序 |
| `Catalytic.Contracts` | 插件 SDK |
| `catalyticui` | Kotlin Compose UI |

