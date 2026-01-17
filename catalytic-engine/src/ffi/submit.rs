//! 结果提交 FFI
//! 
//! Host 完成设备通讯后，通过这些函数将结果返回给 Engine

use std::ffi::{c_char, CStr};
use crate::core::CatEngine;
use crate::core::task::TaskResult;
use crate::error::{SUCCESS, ERR_INVALID_PARAM, ERR_INTERNAL};

/// 提交成功结果
/// 
/// # 参数
/// - engine: Engine 实例指针
/// - slot_id: 槽位 ID
/// - task_id: 任务 ID (由 EngineTaskCallback 传入)
/// - data: 响应数据
/// - data_len: 数据长度
/// 
/// # 返回
/// - 0: 成功
/// - -1: 参数无效
/// - -2: 任务未找到
#[no_mangle]
pub unsafe extern "C" fn cat_engine_submit_result(
    engine: *const CatEngine,
    slot_id: u32,
    task_id: u64,
    data: *const u8,
    data_len: u32,
) -> i32 {
    if engine.is_null() {
        return ERR_INVALID_PARAM;
    }
    
    let engine = &*engine;
    
    // 构建数据 Vec
    let data_vec = if data.is_null() || data_len == 0 {
        Vec::new()
    } else {
        std::slice::from_raw_parts(data, data_len as usize).to_vec()
    };
    
    // 提交结果
    let success = engine.task_registry().submit(task_id, slot_id, TaskResult::Ok(data_vec));
    
    if success {
        SUCCESS
    } else {
        ERR_INTERNAL
    }
}

/// 提交超时结果
/// 
/// # 参数
/// - engine: Engine 实例指针
/// - slot_id: 槽位 ID
/// - task_id: 任务 ID
/// 
/// # 返回
/// - 0: 成功
/// - -1: 参数无效
/// - -2: 任务未找到
#[no_mangle]
pub unsafe extern "C" fn cat_engine_submit_timeout(
    engine: *const CatEngine,
    slot_id: u32,
    task_id: u64,
) -> i32 {
    if engine.is_null() {
        return ERR_INVALID_PARAM;
    }
    
    let engine = &*engine;
    let success = engine.task_registry().submit(task_id, slot_id, TaskResult::Timeout);
    
    if success {
        SUCCESS
    } else {
        ERR_INTERNAL
    }
}

/// 提交错误结果
/// 
/// # 参数
/// - engine: Engine 实例指针
/// - slot_id: 槽位 ID
/// - task_id: 任务 ID
/// - message: 错误消息 (UTF-8 字符串)
/// 
/// # 返回
/// - 0: 成功
/// - -1: 参数无效
/// - -2: 任务未找到
#[no_mangle]
pub unsafe extern "C" fn cat_engine_submit_error(
    engine: *const CatEngine,
    slot_id: u32,
    task_id: u64,
    message: *const c_char,
) -> i32 {
    if engine.is_null() {
        return ERR_INVALID_PARAM;
    }
    
    let engine = &*engine;
    
    // 解析错误消息
    let msg = if message.is_null() {
        String::from("Unknown error")
    } else {
        match CStr::from_ptr(message).to_str() {
            Ok(s) => s.to_string(),
            Err(_) => String::from("Invalid UTF-8 error message"),
        }
    };
    
    let success = engine.task_registry().submit(task_id, slot_id, TaskResult::Error(msg));
    
    if success {
        SUCCESS
    } else {
        ERR_INTERNAL
    }
}
