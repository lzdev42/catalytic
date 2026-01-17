# Catalyst Host 技术架构文档 (Host Technical Architecture)

**生效日期**: 2026-01-13
**版本**: 3.0
**状态**: MVP Complete

## 1. 系统概述

Catalyst Host 是负责连接 Engine (大脑)、UI (界面) 和 Plugins (手脚) 的中间件服务。它是一个基于 .NET 10 的控制台应用程序 (`Catalytic.exe`)，设计为"Headless Service"，通常由 UI 自动启动和管理。

**核心职责**:
1. **Engine 托管**: 加载 `libcatalytic` 动态库，封装 FFI 调用。
2. **通讯枢纽**: 提供 gRPC 服务供 UI 调用，建立 Channel 桥接 Engine 事件。
3. **插件加载**: 动态加载 .NET 插件 (`ICommunicator`)，负责实际的硬件 IO。
4. **透明运行**: 不持有业务状态（状态全在 Engine），不持有配置（配置全由 UI 传入）。

---

## 2. 核心架构

```mermaid
graph TD
    UI[Kotlin Desktop UI] <-->|gRPC/Protobuf| HostSvc[HostGrpcService]
    
    subgraph Host [Catalyst Host (.NET 8/10)]
        HostSvc --> EngineWrapper[Engine C# SDK]
        HostSvc --> Plugins[Plugin Manager]
        
        EngineWrapper <-->|P/Invoke| Native[libcatalytic (Rust)]
        
        Native -.->|Callback| EngineWrapper
        EngineWrapper -->|Channel| HostSvc
        
        EngineWrapper -->|Task| Plugins
        Plugins -->|Driver API| Hardware[(Hardware Instruments)]
    end
```

### 2.1 关键组件

| 组件 | 类/文件 | 职责 |
|------|---------|------|
| **HostGrpcService** | `Grpc/HostGrpcService.cs` | 实现了 `host_service.proto` 定义的所有 RPC 接口。作为 UI 的唯一入口。 |
| **Engine SDK** | `Engine/Engine.cs` | 封装了 `NativeMethods`，将 C-style FFI 转换为 C# 友好的 .NET API (Events, Delegates)。 |
| **Plugin Manager** | `Plugin/PluginManager.cs` | 扫描并加载 DLL 插件，管理 Protocol Driver 生命周期。 |
| **Service Bridge** | `System.Threading.Channels` | 在 `OnUIUpdate` 回调与 gRPC `Subscribe` 流之间建立异步缓冲通道。 |

---

## 3. 核心机制

### 3.1 零延迟事件桥接 (Zero-Latency Event Bridge)

为了实现 UI 的实时刷新，Host 采用了**推送模式**而非轮询：

1. **Rust Push**: Engine 状态变化 -> 调用 C 回调 `cat_engine_on_ui_update`。
2. **FFI Marshaling**: C# `NativeMethods` 接收回调 -> 触发 `Engine.SnapshotReceived` 事件。
3. **Channel Buffering**: `HostGrpcService` 的 Event Handler 接收事件 -> 写入 `Channel<Event>`。
4. **gRPC Streaming**: `Subscribe` 方法的 `while` 循环从 Channel 读取 -> `responseStream.WriteAsync` 推送给 UI。

通过此链路，硬件层毫秒级的状态变化可实时反映到 UI 界面。

### 3.2 任务分发 (Task Dispatching)

当 Engine 需要操作硬件时：

1. Engine 调用 `OnEngineTask` 回调 (Target Device, Command Payload)。
2. Host 查找对应的 Plugin 实例 (`IProtocolDriver`)。
3. Host 调用插件的 `ExecuteCommand(payload)`。
4. Host 将插件返回值通过 `Engine.SubmitResult` 回传给 Engine。

### 3.3 透明配置 (Transparent Configuration)

Host 自身不读取 `config.json`。
- **启动参数**: 通过 `--work-dir` 和 `--port` 接收基本参数。
- **业务配置**: UI 通过 `UpdateDeviceType` 等 API 将配置 JSON 直接写入 Engine。
- **优势**: 保持 Host 无状态，避免 Host 和 UI 配置不一致。

---

## 4. 开发指南

### 4.1 目录结构
```
catalytic/Catalytic/
├── Engine/          # Engine FFI 封装层
├── Grpc/            # gRPC 服务实现
├── Plugin/          # 插件系统
├── Protos/          # Protobuf 定义
└── Program.cs       # 启动入口
```

### 4.2 编译与运行
```bash
# 运行
dotnet run --project Catalytic -- --port 5000

# 发布
dotnet publish -c Release -r osx-arm64
```

### 4.3 常见问题
- **DLL Not Found**: 确保 `libcatalytic.dylib` 与 Host 可执行文件在同一目录，或在系统 PATH 中。
- **Task ID Mismatch**: C# 端 `ulong` 严格对应 Rust `u64`。
- **JSON Serialization**: Host 负责将 C# 对象序列化为 Engine 期望的 Snake_case JSON。使用 `System.Text.Json`。

---

## 5. 插件系统

Host 使用统一的插件接口。插件必须实现 `CatalyticKit` 程序集中定义的接口，如 `ICommunicator` 或 `IProcessor`。

插件 DLL 放置在 `work-dir/plugins/` 目录下即可自动加载。 Host 不再区分 Transport 和 Protocol 类型，统一由 `plugin_id` 标识。业务插件可通过 `IPluginContext` 调用通讯插件。
