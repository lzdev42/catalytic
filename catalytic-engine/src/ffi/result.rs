use std::ffi::{c_char, CString};
use crate::core::CatEngine;
use crate::ffi::helpers::to_cstring_ptr;

/// 获取槽位状态 (JSON)
/// 
/// 返回的 JSON 包含：
/// - slot_id: 槽位 ID
/// - sn: 序列号
/// - status: 状态字符串
/// - current_step: 当前步骤索引
/// - start_time: 开始时间戳
/// - duration_ms: 持续时长
/// - last_error: 最后一次错误信息 (New)
#[no_mangle]
pub unsafe extern "C" fn cat_engine_get_slot_status_json(engine: *const CatEngine, slot_id: u32) -> *mut c_char {
    if engine.is_null() {
        return std::ptr::null_mut();
    }
    let engine = &*engine;

    let slot = match engine.get_slot(slot_id) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let g = slot.read();
    
    let json = serde_json::json!({
        "slot_id": g.slot_id,
        "sn": g.sn,
        "status": g.status(),
        "current_step": g.current_step_index,
        "start_time": g.start_time,
        "duration_ms": g.elapsed_ms(),
        // [NEW] 暴露错误信息给 UI
        "last_error": g.last_error
    });

    to_cstring_ptr(&json)
}

/// 释放字符串内存
#[no_mangle]
pub extern "C" fn cat_engine_free_string(s: *mut c_char) {
    if s.is_null() { return; }
    unsafe {
        let _ = CString::from_raw(s);
    }
}
