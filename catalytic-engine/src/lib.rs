//! Catalytic Engine - 自动化测试引擎核心
//!
//! 本库提供完整的 FFI 接口，供 C#、Kotlin、Python 等外部语言调用。

pub mod core;
pub mod error;
pub mod ffi;
pub mod model;
pub mod parser;
pub mod checker;
pub mod ui;
pub mod storage;

// 导出 FFI 函数
pub use ffi::*;
