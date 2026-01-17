using System.Runtime.InteropServices;

namespace Catalytic.Engine;

/// <summary>
/// Engine 原生方法声明 (P/Invoke)
/// 内部使用，对外通过 Engine 类访问
/// </summary>
internal static partial class NativeMethods
{
    private const string LibName = "catalytic";
    
    // ========== 生命周期 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_create")]
    internal static partial IntPtr Create(uint slotCount);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_destroy")]
    internal static partial void Destroy(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_free_json")]
    internal static partial void FreeJson(IntPtr json);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_get_slot_count")]
    internal static partial int GetSlotCount(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_set_data_path", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int SetDataPath(IntPtr engine, string path);
    
    // ========== 控制 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_start_slot")]
    internal static partial int StartSlot(IntPtr engine, uint slotId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_start_all_slots")]
    internal static partial int StartAllSlots(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_stop_slot")]
    internal static partial int StopSlot(IntPtr engine, uint slotId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_stop_all_slots")]
    internal static partial int StopAllSlots(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_pause_slot")]
    internal static partial int PauseSlot(IntPtr engine, uint slotId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_pause_all_slots")]
    internal static partial int PauseAllSlots(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_resume_slot")]
    internal static partial int ResumeSlot(IntPtr engine, uint slotId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_resume_all_slots")]
    internal static partial int ResumeAllSlots(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_skip_current_step")]
    internal static partial int SkipCurrentStep(IntPtr engine, uint slotId);
    
    // ========== 配置 ==========
    
    /// <summary>
    /// 获取引擎配置
    /// ⚠️ 返回的指针必须调用 FreeJson 释放
    /// </summary>
    [LibraryImport(LibName, EntryPoint = "cat_engine_get_config_json")]
    internal static partial IntPtr GetConfigJson(IntPtr engine);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_load_config", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int LoadConfig(IntPtr engine, string configJson);
    
    // ========== 状态查询 ==========
    
    /// <summary>
    /// 获取槽位状态
    /// ⚠️ 返回的指针必须调用 FreeJson 释放
    /// </summary>
    [LibraryImport(LibName, EntryPoint = "cat_engine_get_slot_status_json")]
    internal static partial IntPtr GetSlotStatusJson(IntPtr engine, uint slotId);
    
    /// <summary>
    /// 获取测试步骤
    /// ⚠️ 返回的指针必须调用 FreeJson 释放
    /// </summary>
    [LibraryImport(LibName, EntryPoint = "cat_engine_get_test_steps_json")]
    internal static partial IntPtr GetTestStepsJson(IntPtr engine);
    
    // ========== 结果提交 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_submit_result")]
    internal static partial int SubmitResult(IntPtr engine, uint slotId, ulong taskId, 
        [MarshalAs(UnmanagedType.LPArray, SizeParamIndex = 4)] byte[] data, uint dataLen);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_submit_timeout")]
    internal static partial int SubmitTimeout(IntPtr engine, uint slotId, ulong taskId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_submit_error", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int SubmitError(IntPtr engine, uint slotId, ulong taskId, string message);
    
    // ========== 设备管理 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_add_device_type", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int AddDeviceType(IntPtr engine, string typeId, string configJson);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_add_device_instance", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int AddDeviceInstance(IntPtr engine, string typeId, string instanceJson);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_remove_device_instance", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int RemoveDeviceInstance(IntPtr engine, string typeId, string label);
    
    // ========== 步骤管理 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_add_test_step", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int AddTestStep(IntPtr engine, string stepJson);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_update_test_step", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int UpdateTestStep(IntPtr engine, string stepJson);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_remove_test_step")]
    internal static partial int RemoveTestStep(IntPtr engine, uint stepId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_reorder_steps")]
    internal static partial int ReorderSteps(IntPtr engine, 
        [MarshalAs(UnmanagedType.LPArray, SizeParamIndex = 2)] uint[] stepIds, uint count);
    
    // ========== 槽位管理 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_set_slot_count")]
    internal static partial int SetSlotCount(IntPtr engine, uint count);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_set_slot_binding", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int SetSlotBinding(IntPtr engine, uint slotId, string bindingJson);
    
    /// <summary>
    /// 获取槽位绑定信息
    /// ⚠️ 返回的指针必须调用 FreeJson 释放
    /// </summary>
    [LibraryImport(LibName, EntryPoint = "cat_engine_get_slot_binding")]
    internal static partial IntPtr GetSlotBinding(IntPtr engine, uint slotId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_set_slot_sn", StringMarshalling = StringMarshalling.Utf8)]
    internal static partial int SetSlotSn(IntPtr engine, uint slotId, string sn);
    
    /// <summary>
    /// 获取槽位 SN
    /// ⚠️ 返回的指针必须调用 FreeJson 释放
    /// </summary>
    [LibraryImport(LibName, EntryPoint = "cat_engine_get_slot_sn")]
    internal static partial IntPtr GetSlotSn(IntPtr engine, uint slotId);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_clear_slot_sn")]
    internal static partial int ClearSlotSn(IntPtr engine, uint slotId);
    
    // ========== 回调注册 ==========
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_register_engine_task_callback")]
    internal static partial void RegisterEngineTaskCallback(IntPtr engine, EngineTaskCallback callback, IntPtr userData);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_register_host_task_callback")]
    internal static partial void RegisterHostTaskCallback(IntPtr engine, HostTaskCallback callback, IntPtr userData);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_register_ui_callback")]
    internal static partial void RegisterUICallback(IntPtr engine, UIUpdateCallback callback, IntPtr userData);
    
    [LibraryImport(LibName, EntryPoint = "cat_engine_register_log_callback")]
    internal static partial void RegisterLogCallback(IntPtr engine, LogCallback callback, IntPtr userData);
}
