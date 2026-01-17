//! 槽位管理 FFI

use std::ffi::c_char;
use std::collections::HashMap;
use crate::core::CatEngine;
use crate::ffi::helpers::{to_cstring_ptr, str_to_cstring_ptr, parse_json_from_ptr, str_from_ptr};
use crate::error::{SUCCESS, ERR_INVALID_PARAM, ERR_INVALID_STATE, ERR_INTERNAL};

/// 设置槽位数量
#[no_mangle]
pub unsafe extern "C" fn cat_engine_set_slot_count(engine: *mut CatEngine, new_count: u32) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return ERR_INVALID_PARAM; }
        match (*engine).set_slot_count(new_count) {
            Ok(_) => SUCCESS,
            Err(crate::error::EngineError::InvalidSlotState { .. }) => ERR_INVALID_STATE,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 设置槽位设备绑定（支持多设备）
#[no_mangle]
pub unsafe extern "C" fn cat_engine_set_slot_binding(
    engine: *mut CatEngine, slot_id: u32, bindings_json: *const c_char
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || bindings_json.is_null() { return ERR_INVALID_PARAM; }
        
        // 解析多设备格式: {"type_id": ["device_a", "device_b"]}
        let devices: HashMap<String, Vec<String>> = match parse_json_from_ptr(bindings_json) {
            Some(d) => d,
            None => return ERR_INVALID_PARAM,
        };

        match (*engine).set_slot_binding(slot_id, devices) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 获取槽位设备绑定
#[no_mangle]
pub unsafe extern "C" fn cat_engine_get_slot_binding(engine: *const CatEngine, slot_id: u32) -> *mut c_char {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return std::ptr::null_mut(); }
        (*engine).get_slot_binding(slot_id)
            .map(|b| to_cstring_ptr(&b.devices))
            .unwrap_or(std::ptr::null_mut())
    }, std::ptr::null_mut())
}

/// 设置槽位 SN
#[no_mangle]
pub unsafe extern "C" fn cat_engine_set_slot_sn(
    engine: *mut CatEngine, slot_id: u32, sn: *const c_char
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || sn.is_null() { return ERR_INVALID_PARAM; }
        
        let sn_str = match str_from_ptr(sn) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };

        let slot = match (*engine).get_slot(slot_id) {
            Ok(s) => s,
            Err(_) => return ERR_INVALID_PARAM,
        };

        let mut slot_guard = slot.write();
        if slot_guard.state_machine.is_running() { return ERR_INVALID_STATE; }
        slot_guard.set_sn(sn_str);
        SUCCESS
    })
}

/// 获取槽位 SN
#[no_mangle]
pub unsafe extern "C" fn cat_engine_get_slot_sn(engine: *const CatEngine, slot_id: u32) -> *mut c_char {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return std::ptr::null_mut(); }
        (*engine).get_slot(slot_id)
            .ok()
            .and_then(|slot| slot.read().sn.as_ref().map(|s| str_to_cstring_ptr(s)))
            .unwrap_or(std::ptr::null_mut())
    }, std::ptr::null_mut())
}

/// 清除槽位 SN
#[no_mangle]
pub unsafe extern "C" fn cat_engine_clear_slot_sn(engine: *mut CatEngine, slot_id: u32) {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return; }
        if let Ok(slot) = (*engine).get_slot(slot_id) {
            slot.write().clear_sn();
        }
    }, ())
}
