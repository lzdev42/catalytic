namespace CatalyticKit;

public static class CommunicatorExtensions
{
    /// <summary>
    /// 使用枚举执行动作
    /// </summary>
    public static Task<byte[]> ExecuteAsync(
        this ICommunicator communicator,
        string address,
        CommAction action,
        byte[] payload,
        int timeoutMs,
        CancellationToken ct)
    {
        return communicator.ExecuteAsync(address, action.ToString().ToLowerInvariant(), payload, timeoutMs, ct);
    }
    
    /// <summary>
    /// 发送数据
    /// </summary>
    public static Task SendAsync(this ICommunicator communicator, string address, byte[] data, CancellationToken ct = default)
        => communicator.ExecuteAsync(address, CommAction.Send, data, 0, ct);
    
    /// <summary>
    /// 读取可用数据
    /// </summary>
    public static Task<byte[]> ReadAsync(this ICommunicator communicator, string address, int timeoutMs, CancellationToken ct = default)
        => communicator.ExecuteAsync(address, CommAction.Read, [], timeoutMs, ct);
    
    /// <summary>
    /// 建立连接
    /// </summary>
    public static Task ConnectAsync(this ICommunicator communicator, string address, int timeoutMs = 5000, CancellationToken ct = default)
        => communicator.ExecuteAsync(address, CommAction.Connect, [], timeoutMs, ct);
    
    /// <summary>
    /// 断开连接
    /// </summary>
    public static Task DisconnectAsync(this ICommunicator communicator, string address, CancellationToken ct = default)
        => communicator.ExecuteAsync(address, CommAction.Disconnect, [], 1000, ct);
    
    /// <summary>
    /// 查询连接状态
    /// </summary>
    public static Task<byte[]> GetStatusAsync(this ICommunicator communicator, string address, CancellationToken ct = default)
        => communicator.ExecuteAsync(address, CommAction.Status, [], 1000, ct);
}
