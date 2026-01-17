namespace Catalytic.Engine;

/// <summary>
/// Engine 任务事件参数
/// 当 Engine 需要 Host 执行设备操作时传递
/// </summary>
public sealed class EngineTaskEventArgs
{
    /// <summary>
    /// 槽位 ID
    /// </summary>
    public required int SlotId { get; init; }
    
    /// <summary>
    /// 任务 ID（用于提交结果时使用）
    /// </summary>
    public required ulong TaskId { get; init; }
    
    /// <summary>
    /// 设备类型（如 "dmm"）
    /// </summary>
    public required string DeviceType { get; init; }
    
    /// <summary>
    /// 设备地址（如 "TCPIP0::192.168.1.101::INSTR"）
    /// </summary>
    public required string DeviceAddress { get; init; }
    
    /// <summary>
    /// 通讯插件 ID（如 "catalytic.serial"）
    /// </summary>
    public required string PluginId { get; init; }
    
    /// <summary>
    /// 动作类型（"send"/"query"/"wait"）
    /// </summary>
    public required string ActionType { get; init; }
    
    /// <summary>
    /// 指令数据
    /// </summary>
    public required byte[] Payload { get; init; }
    
    /// <summary>
    /// 超时时间（毫秒）
    /// </summary>
    public required int TimeoutMs { get; init; }
    
    /// <summary>
    /// 获取 Payload 的字符串形式（UTF-8）
    /// </summary>
    public string PayloadString => System.Text.Encoding.UTF8.GetString(Payload);
}

/// <summary>
/// Host 任务事件参数
/// 当 Engine 需要 Host 执行复杂任务时传递（HostControlled 模式）
/// </summary>
public sealed class HostTaskEventArgs
{
    /// <summary>
    /// 槽位 ID
    /// </summary>
    public required int SlotId { get; init; }
    
    /// <summary>
    /// 任务 ID（用于提交结果时使用）
    /// </summary>
    public required ulong TaskId { get; init; }
    
    /// <summary>
    /// 任务名称
    /// </summary>
    public required string TaskName { get; init; }
    
    /// <summary>
    /// 任务参数
    /// </summary>
    public required byte[] Params { get; init; }
    
    /// <summary>
    /// 获取参数的 JSON 字符串形式
    /// </summary>
    public string ParamsJson => System.Text.Encoding.UTF8.GetString(Params);
    
    /// <summary>
    /// 超时时间（毫秒）
    /// </summary>
    public required int TimeoutMs { get; init; }
}

/// <summary>
/// 日志事件参数
/// 当 Engine 产生日志或错误时传递
/// </summary>
public sealed class LogEventArgs
{
    /// <summary>
    /// 时间戳（Unix 毫秒）
    /// </summary>
    public required ulong Timestamp { get; init; }
    
    /// <summary>
    /// 日志级别 (info/io/warn/error)
    /// </summary>
    public required string Level { get; init; }
    
    /// <summary>
    /// 日志来源 (check/device/executor)
    /// </summary>
    public required string Source { get; init; }
    
    /// <summary>
    /// 日志消息
    /// </summary>
    public required string Message { get; init; }
}
