using Catalytic.Plugin;
using CatalyticKit;

namespace Catalytic.Engine;

/// <summary>
/// Engine 任务分发器
/// 负责接收 Engine 回调（设备操作任务），查找到对应的协议驱动，并执行任务
/// </summary>
public sealed class EngineTaskDispatcher
{
    private readonly Engine _engine;
    private readonly PluginManager _pluginManager;

    public EngineTaskDispatcher(Engine engine, PluginManager pluginManager)
    {
        _engine = engine;
        _pluginManager = pluginManager;
        
        // 注册回调
        engine.OnEngineTask(HandleEngineTask);
    }

    private void HandleEngineTask(EngineTaskEventArgs args)
    {
        // 关键点：使用 Task.Run 确保在线程池中执行，
        // 即使 driver.ExecuteAsync 的同步部分耗时较长，也不会阻塞 Engine 的回调线程
        Task.Run(() => ExecuteAsync(args));
    }

    private async Task ExecuteAsync(EngineTaskEventArgs args)
    {
        try
        {
            var communicator = _pluginManager.GetCommunicatorById(args.PluginId);
            if (communicator == null)
            {
                _engine.SubmitError(args.SlotId, args.TaskId, $"No communicator for plugin: {args.PluginId}");
                return;
            }

            using var cts = new CancellationTokenSource(args.TimeoutMs);
            try
            {
                var result = await communicator.ExecuteAsync(
                    args.DeviceAddress,
                    args.ActionType,
                    args.Payload,
                    args.TimeoutMs,
                    cts.Token);
                _engine.SubmitResult(args.SlotId, args.TaskId, result);
            }
            catch (OperationCanceledException)
            {
                _engine.SubmitTimeout(args.SlotId, args.TaskId);
            }
            catch (Exception ex)
            {
                _engine.SubmitError(args.SlotId, args.TaskId, ex.Message);
            }
        }
        catch (Exception ex)
        {
            // 兜底捕获：防止 Task.Run 内部发生未捕获异常导致进程崩溃
            Logger.Error($"Critical error in EngineTaskDispatcher: {ex}");
            // 尝试通知 Engine 任务失败（如果是一般异常）
            try { _engine.SubmitError(args.SlotId, args.TaskId, "Internal Host Error"); } catch { }
        }
    }
}
