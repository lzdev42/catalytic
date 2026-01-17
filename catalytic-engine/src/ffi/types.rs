//! FFI 类型定义

use std::ffi::c_void;

/// 不透明引擎指针类型（用于 FFI）
pub type CatEnginePtr = *mut c_void;
