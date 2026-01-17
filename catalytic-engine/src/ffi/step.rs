//! 测试步骤管理 FFI

use std::ffi::c_char;
use crate::core::CatEngine;
use crate::model::TestStep;
use crate::ffi::helpers::{to_cstring_ptr, parse_json_from_ptr};
use crate::error::{SUCCESS, ERR_INVALID_PARAM, ERR_INTERNAL};

/// 获取所有测试步骤 JSON
///
/// # Safety
/// engine 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_get_test_steps_json(
    engine: *const CatEngine,
) -> *mut c_char {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() {
            return std::ptr::null_mut();
        }
    
        let engine = &*engine;
    
        let steps = engine.get_test_steps();
        to_cstring_ptr(steps)
    }, std::ptr::null_mut())
}

/// 添加测试步骤
///
/// # Safety
/// engine 和 step_json 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_add_test_step(
    engine: *mut CatEngine,
    step_json: *const c_char,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || step_json.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
    
        let step: TestStep = match parse_json_from_ptr(step_json) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };
    
        match engine.add_test_step(step) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 更新测试步骤
///
/// # Safety
/// engine 和 step_json 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_update_test_step(
    engine: *mut CatEngine,
    step_id: u32,
    step_json: *const c_char,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || step_json.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
    
        let step: TestStep = match parse_json_from_ptr(step_json) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };
    
        match engine.update_test_step(step_id, step) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 移除测试步骤
///
/// # Safety
/// engine 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_remove_test_step(
    engine: *mut CatEngine,
    step_id: u32,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
    
        match engine.remove_test_step(step_id) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 重新排序测试步骤
///
/// # Safety
/// engine 和 step_ids 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_reorder_steps(
    engine: *mut CatEngine,
    step_ids: *const u32,
    count: u32,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || step_ids.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
        let ids = std::slice::from_raw_parts(step_ids, count as usize);
    
        match engine.reorder_steps(ids) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}
