using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;

namespace Catalytic.Net;

/// <summary>
/// 客户端会话信息
/// 封装了单个客户端连接的所有相关数据
/// </summary>
public sealed class ClientSession
{
    /// <summary>会话唯一标识</summary>
    public Guid Id { get; }
    
    /// <summary>底层 TcpClient 对象</summary>
    public TcpClient Client { get; }
    
    /// <summary>网络流，用于读写数据</summary>
    public NetworkStream Stream { get; }
    
    /// <summary>远程端点地址</summary>
    public EndPoint? RemoteEndPoint { get; }
    
    /// <summary>连接建立时间（UTC）</summary>
    public DateTime ConnectedAt { get; }
    
    internal ClientSession(Guid id, TcpClient client)
    {
        Id = id;
        Client = client;
        Stream = client.GetStream();
        RemoteEndPoint = client.Client.RemoteEndPoint;
        ConnectedAt = DateTime.UtcNow;
    }
}

/// <summary>
/// 高性能异步 TCP 服务器
/// 处理原始字节流，不包含任何协议逻辑
/// </summary>
public sealed class AsyncTcpServer : IAsyncDisposable
{
    private readonly TcpListener _listener;
    private readonly ConcurrentDictionary<Guid, ClientSession> _sessions = new();
    private readonly CancellationTokenSource _cts = new();
    private Task? _acceptTask;
    private int _disposed;  // 用于线程安全的释放检查
    
    /// <summary>新客户端连接事件</summary>
    public event Action<ClientSession>? OnClientConnected;
    
    /// <summary>收到数据事件（客户端ID + 数据）</summary>
    public event Func<Guid, ReadOnlyMemory<byte>, Task>? OnDataReceived;
    
    /// <summary>客户端断开连接事件</summary>
    public event Action<Guid>? OnClientDisconnected;
    
    /// <summary>错误发生事件</summary>
    public event Action<Guid, Exception>? OnError;
    
    /// <summary>监听端口</summary>
    public int Port { get; }
    
    /// <summary>服务器是否正在运行</summary>
    public bool IsRunning { get; private set; }
    
    /// <summary>
    /// 创建 TCP 服务器
    /// </summary>
    /// <param name="port">监听端口</param>
    /// <param name="address">绑定地址，默认为所有接口</param>
    public AsyncTcpServer(int port, IPAddress? address = null)
    {
        Port = port;
        _listener = new TcpListener(address ?? IPAddress.Any, port);
    }
    
    /// <summary>
    /// 启动服务器
    /// </summary>
    public void Start()
    {
        if (IsRunning) return;
        
        _listener.Start();
        IsRunning = true;
        _acceptTask = AcceptLoopAsync(_cts.Token);
    }
    
    /// <summary>
    /// 连接接受循环
    /// </summary>
    private async Task AcceptLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                var client = await _listener.AcceptTcpClientAsync(ct);
                var session = new ClientSession(Guid.NewGuid(), client);
                
                // 配置 Socket 参数
                SocketHelper.ConfigureSocket(client);
                _sessions.TryAdd(session.Id, session);
                
                // 通知新连接
                OnClientConnected?.Invoke(session);
                
                // 启动该客户端的数据接收循环（不等待）
                _ = ReceiveLoopAsync(session, ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex)
            {
                OnError?.Invoke(Guid.Empty, ex);
            }
        }
    }
    
    /// <summary>
    /// 单个客户端的数据接收循环
    /// </summary>
    private async Task ReceiveLoopAsync(ClientSession session, CancellationToken ct)
    {
        var buffer = new byte[8192];
        
        try
        {
            while (!ct.IsCancellationRequested && session.Client.Connected)
            {
                var bytesRead = await session.Stream.ReadAsync(buffer, ct);
                
                // 读取到 0 字节表示连接关闭
                if (bytesRead == 0) break;
                
                // 触发数据接收事件
                if (OnDataReceived != null)
                {
                    await OnDataReceived.Invoke(session.Id, buffer.AsMemory(0, bytesRead));
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            OnError?.Invoke(session.Id, ex);
        }
        finally
        {
            // 清理并通知断开
            _sessions.TryRemove(session.Id, out _);
            session.Client.Dispose();
            OnClientDisconnected?.Invoke(session.Id);
        }
    }
    
    /// <summary>
    /// 发送数据到指定客户端
    /// </summary>
    /// <param name="clientId">客户端ID</param>
    /// <param name="data">要发送的数据</param>
    /// <param name="ct">取消令牌</param>
    /// <returns>发送成功返回 true，客户端不存在或失败返回 false</returns>
    public async Task<bool> SendAsync(Guid clientId, ReadOnlyMemory<byte> data, CancellationToken ct = default)
    {
        if (!_sessions.TryGetValue(clientId, out var session)) return false;
        
        try
        {
            await session.Stream.WriteAsync(data, ct);
            return true;
        }
        catch
        {
            return false;
        }
    }
    
    /// <summary>
    /// 广播数据到所有已连接的客户端
    /// </summary>
    public async Task BroadcastAsync(ReadOnlyMemory<byte> data, CancellationToken ct = default)
    {
        var tasks = _sessions.Keys.Select(id => SendAsync(id, data, ct));
        await Task.WhenAll(tasks);
    }
    
    /// <summary>
    /// 断开指定客户端的连接
    /// </summary>
    public void Disconnect(Guid clientId)
    {
        if (_sessions.TryRemove(clientId, out var session))
        {
            session.Client.Dispose();
        }
    }
    
    /// <summary>
    /// 获取客户端会话
    /// </summary>
    public ClientSession? GetSession(Guid clientId) => 
        _sessions.TryGetValue(clientId, out var s) ? s : null;
    
    /// <summary>
    /// 获取所有已连接客户端的 ID
    /// </summary>
    public IEnumerable<Guid> GetAllClientIds() => _sessions.Keys;
    
    /// <summary>
    /// 当前连接数
    /// </summary>
    public int ClientCount => _sessions.Count;
    
    /// <summary>
    /// 异步释放资源
    /// 使用 Interlocked 确保只执行一次
    /// </summary>
    public async ValueTask DisposeAsync()
    {
        // 防止重复释放
        if (Interlocked.Exchange(ref _disposed, 1) == 1) return;
        
        IsRunning = false;
        await _cts.CancelAsync();
        _listener.Stop();
        
        // 等待接受循环结束
        if (_acceptTask != null)
        {
            try { await _acceptTask; } catch { }
        }
        
        // 关闭所有客户端连接
        foreach (var session in _sessions.Values)
        {
            session.Client.Dispose();
        }
        _sessions.Clear();
        _cts.Dispose();
    }
}