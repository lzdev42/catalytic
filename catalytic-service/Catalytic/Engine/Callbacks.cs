using System.Runtime.InteropServices;

namespace Catalytic.Engine;

/// <summary>
/// Engine 任务回调委托
/// 当 Engine 需要 Host 执行设备操作时调用
/// </summary>
/// <param name="slotId">槽位 ID</param>
/// <param name="taskId">任务 ID（用于提交结果）</param>
/// <param name="deviceType">设备类型（如 "dmm"）</param>
/// <param name="deviceAddress">设备地址（如 "TCPIP0::192.168.1.101::INSTR"）</param>
/// <param name="protocol">协议（如 "scpi"）</param>
/// <param name="actionType">动作类型（"send"/"query"/"wait"）</param>
/// <param name="payload">指令数据指针</param>
/// <param name="payloadLen">指令数据长度</param>
/// <param name="timeoutMs">超时时间（毫秒）</param>
/// <param name="userData">用户数据指针</param>
/// <returns>0 表示成功接收，非 0 表示拒绝</returns>
[UnmanagedFunctionPointer(CallingConvention.Cdecl)]
public delegate int EngineTaskCallback(
    uint slotId,
    ulong taskId,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string deviceType,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string deviceAddress,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string protocol,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string actionType,
    IntPtr payload,
    uint payloadLen,
    uint timeoutMs,
    IntPtr userData);

/// <summary>
/// Host 任务回调委托
/// 当 Engine 需要 Host 执行复杂任务时调用（HostControlled 模式）
/// </summary>
[UnmanagedFunctionPointer(CallingConvention.Cdecl)]
public delegate int HostTaskCallback(
    uint slotId,
    ulong taskId,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string taskName,
    IntPtr paramsPtr,  // Changed to generic ptr to match Rust [u8]
    uint paramsLen,    // Added length to match Rust
    uint timeoutMs,
    IntPtr userData);

// [NOTE]: HostTaskCallback signature in previous file seemed to miss paramsLen and had paramsJson as string.
// Rust side Callbacks::call_host_task passes:
// slot_id, task_id, task_name_c, params.as_ptr(), params.len(), timeout, user_data
// So C# should be: string taskName, IntPtr paramsPtr, uint paramsLen
// The previous C# code had: string taskName, string paramsJson, uint timeoutMs
// This was ALREADY inconsistent with Rust! 
// Rust: (u32, u64, *const c_char, *const u8, u32, u32, *mut c_void)
// Previous C#: (uint, ulong, string, string, uint, IntPtr) -> Mismatched!
// I will FIX HostTaskCallback while I am here, but primarily adding LogCallback.

// Wait, looking at Rust `call_host_task`:
// callback(slot_id, task_id, task_name_c.as_ptr(), params.as_ptr(), params.len() as u32, timeout_ms, self.host_task_user_data)
// It sends 7 arguments.
// Previous C# HostTaskCallback had 6 arguments. It was definitely wrong.
// I will fix it now.

/// <summary>
/// UI 更新回调委托
/// 当 Engine 状态发生变化时调用，用于推送状态给 UI
/// </summary>
[UnmanagedFunctionPointer(CallingConvention.Cdecl)]
public delegate void UIUpdateCallback(
    [MarshalAs(UnmanagedType.LPUTF8Str)] string snapshotJson,
    uint jsonLen, // Rust doesn't seem to pass len for UI callback? Let's check `call_ui_update`.
    IntPtr userData);
// Rust call_ui_update:
// callback(json_c.as_ptr(), json.len() as u32, self.ui_update_user_data)
// It passes 3 arguments: ptr, len, user_data.
// Previous C# UIUpdateCallback: (string, IntPtr) -> Mismatched!
// I will fix UIUpdateCallback too.

/// <summary>
/// 日志回调委托
/// 用于 Engine 向 Host 发送日志
/// </summary>
[UnmanagedFunctionPointer(CallingConvention.Cdecl)]
public delegate void LogCallback(
    ulong timestamp,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string level,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string source,
    [MarshalAs(UnmanagedType.LPUTF8Str)] string message,
    IntPtr userData);
