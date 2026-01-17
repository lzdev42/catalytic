//! 测试执行器

use std::time::{Duration, Instant};
use std::sync::Arc;
use parking_lot::RwLock;

use crate::core::engine::{CatEngine, Callbacks};
use crate::core::slot::SlotContext;
use crate::core::task::{generate_task_id, TaskRegistry, TaskResult};
use crate::model::{TestStep, ExecutionMode, CheckType, StepResult, StepStatus, Variable, CheckResultDetail, SlotStatus, DeviceType};
use crate::parser::parse_response;
use crate::checker::{execute_check, CheckOutput};
use crate::error::{Result, EngineError};
use std::collections::HashMap;

/// 执行单个槽位的所有测试步骤（阻塞版本）
pub fn run_slot(engine: &CatEngine, slot_id: u32) -> Result<()> {
    let slot = engine.get_slot(slot_id)?;
    let callbacks = engine.callbacks();
    let task_registry = engine.task_registry();
    let steps = engine.get_test_steps().to_vec();
    let device_types = engine.get_device_types_map();
    
    if steps.is_empty() { return Ok(()); }

    engine.runtime().block_on(async { 
        run_slot_async(slot, callbacks, task_registry, steps, device_types).await 
    })
}

/// 在后台启动槽位执行（非阻塞，用于并行执行）
pub fn spawn_slot(engine: &CatEngine, slot_id: u32) -> Result<()> {
    let slot = engine.get_slot(slot_id)?;
    let callbacks = engine.callbacks();
    let task_registry = engine.task_registry();
    let steps = engine.get_test_steps().to_vec();
    let device_types = engine.get_device_types_map();
    
    if steps.is_empty() { return Ok(()); }

    engine.runtime().spawn(async move {
        let _ = run_slot_async(slot, callbacks, task_registry, steps, device_types).await;
    });

    Ok(())
}

/// 完成槽位执行（统一的收尾逻辑）
fn finish_slot(slot: &Arc<RwLock<SlotContext>>) {
    let mut g = slot.write();
    let _ = g.state_machine.transition(SlotStatus::Completed);
    g.mark_end();
    g.reinit_control_channel();
}

/// 异步执行槽位测试
/// 异步执行槽位测试
async fn run_slot_async(
    slot: Arc<RwLock<SlotContext>>,
    callbacks: Arc<RwLock<Callbacks>>,
    task_registry: Arc<TaskRegistry>,
    steps: Vec<TestStep>,
    device_types: HashMap<String, DeviceType>,
) -> Result<()> {
    use crate::core::slot::ControlSignal;
    use tokio::sync::mpsc;

    // [FIX] 启动时设置状态为 Running
    {
        let mut g = slot.write();
        // 允许从 Idle/Completed/Error 重置为 Running
        g.state_machine.force_state(SlotStatus::Running);
        g.mark_start();
    }
    
    let total = steps.len();
    let mut idx = 0usize;
    
    // 取出控制信号接收端
    let mut control_rx: Option<mpsc::Receiver<ControlSignal>> = {
        slot.write().take_control_rx()
    };

    while idx < total {
        let step = &steps[idx];

        // 构造步骤执行的 Future
        // 注意：execute_step 内部包含 check/save 逻辑，如果被 Cancelled，这些逻辑也不会执行
        // 这符合 "Stop" 的语义，但对于 "Pause" 意味着该步骤未完成，Resume 后需要重试
        let step_future = execute_step(&slot, step, &callbacks, &task_registry, &device_types);

        // 构造信号等待 Future
        let signal_future = async {
            if let Some(rx) = control_rx.as_mut() {
                rx.recv().await
            } else {
                std::future::pending().await
            }
        };

        // [P0 FIX 1] 使用 select! 同时等待执行结果和控制信号
        tokio::select! {
            result = step_future => {
                // --- 步骤执行完成 ---
                
                // 记录结果并推送 UI
                {
                    let mut g = slot.write();
                    g.current_step_index = idx;
                    g.add_step_result(result.clone());
                }
                push_ui_update(&slot, &callbacks, idx, total, Some(step));

                // [P0 FIX 2] 跳转逻辑容错处理
                let next_idx = match result.status {
                    StepStatus::Passed | StepStatus::Skipped => {
                        resolve_jump(step.next_on_pass, idx + 1, &steps, &callbacks)
                    }
                    StepStatus::Failed => {
                        resolve_jump(step.next_on_fail, total, &steps, &callbacks)
                    }
                    StepStatus::Timeout => {
                        resolve_jump(step.next_on_timeout, total, &steps, &callbacks)
                    }
                    StepStatus::Error => {
                        resolve_jump(step.next_on_error, total, &steps, &callbacks)
                    }
                    _ => total, 
                };
                
                idx = next_idx;
            }

            Some(signal) = signal_future => {
                // --- 收到控制信号 (打断当前步骤) ---
                match signal {
                    ControlSignal::Stop => {
                        // 停止：退出循环，标记完成
                        // 注意：此时 step_future 被 drop，底层的 engine_task 如果还在跑，
                        // 会在 TaskRegistry::cancel 中被清理（依赖 execute_engine_controlled 中的 drop 逻辑或 task_registry 的懒清理）
                         // 实际 execute_engine_controlled 内部也没有 Drop guard，但 task_registry 的清理通常是被动的
                         // 只要接收端 rx 被 drop，Host 回调也没事
                        finish_slot(&slot);
                        return Ok(());
                    }
                    ControlSignal::Pause => {
                        // 暂停：更新状态
                        {
                            let mut g = slot.write();
                            let _ = g.state_machine.transition(SlotStatus::Paused);
                        }
                        push_ui_update(&slot, &callbacks, idx, total, Some(step));
                        
                        // 进入阻塞等待循环 (只响应 Resume/Stop)
                        if let Some(rx) = control_rx.as_mut() {
                            loop {
                                if let Some(sig) = rx.recv().await {
                                    match sig {
                                        ControlSignal::Resume => {
                                            let mut g = slot.write();
                                            let _ = g.state_machine.transition(SlotStatus::Running);
                                            break; // 跳出等待循环，触发 continue，重试当前步骤
                                        }
                                        ControlSignal::Stop => {
                                            finish_slot(&slot);
                                            return Ok(());
                                        }
                                        _ => {} // 暂停期间忽略其他信号
                                    }
                                } else {
                                    // 通道关闭
                                    control_rx = None;
                                    break;
                                }
                            }
                        }
                        // Resume 后，idx 不变，continue 重新执行该步骤
                        continue;
                    }
                    ControlSignal::SkipCurrent => {
                        // 跳过当前：idx + 1
                        idx += 1;
                        continue;
                    }
                    _ => {} // 其他信号忽略，继续执行（理论上 select 里的 future 已经 drop 了步骤...）
                           // 这里的 StepNext 没实现，暂不处理
                }
            }
        }
    }

    // 更新状态为 Completed
    finish_slot(&slot);

    Ok(())
}

/// [Helper] 解析跳转目标，如果不存在则记录错误并返回 default
fn resolve_jump(
    target_id: Option<u32>, 
    default: usize, 
    steps: &[TestStep], 
    callbacks: &Arc<RwLock<Callbacks>>
) -> usize {
    if let Some(id) = target_id {
        let idx = find_step_index(steps, id);
        if idx >= steps.len() {
            emit_log(callbacks, "error", "executor", &format!("Jump target step_id={} not found! Stopping slot.", id));
            steps.len() // 跳转到末尾引发结束
        } else {
            idx
        }
    } else {
        default
    }
}

/// 执行单个步骤
async fn execute_step(
    slot: &Arc<RwLock<SlotContext>>,
    step: &TestStep,
    callbacks: &Arc<RwLock<Callbacks>>,
    task_registry: &Arc<TaskRegistry>,
    device_types: &HashMap<String, DeviceType>,
) -> StepResult {
    let start = Instant::now();
    let (slot_id, device_bindings) = {
        let g = slot.read();
        (g.slot_id, g.device_bindings.clone())
    };

    let raw_data = match step.execution_mode {
        ExecutionMode::EngineControlled => {
            execute_engine_controlled(slot_id, step, callbacks, task_registry, &device_bindings, device_types).await
        }
        ExecutionMode::HostControlled => {
            execute_host_controlled(slot_id, step, callbacks, task_registry).await
        }
    };

    let elapsed_ms = start.elapsed().as_millis() as u32;

    match raw_data {
        // [MODIFIED] 传入 callbacks 供 process_response 使用
        Ok(data) => process_response(slot, step, data, elapsed_ms, callbacks),
        
        Err(EngineError::Timeout(_)) => {
            emit_log(callbacks, "warn", "executor", &format!("Step {} execution timeout", step.step_id));
            StepResult::timeout(step.step_id, step.step_name.clone(), elapsed_ms)
        },
        
        Err(EngineError::ExecutionError(msg)) => {
            {
                let mut g = slot.write();
                g.set_error(msg.clone());
            }
            emit_log(callbacks, "error", "executor", &format!("Step {} execution failed: {}", step.step_id, msg));
            StepResult::failed(step.step_id, step.step_name.clone(), elapsed_ms, msg.clone(), Some(msg))
        },
        Err(e) => {
             let msg = e.to_string();
             {
                let mut g = slot.write();
                g.set_error(msg.clone());
            }
            emit_log(callbacks, "error", "executor", &format!("Step {} unknown error: {}", step.step_id, msg));
            StepResult::failed(step.step_id, step.step_name.clone(), elapsed_ms, msg.clone(), Some(msg))
        }
    }
}



/// EngineControlled 模式执行
async fn execute_engine_controlled(
    slot_id: u32,
    step: &TestStep,
    callbacks: &Arc<RwLock<Callbacks>>,
    task_registry: &Arc<TaskRegistry>,
    device_bindings: &HashMap<String, crate::model::DeviceInstance>,
    device_types: &HashMap<String, DeviceType>,
) -> std::result::Result<Vec<u8>, crate::error::EngineError> {
    use crate::error::EngineError;

    let task = step.engine_task.as_ref().ok_or_else(|| EngineError::ExecutionError("缺少 engine_task".into()))?;
    let max_iter = task.loop_max_iterations.unwrap_or(1);
    let delay = task.loop_delay_ms.unwrap_or(0);
    let timeout = task.timeout_ms;
    
    // 解析设备信息
    let device_type_name = &task.target_device;
    let (device_address, plugin_id) = if let Some(instance) = device_bindings.get(device_type_name) {
        let pid = device_types.get(device_type_name)
            .map(|dt| dt.plugin_id.as_str())
            .unwrap_or("");
        (instance.address.clone(), pid.to_string())
    } else {
        (String::new(), String::new())
    };

    let mut last_data = vec![];

    for i in 0..max_iter {
        let task_id = generate_task_id();
        
        // 注册任务，获取接收端
        let rx = task_registry.register(task_id, slot_id);

        // 调用回调（传递真实设备信息）
        let ret = callbacks.read().call_engine_task(
            slot_id, task_id, device_type_name, &device_address, &plugin_id,
            task.action_type.as_str(), &task.payload, timeout,
        );
        
        if ret != 0 {
            task_registry.cancel(task_id);
            return Err(EngineError::ExecutionError(format!("回调返回错误: {}", ret)));
        }

        // 等待结果（带超时）
        let result = tokio::select! {
            r = rx => r.ok(),
            _ = tokio::time::sleep(Duration::from_millis(timeout as u64)) => {
                task_registry.cancel(task_id);
                None
            }
        };

        match result {
            Some(TaskResult::Ok(data)) => {
                last_data = data;
            }

            Some(TaskResult::Timeout) => return Err(EngineError::Timeout(timeout as u64)),
            Some(TaskResult::Error(msg)) => return Err(EngineError::ExecutionError(msg)),
            None => return Err(EngineError::Timeout(timeout as u64)),
        }

        // 循环延迟
        if delay > 0 && i < max_iter - 1 {
            tokio::time::sleep(Duration::from_millis(delay as u64)).await;
        }
    }

    Ok(last_data)
}

/// HostControlled 模式执行
async fn execute_host_controlled(
    slot_id: u32,
    step: &TestStep,
    callbacks: &Arc<RwLock<Callbacks>>,
    task_registry: &Arc<TaskRegistry>,
) -> std::result::Result<Vec<u8>, crate::error::EngineError> {
    use crate::error::EngineError;

    let task = step.host_task.as_ref().ok_or_else(|| EngineError::ExecutionError("缺少 host_task".into()))?;
    let timeout = task.timeout_ms;
    let task_id = generate_task_id();
    let params = serde_json::to_vec(&task.params).unwrap_or_default();

    // 注册任务
    let rx = task_registry.register(task_id, slot_id);

    // 调用回调
    let ret = callbacks.read().call_host_task(slot_id, task_id, &task.task_name, &params, timeout);
    
    if ret != 0 {
        task_registry.cancel(task_id);
        return Err(EngineError::ExecutionError(format!("回调返回错误: {}", ret)));
    }

    // 等待结果
    let result = tokio::select! {
        r = rx => r.ok(),
        _ = tokio::time::sleep(Duration::from_millis(timeout as u64)) => {
            task_registry.cancel(task_id);
            None
        }
    };

    match result {
        Some(TaskResult::Ok(data)) => Ok(data),
        Some(TaskResult::Timeout) => Err(EngineError::Timeout(timeout as u64)),
        Some(TaskResult::Error(msg)) => Err(EngineError::ExecutionError(msg)),
        None => Err(EngineError::Timeout(timeout as u64)),
    }
}

/// 处理响应数据：解析 → 存变量 → 检查
fn process_response(
    slot: &Arc<RwLock<SlotContext>>,
    step: &TestStep,
    data: Vec<u8>,
    elapsed_ms: u32,
    callbacks: &Arc<RwLock<Callbacks>>,
) -> StepResult {
    let mut g = slot.write();

    // 解析（链式调用）
    let parsed = step.engine_task.as_ref()
        .and_then(|t| t.parse_rule.as_ref())
        .and_then(|r| parse_response(&data, r).ok())
        .map(|s| Variable::from_string(&s));

    // 存变量
    if let (Some(name), Some(v)) = (&step.save_to, &parsed) {
        g.variables.set(name, v.clone());
    }

    // 执行检查
    let check_result = if step.check_type != CheckType::None {
        if let Some(rule) = step.check_rule.as_ref() {
             match execute_check(rule, parsed.as_ref(), &g.variables) {
                 Ok(output) => Some(output),
                 Err(e) => {
                     let err_msg = e.to_string();
                     // [ACTION-1] 状态持久化：存入槽位的 last_error
                     g.set_error(err_msg.clone());
                     
                     // [ACTION-2] 通讯单例发送：立即通过专用回调告知 Host
                     emit_log(callbacks, "error", "check", &format!("Check execution failed: {}", err_msg));

                     return StepResult {
                         step_id: step.step_id,
                         step_name: step.step_name.clone(),
                         status: StepStatus::Error,
                         elapsed_ms,
                         final_value: parsed.map(|v| serde_json::to_value(&v).unwrap_or_default()),
                         check_result: None,
                         result_summary: format!("检查执行错误: {}", err_msg),
                         error_message: Some(err_msg),
                     };
                 }
             }
        } else {
            None
        }
    } else {
        None
    };

    build_result(step, elapsed_ms, parsed, check_result)
}

/// 专用通讯函数：emit_log
fn emit_log(callbacks: &Arc<RwLock<Callbacks>>, level: &str, source: &str, msg: &str) {
    let cb_guard = callbacks.read();
    if let Some(cb) = cb_guard.log {
        let ts = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_millis() as u64;
        
        let c_level = std::ffi::CString::new(level).unwrap_or_default();
        let c_source = std::ffi::CString::new(source).unwrap_or_default();
        let c_msg = std::ffi::CString::new(msg).unwrap_or_default();
        
        (cb)(
            ts, 
            c_level.as_ptr(), 
            c_source.as_ptr(), 
            c_msg.as_ptr(), 
            cb_guard.log_user_data
        );
    }
}

/// 构建步骤结果
fn build_result(step: &TestStep, elapsed_ms: u32, value: Option<Variable>, check: Option<CheckOutput>) -> StepResult {
    let (status, summary) = check.as_ref()
        .map(|c| (if c.passed { StepStatus::Passed } else { StepStatus::Failed }, c.summary.clone()))
        .unwrap_or((StepStatus::Passed, "完成".to_string()));

    StepResult {
        step_id: step.step_id,
        step_name: step.step_name.clone(),
        status, elapsed_ms,
        final_value: value.map(|v| serde_json::to_value(&v).unwrap_or_default()),
        check_result: check.map(|c| CheckResultDetail { template: c.template, params: c.params, actual: c.actual, passed: c.passed }),
        result_summary: summary,
        error_message: None,
    }
}

/// 推送 UI 更新
fn push_ui_update(
    slot: &Arc<RwLock<SlotContext>>, 
    callbacks: &Arc<RwLock<Callbacks>>, 
    current: usize, 
    total: usize,
    step: Option<&TestStep>
) {
    let g = slot.read();
    let variables = g.variables.to_display_map();
    
    let json = serde_json::json!({
        "type": "ui_snapshot",
        "timestamp": std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() as u64,
        "slots": [{
            "slot_id": g.slot_id, 
            "sn": g.sn, 
            "status": g.status(), 
            "progress": {
                "current": current, 
                "total": total
            },
            "current_step_name": step.map(|s| s.step_name.clone()),
            "current_step_desc": step.map(|s| s.step_name.clone()),
            "variables": variables
        }]
    });
    callbacks.read().call_ui_update(&json.to_string());
}

/// 根据 step_id 查找步骤索引
fn find_step_index(steps: &[TestStep], step_id: u32) -> usize {
    steps.iter().position(|s| s.step_id == step_id).unwrap_or(steps.len())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicBool, Ordering};
    use parking_lot::RwLock;

    // 原子操作标志位
    static LOG_CAUGHT: AtomicBool = AtomicBool::new(false);

    extern "C" fn mock_log(
        _ts: u64,
        _l: *const std::ffi::c_char,
        _s: *const std::ffi::c_char,
        _m: *const std::ffi::c_char,
        _u: *mut std::ffi::c_void,
    ) {
        LOG_CAUGHT.store(true, Ordering::SeqCst);
    }

    #[test]
    fn test_async_emit_log() {
        let mut cb = Callbacks::default();
        cb.log = Some(mock_log);
        let cb_arc = Arc::new(RwLock::new(cb));

        LOG_CAUGHT.store(false, Ordering::SeqCst);
        emit_log(&cb_arc, "error", "test", "fire and forget");
        
        assert!(LOG_CAUGHT.load(Ordering::SeqCst), "Log Callback 触发失败！");
    }
}
