using System.Net;
using System.Net.Sockets;

namespace Catalytic.Net;

/// <summary>
/// 高性能异步 TCP 客户端
/// 处理原始字节流，不包含任何协议逻辑
/// </summary>
public sealed class AsyncTcpClient : IAsyncDisposable
{
    private TcpClient? _client;
    private NetworkStream? _stream;
    private CancellationTokenSource? _cts;
    private Task? _receiveTask;
    
    /// <summary>连接成功事件</summary>
    public event Action? OnConnected;
    
    /// <summary>收到数据事件</summary>
    public event Func<ReadOnlyMemory<byte>, Task>? OnDataReceived;
    
    /// <summary>连接断开事件</summary>
    public event Action? OnDisconnected;
    
    /// <summary>错误发生事件</summary>
    public event Action<Exception>? OnError;
    
    /// <summary>是否已连接</summary>
    public bool IsConnected => _client?.Connected ?? false;
    
    /// <summary>本地端点地址</summary>
    public EndPoint? LocalEndPoint => _client?.Client.LocalEndPoint;
    
    /// <summary>远程端点地址</summary>
    public EndPoint? RemoteEndPoint => _client?.Client.RemoteEndPoint;
    
    /// <summary>
    /// 连接到服务器
    /// </summary>
    /// <param name="host">服务器地址</param>
    /// <param name="port">服务器端口</param>
    /// <param name="ct">取消令牌</param>
    public async Task ConnectAsync(string host, int port, CancellationToken ct = default)
    {
        // 先断开现有连接
        await DisconnectAsync();
        
        _client = new TcpClient();
        _cts = new CancellationTokenSource();
        
        try
        {
            await _client.ConnectAsync(host, port, ct);
            
            // 配置 Socket 参数
            SocketHelper.ConfigureSocket(_client);
            _stream = _client.GetStream();
            
            // 通知连接成功
            OnConnected?.Invoke();
            
            // 启动数据接收循环
            _receiveTask = ReceiveLoopAsync(_cts.Token);
        }
        catch (Exception ex)
        {
            OnError?.Invoke(ex);
            await DisconnectAsync();
            throw;
        }
    }
    
    /// <summary>
    /// 连接到服务器（使用 IP 地址）
    /// </summary>
    public Task ConnectAsync(IPAddress address, int port, CancellationToken ct = default) =>
        ConnectAsync(address.ToString(), port, ct);
    
    /// <summary>
    /// 数据接收循环
    /// </summary>
    private async Task ReceiveLoopAsync(CancellationToken ct)
    {
        var buffer = new byte[8192];
        
        try
        {
            while (!ct.IsCancellationRequested && _stream != null)
            {
                var bytesRead = await _stream.ReadAsync(buffer, ct);
                
                // 读取到 0 字节表示连接关闭
                if (bytesRead == 0) break;
                
                // 触发数据接收事件
                if (OnDataReceived != null)
                {
                    await OnDataReceived.Invoke(buffer.AsMemory(0, bytesRead));
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            OnError?.Invoke(ex);
        }
        finally
        {
            OnDisconnected?.Invoke();
        }
    }
    
    /// <summary>
    /// 发送数据
    /// </summary>
    /// <param name="data">要发送的数据</param>
    /// <param name="ct">取消令牌</param>
    public async Task SendAsync(ReadOnlyMemory<byte> data, CancellationToken ct = default)
    {
        if (_stream == null || !IsConnected)
            throw new InvalidOperationException("未连接到服务器");
        
        await _stream.WriteAsync(data, ct);
    }
    
    /// <summary>
    /// 断开连接并释放资源
    /// </summary>
    public async Task DisconnectAsync()
    {
        _cts?.Cancel();
        
        // 等待接收循环结束
        if (_receiveTask != null)
        {
            try { await _receiveTask; } catch { }
            _receiveTask = null;
        }
        
        // 释放资源
        _stream?.Dispose();
        _stream = null;
        _client?.Dispose();
        _client = null;
        _cts?.Dispose();
        _cts = null;
    }
    
    /// <summary>
    /// 异步释放资源
    /// </summary>
    public async ValueTask DisposeAsync() => await DisconnectAsync();
}