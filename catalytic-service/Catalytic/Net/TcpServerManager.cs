using System.Collections.Concurrent;

namespace Catalytic.Net;

/// <summary>
/// TCP 服务器管理器
/// 在 AsyncTcpServer 基础上提供客户端会话管理和数据缓冲功能
/// </summary>
public sealed class TcpServerManager : IAsyncDisposable
{
    private readonly AsyncTcpServer _server;
    private readonly ConcurrentDictionary<Guid, ClientInfo> _clients = new();

    /// <summary>
    /// 扩展的客户端信息
    /// 包含会话信息、数据缓冲区和自定义标签
    /// </summary>
    public sealed class ClientInfo
    {
        /// <summary>客户端唯一标识</summary>
        public Guid Id { get; }
        
        /// <summary>底层会话对象</summary>
        public ClientSession Session { get; }
        
        /// <summary>数据缓冲区，用于累积接收到的数据</summary>
        public MemoryStream Buffer { get; } = new();
        
        /// <summary>自定义标签，可存储任意用户数据</summary>
        public object? Tag { get; set; }
        
        /// <summary>最后活动时间（UTC）</summary>
        public DateTime LastActivityAt { get; internal set; }

        internal ClientInfo(Guid id, ClientSession session)
        {
            Id = id;
            Session = session;
            LastActivityAt = DateTime.UtcNow;
        }
    }

    /// <summary>新客户端连接事件</summary>
    public event Action<ClientInfo>? OnClientConnected;
    
    /// <summary>收到数据事件（返回累积的缓冲区，调用者决定是否完整）</summary>
    public event Func<ClientInfo, ReadOnlyMemory<byte>, Task>? OnDataReceived;
    
    /// <summary>客户端断开连接事件</summary>
    public event Action<Guid>? OnClientDisconnected;
    
    /// <summary>错误发生事件</summary>
    public event Action<Guid, Exception>? OnError;

    /// <summary>监听端口</summary>
    public int Port => _server.Port;
    
    /// <summary>服务器是否正在运行</summary>
    public bool IsRunning => _server.IsRunning;
    
    /// <summary>当前连接数</summary>
    public int ClientCount => _clients.Count;

    /// <summary>
    /// 创建 TCP 服务器管理器
    /// </summary>
    /// <param name="port">监听端口</param>
    public TcpServerManager(int port)
    {
        _server = new AsyncTcpServer(port);
        
        // 订阅底层服务器事件
        _server.OnClientConnected += HandleClientConnected;
        _server.OnDataReceived += HandleDataReceived;
        _server.OnClientDisconnected += HandleClientDisconnected;
        _server.OnError += (id, ex) => OnError?.Invoke(id, ex);
    }

    /// <summary>
    /// 启动服务器
    /// </summary>
    public void Start() => _server.Start();

    /// <summary>
    /// 处理新客户端连接
    /// </summary>
    private void HandleClientConnected(ClientSession session)
    {
        var info = new ClientInfo(session.Id, session);
        _clients.TryAdd(session.Id, info);
        OnClientConnected?.Invoke(info);
    }

    /// <summary>
    /// 处理接收到的数据
    /// 数据会累积到缓冲区中
    /// </summary>
    private async Task HandleDataReceived(Guid clientId, ReadOnlyMemory<byte> data)
    {
        if (!_clients.TryGetValue(clientId, out var info)) return;

        // 更新活动时间并写入缓冲区
        info.LastActivityAt = DateTime.UtcNow;
        info.Buffer.Write(data.Span);
        
        // 触发事件，使用 GetBuffer() 避免不必要的数组分配
        if (OnDataReceived != null)
        {
            var bufferData = info.Buffer.GetBuffer().AsMemory(0, (int)info.Buffer.Length);
            await OnDataReceived.Invoke(info, bufferData);
        }
    }

    /// <summary>
    /// 处理客户端断开连接
    /// </summary>
    private void HandleClientDisconnected(Guid clientId)
    {
        if (_clients.TryRemove(clientId, out var info))
        {
            info.Buffer.Dispose();
        }
        OnClientDisconnected?.Invoke(clientId);
    }

    /// <summary>
    /// 清空客户端缓冲区
    /// 在处理完完整消息后调用
    /// </summary>
    public void ClearBuffer(Guid clientId)
    {
        if (_clients.TryGetValue(clientId, out var info))
        {
            info.Buffer.SetLength(0);
        }
    }

    /// <summary>
    /// 消费缓冲区前 N 个字节
    /// 在处理完一条消息后调用，保留剩余数据
    /// </summary>
    /// <param name="clientId">客户端ID</param>
    /// <param name="length">要消费的字节数</param>
    public void ConsumeBuffer(Guid clientId, int length)
    {
        if (!_clients.TryGetValue(clientId, out var info)) return;
        
        var remaining = info.Buffer.Length - length;
        if (remaining <= 0)
        {
            info.Buffer.SetLength(0);
        }
        else
        {
            // 将剩余数据移动到缓冲区开头
            var buffer = info.Buffer.GetBuffer();
            Buffer.BlockCopy(buffer, length, buffer, 0, (int)remaining);
            info.Buffer.SetLength(remaining);
        }
    }

    /// <summary>
    /// 发送数据到指定客户端
    /// </summary>
    public Task<bool> SendAsync(Guid clientId, ReadOnlyMemory<byte> data, CancellationToken ct = default) =>
        _server.SendAsync(clientId, data, ct);

    /// <summary>
    /// 广播数据到所有客户端
    /// </summary>
    public Task BroadcastAsync(ReadOnlyMemory<byte> data, CancellationToken ct = default) =>
        _server.BroadcastAsync(data, ct);

    /// <summary>
    /// 断开指定客户端的连接
    /// </summary>
    public void Disconnect(Guid clientId) => _server.Disconnect(clientId);

    /// <summary>
    /// 获取客户端信息
    /// </summary>
    public ClientInfo? GetClient(Guid clientId) =>
        _clients.TryGetValue(clientId, out var info) ? info : null;

    /// <summary>
    /// 获取所有已连接的客户端
    /// </summary>
    public IEnumerable<ClientInfo> GetAllClients() => _clients.Values;

    /// <summary>
    /// 异步释放资源
    /// </summary>
    public async ValueTask DisposeAsync()
    {
        await _server.DisposeAsync();
        
        // 释放所有客户端缓冲区
        foreach (var info in _clients.Values)
        {
            info.Buffer.Dispose();
        }
        _clients.Clear();
    }
}
