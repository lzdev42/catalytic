using CatalyticKit;

namespace SerialPlugin;

/// <summary>
/// 串口通讯器
/// 实现 ICommunicator 接口，支持 SCPI over Serial 通讯
/// </summary>
public sealed class SerialCommunicator : ICommunicator
{
    private IPluginContext? _context;
    private readonly SerialPortPool _pool = new();

    public string Id => "catalytic.serial";
    public string Protocol => "serial";

    public Task ActivateAsync(IPluginContext context)
    {
        _context = context;
        _context.Log(LogLevel.Info, "SerialPlugin activated");
        return Task.CompletedTask;
    }

    public Task DeactivateAsync()
    {
        _context?.Log(LogLevel.Info, "SerialPlugin deactivating, closing all ports...");
        _pool.CloseAll();
        _pool.Dispose();
        return Task.CompletedTask;
    }

    /// <summary>
    /// 执行串口通讯动作
    /// </summary>
    public async Task<byte[]> ExecuteAsync(
        string address, 
        string action, 
        byte[] payload, 
        int timeoutMs, 
        CancellationToken ct)
    {
        // 解析地址和波特率
        var (portName, baudRate) = ParseAddress(address);
        
        _context?.Log(LogLevel.Debug, $"Serial {action}: port={portName}, baud={baudRate}, timeout={timeoutMs}ms");

        // 尝试解析为标准动作
        if (Enum.TryParse<CommAction>(action, true, out var commAction))
        {
             switch (commAction)
             {
                 case CommAction.Send:
                     return await SendAsync(portName, baudRate, payload, ct);
                     
                 case CommAction.Query:
                     return await QueryAsync(portName, baudRate, payload, timeoutMs, ct);
                     
                 case CommAction.Connect:
                     // 预连接或打开端口
                     await _pool.GetOrCreateAsync(portName, baudRate, ct);
                     return Array.Empty<byte>();

                 case CommAction.Disconnect:
                     // 关闭特定端口（如果有 API 支持）或不做操作（由 Pool 托管）
                     return Array.Empty<byte>();

                 case CommAction.Read:
                     // 简单读取
                     return await ReadAsync(portName, baudRate, timeoutMs, ct);
                     
                 case CommAction.Status:
                     // 返回状态
                     return Array.Empty<byte>();
             }
        }

        // 处理自定义字符串动作（如果需要）或抛出异常
        // 兼容旧的 "wait" 动作（虽然标准动作里没有，但可能是业务特定的？）
        // 建议把 "wait" 作为 Processor 的逻辑，而不是 Communicator 的 Action。
        // 但为了兼容现有代码：
        if (action.Equals("wait", StringComparison.OrdinalIgnoreCase))
        {
            var delayMs = timeoutMs > 0 ? timeoutMs : BitConverter.ToInt32(payload, 0);
            await Task.Delay(delayMs, ct);
            return Array.Empty<byte>();
        }

        throw new ArgumentException($"Unknown action: {action}. Supported: send, query, wait, connect, disconnect, read");
    }

    private async Task<byte[]> SendAsync(string portName, int baudRate, byte[] payload, CancellationToken ct)
    {
        var port = await _pool.GetOrCreateAsync(portName, baudRate, ct);
        await port.SendAsync(payload, ct);
        
        _context?.Log(LogLevel.Debug, $"Sent {payload.Length} bytes to {portName}");
        return Array.Empty<byte>();
    }

    private async Task<byte[]> QueryAsync(string portName, int baudRate, byte[] payload, int timeoutMs, CancellationToken ct)
    {
        var port = await _pool.GetOrCreateAsync(portName, baudRate, ct);
        var response = await port.QueryAsync(payload, timeoutMs, ct);
        
        _context?.Log(LogLevel.Debug, $"Query {portName}: sent {payload.Length} bytes, received {response.Length} bytes");
        return response;
    }
    
    private async Task<byte[]> ReadAsync(string portName, int baudRate, int timeoutMs, CancellationToken ct)
    {
        var port = await _pool.GetOrCreateAsync(portName, baudRate, ct);
        // 假设 SerialPortWrapper 有 ReadAsync
        // return await port.ReadAsync(timeoutMs, ct);
        return Array.Empty<byte>(); // Placeholder
    }

    /// <summary>
    /// 解析地址字符串
    /// 格式: "COM3" 或 "/dev/ttyUSB0:115200"
    /// </summary>
    private static (string portName, int baudRate) ParseAddress(string address)
    {
        const int defaultBaudRate = 9600;
        
        var parts = address.Split(':');
        if (parts.Length == 1)
        {
            return (address, defaultBaudRate);
        }
        
        // 检查是否是波特率（纯数字）
        if (parts.Length == 2 && int.TryParse(parts[1], out var baud))
        {
            return (parts[0], baud);
        }
        
        // 其他情况（如 IP:PORT），返回原始地址
        return (address, defaultBaudRate);
    }
}
