using System.Net.Sockets;

namespace Catalytic.Net;

/// <summary>
/// Socket 工具类
/// 提供共享的 Socket 配置方法
/// </summary>
internal static class SocketHelper
{
    /// <summary>
    /// 配置 Socket 以获得最佳性能
    /// </summary>
    /// <param name="client">要配置的 TcpClient</param>
    public static void ConfigureSocket(TcpClient client)
    {
        // 禁用 Nagle 算法，减少小包延迟
        client.NoDelay = true;
        
        // 设置较大的缓冲区以提高吞吐量
        client.ReceiveBufferSize = 65536;
        client.SendBufferSize = 65536;
        
        // 启用 TCP KeepAlive，检测死连接
        client.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.KeepAlive, true);
    }
}
