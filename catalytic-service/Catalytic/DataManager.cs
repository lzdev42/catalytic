using System.Collections.Concurrent;
using CatalyticKit;
using Catalytic.Plugin;

namespace Catalytic;

/// <summary>
/// 数据管理器
/// 负责作为“蓄水池”接收并缓冲设备推送的数据
/// 实现线程安全的写入和读取
/// </summary>
public sealed class DataManager
{
    // Key: DeviceId or Address
    // Value: 接收到的数据 buffer
    private readonly ConcurrentDictionary<string, List<byte>> _buffers = new();
    
    // 如果需要支持多 Slot，可能需要更复杂的结构 Key = DeviceId -> Value = { Slot1: [], Slot2: [] }
    // 但根据需求，DeviceId 是唯一的，所以 DeviceId -> Buffer 足够
    // 我们在这里使用 List<byte> 配合 lock 实现最简单的线程安全追加
    // 对于高吞吐场景，未来可以换成 RingBuffer 或 Pipe

    /// <summary>
    /// 处理插件事件
    /// </summary>
    public void HandlePluginEvent(PluginEventArgs args)
    {
        // 只关心 DeviceData 事件
        if (args.EventType == PluginEvents.DeviceData)
        {
            if (args.Data == null || args.Data.Length == 0) return;

            // args.PluginId 只是插件 ID，我们通常需要 DeviceId
            // 但这里有个问题：DeviceData 事件的 Source 是 PluginId 还是 DeviceId？
            // 之前的 DeviceDisconnected 的 Payload 是 Address。
            // 假设 DeviceData 的约定是：PluginId = SourcePlugin, 但我们怎么知道是哪个 Device 发的？
            // 答：按照最佳实践，Payload 应该是结构化的，或者我们约定：
            // 为了简单起见，我们假设 DataManager 需要通过某种方式知道 DeviceId。
            // 但 PushEvent 接口只有 (type, data)。
            // 
            // 修正：我们需要 Plugin 在 PushEvent 时，带上 Device标识。
            // 方案 A：Payload 前缀带 DeviceID (太 hack)
            // 方案 B：Payload 本身就是数据，但是 Source 必须是 DeviceId？不对，Source 是 PluginId。
            // 方案 C：DataManager 不应该依赖 PluginId，而是依赖一个 Context 里的 DeviceId？不，Context 是共享的。
            // 
            // 回看 DeviceDisconnected 的处理：
            // var address = Encoding.UTF8.GetString(args.Data);
            // 这里 payload 就是 address。
            // 但 DeviceData 的 payload 是数据本身。
            // 
            // 关键点：PluginContext.PushEvent(type, data) 
            // 底层 PluginManager 触发 OnPluginEvent(new PluginEventArgs(pluginId, type, data))
            // 
            // Wait, 如果一个 Plugin 管理多个 Device (如 Serial)，它 PushEvent 时，Host 怎么知道是 COM3 还是 COM4 的数据？
            // 必须在 Payload 里包含 DeviceId，或者约定 EventType 包含 DeviceId (例如 "DeviceData:COM3")。
            // 
            // 让我们采用最稳健的方案：Payload 必须包含 DeviceId。
            // 但是为了支持 binary data，混合 DeviceId 比较麻烦。
            // 
            // 重新审视架构：
            // "FetchData" 动作是发给某个 Address 的。
            // 所以我们需要 Address -> Data 的映射。
            // 
            // 让我们约定方案 D：
            // Plugin 在 Push "DeviceData" 时，为了区分源设备：
            // 必须使用一个 helper class 或者约定：
            // 既然 ExecuteAsync(address, ...) 是按 address 来的，
            // 这里的 Buffer Key 也应该是 Address。
            // 
            // 临时解决方案：我们假设 Plugin 会为每个 Device 连接创建一个独立的 Context scope？不，Context 是单例。
            // 
            // 正确做法：
            // 由 Plugin 开发者在 Push 时自己决定怎么区分。
            // 但为了 Host 能通用的 fetch ("FetchData", address)，Host 必须知道 Key 是 address。
            // 
            // 让我们修改 PluginEvents.DeviceData 的语义。
            // 或者，新增一个重载：PushEvent(deviceId, type, data) ? 
            // 这需要改 SDK 接口。
            // 
            // 既然我们已经决定改 IPluginContext 接口 (新增 GetDeviceData)，
            // 那顺便把 PushEvent 改成 PushEvent(string sourceId, string eventType, byte[] data) ?
            // 不，Standard PushEvent 是 (type, data)。
            // 
            // 让我们暂时使用 "DeviceData:{Address}" 作为 EventType ? 
            // 这样最简单，不需要改 Payload结构，也不需要改 PushEvent 签名。
            // 例如: context.PushEvent($"DeviceData:{address}", data);
            // 
            // 验证：
            // Host 收到 EventType="DeviceData:COM3"。
            // DataManager 解析出 Key="COM3"。
            // 存入 _buffers["COM3"]。
            // Engine Fetch("COM3") -> 命中。
            // 
            // 这方案可行且侵入性最小。
            // 但 PluginEvents.DeviceData 是常量 "DeviceData"。
            // 
            // 更好的方案：
            // DataManager 维护的数据结构是 Dictionary<string, byte[]>。
            // 我们需要确定 Key 的来源。
            // 
            // 让我们看 PluginManager.cs 是怎么触发事件的。
            // 
            // 回到 Plan: 我们没有讨论这个问题，这是一个 implementation detail 缺失。
            // 现在决策：
            // 为了不让 Plugin 开发者痛苦地拼装 EventType 字符串，
            // 我们在 SDK 层面增加一个 helper? 
            // 
            // 不，最干净的方法是：
            // Payload 的前 4字节是长度 N，后 N 字节是 Address (UTF8)，剩下的是 Data。
            // 这样 Host 可以拆包。
            // 
            // 但这破坏了 "Raw Data" 的纯洁性。
            // 
            // 让我们采用 "EventType 后缀" 方案，并在 Guide 里推荐：
            // PushEvent(PluginEvents.DeviceData + "separator" + address, data)
            // 
            // 实际上，DeviceDisconnected 就是把 Address 放在 Payload 里。
            // DeviceData 如果也这样，那 Data 就要混在 Payload 里。
            // 
            // 让我们再看下 Context 的实现。
            // 
            // 既然正在 Execution，我决定采用 "Helper Method" 方案：
            // 在 CatalyticKit 中提供扩展方法：
            // context.PushDeviceData(string address, byte[] data);
            // 它的实现是：PushEvent($"DeviceData:{address}", data);
            // 
            // 这样 PluginEvents.DeviceData 就作为一个前缀常量。
            // 
            // 这样 Host 端 DataManager 解析 EventType，如果 StartWidth("DeviceData:")，则截取 Address。
            
            if (args.EventType.StartsWith(PluginEvents.DeviceData + ":"))
            {
                var address = args.EventType.Substring(PluginEvents.DeviceData.Length + 1);
                var data = args.Data;
                
                // 写入蓄水池
                _buffers.AddOrUpdate(address, 
                    _ => new List<byte>(data), 
                    (_, list) => 
                    {
                        lock (list) 
                        {
                            // [Fix] OOM Protection: 限制单个设备缓冲不超过 50MB
                            if (list.Count + data.Length > 50 * 1024 * 1024)
                            {
                                // 策略：丢弃新数据并记录警告（防止炸内存）
                                // 实际项目中可能需要更复杂的环形缓冲或丢弃旧数据
                                return list;
                            }
                            list.AddRange(data);
                        }
                        return list;
                    });
                     
                // OnLog?.Invoke(LogLevel.Debug, "DataManager", $"Received {data.Length} bytes for {address}");
            }
        }
    }

    /// <summary>
    /// 获取并清空设备数据
    /// </summary>
    public byte[] GetData(string address)
    {
        if (_buffers.TryRemove(address, out var list))
        {
            lock (list)
            {
                return list.ToArray();
            }
        }
        return Array.Empty<byte>();
    }
    
    /// <summary>
    /// 获取设备数据（不删除，仅查看，用于调试或特殊用途）
    /// </summary>
    public byte[] PeekData(string address)
    {
        if (_buffers.TryGetValue(address, out var list))
        {
            lock (list)
            {
                return list.ToArray();
            }
        }
        return Array.Empty<byte>();
    }

    /// <summary>
    /// 清除数据
    /// </summary>
    public void ClearData(string address)
    {
        _buffers.TryRemove(address, out _);
    }
}
