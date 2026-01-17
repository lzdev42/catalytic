//! 引擎生命周期 FFI

use std::ffi::{c_char, CString};
use crate::core::CatEngine;
use crate::error::ERR_INTERNAL;

/// 创建引擎实例
///
/// # Safety
/// 调用者负责调用 cat_engine_destroy 释放返回的指针
#[no_mangle]
pub extern "C" fn cat_engine_create(slot_count: u32) -> *mut CatEngine {
    use crate::ffi_guard;
    ffi_guard!({
        match CatEngine::new(slot_count) {
            Ok(engine) => Box::into_raw(Box::new(engine)),
            Err(_) => std::ptr::null_mut(),
        }
    }, std::ptr::null_mut())
}

/// 销毁引擎实例
///
/// # Safety
/// engine 必须是 cat_engine_create 返回的有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_destroy(engine: *mut CatEngine) {
    use crate::ffi_guard;
    ffi_guard!({
        if !engine.is_null() {
            drop(Box::from_raw(engine));
        }
    }, ())
}

/// 释放 JSON 字符串
///
/// # Safety
/// json 必须是引擎返回的有效 CString 指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_free_json(json: *mut c_char) {
    use crate::ffi_guard;
    ffi_guard!({
        if !json.is_null() {
            drop(CString::from_raw(json));
        }
    }, ())
}

/// 获取槽位数量
#[no_mangle]
pub unsafe extern "C" fn cat_engine_get_slot_count(engine: *const CatEngine) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() {
            return ERR_INTERNAL;
        }
        let engine = &*engine;
        engine.slot_count() as i32
    })
}

/// 设置数据存储路径
/// 
/// # Safety
/// - engine 必须是有效的 CatEngine 指针
/// - path 必须是 UTF-8 编码的空终止字符串
/// 
/// # 返回
/// - 0: 成功
/// - 非0: 失败
#[no_mangle]
pub unsafe extern "C" fn cat_engine_set_data_path(
    engine: *mut CatEngine,
    path: *const c_char,
) -> i32 {
    use crate::ffi::helpers::str_from_ptr;
    use crate::ffi_guard;
    use crate::error::SUCCESS;

    ffi_guard!({
        if engine.is_null() || path.is_null() {
            return ERR_INTERNAL;
        }
        
        let engine = &mut *engine;
        let path_str = match str_from_ptr(path) {
            Some(s) => s,
            None => return ERR_INTERNAL,
        };
        
        match engine.set_data_path(&path_str) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}
