# Catalyst 技术要点总结（面试必背）

> 🎯 这个文档是让你能在面试时**用自己的话讲清楚**这个项目的技术架构。

---

## 一、整体架构（一句话版）

> "Catalyst 分三层：**Engine** 是大脑，负责测试逻辑和状态管理；**Host** 是手脚，负责硬件通讯和插件加载；**UI** 是脸面，负责可视化配置和状态展示。"

```
┌─────────────┐
│    UI       │  Kotlin Compose (跨平台)
│  (界面层)    │
└──────┬──────┘
       │ gRPC
┌──────▼──────┐
│    Host     │  C# .NET (服务层)
│  (服务层)    │
└──────┬──────┘
       │ FFI (C接口)
┌──────▼──────┐
│   Engine    │  Rust (核心引擎)
│  (核心层)    │
└─────────────┘
```

---

## 二、为什么这么分层？

> "解耦。Engine 不知道硬件怎么连，Host 不知道测试逻辑是什么，UI 不知道底层细节。改一层不影响其他层。"

| 层 | 不关心什么 | 只关心什么 |
|---|-----------|-----------|
| Engine | 硬件怎么连、UI长什么样 | 测试步骤怎么执行、Pass/Fail怎么判定 |
| Host | 测试逻辑、UI界面 | 收到指令后怎么操作硬件、怎么加载插件 |
| UI | 硬件细节、执行逻辑 | 配置怎么展示、状态怎么刷新 |

---

## 三、Engine（Rust）

### 为什么用 Rust？

> "三个原因：**稳定**（没有GC卡顿）、**性能**（状态机和逻辑判定快）、**FFI友好**（能编译成动态库让C#调用）。"

### 核心职责

1. **状态管理**：维护每个测试槽位的状态（Idle/Running/Paused/Completed/Error）
2. **逻辑执行**：按顺序执行测试步骤，解析返回值，判定Pass/Fail
3. **数据存储**：用 redb（嵌入式数据库）持久化配置和结果

### 关键技术点

| 技术点 | 怎么说 |
|--------|--------|
| **状态机** | "每个槽位是一个独立的状态机，Idle → Running → Completed，互不干扰" |
| **FFI** | "对外暴露C接口，Host通过P/Invoke调用。数据用JSON序列化传递" |
| **redb** | "纯Rust的嵌入式KV数据库，比SQLite轻量，不需要装额外的东西" |
| **两种执行模式** | "Engine-Controlled：Engine发指令让Host操作硬件；Host-Controlled：Host自己处理复杂逻辑" |

### 设计亮点

> "Engine 是无网络、无 UI、纯逻辑的。它完全不知道外面的世界长什么样，只通过回调告诉 Host '该干活了' 或者 '状态变了'。"

---

## 四、Host（C# .NET）

### 为什么用 C#？

> "工控领域生态好，大量仪器的SDK是.NET的。而且写服务逻辑比Rust快很多。"

### 核心职责

1. **Engine托管**：加载 Rust 动态库，封装 FFI 调用
2. **gRPC服务**：给 UI 提供接口（ListDeviceTypes、StartTest、Subscribe等）
3. **插件系统**：动态加载硬件驱动（统一接口 `ICommunicator`，如 Serial, TCP, VISA）
4. **事件桥接**：把 Engine 的回调转成 gRPC 流推给 UI

### 关键技术点

| 技术点 | 怎么说 |
|--------|--------|
| **P/Invoke** | "C#调用非托管DLL的方式。用`[LibraryImport]`声明函数签名，运行时加载Rust编译的.dylib/.dll" |
| **gRPC** | "UI和Host之间用Protobuf定义接口，强类型、跨语言、支持流式推送" |
| **Channel** | "Engine回调是同步的，gRPC流是异步的。中间用`System.Threading.Channels`做缓冲" |
| **插件架构** | "SDK名为 `CatalyticKit`。废弃Transport/Protocol区分，统一用 `plugin_id` 标识。业务插件可直接调用通讯插件。" |

### 设计亮点

> "Host 是**透明的**——它不持有业务状态（状态在Engine），不持有配置（配置由UI传入）。所以它可以随时重启，不会丢数据。"

---

## 五、UI（Kotlin Compose）

### 为什么用 Kotlin Compose？

> "跨平台。一套代码跑 Windows、Mac、Linux，维护成本除以三。而且声明式UI比传统方式好写。"

### 核心职责

1. **可视化配置**：设备管理、测试步骤编排
2. **状态展示**：实时显示测试进度、变量值、日志
3. **Host管理**：启动/关闭 Host 进程

### 关键技术点

| 技术点 | 怎么说 |
|--------|--------|
| **Compose Multiplatform** | "JetBrains出的，跟Android Compose一样的API，但能跑桌面" |
| **MVVM** | "View只管渲染，ViewModel管状态和逻辑，Repository管数据来源" |
| **StateFlow** | "ViewModel暴露StateFlow，UI订阅它，状态变了界面自动刷新" |
| **Repository模式** | "定义`EngineRepository`接口，具体实现可以是gRPC也可以是Mock，方便测试" |

### 设计亮点

> "UI 只描述'界面应该是什么样'，不关心'怎么变成这样'。状态变了，Compose自动重绘。"

---

## 六、层间通信

### UI ↔ Host：gRPC

> "用Protobuf定义接口，支持：
> - 普通RPC（调一次返回一次）
> - 流式RPC（Subscribe订阅后持续推送状态更新）"

### Host ↔ Engine：FFI + JSON

> "Host通过P/Invoke调用Engine的C接口。
> - **输入**：JSON字符串（配置、命令）
> - **输出**：JSON字符串（状态、结果）
> - **回调**：Engine主动通知Host（OnEngineTask、OnUIUpdate）"

---

## 七、核心设计决策（ADR）

| 决策 | 为什么 |
|------|--------|
| **嵌套设备配置** | DeviceType里直接包含devices和commands，一次保存一个完整的设备类型，不用分开调API |
| **Host透明化** | Host不存业务状态，重启不丢数据，状态全在Engine |
| **事件驱动更新** | Engine状态变了主动推送，UI不轮询，实时性好 |
| **插件架构增强** | 2026年1月升级：统一用 `plugin_id` 替代 `transport/protocol` 双字段，大大简化了配置复杂度 |

---

## 八、如果面试官追问

### "为什么不直接用Rust写UI？"

> "Rust的UI生态还不成熟。Compose Multiplatform稳定、生态好、跟我之前的Swift/SwiftUI经验能平移。"

### "FFI传JSON性能有问题吗？"

> "工控场景数据量不大，毫秒级延迟完全可接受。而且JSON可读性好，调试方便。"

### "gRPC为什么不用REST？"

> "gRPC支持流式推送，适合实时状态更新。REST要轮询，延迟高、浪费带宽。"

### "redb为什么不用SQLite？"

> "redb是纯Rust实现，不需要系统依赖，跨平台部署更简单。而且我们的数据模型是KV形式，不需要关系数据库。"

---

## 快速自测

能用自己的话说清楚吗？

- [ ] 三层架构各自负责什么
- [ ] 为什么Engine用Rust
- [ ] 为什么Host用C#
- [ ] 为什么UI用Kotlin Compose
- [ ] UI和Host怎么通信（gRPC）
- [ ] Host和Engine怎么通信（FFI+JSON）
- [ ] 什么是"Host透明化"
- [ ] 什么是"事件驱动更新"
