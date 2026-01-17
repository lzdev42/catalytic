# Catalyst Project Documentation

## 1. 核心上下文 (Context Trinity)
The Single Source of Truth for project decisions.

| Document | Description |
|----------|-------------|
| [PROJECT_BLUEPRINT.md](./PROJECT_BLUEPRINT.md) | **项目蓝图**: 定义核心价值、技术栈、目录结构和关键文件位置。 |
| [IMPLEMENTATION_ROADMAP.md](./IMPLEMENTATION_ROADMAP.md) | **实施路线图**: 跟踪 MVP 非功能性需求、阶段目标和完成情况。 |
| [MEMORY_BANK.md](./MEMORY_BANK.md) | **决策记录 (ADR)**: 记录关键设计决策(如数据结构、协议)及其背后的逻辑。 |

## 2. 技术架构 (Technical Architecture v3.0)
Definitive guides for developers, aligned with MVP v3.0 codebase.

| Document | Description |
|----------|-------------|
| [TECH_DOC_ENGINE.md](./TECH_DOC_ENGINE.md) | **Engine 架构**: Rust 核心、FFI 接口、状态机与存储模型。 |
| [TECH_DOC_HOST.md](./TECH_DOC_HOST.md) | **Host 架构**: C# 服务、gRPC 实现、插件系统与零延迟事件桥接。 |
| [TECH_DOC_UI.md](./TECH_DOC_UI.md) | **UI 架构**: Kotlin Compose Multiplatform、MVVM、Repository 模式。 |
| [DATA_STRUCTURE_ALIGNMENT.md](./DATA_STRUCTURE_ALIGNMENT.md) | **数据结构对齐**: Engine/Host/UI 三层数据字段的严格映射规范。 |

## 3. Legacy / Reference Documents
Older documents or specific guides.

| Document | Description |
|----------|-------------|
| [COMPOSE_UI_TESTING_GUIDE.md](./COMPOSE_UI_TESTING_GUIDE.md) | Guide for writing Compose UI tests. |
| [PLUGIN_DEVELOPER_GUIDE.md](./PLUGIN_DEVELOPER_GUIDE.md) | Guide for developing hardware plugins. |
| [ENGINE_BUSINESS_LOGIC.md](./ENGINE_BUSINESS_LOGIC.md) | Specific details on business logic (partially superseded by TECH_DOC_ENGINE). |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Old architecture overview. |
| [HOST_DESIGN.md](./HOST_DESIGN.md) | Old Host design. |
| [ENGINE_DEVELOPER_GUIDE.md](./ENGINE_DEVELOPER_GUIDE.md) | Old Engine guide. |

## 4. Chinese Legacy Documents (归档)

| Document | Description |
|----------|-------------|
| [引擎设计.md](./引擎设计.md) | Engine design (Legacy) |
| [执行器设计方案.md](./执行器设计方案.md) | Executor design (Legacy) |
| [详细设计_LLD.md](./详细设计_LLD.md) | Low-level design (Legacy) |
| [测试用例设计.md](./测试用例设计.md) | Test case design (Legacy) |
