//! FFI 辅助函数

use std::ffi::{c_char, CStr, CString};
use serde::Serialize;

/// 将可序列化对象转换为 C 字符串指针
/// 
/// 返回的指针需要调用 `cat_engine_free_json` 释放
pub fn to_cstring_ptr<T: Serialize + ?Sized>(value: &T) -> *mut c_char {
    serde_json::to_string(value)
        .ok()
        .and_then(|s| CString::new(s).ok())
        .map(|c| c.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// 将字符串转换为 C 字符串指针
pub fn str_to_cstring_ptr(s: &str) -> *mut c_char {
    CString::new(s)
        .map(|c| c.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// 从 C 字符串指针解析 JSON
pub fn parse_json_from_ptr<'a, T: serde::de::DeserializeOwned>(ptr: *const c_char) -> Option<T> {
    if ptr.is_null() {
        return None;
    }
    unsafe {
        CStr::from_ptr(ptr)
            .to_str()
            .ok()
            .and_then(|s| serde_json::from_str(s).ok())
    }
}

/// 从 C 字符串指针获取字符串
pub fn str_from_ptr(ptr: *const c_char) -> Option<String> {
    if ptr.is_null() {
        return None;
    }
    unsafe {
        CStr::from_ptr(ptr)
            .to_str()
            .ok()
            .map(|s| s.to_string())
    }
}
/// FFI 异常防护宏
///
/// 用于捕获 Rust 侧的 Panic，防止跨语言边界传播导致 Host 进程崩溃。
///
/// # 使用方法
///
/// ```ignore
/// // 默认返回 ERR_INTERNAL
/// ffi_guard! {
///     some_risky_operation()
/// }
///
/// // 自定义返回值
/// ffi_guard! {
///     some_risky_operation()
/// , std::ptr::null_mut() }
/// ```
#[macro_export]
macro_rules! ffi_guard {
    ($body:expr) => {
        $crate::ffi_guard!($body, crate::error::ERR_INTERNAL)
    };
    ($body:expr, $ret:expr) => {
        {
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                $body
            }));
            
            match result {
                Ok(val) => val,
                Err(_) => {
                    eprintln!("[CatalyticEngine] Panic caught at FFI boundary!");
                    $ret
                }
            }
        }
    };
}
