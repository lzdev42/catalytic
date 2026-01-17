# VCM 项目 Catalytic 适配性：红队复盘 (Red Team Review)

我重新审视了方案，抛开架构的“优雅”不谈，站在**一线工程师填坑**的角度，找出可能存在的**过度乐观**之处。

## 潜在风险与挑战 (The "Gotchas")

### 1. 实时性 (Data Latency) —— **最大的隐患**
*   **VCM 原生**: `Brain.cs` 可能是在一个紧凑的循环里直接读写 TCP，延迟极低。
*   **Catalytic**: Host -> gRPC -> Engine (Rust) -> FFI -> C# Plugin -> TCP。
    *   **问题**: 如果那个“测试”过程需要 **高频交互** (例如：发指令 -> 读反馈 -> 马上调整指令 -> 再发)，Catalytic 的跨层调用（gRPC + FFI）会引入毫秒级延迟。虽然单次调用如果不频繁没问题，但如果是闭环控制 (Closed-loop control)，可能会跟不上。
    *   **缓解**: 如果交互是原子化的（发一个大 Pattern，收一个大 Result，中间不纠结），那没问题。我看代码像是一次性发的，应该还好。

### 2. 数据量 (Data Volume)
*   **VCM 场景**: 那个 `CLSweep` 涉及到的波形数据 (`double[]`) 可能很大（几千上万个点）。
*   **Catalytic**: 所有变量都要经过 gRPC 序列化 (Protobuf) 和 FFI 内存拷贝。
    *   **风险**: 如果每次循环都传几 MB 的数据给 UI 画图，Host 和 UI 都会卡顿。
    *   **现实**: 原代码里的图表是 `DownSample` (降采样) 后再画的。我们必须确保 **不要** 把几万个点的原始数据通过 `Report` 变量抛给 UI。**只抛出降采样后的预览数据** 或 **文件路径** 给 UI，原始数据直接存盘。

### 3. DLL 依赖地狱 (Dependency Hell)
*   **现状**: VCM 项目直接引用 DLL。
*   **Catalytic**: 如果封装成插件，Plugin 在 Host 进程里加载。Host 是 .NET 8/9，而那些算法 DLL 可能是老旧的 .NET Framework 4.5 甚至更老，或者是 C++ 编译的 x86 (32位) 版本。
    *   **大坑**: 现在的 Host 是 64 位的。如果那些算法 DLL 是 32 位的 C/C++ 库，**直接加载会崩溃**。你需要一个独立的 32 位宿主进程或者用 IPC 通信，这就把简单问题搞复杂了。
    *   **检查**: 必须确认 `gen_cmd_type1_F20.dll` 是 AnyCPU 还是 x86。如果是 x86，Host 必须得能跑在 32 位模式，或者插件得做进程外隔离。

### 4. 调试难度 (Debuggability)
*   **VCM 原生**: 在 `Brain.cs` 打断点，F5 直接跑。
*   **Catalytic**:
    1.  Host 启动。
    2.  Engine 加载。
    3.  Plugin 加载。
    4.  PLC 触发。
    *   **痛点**: 当你发现“结果不对”时，要在只加载了 Plugin DLL 的 Host 进程里调试插件逻辑，比直接调试 Console App 麻烦。不仅要 Attach Process，还得确保符号文件 (.pdb) 都在对的位置。这对现场工程师要求变高了。

### 5. 状态同步 (State Sync)
*   **场景**: 如果 PLC 发了 `AF_UP`，Engine 刚跑一半，PLC 突然发了 `EMERGENCY_STOP` 或者断电了。
*   **Catalytic**: Engine 正在跑个 `while` 循环等 Socket。Host 收到了 PLC 停止信号。Host 怎么强杀 Engine 的当前步骤？
    *   **缺失**: Engine 目前的 API 支持优雅的 `StopTest`，但如果 Plugin 里的 Socket `Read` 卡死了（没有设置超时），Engine 线程就挂在那了。必须确保所有 Plugin 的 IO 操作都有严格的超时控制。

---

## 结论修正

**总体可行，但工程落地有 2 个硬门槛：**
1.  **DLL 位数兼容性**: 必须先确认那些算法库能不能在 64 位 .NET 环境跑。如果不行，这就是个巨大的坑。
2.  **插件质量要求高**: 不能像以前那样随意写 `while(true)`，插件必须处理好超时和取消，否则会把整个 Engine 卡死。

**Catalytic 适合“正规军”作战，不适合“游击队”随意糊代码。** 如果开发人员习惯了 copy-paste 且不愿意遵守插件规范，Catalytic 反而会让他们觉得“束手束脚”。
