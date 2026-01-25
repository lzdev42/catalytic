using System.Collections.Concurrent;
using System.Text.Json;
using CatalyticKit;
using Catalytic.Plugin;
using Catalytic.Engine;

namespace Catalytic;

/// <summary>
/// 设备连接状态
/// </summary>
public enum DeviceConnectionState
{
    Disconnected,
    Connecting,
    Connected,
    Error
}

/// <summary>
/// 设备连接信息
/// </summary>
/// <summary>
/// 设备连接信息 (Immutable Record)
/// </summary>
public sealed record DeviceConnection
{
    public required string DeviceId { get; init; }
    public required string DeviceTypeId { get; init; }
    public required string Address { get; init; }
    public required string PluginId { get; init; }
    public DeviceConnectionState State { get; init; } = DeviceConnectionState.Disconnected;
    public string? ErrorMessage { get; init; }
}

/// <summary>
/// 设备连接管理器
/// 负责设备连接的生命周期管理
/// </summary>
public sealed class DeviceManager
{
    private readonly PluginManager _pluginManager;
    private readonly Engine.Engine _engine;
    private readonly ConcurrentDictionary<string, DeviceConnection> _connections = new();

    /// <summary>
    /// 日志回调
    /// </summary>
    public Action<LogLevel, string, string>? OnLog { get; set; }

    public DeviceManager(PluginManager pluginManager, Engine.Engine engine)
    {
        _pluginManager = pluginManager;
        _engine = engine;
    }

    /// <summary>
    /// 连接指定设备
    /// </summary>
    public async Task<(bool Success, string? Error)> ConnectAsync(string deviceId, CancellationToken ct = default)
    {
        // 从 Engine 配置中查找设备
        var deviceInfo = FindDeviceInConfig(deviceId);
        if (deviceInfo == null)
            return (false, $"设备不存在: {deviceId}");

        var (address, deviceTypeId, pluginId) = deviceInfo.Value;

        // 获取或创建连接记录
        var connection = _connections.GetOrAdd(deviceId, _ => new DeviceConnection
        {
            DeviceId = deviceId,
            DeviceTypeId = deviceTypeId,
            Address = address,
            PluginId = pluginId,
            State = DeviceConnectionState.Disconnected
        });

        if (connection.State == DeviceConnectionState.Connected)
        {
            Log(LogLevel.Debug, $"设备 {deviceId} 已经连接");
            return (true, null);
        }

        // 获取通讯器
        var communicator = _pluginManager.GetCommunicatorById(pluginId);
        if (communicator == null)
        {
            var error = $"找不到通讯器插件: {pluginId}";
            var errorState = connection with { State = DeviceConnectionState.Error, ErrorMessage = error };
            _connections[deviceId] = errorState;
            return (false, error);
        }

        // 尝试连接
        var connectingState = connection with { State = DeviceConnectionState.Connecting, ErrorMessage = null };
        _connections[deviceId] = connectingState;

        try
        {
            Log(LogLevel.Info, $"正在连接设备 {deviceId} ({address})...");
            await communicator.ExecuteAsync(
                address,
                "Connect",
                [],
                5000,  // 5秒超时
                ct);

            var connectedState = connection with { State = DeviceConnectionState.Connected };
            
            // CAS: 只有当当前状态仍为 connectingState 时才更新
            if (_connections.TryUpdate(deviceId, connectedState, connectingState))
            {
                Log(LogLevel.Info, $"设备 {deviceId} 连接成功");
                return (true, null);
            }
            else
            {
                var msg = $"设备 {deviceId} 连接期间状态发生变更，放弃更新为 Connected";
                Log(LogLevel.Warning, msg);
                // 可选：既然我们物理上连接成功了但状态被抢占（比如被断开），可能需要回滚物理连接？
                // 暂时保持最小修复：只防止状态覆盖。
                return (false, "Connection prohibited by concurrent state change");
            }
        }
        catch (Exception ex)
        {
            var errorState = connection with { State = DeviceConnectionState.Error, ErrorMessage = ex.Message };
            // CAS: 尝试更新错误状态，如果失败说明状态已变，不再强行覆盖
            _connections.TryUpdate(deviceId, errorState, connectingState);
            
            Log(LogLevel.Error, $"设备 {deviceId} 连接失败: {ex.Message}");
            return (false, ex.Message);
        }
    }

    /// <summary>
    /// 断开指定设备
    /// </summary>
    public async Task<(bool Success, string? Error)> DisconnectAsync(string deviceId, CancellationToken ct = default)
    {
        if (!_connections.TryGetValue(deviceId, out var connection))
        {
            // 设备未在连接管理中，视为已断开
            return (true, null);
        }

        if (connection.State == DeviceConnectionState.Disconnected)
        {
            Log(LogLevel.Debug, $"设备 {deviceId} 已经断开");
            return (true, null);
        }

        var communicator = _pluginManager.GetCommunicatorById(connection.PluginId);
        if (communicator == null)
        {
            // 插件不存在，直接标记为断开
            var disconnectedState = connection with { State = DeviceConnectionState.Disconnected, ErrorMessage = null };
            _connections.TryUpdate(deviceId, disconnectedState, connection);
            return (true, null);
        }

        try
        {
            Log(LogLevel.Info, $"正在断开设备 {deviceId}...");
            await communicator.ExecuteAsync(
                connection.Address,
                "Disconnect",
                [],
                3000,
                ct);

            var disconnectedState = connection with { State = DeviceConnectionState.Disconnected, ErrorMessage = null };
            _connections.TryUpdate(deviceId, disconnectedState, connection);
            Log(LogLevel.Info, $"设备 {deviceId} 已断开");
            return (true, null);
        }
        catch (Exception ex)
        {
            // 即使断开失败，也标记为断开
            var disconnectedState = connection with { State = DeviceConnectionState.Disconnected, ErrorMessage = null };
            _connections.TryUpdate(deviceId, disconnectedState, connection);
            Log(LogLevel.Warning, $"设备 {deviceId} 断开时出错: {ex.Message}");
            return (true, null);
        }
    }

    /// <summary>
    /// 获取所有设备的连接状态
    /// </summary>
    public IReadOnlyList<DeviceConnection> GetAllConnectionStatus()
    {
        // 同步配置中的设备列表
        SyncWithEngineConfig();
        return _connections.Values.ToList();
    }

    /// <summary>
    /// 获取指定设备的连接状态
    /// </summary>
    public DeviceConnection? GetConnectionStatus(string deviceId)
    {
        SyncWithEngineConfig();
        return _connections.TryGetValue(deviceId, out var connection) ? connection : null;
    }

    /// <summary>
    /// 检查设备是否已连接
    /// </summary>
    public bool IsConnected(string deviceId)
    {
        return _connections.TryGetValue(deviceId, out var connection) 
               && connection.State == DeviceConnectionState.Connected;
    }

    /// <summary>
    /// 断开所有设备
    /// </summary>
    public async Task DisconnectAllAsync(CancellationToken ct = default)
    {
        Log(LogLevel.Info, "正在断开所有设备...");
        foreach (var deviceId in _connections.Keys.ToList())
        {
            await DisconnectAsync(deviceId, ct);
        }
        Log(LogLevel.Info, "所有设备已断开");
    }

    /// <summary>
    /// 当设备配置被删除时调用
    /// </summary>
    public async Task OnDeviceDeletedAsync(string deviceId, CancellationToken ct = default)
    {
        if (_connections.TryGetValue(deviceId, out var connection))
        {
            if (connection.State == DeviceConnectionState.Connected)
            {
                await DisconnectAsync(deviceId, ct);
            }
            _connections.TryRemove(deviceId, out _);
        }
    }

    /// <summary>
    /// 从 Engine 配置中查找设备
    /// </summary>
    private (string Address, string DeviceTypeId, string PluginId)? FindDeviceInConfig(string deviceId)
    {
        try
        {
            var configJson = _engine.GetConfig();
            using var doc = JsonDocument.Parse(configJson);
            
            if (!doc.RootElement.TryGetProperty("device_types", out var types))
                return null;

            foreach (var type in types.EnumerateArray())
            {
                var typeId = type.GetProperty("id").GetString() ?? "";
                var pluginId = type.TryGetProperty("plugin_id", out var p) ? p.GetString() ?? "" : "";

                // 检查 "devices" 或 "instances"
                JsonElement deviceList;
                if (!type.TryGetProperty("devices", out deviceList) && 
                    !type.TryGetProperty("instances", out deviceList))
                    continue;

                if (deviceList.ValueKind != JsonValueKind.Array)
                    continue;

                foreach (var dev in deviceList.EnumerateArray())
                {
                    var id = dev.TryGetProperty("id", out var idProp) ? idProp.GetString() : null;
                    if (id == deviceId)
                    {
                        var address = dev.TryGetProperty("address", out var addr) ? addr.GetString() ?? "" : "";
                        return (address, typeId, pluginId);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log(LogLevel.Error, $"查找设备配置失败: {ex.Message}");
        }

        return null;
    }

    /// <summary>
    /// 同步 Engine 配置中的设备列表
    /// </summary>
    private void SyncWithEngineConfig()
    {
        try
        {
            var configJson = _engine.GetConfig();
            using var doc = JsonDocument.Parse(configJson);
            
            var configDeviceIds = new HashSet<string>();

            if (doc.RootElement.TryGetProperty("device_types", out var types))
            {
                foreach (var type in types.EnumerateArray())
                {
                    var typeId = type.GetProperty("id").GetString() ?? "";
                    var pluginId = type.TryGetProperty("plugin_id", out var p) ? p.GetString() ?? "" : "";

                    JsonElement deviceList;
                    if (!type.TryGetProperty("devices", out deviceList) && 
                        !type.TryGetProperty("instances", out deviceList))
                        continue;

                    if (deviceList.ValueKind != JsonValueKind.Array)
                        continue;

                    foreach (var dev in deviceList.EnumerateArray())
                    {
                        var id = dev.TryGetProperty("id", out var idProp) ? idProp.GetString() : null;
                        if (string.IsNullOrEmpty(id)) continue;

                        configDeviceIds.Add(id);

                        // 添加新设备到连接管理
                        if (!_connections.ContainsKey(id))
                        {
                            var address = dev.TryGetProperty("address", out var addr) ? addr.GetString() ?? "" : "";
                            _connections[id] = new DeviceConnection
                            {
                                DeviceId = id,
                                DeviceTypeId = typeId,
                                Address = address,
                                PluginId = pluginId,
                                State = DeviceConnectionState.Disconnected
                            };
                        }
                    }
                }
            }

            // 移除已删除的设备
            var toRemove = _connections.Keys.Where(id => !configDeviceIds.Contains(id)).ToList();
            foreach (var id in toRemove)
            {
                _connections.TryRemove(id, out _);
            }
        }
        catch (Exception ex)
        {
            Log(LogLevel.Error, $"同步设备配置失败: {ex.Message}");
        }
    }

    /// <summary>
    /// 处理插件推送的事件
    /// </summary>
    public void HandlePluginEvent(PluginEventArgs args)
    {
        if (args.EventType == PluginEvents.DeviceDisconnected)
        {
            try 
            {
                // 注意：Payload 可能为空，增加安全性检查
                if (args.Data == null || args.Data.Length == 0) return;

                var address = System.Text.Encoding.UTF8.GetString(args.Data);
                
                // 遍历查找该地址对应的所有已连接设备
                foreach (var kvp in _connections)
                {
                    var connection = kvp.Value;
                    if (connection.Address == address && connection.State == DeviceConnectionState.Connected)
                    {
                        // 原子替换状态为已断开
                        var disconnectedState = connection with { State = DeviceConnectionState.Disconnected };
                        
                        // CAS: 确保我们替换的是同一个 connection 对象（状态未变）
                        if (_connections.TryUpdate(connection.DeviceId, disconnectedState, connection))
                        {
                            Log(LogLevel.Warning, $"收到插件断线通知，设备 {connection.DeviceId} ({address}) 已标记为断开");
                        }
                        else
                        {
                             Log(LogLevel.Warning, $"收到插件断线通知，但设备 {connection.DeviceId} 状态已变更，放弃更新");
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Log(LogLevel.Error, $"处理断线通知失败: {ex.Message}");
            }
        }
    }

    private void Log(LogLevel level, string message)
    {
        OnLog?.Invoke(level, "DeviceManager", message);
    }
}
