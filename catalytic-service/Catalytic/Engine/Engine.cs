using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Catalytic.Engine;

/// <summary>
/// Catalytic Engine 封装
/// 提供与 Rust 引擎交互的 C# 友好 API
/// </summary>
public sealed class Engine : IDisposable
{
    private readonly EngineHandle _handle;
    private bool _disposed;
    
    // 保持委托引用，防止被 GC 回收
    private EngineTaskCallback? _engineTaskCallback;
    private HostTaskCallback? _hostTaskCallback;
    private UIUpdateCallback? _uiUpdateCallback;
    private LogCallback? _logCallback; // [NEW] Keep log callback reference
    
    /// <summary>
    /// 创建 Engine 实例
    /// </summary>
    /// <param name="slotCount">槽位数量</param>
    public Engine(int slotCount)
    {
        if (slotCount <= 0)
            throw new ArgumentOutOfRangeException(nameof(slotCount), "槽位数量必须大于 0");
        
        var ptr = NativeMethods.Create((uint)slotCount);
        if (ptr == IntPtr.Zero)
            throw new InvalidOperationException("创建 Engine 失败");
        
        _handle = new EngineHandle(ptr);
    }
    
    /// <summary>
    /// 槽位数量
    /// </summary>
    public int SlotCount => NativeMethods.GetSlotCount(_handle.DangerousGetHandle());
    
    // ========== 私有工具方法 ==========
    

    
    private void ThrowIfDisposed()
    {
        ObjectDisposedException.ThrowIf(_disposed, this);
    }
    
    private IntPtr Handle => _handle.DangerousGetHandle();
    
    // ========== 配置 ==========
    
    /// <summary>
    /// 获取当前配置
    /// </summary>
    public string GetConfig()
    {
        ThrowIfDisposed();
        Logger.Debug("[FFI] >> GetConfig");
        var sw = Stopwatch.StartNew();
        var ptr = NativeMethods.GetConfigJson(Handle);
        var json = NativeUtils.ConsumeJsonString(ptr);
        sw.Stop();
        Logger.Debug($"[FFI] << GetConfig returned {json.Length} bytes ({sw.ElapsedMilliseconds}ms)");
        Logger.Debug($"[FFI] Payload: {json}");
        return json;
    }
    
    /// <summary>
    /// 加载配置
    /// </summary>
    public void LoadConfig(string configJson)
    {
        ThrowIfDisposed();
        var result = NativeMethods.LoadConfig(Handle, configJson);
        if (result != 0)
            throw new InvalidOperationException($"加载配置失败，错误码: {result}");
    }
    
    /// <summary>
    /// 设置数据存储路径
    /// Engine 会在此路径下创建 engine.db 数据库文件
    /// </summary>
    public void SetDataPath(string path)
    {
        ThrowIfDisposed();
        var result = NativeMethods.SetDataPath(Handle, path);
        if (result != 0)
            throw new InvalidOperationException($"设置数据路径失败，错误码: {result}");
    }
    
    // ========== 控制 ==========
    
    public void StartSlot(int slotId)
    {
        ThrowIfDisposed();
        NativeMethods.StartSlot(Handle, (uint)slotId);
    }
    
    public void StartAllSlots()
    {
        ThrowIfDisposed();
        NativeMethods.StartAllSlots(Handle);
    }
    
    public void StopSlot(int slotId)
    {
        ThrowIfDisposed();
        NativeMethods.StopSlot(Handle, (uint)slotId);
    }
    
    public void StopAllSlots()
    {
        ThrowIfDisposed();
        NativeMethods.StopAllSlots(Handle);
    }
    
    public void PauseSlot(int slotId)
    {
        ThrowIfDisposed();
        NativeMethods.PauseSlot(Handle, (uint)slotId);
    }
    
    public void PauseAllSlots()
    {
        ThrowIfDisposed();
        NativeMethods.PauseAllSlots(Handle);
    }
    
    public void ResumeSlot(int slotId)
    {
        ThrowIfDisposed();
        NativeMethods.ResumeSlot(Handle, (uint)slotId);
    }
    
    public void ResumeAllSlots()
    {
        ThrowIfDisposed();
        NativeMethods.ResumeAllSlots(Handle);
    }
    
    public void SkipCurrentStep(int slotId)
    {
        ThrowIfDisposed();
        NativeMethods.SkipCurrentStep(Handle, (uint)slotId);
    }
    
    // ========== 状态查询 ==========
    
    public string GetSlotStatus(int slotId)
    {
        ThrowIfDisposed();
        return NativeUtils.ConsumeJsonString(NativeMethods.GetSlotStatusJson(Handle, (uint)slotId));
    }
    
    public string GetTestSteps()
    {
        ThrowIfDisposed();
        return NativeUtils.ConsumeJsonString(NativeMethods.GetTestStepsJson(Handle));
    }
    
    // ========== 结果提交 ==========
    
    public void SubmitResult(int slotId, ulong taskId, byte[] data)
    {
        ThrowIfDisposed();
        var result = NativeMethods.SubmitResult(Handle, (uint)slotId, taskId, data, (uint)data.Length);
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("提交任务结果", result));
    }
    
    public void SubmitTimeout(int slotId, ulong taskId)
    {
        ThrowIfDisposed();
        var result = NativeMethods.SubmitTimeout(Handle, (uint)slotId, taskId);
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("提交任务超时", result));
    }
    
    public void SubmitError(int slotId, ulong taskId, string message)
    {
        ThrowIfDisposed();
        var result = NativeMethods.SubmitError(Handle, (uint)slotId, taskId, message);
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("提交任务错误", result));
    }
    
    // ========== 设备管理 ==========
    
    public void AddDeviceType(string typeId, string configJson)
    {
        ThrowIfDisposed();
        Logger.Debug($"[FFI] >> AddDeviceType id={typeId} size={configJson.Length}");
        Logger.Debug($"[FFI] Payload: {configJson}");
        var sw = Stopwatch.StartNew();
        var result = NativeMethods.AddDeviceType(Handle, typeId, configJson);
        sw.Stop();
        Logger.Debug($"[FFI] << AddDeviceType returned {result} ({sw.ElapsedMilliseconds}ms)");
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("添加设备类型", result));
    }
    
    public void AddDeviceInstance(string typeId, string instanceJson)
    {
        ThrowIfDisposed();
        var result = NativeMethods.AddDeviceInstance(Handle, typeId, instanceJson);
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("添加设备实例", result));
    }
    
    public void RemoveDeviceInstance(string typeId, string label)
    {
        ThrowIfDisposed();
        NativeMethods.RemoveDeviceInstance(Handle, typeId, label);
    }
    
    // ========== 步骤管理 ==========
    
    public void AddTestStep(string stepJson)
    {
        ThrowIfDisposed();
        Logger.Debug($"[FFI] >> AddTestStep size={stepJson.Length}");
        Logger.Debug($"[FFI] Payload: {stepJson}");
        var sw = Stopwatch.StartNew();
        var result = NativeMethods.AddTestStep(Handle, stepJson);
        sw.Stop();
        Logger.Debug($"[FFI] << AddTestStep returned {result} ({sw.ElapsedMilliseconds}ms)");
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("添加测试步骤", result));
    }
    
    public void UpdateTestStep(string stepJson)
    {
        ThrowIfDisposed();
        Logger.Debug($"[FFI] >> UpdateTestStep size={stepJson.Length}");
        Logger.Debug($"[FFI] Payload: {stepJson}");
        var sw = Stopwatch.StartNew();
        var result = NativeMethods.UpdateTestStep(Handle, stepJson);
        sw.Stop();
        Logger.Debug($"[FFI] << UpdateTestStep returned {result} ({sw.ElapsedMilliseconds}ms)");
        if (result != 0)
            throw new InvalidOperationException(ErrorTranslator.FormatError("更新测试步骤", result));
    }
    
    public void RemoveTestStep(int stepId)
    {
        ThrowIfDisposed();
        NativeMethods.RemoveTestStep(Handle, (uint)stepId);
    }
    
    public void ReorderSteps(int[] stepIds)
    {
        ThrowIfDisposed();
        var ids = stepIds.Select(id => (uint)id).ToArray();
        NativeMethods.ReorderSteps(Handle, ids, (uint)ids.Length);
    }
    
    // ========== 槽位管理 ==========
    
    public void SetSlotCount(int count)
    {
        ThrowIfDisposed();
        NativeMethods.SetSlotCount(Handle, (uint)count);
    }
    
    public void SetSlotBinding(int slotId, string bindingJson)
    {
        ThrowIfDisposed();
        Logger.Debug($"[FFI] >> SetSlotBinding slot={slotId} size={bindingJson.Length}");
        Logger.Debug($"[FFI] Payload: {bindingJson}");
        var sw = Stopwatch.StartNew();
        NativeMethods.SetSlotBinding(Handle, (uint)slotId, bindingJson);
        sw.Stop();
        Logger.Debug($"[FFI] << SetSlotBinding ({sw.ElapsedMilliseconds}ms)");
    }
    
    public string GetSlotBinding(int slotId)
    {
        ThrowIfDisposed();
        return NativeUtils.ConsumeJsonString(NativeMethods.GetSlotBinding(Handle, (uint)slotId));
    }
    
    public void SetSlotSn(int slotId, string sn)
    {
        ThrowIfDisposed();
        NativeMethods.SetSlotSn(Handle, (uint)slotId, sn);
    }
    
    public string GetSlotSn(int slotId)
    {
        ThrowIfDisposed();
        return NativeUtils.ConsumeJsonString(NativeMethods.GetSlotSn(Handle, (uint)slotId));
    }
    
    public void ClearSlotSn(int slotId)
    {
        ThrowIfDisposed();
        NativeMethods.ClearSlotSn(Handle, (uint)slotId);
    }
    
    // ========== 回调注册 ==========
    
    /// <summary>
    /// 注册 Engine 任务回调
    /// 当 Engine 需要 Host 执行设备操作时调用
    /// </summary>
    /// <param name="handler">回调处理函数</param>
    public void OnEngineTask(Action<EngineTaskEventArgs> handler)
    {
        ThrowIfDisposed();
        
        // 创建委托并保持引用，防止被 GC 回收
        _engineTaskCallback = (slotId, taskId, deviceType, deviceAddress, protocol, actionType, payload, payloadLen, timeoutMs, userData) =>
        {
            try
            {
                // 复制 payload 数据
                // 复制 payload 数据
                var payloadBytes = NativeUtils.ReadByteArray(payload, (int)payloadLen);
                
                var args = new EngineTaskEventArgs
                {
                    SlotId = (int)slotId,
                    TaskId = taskId,
                    DeviceType = deviceType,
                    DeviceAddress = deviceAddress,
                    PluginId = protocol, // Rust 传递的是 plugin_id，变量名保持兼容
                    ActionType = actionType,
                    Payload = payloadBytes,
                    TimeoutMs = (int)timeoutMs
                };
                
                handler(args);
                return 0; // 成功
            }
            catch
            {
                return -1; // 失败
            }
        };
        
        NativeMethods.RegisterEngineTaskCallback(Handle, _engineTaskCallback, IntPtr.Zero);
    }
    
    /// <summary>
    /// 注册 Host 任务回调
    /// 当 Engine 需要 Host 执行复杂任务时调用（HostControlled 模式）
    /// </summary>
    public void OnHostTask(Action<HostTaskEventArgs> handler)
    {
        ThrowIfDisposed();
        
        _hostTaskCallback = (slotId, taskId, taskName, paramsPtr, paramsLen, timeoutMs, userData) =>
        {
            try
            {
                var paramsBytes = NativeUtils.ReadByteArray(paramsPtr, (int)paramsLen);

                var args = new HostTaskEventArgs
                {
                    SlotId = (int)slotId,
                    TaskId = taskId,
                    TaskName = taskName,
                    Params = paramsBytes,
                    TimeoutMs = (int)timeoutMs
                };
                
                handler(args);
                return 0;
            }
            catch
            {
                return -1;
            }
        };
        
        NativeMethods.RegisterHostTaskCallback(Handle, _hostTaskCallback, IntPtr.Zero);
    }
    
    /// <summary>
    /// 当收到 Engine 推送的 UI 快照时触发
    /// </summary>
    public event Action<string>? SnapshotReceived;

    /// <summary>
    /// 注册 UI 更新回调
    /// 当 Engine 状态变化时调用，用于推送状态给 UI
    /// </summary>
    public void OnUIUpdate(Action<string> handler)
    {
        ThrowIfDisposed();
        
        _uiUpdateCallback = (snapshotJson, jsonLen, userData) =>
        {
            // 触发公共事件
            SnapshotReceived?.Invoke(snapshotJson);
            
            // 触发旧的回调（如果有相关兼容需求，保留此调用）
            handler(snapshotJson);
        };
        
        NativeMethods.RegisterUICallback(Handle, _uiUpdateCallback, IntPtr.Zero);
    }

    /// <summary>
    /// 注册日志回调 (New)
    /// </summary>
    public void OnLog(Action<LogEventArgs> handler)
    {
        ThrowIfDisposed();
        
        _logCallback = (timestamp, level, source, message, userData) =>
        {
            try 
            {
                var args = new LogEventArgs 
                {
                    Timestamp = timestamp,
                    Level = level,
                    Source = source,
                    Message = message
                };
                handler(args);
            }
            catch
            {
                // 日志回调尽量不抛出异常影响 Engine
            }
        };
        
        NativeMethods.RegisterLogCallback(Handle, _logCallback, IntPtr.Zero);
    }
    
    // ========== IDisposable ==========
    
    public void Dispose()
    {
        if (_disposed) return;
        
        _handle.Dispose();
        _disposed = true;
    }
}
