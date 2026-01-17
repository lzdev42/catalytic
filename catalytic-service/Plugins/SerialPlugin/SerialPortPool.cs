using System.Collections.Concurrent;
using System.IO.Ports;

namespace SerialPlugin;

/// <summary>
/// 串口连接池
/// 避免频繁开关串口，提高性能和稳定性
/// </summary>
internal sealed class SerialPortPool : IDisposable
{
    private readonly ConcurrentDictionary<string, PooledSerialPort> _ports = new();
    private readonly SemaphoreSlim _createLock = new(1, 1);
    private bool _disposed;

    /// <summary>
    /// 获取或创建串口连接
    /// </summary>
    public async Task<PooledSerialPort> GetOrCreateAsync(
        string portName, 
        int baudRate = 9600,
        CancellationToken ct = default)
    {
        if (_disposed) throw new ObjectDisposedException(nameof(SerialPortPool));

        // 快速路径：已存在且打开
        if (_ports.TryGetValue(portName, out var existing) && existing.IsOpen)
        {
            return existing;
        }

        // 慢速路径：创建新连接
        await _createLock.WaitAsync(ct);
        try
        {
            // 双重检查
            if (_ports.TryGetValue(portName, out existing) && existing.IsOpen)
            {
                return existing;
            }

            // 关闭旧连接（如果存在但已关闭）
            if (existing != null)
            {
                existing.Dispose();
                _ports.TryRemove(portName, out _);
            }

            // 创建新连接
            var port = new PooledSerialPort(portName, baudRate);
            port.Open();
            
            _ports[portName] = port;
            return port;
        }
        finally
        {
            _createLock.Release();
        }
    }

    /// <summary>
    /// 关闭指定串口
    /// </summary>
    public void Close(string portName)
    {
        if (_ports.TryRemove(portName, out var port))
        {
            port.Dispose();
        }
    }

    /// <summary>
    /// 关闭所有串口
    /// </summary>
    public void CloseAll()
    {
        foreach (var kvp in _ports)
        {
            kvp.Value.Dispose();
        }
        _ports.Clear();
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        CloseAll();
        _createLock.Dispose();
    }
}

/// <summary>
/// 池化串口包装器
/// 提供线程安全的读写操作
/// </summary>
internal sealed class PooledSerialPort : IDisposable
{
    private readonly SerialPort _port;
    private readonly SemaphoreSlim _lock = new(1, 1);
    private bool _disposed;

    public bool IsOpen => !_disposed && _port.IsOpen;
    public string PortName => _port.PortName;

    public PooledSerialPort(string portName, int baudRate)
    {
        _port = new SerialPort(portName, baudRate)
        {
            ReadTimeout = 5000,
            WriteTimeout = 5000,
            NewLine = "\n",
            Encoding = System.Text.Encoding.ASCII
        };
    }

    public void Open()
    {
        if (!_port.IsOpen)
        {
            _port.Open();
            _port.DiscardInBuffer();
            _port.DiscardOutBuffer();
        }
    }

    /// <summary>
    /// 发送数据（不等待响应）
    /// </summary>
    public async Task SendAsync(byte[] data, CancellationToken ct)
    {
        await _lock.WaitAsync(ct);
        try
        {
            _port.DiscardInBuffer();
            await _port.BaseStream.WriteAsync(data, 0, data.Length, ct);
            await _port.BaseStream.FlushAsync(ct);
        }
        finally
        {
            _lock.Release();
        }
    }

    /// <summary>
    /// 查询（发送后等待响应）
    /// </summary>
    public async Task<byte[]> QueryAsync(byte[] data, int timeoutMs, CancellationToken ct)
    {
        await _lock.WaitAsync(ct);
        try
        {
            _port.DiscardInBuffer();
            
            // 发送
            await _port.BaseStream.WriteAsync(data, 0, data.Length, ct);
            await _port.BaseStream.FlushAsync(ct);

            // 等待响应
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            cts.CancelAfter(timeoutMs);

            var buffer = new byte[4096];
            var ms = new MemoryStream();

            try
            {
                // 读取直到超时或收到换行符
                while (true)
                {
                    var bytesRead = await _port.BaseStream.ReadAsync(buffer, 0, buffer.Length, cts.Token);
                    if (bytesRead == 0) break;
                    
                    ms.Write(buffer, 0, bytesRead);
                    
                    // 检查是否包含行结束符
                    if (ContainsLineEnding(buffer, bytesRead))
                        break;
                }
            }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            {
                // 超时，返回已收到的数据
            }

            return ms.ToArray();
        }
        finally
        {
            _lock.Release();
        }
    }

    private static bool ContainsLineEnding(byte[] buffer, int length)
    {
        for (int i = 0; i < length; i++)
        {
            if (buffer[i] == '\n' || buffer[i] == '\r')
                return true;
        }
        return false;
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        
        try
        {
            if (_port.IsOpen)
                _port.Close();
        }
        catch { /* ignore */ }
        
        _port.Dispose();
        _lock.Dispose();
    }
}
