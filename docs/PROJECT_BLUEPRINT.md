# Catalytic 项目核心蓝图

## 核心功能与目标
Catalytic 是一个**低代码自动化测试平台**，通过串口/网络与仪器通讯，自动执行测试流程、收集数据、判定结果。

**目标用户**：工厂自动化测试工程师

---

## 技术栈

| 层 | 技术 | 说明 |
|---|------|------|
| Engine | Rust | 测试执行引擎，编译为动态库 (libcatalytic.dylib) |
| Host | C# (.NET 10) | 设备通讯、插件管理、gRPC 服务、事件桥接 |
| UI | Kotlin Compose Multiplatform | 桌面应用 |
| 协议 | gRPC (Protobuf) | Host ↔ UI 通讯 |

---

## 核心架构

```
┌─────────────┐     gRPC     ┌─────────────┐      FFI      ┌─────────────┐
│     UI      │◄───────────►│    Host     │◄────────────►│   Engine    │
│  (Compose)  │              │    (C#)     │               │   (Rust)    │
└─────────────┘              └──────┬──────┘               └─────────────┘
                                    │
                             ┌──────┴──────┐
                             │   Plugins   │
                             │(CatalyticKit)│
                             └─────────────┘
```

---

## 项目目录结构

```
sheji/                              # 项目根目录
├── catalytic/                      # Host + Engine 解决方案
│   ├── Catalytic/                  # Host 项目 (C#)
│   │   ├── Program.cs              # 入口点
│   │   ├── ConfigManager.cs        # 两级配置管理
│   │   ├── Engine/                 # Engine FFI 封装
│   │   ├── Grpc/                   # gRPC 服务实现
│   │   └── Plugin/                 # 插件系统
│   ├── CatalyticKit/               # 插件契约接口 (原 Catalytic.Contracts)
│   └── SerialPlugin/               # 串口通讯插件
├── catalyticui/                    # UI 项目 (Kotlin)
│   └── composeApp/
│       ├── src/commonMain/         # 共享代码
│       │   └── kotlin/.../
│       │       ├── ui/             # UI 组件
│       │       ├── viewmodel/      # ViewModel
│       │       ├── model/          # 数据模型
│       │       ├── log/            # 日志模块 (LogManager)
│       │       └── data/           # Repository 接口
│       └── src/jvmMain/            # Desktop 实现
│           └── kotlin/.../data/grpc/  # gRPC 实现
├── engine/                         # Engine 源码 (Rust)
└── docs/                           # 文档 (包含本三件套)
```

---

## 关键文件位置

| 文件 | 路径 | 说明 |
|------|------|------|
| Host 入口 | `catalytic/Catalytic/Program.cs` | 启动配置加载、Engine、gRPC |
| 配置管理 | `catalytic/Catalytic/ConfigManager.cs` | 两级配置机制 |
| gRPC 服务 | `catalytic/Catalytic/Grpc/HostGrpcService.cs` | 所有 API 实现 |
| Proto 定义 | `catalytic/protos/host_service.proto` | gRPC 接口定义 |
| UI 入口 | `catalyticui/composeApp/src/jvmMain/.../main.kt` | 创建 GrpcRepository |
| UI 主屏 | `catalyticui/.../ui/screens/MainScreen.kt` | 槽位网格布局 |
| ViewModel | `catalyticui/.../viewmodel/MainViewModel.kt` | 测试控制逻辑 |
| LogManager | `catalyticui/.../log/LogManager.kt` | 独立日志模块 |
| UI 测试指南 | `docs/COMPOSE_UI_TESTING_GUIDE.md` | Compose 测试完整教程 |

---

## 开发规范

- **命名**: PascalCase (C#), camelCase (Kotlin), snake_case (Rust)
- **错误处理**: 使用 Result 模式，不抛异常
- **日志**: 统一通过 Logger 类，格式 `[时间] [级别] [模块] 消息`
- **UI**: Material Design 3，暗色主题为主

---

## 部署目录结构

```
catalytic/                          # 部署根目录
├── host/Catalytic.exe              # Host 程序
├── ui/CatalyticUI.exe              # UI 程序
├── plugins/                        # 插件目录
│   └── SerialPlugin/
├── config.json                     # 配置文件
├── libcatalytic.dylib              # Engine 库
├── data/                           # 数据目录 (engine.db)
├── reports/                        # 测试报告
└── logs/                           # 日志文件
```

## 启动命令

```bash
# 启动应用（UI 会自动启动 Host）
cd sheji/catalyticui && ./gradlew run
```

**内部启动流程**:
1. UI 检测端口 5000 是否被占用
2. 如果被其他进程占用，自动选择空闲端口
3. UI 启动 Host: `./Catalytic --port <port> --work-dir <workdir>`
4. UI 连接 gRPC 服务

**开发调试命令**:
```bash
# 手动启动 Host（调试用）
cd catalyticui/composeApp/host
./Catalytic --port 5000 --work-dir /path/to/workfolder

# 运行 Compose UI 测试
cd catalyticui && ./gradlew desktopTest
```

