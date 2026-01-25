# Catalytic 插件开发指南 (v4.0)

*(更新日期: 2026-01-16 | SDK 版本: 4.0.0)*

---

## 目录

1. [简介](#1-简介)
2. [开发环境准备](#2-开发环境准备)
3. [快速开始：你的第一个插件](#3-快速开始你的第一个插件)
4. [核心概念](#4-核心概念)
5. [SDK API 完整参考](#5-sdk-api-完整参考)
6. [完整示例：通讯器插件](#6-完整示例通讯器插件)
7. [完整示例：处理器插件](#7-完整示例处理器插件)
8. [错误处理最佳实践](#8-错误处理最佳实践)
9. [高级功能](#9-高级功能)
10. [调试与排查问题](#10-调试与排查问题)
11. [部署插件](#11-部署插件)
12. [常见问题 FAQ](#12-常见问题-faq)

---

## 1. 简介

### 什么是 Catalytic 插件？

Catalytic 采用模块化插件架构。**所有与硬件交互的功能都通过插件实现。** 无论是串口通讯、TCP Socket，还是固件烧录、校准算法，本质上都是 Catalytic 的插件。

### 插件的两种类型

| 类型 | 接口 | 用途 | 典型场景 |
|------|------|------|----------|
| **通讯器** | `ICommunicator` | 底层设备通讯 | 串口、TCP、VISA、Modbus |
| **处理器** | `IProcessor` | 复杂业务逻辑 | 固件烧录、产品校准、数据分析 |

### 为什么使用插件？

- ✅ **易扩展**: 将 DLL 放入 `plugins` 文件夹，重启 Host 即可加载
- ✅ **隔离性**: 插件崩溃不会影响主程序
- ✅ **复用性**: 一个通讯器可以被多个处理器复用
- ✅ **跨平台**: 基于 .NET 10，支持 Windows / macOS / Linux

---

## 2. 开发环境准备

### 必需软件

| 软件 | 版本 | 下载地址 |
|------|------|----------|
| .NET SDK | **10.0+** | https://dotnet.microsoft.com/download |
| 代码编辑器 | 任意 | VS Code / Visual Studio / Rider |

### 验证安装

打开终端（或 CMD），运行：

```bash
dotnet --version
# 输出: 10.0.xxx
```

---

## 3. 快速开始：你的第一个插件

### 第一步：创建项目

```bash
# 创建类库项目
dotnet new classlib -n MyFirstPlugin -f net10.0

# 进入项目目录
cd MyFirstPlugin
```

### 第二步：添加 SDK 引用

#### 方式 A: 直接引用 DLL（推荐）

将 Catalytic 提供的 `CatalyticKit.dll` 复制到 `lib/` 目录，然后编辑 `.csproj`：

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>

  <ItemGroup>
    <Reference Include="CatalyticKit">
      <HintPath>lib/CatalyticKit.dll</HintPath>
    </Reference>
  </ItemGroup>
</Project>
```

#### 方式 B: 使用 NuGet（如果已发布）

```bash
dotnet add package CatalyticKit
```

### 第三步：创建清单文件

在项目根目录创建 `manifest.json`：

```json
{
    "id": "my-company.my-first-plugin",
    "name": "My First Plugin",
    "version": "1.0.0",
    "entry": "MyFirstPlugin.dll",
    "capabilities": {
        "protocols": ["demo"],
        "tasks": []
    }
}
```

> ⚠️ **重要**: `id` 必须全局唯一，建议格式为 `公司名.插件名`

### 第四步：实现插件

编辑 `Class1.cs`（重命名为 `DemoPlugin.cs`）：

```csharp
using CatalyticKit;

namespace MyFirstPlugin;

public class DemoPlugin : ICommunicator
{
    private IPluginContext? _context;

    // 插件唯一标识（必须与 manifest.json 中的 id 一致）
    public string Id => "my-company.my-first-plugin";
    
    // 支持的协议名称
    public string Protocol => "demo";

    // 插件激活时调用
    public Task ActivateAsync(IPluginContext context)
    {
        _context = context;
        _context.Log(LogLevel.Info, "🎉 插件已激活！");
        return Task.CompletedTask;
    }

    // 插件停用时调用
    public Task DeactivateAsync()
    {
        _context?.Log(LogLevel.Info, "👋 插件正在停用...");
        return Task.CompletedTask;
    }

    // 执行通讯动作
    public Task<byte[]> ExecuteAsync(
        string address,
        string action,
        byte[] payload,
        int timeoutMs,
        CancellationToken ct)
    {
        _context?.Log(LogLevel.Debug, $"收到请求: address={address}, action={action}");
        
        // 这里实现你的通讯逻辑
        // 示例：返回 "Hello" 的字节数组
        return Task.FromResult("Hello from plugin!"u8.ToArray());
    }
}
```

### 第五步：编译

```bash
dotnet build -c Release
```

输出文件位于 `bin/Release/net10.0/`

### 第六步：部署测试

将以下文件复制到 Catalytic 的 `plugins/my-company.my-first-plugin/` 目录：

```
plugins/
└── my-company.my-first-plugin/
    ├── manifest.json
    └── MyFirstPlugin.dll
```

重启 Catalytic，在日志中应该能看到 "🎉 插件已激活！"

---

## 4. 核心概念

### 4.1 插件 ID

每个插件必须有一个**全局唯一 ID**。

| 格式 | 正确示例 | 错误示例 |
|------|----------|----------|
| `publisher.name` | `acme.scpi-driver` | `scpi`（太短）|
| 小写 + 连字符 | `my-company.modbus` | `MyCompany.Modbus`（大写）|

> 💡 ID 是 UI 中选择插件的唯一凭证，请谨慎命名。

### 4.2 清单文件 (manifest.json)

每个插件目录**必须**包含一个 `manifest.json`：

```json
{
    "id": "acme.serial",           // 必填：全局唯一 ID
    "name": "Acme Serial Driver",  // 必填：显示名称
    "version": "1.0.0",            // 必填：版本号
    "entry": "Acme.Serial.dll",    // 必填：入口 DLL 文件名
    "capabilities": {
        "protocols": ["serial"],   // 通讯器支持的协议列表
        "tasks": []                // 处理器支持的任务列表
    }
}
```

### 4.3 生命周期

```
┌─────────────────────────────────────────────────────────────┐
│                     插件生命周期                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  [Host 启动]                                                 │
│       │                                                      │
│       ▼                                                      │
│  1. LoadAllAsync()  ──> 读取 manifest.json                   │
│       │                                                      │
│       ▼                                                      │
│  2. LoadAssembly()  ──> 加载 DLL                             │
│       │                                                      │
│       ▼                                                      │
│  3. CreateInstance() ──> 反射创建插件实例                     │
│       │                                                      │
│       ▼                                                      │
│  4. ActivateAsync() ──> 【你在这里初始化资源】                │
│       │                                                      │
│       ▼                                                      │
│  5. ExecuteAsync()  ──> 【处理请求】 (循环调用)               │
│       │                                                      │
│       ▼                                                      │
│  6. DeactivateAsync() ──> 【你在这里释放资源】                │
│       │                                                      │
│       ▼                                                      │
│  [Host 关闭]                                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. SDK API 完整参考

### 5.1 IPlugin（基础接口）

所有插件都必须实现此接口。

```csharp
public interface IPlugin
{
    /// <summary>
    /// 插件唯一标识
    /// 格式建议: "公司.插件名"，例如 "acme.scpi-driver"
    /// 必须与 manifest.json 中的 id 字段一致
    /// </summary>
    string Id { get; }
    
    /// <summary>
    /// 插件激活时调用
    /// 在此进行初始化工作：打开连接、加载配置等
    /// </summary>
    /// <param name="context">插件上下文，提供日志、获取其他插件等能力</param>
    Task ActivateAsync(IPluginContext context);
    
    /// <summary>
    /// 插件停用时调用
    /// 在此进行清理工作：关闭连接、释放资源等
    /// </summary>
    Task DeactivateAsync();
}
```

### 5.2 ICommunicator（通讯器）

实现此接口以处理硬件通讯。

```csharp
public interface ICommunicator : IPlugin
{
    /// <summary>
    /// 该通讯器支持的协议名称
    /// 例如 "serial"、"tcp"、"visa"、"modbus"
    /// 此名称会出现在 UI 的协议选择器中
    /// </summary>
    string Protocol { get; }
    
    /// <summary>
    /// 执行通讯动作
    /// </summary>
    /// <param name="address">设备地址，格式由协议决定
    ///     串口: "COM3" 或 "/dev/ttyUSB0"
    ///     TCP: "192.168.1.100:5025"
    ///     VISA: "TCPIP::192.168.1.100::INSTR"
    /// </param>
    /// <param name="action">动作类型，推荐使用 CommAction 枚举
    ///     "connect" / "disconnect" / "send" / "read" / "query" / "status"
    /// </param>
    /// <param name="payload">要发送的数据</param>
    /// <param name="timeoutMs">超时时间（毫秒），0 表示无超时</param>
    /// <param name="ct">取消令牌，当测试被停止时会触发取消</param>
    /// <returns>设备返回的数据，无数据时返回空数组</returns>
    Task<byte[]> ExecuteAsync(
        string address, 
        string action, 
        byte[] payload, 
        int timeoutMs, 
        CancellationToken ct);
}
```

### 5.3 IProcessor（处理器）

实现此接口以处理复杂业务逻辑。

```csharp
public interface IProcessor : IPlugin
{
    /// <summary>
    /// 该处理器支持的任务名称
    /// 例如 "burn_firmware"、"calibrate"、"analyze_data"
    /// 此名称用于 UI 中配置 Host 任务
    /// </summary>
    string TaskName { get; }
    
    /// <summary>
    /// 执行处理逻辑
    /// </summary>
    /// <param name="parametersJson">任务参数（JSON 格式）
    ///     示例: {"file_path": "/tmp/fw.bin", "baudrate": 115200}
    /// </param>
    /// <param name="ct">取消令牌，当测试被停止时会触发取消</param>
    /// <returns>处理结果数据，将被 Engine 解析并存储为变量</returns>
    Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct);
}
```

### 5.4 IPluginContext（上下文）

在 `ActivateAsync` 中传入，保存它以便后续使用。

```csharp
public interface IPluginContext
{
    /// <summary>
    /// 插件目录路径
    /// 用于访问插件附带的资源文件（如配置文件、固件等）
    /// </summary>
    string PluginDirectory { get; }
    
    /// <summary>
    /// 写日志到 Catalytic 日志系统
    /// 日志会显示在 UI 的系统日志面板中
    /// </summary>
    /// <param name="level">日志级别: Debug / Info / Warning / Error</param>
    /// <param name="message">日志内容</param>
    void Log(LogLevel level, string message);

    /// <summary>
    /// 获取其他通讯器（用于插件互调）
    /// 典型用途：处理器插件需要调用串口通讯器发送命令
    /// </summary>
    /// <param name="protocolOrId">协议名（如 "serial"）或插件 ID</param>
    /// <returns>通讯器实例，未找到返回 null</returns>
    ICommunicator? GetCommunicator(string protocolOrId);

    /// <summary>
    /// 推送事件到 Catalytic
    /// 用于设备主动推送数据（如 CAN 帧监控、设备报警）
    /// </summary>
    void PushEvent(string eventType, byte[] data);
}
```

### 5.5 CommAction（标准动作枚举）

推荐使用此枚举而不是手动字符串判断。

```csharp
public enum CommAction
{
    Connect,     // 建立连接
    Disconnect,  // 断开连接
    Send,        // 发送数据（不等响应）
    Read,        // 读取当前可用数据
    Query,       // 发送 + 读取（便捷方法）
    Status       // 查询连接状态
}
```

### 5.6 CommunicatorExtensions（便捷扩展方法）

SDK 提供这些扩展方法简化调用：

```csharp
// 发送数据
await communicator.SendAsync("COM3", data, ct);

// 读取数据
byte[] response = await communicator.ReadAsync("COM3", timeoutMs: 1000, ct);

// 建立连接
await communicator.ConnectAsync("COM3", timeoutMs: 5000, ct);

// 断开连接
await communicator.DisconnectAsync("COM3", ct);

// 查询状态
byte[] status = await communicator.GetStatusAsync("COM3", ct);
```

### 5.7 LogLevel（日志级别）

```csharp
public enum LogLevel
{
    Debug,    // 调试信息（开发时使用）
    Info,     // 一般信息
    Warning,  // 警告
    Error     // 错误
}
```

### 5.8 常用工具扩展 (Utility Extensions)

SDK 提供了 `CatalyticKit.StringExtension` 和 `CatalyticKit.ByteExtension`，包含常用的类型转换和格式化工具，建议优先使用以减少重复代码。

#### 字符串扩展 (StringExtension)

```csharp
// 安全转换 (转换失败返回默认值，不抛异常)
bool b1 = "true".ToBool();           // true
bool b2 = "1".ToBool();              // true
int i = "123".ToInt(defaultValue: 0);
double d = "3.14".ToDouble();
DateTime dt = "2026-01-01".ToDateTime(defaultValue: DateTime.Now);

// Hex 字符串转字节数组 (支持空/null/空格/连字符/冒号)
byte[] data1 = "AABBCC".ToBytes();              // [0xAA, 0xBB, 0xCC]
byte[] data2 = "AA-BB-CC".ToBytes();            // [0xAA, 0xBB, 0xCC]
byte[] data3 = "AA BB CC".ToBytes();            // [0xAA, 0xBB, 0xCC]

// 尝试转换 (安全模式)
if ("AABB".TryToBytes(out byte[] result)) { ... }
```

#### 字节扩展 (ByteExtension)

```csharp
byte[] data = { 0xAA, 0xBB, 0xCC };

// 转带空格的 Hex 字符串 (高性能)
string hex = data.ToHexStringWithSpaces(); // "AA BB CC"
```

---

## 6. 完整示例：通讯器插件

这是一个功能完整的串口通讯插件示例。

### SerialCommunicator.cs

```csharp
using System.IO.Ports;
using CatalyticKit;

namespace Acme.Serial;

/// <summary>
/// 串口通讯器插件
/// 支持标准串口设备的读写操作
/// </summary>
public class SerialCommunicator : ICommunicator
{
    private IPluginContext? _context;
    
    // 管理多个串口连接
    private readonly Dictionary<string, SerialPort> _ports = new();
    private readonly object _lock = new();

    public string Id => "acme.serial";
    public string Protocol => "serial";

    public Task ActivateAsync(IPluginContext context)
    {
        _context = context;
        _context.Log(LogLevel.Info, "串口插件已激活");
        return Task.CompletedTask;
    }

    public Task DeactivateAsync()
    {
        // 释放所有串口资源
        lock (_lock)
        {
            foreach (var port in _ports.Values)
            {
                try
                {
                    if (port.IsOpen) port.Close();
                    port.Dispose();
                }
                catch (Exception ex)
                {
                    _context?.Log(LogLevel.Warning, $"关闭串口时出错: {ex.Message}");
                }
            }
            _ports.Clear();
        }
        _context?.Log(LogLevel.Info, "串口插件已停用");
        return Task.CompletedTask;
    }

    public async Task<byte[]> ExecuteAsync(
        string address, 
        string action, 
        byte[] payload, 
        int timeoutMs, 
        CancellationToken ct)
    {
        // 解析动作类型
        if (!Enum.TryParse<CommAction>(action, ignoreCase: true, out var commAction))
        {
            throw new ArgumentException($"未知的动作类型: {action}");
        }

        var port = GetOrCreatePort(address);
        _context?.Log(LogLevel.Debug, $"[{address}] 执行动作: {commAction}");

        switch (commAction)
        {
            case CommAction.Connect:
                return await ConnectAsync(port, timeoutMs, ct);
                
            case CommAction.Disconnect:
                return await DisconnectAsync(port);
                
            case CommAction.Send:
                return await SendAsync(port, payload, ct);
                
            case CommAction.Read:
                return await ReadAsync(port, timeoutMs, ct);
                
            case CommAction.Query:
                return await QueryAsync(port, payload, timeoutMs, ct);
                
            case CommAction.Status:
                return GetStatus(port);
                
            default:
                throw new NotSupportedException($"不支持的动作: {commAction}");
        }
    }

    private async Task<byte[]> ConnectAsync(SerialPort port, int timeoutMs, CancellationToken ct)
    {
        if (port.IsOpen)
        {
            _context?.Log(LogLevel.Debug, $"[{port.PortName}] 已经打开");
            return [];
        }

        try
        {
            port.Open();
            _context?.Log(LogLevel.Info, $"[{port.PortName}] 连接成功");
            return [];
        }
        catch (Exception ex)
        {
            throw new IOException($"打开串口 {port.PortName} 失败: {ex.Message}", ex);
        }
    }

    private Task<byte[]> DisconnectAsync(SerialPort port)
    {
        if (port.IsOpen)
        {
            port.Close();
            _context?.Log(LogLevel.Info, $"[{port.PortName}] 已断开");
        }
        return Task.FromResult(Array.Empty<byte>());
    }

    private Task<byte[]> SendAsync(SerialPort port, byte[] data, CancellationToken ct)
    {
        EnsureOpen(port);
        port.Write(data, 0, data.Length);
        _context?.Log(LogLevel.Debug, $"[{port.PortName}] 发送 {data.Length} 字节");
        return Task.FromResult(Array.Empty<byte>());
    }

    private async Task<byte[]> ReadAsync(SerialPort port, int timeoutMs, CancellationToken ct)
    {
        EnsureOpen(port);
        port.ReadTimeout = timeoutMs > 0 ? timeoutMs : -1;

        var buffer = new byte[4096];
        try
        {
            // 等待数据可用
            var startTime = DateTime.UtcNow;
            while (port.BytesToRead == 0)
            {
                ct.ThrowIfCancellationRequested();
                if (timeoutMs > 0 && (DateTime.UtcNow - startTime).TotalMilliseconds > timeoutMs)
                {
                    throw new TimeoutException($"读取超时 ({timeoutMs}ms)");
                }
                await Task.Delay(10, ct);
            }

            var count = port.Read(buffer, 0, buffer.Length);
            _context?.Log(LogLevel.Debug, $"[{port.PortName}] 读取 {count} 字节");
            return buffer[..count];
        }
        catch (TimeoutException)
        {
            throw;
        }
    }

    private async Task<byte[]> QueryAsync(SerialPort port, byte[] data, int timeoutMs, CancellationToken ct)
    {
        await SendAsync(port, data, ct);
        await Task.Delay(50, ct); // 给设备一点处理时间
        return await ReadAsync(port, timeoutMs, ct);
    }

    private byte[] GetStatus(SerialPort port)
    {
        var status = port.IsOpen ? "connected" : "disconnected";
        return System.Text.Encoding.UTF8.GetBytes(status);
    }

    private void EnsureOpen(SerialPort port)
    {
        if (!port.IsOpen)
        {
            throw new InvalidOperationException($"串口 {port.PortName} 未打开，请先执行 Connect");
        }
    }

    private SerialPort GetOrCreatePort(string portName)
    {
        lock (_lock)
        {
            if (_ports.TryGetValue(portName, out var port))
            {
                return port;
            }

            var newPort = new SerialPort(portName)
            {
                BaudRate = 9600,
                DataBits = 8,
                Parity = Parity.None,
                StopBits = StopBits.One
            };
            _ports[portName] = newPort;
            return newPort;
        }
    }
}
```

### manifest.json

```json
{
    "id": "acme.serial",
    "name": "Acme Serial Driver",
    "version": "1.0.0",
    "entry": "Acme.Serial.dll",
    "capabilities": {
        "protocols": ["serial"],
        "tasks": []
    }
}
```

---

## 7. 完整示例：处理器插件

这是一个固件烧录处理器示例，演示如何调用其他通讯器。

### FirmwareBurner.cs

```csharp
using System.Text.Json;
using CatalyticKit;

namespace Acme.Burner;

/// <summary>
/// 固件烧录参数
/// </summary>
public record BurnParameters
{
    /// <summary>烧录文件路径</summary>
    public string FilePath { get; init; } = "";
    
    /// <summary>目标设备地址</summary>
    public string DeviceAddress { get; init; } = "";
    
    /// <summary>使用的通讯器 ID</summary>
    public string CommunicatorId { get; init; } = "acme.serial";
    
    /// <summary>波特率</summary>
    public int BaudRate { get; init; } = 115200;
}

/// <summary>
/// 固件烧录处理器
/// </summary>
public class FirmwareBurner : IProcessor
{
    private IPluginContext? _context;

    public string Id => "acme.firmware-burner";
    public string TaskName => "burn_firmware";

    public Task ActivateAsync(IPluginContext context)
    {
        _context = context;
        _context.Log(LogLevel.Info, "固件烧录插件已激活");
        return Task.CompletedTask;
    }

    public Task DeactivateAsync()
    {
        _context?.Log(LogLevel.Info, "固件烧录插件已停用");
        return Task.CompletedTask;
    }

    public async Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct)
    {
        // 第一步：解析参数
        BurnParameters? parameters;
        try
        {
            parameters = JsonSerializer.Deserialize<BurnParameters>(parametersJson);
            if (parameters == null)
            {
                throw new ArgumentException("参数解析结果为 null");
            }
        }
        catch (JsonException ex)
        {
            throw new ArgumentException($"JSON 参数解析失败: {ex.Message}", ex);
        }

        _context?.Log(LogLevel.Info, $"开始烧录: {parameters.FilePath} -> {parameters.DeviceAddress}");

        // 第二步：读取固件文件
        if (!File.Exists(parameters.FilePath))
        {
            throw new FileNotFoundException($"固件文件不存在: {parameters.FilePath}");
        }
        var firmware = await File.ReadAllBytesAsync(parameters.FilePath, ct);
        _context?.Log(LogLevel.Info, $"已加载固件，大小: {firmware.Length} 字节");

        // 第三步：获取通讯器
        var communicator = _context?.GetCommunicator(parameters.CommunicatorId);
        if (communicator == null)
        {
            throw new InvalidOperationException($"找不到通讯器: {parameters.CommunicatorId}");
        }

        // 第四步：连接设备
        await communicator.ConnectAsync(parameters.DeviceAddress, timeoutMs: 5000, ct);
        _context?.Log(LogLevel.Info, "设备已连接");

        try
        {
            // 第五步：发送进入烧录模式命令
            await communicator.SendAsync(parameters.DeviceAddress, "BURN_MODE\n"u8.ToArray(), ct);
            await Task.Delay(200, ct); // 等待设备切换模式

            // 第六步：分块发送固件
            const int chunkSize = 256;
            var totalChunks = (firmware.Length + chunkSize - 1) / chunkSize;
            
            for (var i = 0; i < firmware.Length; i += chunkSize)
            {
                ct.ThrowIfCancellationRequested();
                
                var chunk = firmware[i..Math.Min(i + chunkSize, firmware.Length)];
                await communicator.SendAsync(parameters.DeviceAddress, chunk, ct);
                
                var progress = (i / chunkSize + 1) * 100 / totalChunks;
                _context?.Log(LogLevel.Debug, $"烧录进度: {progress}%");
                
                await Task.Delay(10, ct); // 给设备处理时间
            }

            // 第七步：发送完成命令并验证
            await communicator.SendAsync(parameters.DeviceAddress, "BURN_DONE\n"u8.ToArray(), ct);
            var response = await communicator.ReadAsync(parameters.DeviceAddress, timeoutMs: 5000, ct);
            var responseStr = System.Text.Encoding.UTF8.GetString(response);

            if (!responseStr.Contains("OK"))
            {
                throw new InvalidOperationException($"烧录验证失败: {responseStr}");
            }

            _context?.Log(LogLevel.Info, "✅ 烧录成功！");
            
            // 返回结果 JSON
            var result = JsonSerializer.SerializeToUtf8Bytes(new
            {
                success = true,
                bytes_written = firmware.Length,
                device = parameters.DeviceAddress
            });
            return result;
        }
        finally
        {
            // 确保断开连接
            await communicator.DisconnectAsync(parameters.DeviceAddress, ct);
        }
    }
}
```

### manifest.json

```json
{
    "id": "acme.firmware-burner",
    "name": "Acme Firmware Burner",
    "version": "1.0.0",
    "entry": "Acme.Burner.dll",
    "capabilities": {
        "protocols": [],
        "tasks": ["burn_firmware"]
    }
}
```

### UI 中的配置

在 Catalytic UI 的测试步骤配置中：

1. 选择模式：**Host**
2. 任务名称：`burn_firmware`
3. 参数 JSON：
   ```json
   {
       "FilePath": "/path/to/firmware.bin",
       "DeviceAddress": "COM3",
       "CommunicatorId": "acme.serial"
   }
   ```

---

## 8. 错误处理最佳实践

### 8.1 异常类型选择

| 异常类型 | 使用场景 |
|----------|----------|
| `ArgumentException` | 参数无效（如 JSON 解析失败）|
| `InvalidOperationException` | 操作顺序错误（如未连接就发送）|
| `TimeoutException` | 操作超时 |
| `IOException` | 通讯/文件错误 |
| `NotSupportedException` | 不支持的操作 |
| `OperationCanceledException` | 用户取消（由 CancellationToken 触发）|

### 8.2 正确使用 CancellationToken

```csharp
public async Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct)
{
    // ✅ 正确：在长时间操作前检查
    ct.ThrowIfCancellationRequested();
    
    // ✅ 正确：传递给所有异步方法
    await Task.Delay(1000, ct);
    await File.ReadAllBytesAsync(path, ct);
    
    // ✅ 正确：在循环中检查
    for (int i = 0; i < 1000; i++)
    {
        ct.ThrowIfCancellationRequested();
        // ... 处理 ...
    }
}
```

### 8.3 不需要手动捕获的异常

Host 会自动捕获插件抛出的异常并：
- 将异常消息通过 `SubmitError` 返回给 Engine
- 在日志中记录详细信息

```csharp
// ✅ 正确：直接抛出，让 Host 处理
if (parameters == null)
{
    throw new ArgumentException("参数不能为空");
}

// ❌ 错误：不需要手动 try-catch 后返回错误码
try
{
    // ...
}
catch (Exception ex)
{
    return new byte[] { 0xFF }; // 不要这样做！
}
```

### 8.4 资源清理

```csharp
public async Task<byte[]> ExecuteAsync(string address, string action, ...)
{
    Stream? stream = null;
    try
    {
        stream = File.OpenRead(path);
        // ... 使用 stream ...
        return result;
    }
    finally
    {
        // ✅ 确保资源被释放
        stream?.Dispose();
    }
}

// 或使用 using 语句
public async Task<byte[]> ExecuteAsync(...)
{
    using var stream = File.OpenRead(path);
    // ... 自动释放 ...
}
```

---

## 9. 高级功能

### 9.1 插件互调

处理器可以调用其他通讯器：

```csharp
public async Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct)
{
    // 获取串口通讯器
    var serial = _context?.GetCommunicator("acme.serial");
    if (serial == null)
    {
        throw new InvalidOperationException("串口插件未加载");
    }
    
    // 使用扩展方法调用
    await serial.ConnectAsync("COM3", 5000, ct);
    await serial.SendAsync("COM3", "MEAS:VOLT?\n"u8.ToArray(), ct);
    var response = await serial.ReadAsync("COM3", 1000, ct);
    
    return response;
}
```

### 9.2 访问插件目录资源

```csharp
public Task ActivateAsync(IPluginContext context)
{
    _context = context;
    
    // 读取插件附带的配置文件
    var configPath = Path.Combine(context.PluginDirectory, "config.json");
    if (File.Exists(configPath))
    {
        var config = File.ReadAllText(configPath);
        _context.Log(LogLevel.Info, $"已加载配置: {config}");
    }
    
    return Task.CompletedTask;
}
```

### 9.3 推送异步事件

用于设备主动推送数据（如 CAN 帧监控、设备报警）或状态变更（如断线通知）。

**标准事件：设备断线**

当插件检测到设备连接意外断开时，**强烈建议**主动推送 `DeviceDisconnected` 事件，以便 Host 立即更新状态，而不是等待下一次心跳或操作失败。

```csharp
using CatalyticKit; // 引用 PluginEvents

public void OnConnectionLost(string address)
{
    // Payload 必须是 UTF8 编码的设备地址
    var payload = System.Text.Encoding.UTF8.GetBytes(address);
    
    // 使用标准常量推送事件
    _context?.PushEvent(PluginEvents.DeviceDisconnected, payload);
    
    _context?.Log(LogLevel.Warning, $"[{address}] 检测到断线，已通知 Host");
}
```

**自定义事件**

插件也可以定义自己的事件类型，供上层业务处理。
```csharp
public void OnDataReceived(byte[] data)
{
    // 推送自定义事件
    _context?.PushEvent("can_frame", data);
}
```

### 9.4 低代码模式数据推送 (Low-Code Data Push)

当使用 Catalytic Engine 的低代码模式（Engine Controlled）判断 Pass/Fail 时，Host 会使用特殊的 `FetchData` 指令来获取设备数据。为了确保 Engine 能正确解析数据（Regex/Numeric Check）：

> [!IMPORTANT]
> **约束**: 若要支持低代码判断，推送到 `DeviceData` 通道的数据 **必须是 UTF-8 编码的字符串或 JSON**。如果是私有二进制格式，低代码引擎将无法解析。

```csharp
// ✅ 正确：推送到 Host 蓄水池，供 Low-code Engine 或 Business Plugin 读取
// 使用便捷扩展方法
_context?.PushDeviceData(address, System.Text.Encoding.UTF8.GetBytes("VOLT 5.003"));
```

### 9.5 业务插件获取数据 (Processor Data Pull)

业务插件（Processor）在执行计算任务时，可以通过 `GetDeviceData` 接口从 Host 蓄水池中拉取设备刚才推送的数据。

```csharp
// 在 IProcessor.ExecuteAsync 中
public async Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct)
{
    // 假设参数中指定了要处理的设备地址
    var deviceAddress = "TCPIP0::..."; 
    
    // 从 Host 缓冲区拉取数据（拉取后缓冲区会被清空？取决于 Host 实现，当前实现为 GetAndClear）
    // 注意：GetDeviceData 是非阻塞的，如果数据没来，返回空数组
    var data = _context.GetDeviceData(deviceAddress);
    
    if (data.Length == 0)
    {
        _context.Log(LogLevel.Warning, "未获取到设备数据");
        return Array.Empty<byte>();
    }
    
    // 执行复杂计算 (如 FFT, 眼图分析)
    var result = PerformComplexAnalysis(data);
    
    return result;
}
```

---

## 10. 调试与排查问题

### 10.1 使用日志

```csharp
_context?.Log(LogLevel.Debug, "调试信息：变量值 = " + value);
_context?.Log(LogLevel.Info, "操作完成");
_context?.Log(LogLevel.Warning, "警告：超时后重试");
_context?.Log(LogLevel.Error, "错误：连接失败");
```

日志会显示在 Catalytic UI 的系统日志面板中。

### 10.2 使用 Visual Studio 附加调试

1. 启动 Catalytic Host
2. 打开 Visual Studio，选择 **Debug > Attach to Process**
3. 找到 `Catalytic.Host` 进程
4. 在你的插件代码中设置断点
5. 触发插件执行，断点会命中

### 10.3 常见问题检查清单

| 问题 | 检查项 |
|------|--------|
| 插件未加载 | manifest.json 是否存在？格式是否正确？|
| 插件未找到 | manifest.json 中的 `id` 是否与代码中的 `Id` 一致？|
| DLL 加载失败 | 是否缺少依赖 DLL？.NET 版本是否匹配？|
| 超时 | `timeoutMs` 设置是否合理？设备是否响应？|

---

## 11. 部署插件

### 目录结构

```
<Catalytic 工作目录>/
└── plugins/
    └── <你的插件 ID>/
        ├── manifest.json      (必须)
        ├── YourPlugin.dll     (必须)
        ├── dependencies.dll   (如果有依赖)
        └── config.json        (可选配置文件)
```

### 示例

```
/Users/liuzhe/Documents/MyCatalyticData/
└── plugins/
    ├── acme.serial/
    │   ├── manifest.json
    │   ├── Acme.Serial.dll
    │   └── System.IO.Ports.dll
    │
    └── acme.firmware-burner/
        ├── manifest.json
        ├── Acme.Burner.dll
        └── firmware_config.json
```

### 注意

添加或更新插件后，需要**重启 Catalytic Host** 才能加载新插件。

---

## 12. 常见问题 FAQ

### Q1: manifest.json 格式报错

**症状**：日志显示"解析清单失败"

**解决**：
- 检查 JSON 格式（https://jsonlint.com/ 在线验证）
- 确保使用双引号，不是单引号
- 检查是否有多余的逗号

### Q2: 找不到 IPlugin 实现

**症状**：日志显示 "xxx 中没有找到 IPlugin 实现"

**解决**：
- 确保你的类实现了 `ICommunicator` 或 `IProcessor`
- 确保类是 `public` 的
- 确保类不是 `abstract`

### Q3: 协议冲突

**症状**：日志显示 "协议冲突: xxx 已被其他插件注册"

**解决**：
- 确保 `Protocol` 属性值是唯一的
- 如果确实需要多个串口驱动，使用不同的 protocol 名称（如 "serial-v1", "serial-v2"）

### Q4: 插件执行超时

**症状**：测试显示超时错误

**解决**：
- 检查设备是否正确响应
- 增加 UI 中配置的超时时间
- 在插件中添加日志，定位卡住的位置

### Q5: 取消操作不生效

**症状**：点击停止按钮后，插件仍在执行

**解决**：
- 确保在循环和长时间操作中检查 `ct.ThrowIfCancellationRequested()`
- 确保将 `CancellationToken` 传递给所有异步方法

### Q6: 缺少依赖 DLL

**症状**：日志显示 "加载程序集失败" 或 "找不到类型"

**解决**：
- 将所有依赖 DLL 复制到插件目录
- 使用 `dotnet publish` 发布自包含版本

```bash
dotnet publish -c Release --self-contained false
```

---

## 附录：SDK 文件清单

| 文件 | 说明 |
|------|------|
| `CatalyticKit.dll` | SDK 动态库 |
| `IPlugin.cs` | 接口定义 |
| `CommAction.cs` | 标准动作枚举 |
| `CommunicatorExtensions.cs` | 扩展方法 |
| `PluginEvents.cs` | 标准事件常量 |
| `LogLevel.cs` | 日志级别 |

---

> **文档版本**: 4.0.0  
> **最后更新**: 2026-01-16  
> **适用 SDK 版本**: 4.0.0+
