using Catalytic.Plugin;
using CatalyticKit;

namespace Catalytic.Engine;

/// <summary>
/// Host 任务分发器
/// 负责接收 Engine 回调（复杂 Host 任务），查找到对应的任务处理器，并执行任务
/// </summary>
public sealed class HostTaskDispatcher
{
    private readonly Engine _engine;
    private readonly PluginManager _pluginManager;

    public HostTaskDispatcher(Engine engine, PluginManager pluginManager)
    {
        _engine = engine;
        _pluginManager = pluginManager;
        
        // 注册回调
        engine.OnHostTask(HandleHostTask);
    }

    private void HandleHostTask(HostTaskEventArgs args)
    {
        // 关键点：使用 Task.Run 确保在线程池中执行，
        // 即使 Handler.ExecuteAsync 的同步部分耗时较长，也不会阻塞 Engine 的回调线程
        Task.Run(() => ExecuteAsync(args));
    }

    private async Task ExecuteAsync(HostTaskEventArgs args)
    {
        try
        {
            var processor = _pluginManager.GetProcessor(args.TaskName);
            if (processor == null)
            {
                _engine.SubmitError(args.SlotId, args.TaskId, $"No processor for task: {args.TaskName}");
                return;
            }

            using var cts = new CancellationTokenSource(args.TimeoutMs);
            try
            {
                var result = await processor.ExecuteAsync(args.ParamsJson, cts.Token);
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
            Logger.Error($"Critical error in HostTaskDispatcher: {ex}");
            // 尝试通知 Engine 任务失败（如果是一般异常）
            try { _engine.SubmitError(args.SlotId, args.TaskId, "Internal Host Error"); } catch { }
        }
    }
}
