# VCM 项目 Catalytic 适配性分析报告

## 1. 业务逻辑映射 (Mapping)

VCM 项目的核心是 **"PLC 驱动的状态机 + 算法 DLL 调用 + TCP 通信"**。以下是其在 Catalytic 架构中的映射：

| VCM 原生逻辑 | Catalytic 架构位置 | 实现方式 |
| :--- | :--- | :--- |
| **PLC 状态机**<br>(`ModbusCallback`) | **Host 业务层** | Host 程序负责监听 PLC 信号，收到信号后调用 Engine 执行对应流程。 |
| **算法调用**<br>(`CalculateHelper`) | **AlgorithmPlugin** | 封装 `gen_cmd_type1_F20.dll` 等算法库。提供 `GeneratePattern`, `CalculateResult` 等命令。 |
| **下位机通信**<br>(`ClientSocketManager`) | **VcmDriverPlugin** | 封装 TCP 通信。提供 `MoveToZero`, `SendPattern`, `ReadResponse` 等命令。 |
| **扫码与 MES**<br>(`SendQR`, `MesHelper`) | **ScannerPlugin**<br>**MesPlugin** | 封装串口扫码和 HTTP/TCP MES 接口。 |
| **测试流程**<br>(`AF_UP`, `AF_DOWN`...) | **Engine (.json)** | 每个 PLC 信号对应一个或一组 JSON 流程文件（例如 `flow_af_up.json`）。 |
| **数据记录**<br>(`CSVWriter`) | **Host / ReporterPlugin** | Host 监听测试结束事件，收集 Engine 变量并写入 CSV，或由插件完成。 |

---

## 2. 基于 SDK 的开发指南

如果使用 Catalytic SDK 重构 VCM 项目，开发流程如下：

### 第一步：开发插件 (C#)

你需要开发 3-4 个插件，每个插件只关注单一职责：

1.  **`Catalytic.Plugin.VcmDriver`**:
    *   **职责**: 负责跟下位机 TCP 通讯。
    *   **命令**:
        *   `Connect`: 连接板卡 IP:Port。
        *   `Zero`: 发送归零指令。
        *   `RunTest`: 发送 Pattern 字节数组，返回测试结果字节数组。

2.  **`Catalytic.Plugin.VcmAlgo`**:
    *   **职责**: 封装黑盒 DLL。
    *   **命令**:
        *   `GenPattern`: 输入参数（Axis, Mode），输出 `byte[] pattern`。
        *   `CalcStairs`: 输入原始数据 `byte[] raw`，输出 `double score`。

3.  **`Catalytic.Plugin.ModBus`** (通用):
    *   **职责**: 读写 PLC 寄存器。
    *   **命令**: `ReadRegister`, `WriteRegister`, `WaitForSignal`。

### 第二步：定义测试流程 (JSON)

不再在 C# 里写 `if (mode == AF) { ... }`，而是写 JSON 配置。
例如，针对 `AF_UP` 信号的流程文件 `flow_af_up.json`：

```json
{
  "name": "AF_UP_TEST",
  "steps": [
    {
      "name": "生成Pattern",
      "plugin": "VcmAlgo",
      "command": "GenPattern",
      "args": { "axis": "AF", "mode": "CLSweep" },
      "output_var": "af_pattern_bytes"
    },
    {
      "name": "产品归零",
      "plugin": "VcmDriver",
      "command": "Zero"
    },
    {
      "name": "执行测试",
      "plugin": "VcmDriver",
      "command": "RunTest",
      "args": { "pattern": "${af_pattern_bytes}" },
      "output_var": "af_raw_result"
    },
    {
      "name": "算法判定",
      "plugin": "VcmAlgo",
      "command": "CalcStairs",
      "args": { "raw_data": "${af_raw_result}" },
      "output_var": "af_score"
    }
  ]
}
```

### 第三步：编写 Host 胶水代码 (C#)

Host 程序变得非常薄，这就是它的核心逻辑：

```csharp
// HostMain.cs - 伪代码

void OnPlcSignalReceived(Signal signal) {
    string flowFile = "";
    
    switch(signal) {
        case Signal.AF_UP:
            flowFile = "flow_af_up.json";
            break;
        case Signal.AF_DOWN:
            flowFile = "flow_af_down.json";
            break;
        // ... 其他信号
    }

    if (!string.IsNullOrEmpty(flowFile)) {
        // 1. 加载对应流程
        engine.SetFlow(flowFile);
        
        // 2. 传入上下文参数 (如 SN, MES参数)
        engine.SetVariable("current_sn", sn);
        
        // 3. 启动引擎执行
        var result = await engine.StartTestAsync();
        
        // 4. 根据结果回复 PLC
        if (result.IsSuccess) {
            plc.Write(signal.ToString() + "_OK");
        } else {
            plc.Write("ERROR");
        }
    }
}
```

---

## 3. 可行性与优势分析

### 为什么 Catalytic 适用？

1.  **解耦控制与执行**:
    *   **VCM 痛点**: 控制逻辑（PLC信号）、业务逻辑（AF/OIS顺序）、执行逻辑（Socket发送）全部混在 `Brain.cs`。
    *   **Catalytic**: Host 管控制，JSON 管业务流程，Plugin 管执行。层次分明。

2.  **解决 "DLL 黑盒" 问题**:
    *   算法 DLL 被封装在 Plugin 内部。如果算法出错，可以直接调试 Plugin，或者在 JSON 流程中单独 mock 这个步骤的数据，**故障隔离**非常容易。

3.  **调试友好**:
    *   如果现场此时只想测 `AF_UP` 的归零动作，不需要改代码重新编译。只需要新建一个只有 "产品归零" 步骤的 `debug.json`，加载运行即可。

4.  **应对 "堆代码"**:
    *   即使业务逻辑变得很复杂（新增 OIS_Z 轴测试），也只是增加一个新的 Plugin 或修改 JSON，**不会让 `Brain.cs` 膨胀**。Host 代码永远保持稳定。

### 结论
**Catalytic 完美契合 VCM 类项目。** 它能将原本 877 行的意大利面条代码，拆解为清晰的 **"事件驱动 (Host) + 流程配置 (Engine) + 功能原子 (Plugin)"** 结构。维护成本将降低一个数量级。
