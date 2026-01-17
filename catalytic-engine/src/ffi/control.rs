//! 测试控制 FFI

use crate::core::CatEngine;
use crate::core::executor::spawn_slot;
use crate::core::slot::ControlSignal;
use crate::model::SlotStatus;
use crate::error::{SUCCESS, ERR_INVALID_PARAM, ERR_INVALID_STATE, ERR_INTERNAL};

// ============================================================================
// 内部辅助函数
// ============================================================================

/// 内部：发送控制信号到单个槽位
fn send_slot_control(engine: *const CatEngine, slot_id: u32, signal: ControlSignal) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return ERR_INVALID_PARAM; }
        let slot = match unsafe { (&*engine).get_slot(slot_id) } {
            Ok(s) => s,
            Err(_) => return ERR_INVALID_PARAM,
        };
        if slot.read().send_control_blocking(signal) { SUCCESS } else { ERR_INVALID_STATE }
    })
}

/// 内部：对所有槽位执行操作
fn for_all_slots<F>(engine: *const CatEngine, action: F) -> i32
where
    F: Fn(u32),
{
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return ERR_INVALID_PARAM; }
        let slot_count = unsafe { (&*engine).slot_count() };
        for slot_id in 0..slot_count {
            action(slot_id);
        }
        SUCCESS
    })
}

// ============================================================================
// FFI 导出函数
// ============================================================================

/// 开始执行单个槽位的测试（非阻塞，在后台执行）
#[no_mangle]
pub unsafe extern "C" fn cat_engine_start_slot(engine: *mut CatEngine, slot_id: u32) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() { return ERR_INVALID_PARAM; }
        let engine = &*engine;
    
        let slot = match engine.get_slot(slot_id) {
            Ok(s) => s,
            Err(_) => return ERR_INVALID_PARAM,
        };
    
        // 检查并转换状态
        {
            let mut g = slot.write();
            if g.state_machine.transition(SlotStatus::Running).is_err() {
                return ERR_INVALID_STATE;
            }
            g.mark_start();
        }
    
        // 非阻塞启动（在后台 spawn）
        match spawn_slot(engine, slot_id) {
            Ok(_) => SUCCESS,
            Err(_) => ERR_INTERNAL,
        }
    })
}

/// 开始执行所有槽位的测试（并行启动，非阻塞）
#[no_mangle]
pub unsafe extern "C" fn cat_engine_start_all_slots(engine: *mut CatEngine) -> i32 {
    for_all_slots(engine, |slot_id| {
        cat_engine_start_slot(engine, slot_id);
    })
}

/// 暂停单个槽位
#[no_mangle]
pub unsafe extern "C" fn cat_engine_pause_slot(engine: *mut CatEngine, slot_id: u32) -> i32 {
    send_slot_control(engine, slot_id, ControlSignal::Pause)
}

/// 暂停所有槽位
#[no_mangle]
pub unsafe extern "C" fn cat_engine_pause_all_slots(engine: *mut CatEngine) -> i32 {
    for_all_slots(engine, |slot_id| {
        cat_engine_pause_slot(engine, slot_id);
    })
}

/// 恢复单个槽位
#[no_mangle]
pub unsafe extern "C" fn cat_engine_resume_slot(engine: *mut CatEngine, slot_id: u32) -> i32 {
    send_slot_control(engine, slot_id, ControlSignal::Resume)
}

/// 恢复所有槽位
#[no_mangle]
pub unsafe extern "C" fn cat_engine_resume_all_slots(engine: *mut CatEngine) -> i32 {
    for_all_slots(engine, |slot_id| {
        cat_engine_resume_slot(engine, slot_id);
    })
}

/// 停止单个槽位
#[no_mangle]
pub unsafe extern "C" fn cat_engine_stop_slot(engine: *mut CatEngine, slot_id: u32) -> i32 {
    send_slot_control(engine, slot_id, ControlSignal::Stop)
}

/// 停止所有槽位
#[no_mangle]
pub unsafe extern "C" fn cat_engine_stop_all_slots(engine: *mut CatEngine) -> i32 {
    for_all_slots(engine, |slot_id| {
        cat_engine_stop_slot(engine, slot_id);
    })
}

/// 单步执行
#[no_mangle]
pub unsafe extern "C" fn cat_engine_step_next(engine: *mut CatEngine, slot_id: u32) -> i32 {
    send_slot_control(engine, slot_id, ControlSignal::StepNext)
}

/// 跳过当前步骤
#[no_mangle]
pub unsafe extern "C" fn cat_engine_skip_current_step(engine: *mut CatEngine, slot_id: u32) -> i32 {
    send_slot_control(engine, slot_id, ControlSignal::SkipCurrent)
}
