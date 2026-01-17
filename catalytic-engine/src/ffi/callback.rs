//! FFI 回调类型定义

use std::ffi::{c_char, c_void};

/// EngineControlled 模式回调
pub type EngineTaskCallback = extern "C" fn(
    slot_id: u32,
    task_id: u64,
    device_type: *const c_char,
    device_address: *const c_char,
    protocol: *const c_char,
    action_type: *const c_char,
    payload: *const u8,
    payload_len: u32,
    timeout_ms: u32,
    user_data: *mut c_void,
) -> i32;

/// HostControlled 模式回调
pub type HostTaskCallback = extern "C" fn(
    slot_id: u32,
    task_id: u64,
    task_name: *const c_char,
    params: *const u8,
    params_len: u32,
    timeout_ms: u32,
    user_data: *mut c_void,
) -> i32;

/// UI 更新回调
pub type UIUpdateCallback = extern "C" fn(
    update_json: *const c_char,
    json_len: u32,
    user_data: *mut c_void,
);

/// 日志回调 (新增)
/// timestamp: 毫秒时间戳
/// level: "debug", "info", "warn", "error"
/// source: 来源模块
/// message: 日志内容
pub type LogCallback = extern "C" fn(
    timestamp: u64,
    level: *const c_char, 
    source: *const c_char,
    message: *const c_char,
    user_data: *mut c_void,
);

/// 注册 EngineTask 回调
#[no_mangle]
pub unsafe extern "C" fn cat_engine_register_engine_task_callback(
    engine: *mut crate::core::CatEngine,
    callback: EngineTaskCallback,
    user_data: *mut c_void,
) {
    if engine.is_null() {
        return;
    }
    let engine = &*engine;
    engine.register_engine_task_callback(callback, user_data);
}

/// 注册 HostTask 回调
#[no_mangle]
pub unsafe extern "C" fn cat_engine_register_host_task_callback(
    engine: *mut crate::core::CatEngine,
    callback: HostTaskCallback,
    user_data: *mut c_void,
) {
    if engine.is_null() {
        return;
    }
    let engine = &*engine;
    engine.register_host_task_callback(callback, user_data);
}

/// 注册 UI 更新回调
#[no_mangle]
pub unsafe extern "C" fn cat_engine_register_ui_callback(
    engine: *mut crate::core::CatEngine,
    callback: UIUpdateCallback,
    user_data: *mut c_void,
) {
    if engine.is_null() {
        return;
    }
    let engine = &*engine;
    engine.register_ui_callback(callback, user_data);
}

/// 注册日志回调 (新增)
#[no_mangle]
pub unsafe extern "C" fn cat_engine_register_log_callback(
    engine: *mut crate::core::CatEngine,
    callback: LogCallback,
    user_data: *mut c_void,
) {
    if engine.is_null() {
        return;
    }
    let engine = &*engine;
    engine.register_log_callback(callback, user_data);
}
