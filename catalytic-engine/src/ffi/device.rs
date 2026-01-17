//! 设备管理 FFI

use std::ffi::c_char;
use crate::core::CatEngine;
use crate::model::{DeviceType, DeviceInstance};
use crate::ffi::helpers::{str_from_ptr, parse_json_from_ptr};
use crate::error::{SUCCESS, ERR_INVALID_PARAM, ERR_INTERNAL};

/// 添加设备类型
///
/// # Safety
/// engine 和 type_json 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_add_device_type(
    engine: *mut CatEngine,
    type_name: *const c_char,
    type_json: *const c_char,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || type_name.is_null() || type_json.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
        
        let name = match str_from_ptr(type_name) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };
    
        let device_type: DeviceType = match parse_json_from_ptr(type_json) {
            Some(dt) => dt,
            None => return ERR_INVALID_PARAM,
        };
    
        match engine.add_device_type(name, device_type) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 添加设备实例
///
/// # Safety
/// engine, type_name, instance_json 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_add_device_instance(
    engine: *mut CatEngine,
    type_name: *const c_char,
    instance_json: *const c_char,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || type_name.is_null() || instance_json.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
    
        let name = match str_from_ptr(type_name) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };
    
        let instance: DeviceInstance = match parse_json_from_ptr(instance_json) {
            Some(i) => i,
            None => return ERR_INVALID_PARAM,
        };
    
        match engine.add_device_instance(&name, instance) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 移除设备实例
///
/// # Safety
/// engine, type_name, label 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_remove_device_instance(
    engine: *mut CatEngine,
    type_name: *const c_char,
    instance_id: *const c_char,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || type_name.is_null() || instance_id.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
    
        let name = match str_from_ptr(type_name) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };
    
        let id_str = match str_from_ptr(instance_id) {
            Some(s) => s,
            None => return ERR_INVALID_PARAM,
        };
    
        match engine.remove_device_instance(&name, &id_str) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}
