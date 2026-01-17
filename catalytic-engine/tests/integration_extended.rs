//! 补充测试：覆盖 MVP 功能缺口
//! 包括：停止/暂停控制、超时/错误处理、HostControlled 模式、循环执行等

use std::collections::HashMap;
use std::time::Duration;

use catalytic::core::engine::CatEngine;
use catalytic::core::task::{TaskRegistry, TaskResult};
use catalytic::model::{
    TestStep, ExecutionMode, EngineTask, HostTask, ActionType, ParseRule,
    CheckType, CheckRule, CompareOp, DeviceType, DeviceInstance, SlotStatus, StepStatus
};

// --- 静态 Mock 响应数据 ---
static MOCK_RESPONSE: &str = "SUCCESS";

// --- EngineTask Mock 回调 (立即返回) ---
extern "C" fn mock_engine_task_instant(
    slot_id: u32,
    task_id: u64,
    _device: *const std::ffi::c_char,
    _addr: *const std::ffi::c_char,
    _proto: *const std::ffi::c_char,
    _action: *const std::ffi::c_char,
    _payload: *const u8,
    _len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        let registry = &*(user_data as *const TaskRegistry);
        registry.submit(task_id, slot_id, TaskResult::Ok(MOCK_RESPONSE.as_bytes().to_vec()));
    }
    0
}

// --- EngineTask Mock 回调 (超时) ---
extern "C" fn mock_engine_task_timeout(
    slot_id: u32,
    task_id: u64,
    _device: *const std::ffi::c_char,
    _addr: *const std::ffi::c_char,
    _proto: *const std::ffi::c_char,
    _action: *const std::ffi::c_char,
    _payload: *const u8,
    _len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        let registry = &*(user_data as *const TaskRegistry);
        // 提交超时结果而非无响应
        registry.submit(task_id, slot_id, TaskResult::Timeout);
    }
    0
}

// --- EngineTask Mock 回调 (错误) ---
extern "C" fn mock_engine_task_error(
    slot_id: u32,
    task_id: u64,
    _device: *const std::ffi::c_char,
    _addr: *const std::ffi::c_char,
    _proto: *const std::ffi::c_char,
    _action: *const std::ffi::c_char,
    _payload: *const u8,
    _len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        let registry = &*(user_data as *const TaskRegistry);
        registry.submit(task_id, slot_id, TaskResult::Error("Device unreachable".into()));
    }
    0
}

// --- HostTask Mock 回调 ---
extern "C" fn mock_host_task(
    slot_id: u32,
    task_id: u64,
    _name: *const std::ffi::c_char,
    _params: *const u8,
    _len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        let registry = &*(user_data as *const TaskRegistry);
        registry.submit(task_id, slot_id, TaskResult::Ok(b"host_task_result".to_vec()));
    }
    0
}

// 辅助函数：创建基础测试引擎
fn create_test_engine() -> CatEngine {
    let mut engine = CatEngine::new(1).unwrap();
    
    // 添加设备类型
    let dmm_type = DeviceType {
        type_name: "MockDevice".into(),
        name: "Mock Device".into(),
        plugin_id: "mock.plugin".into(),
        instances: vec![],
        commands: vec![],
    };
    engine.add_device_type("MockDevice".into(), dmm_type).unwrap();
    
    // 添加设备实例
    let inst = DeviceInstance {
        id: "mock_inst".into(),
        name: "MockInst".into(),
        address: "mock://test".into(),
        ..Default::default()
    };
    engine.add_device_instance("MockDevice", inst).unwrap();
    
    // 绑定槽位
    let mut binding = HashMap::new();
    binding.insert("MockDevice".into(), vec!["mock_inst".into()]);
    engine.set_slot_binding(0, binding).unwrap();
    
    engine
}

// ========== 测试：超时处理 ==========
#[test]
fn test_submit_timeout_handling() {
    use std::sync::Arc;
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_engine_task_timeout, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    // 添加测试步骤
    let step = TestStep {
        step_id: 1,
        step_name: "Timeout_Step".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 100,
            ..Default::default()
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    // 执行
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(500));
    
    // 验证
    let slot = engine.get_slot(0).unwrap();
    let guard = slot.read();
    assert_eq!(guard.status(), SlotStatus::Completed);
    
    // 检查步骤结果是否为超时
    if let Some(result) = guard.step_results.get(0) {
        assert_eq!(result.status, StepStatus::Timeout);
    }
}

// ========== 测试：错误处理 ==========
#[test]
fn test_submit_error_handling() {
    use std::sync::Arc;
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_engine_task_error, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    let step = TestStep {
        step_id: 1,
        step_name: "Error_Step".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 1000,
            ..Default::default()
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(300));
    
    let slot = engine.get_slot(0).unwrap();
    let guard = slot.read();
    assert_eq!(guard.status(), SlotStatus::Completed);
    
    if let Some(result) = guard.step_results.get(0) {
        assert_eq!(result.status, StepStatus::Failed);
        assert!(result.error_message.as_ref().unwrap().contains("unreachable"));
    }
}

// ========== 测试：HostControlled 模式 ==========
#[test]
fn test_host_controlled_mode() {
    use std::sync::Arc;
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_engine_task_instant, registry_ptr);
    engine.register_host_task_callback(mock_host_task, registry_ptr);
    
    let step = TestStep {
        step_id: 1,
        step_name: "HostTask_Step".into(),
        execution_mode: ExecutionMode::HostControlled,
        host_task: Some(HostTask {
            task_name: "burn_firmware".into(),
            params: serde_json::json!({"target": "device1"}),
            timeout_ms: 1000,
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(300));
    
    let slot = engine.get_slot(0).unwrap();
    let guard = slot.read();
    assert_eq!(guard.status(), SlotStatus::Completed);
    assert_eq!(guard.step_results.len(), 1);
    assert_eq!(guard.step_results[0].status, StepStatus::Passed);
}

// ========== 测试：检查失败场景 ==========
#[test]
fn test_check_failure_step_status() {
    use std::sync::Arc;
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_engine_task_instant, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    // 步骤返回 "SUCCESS"，但检查规则期望数值 > 100
    let step = TestStep {
        step_id: 1,
        step_name: "FailingCheck_Step".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 1000,
            ..Default::default()
        }),
        save_to: Some("val".into()),
        check_type: CheckType::Builtin,
        check_rule: Some(CheckRule::Threshold {
            variable: "val".into(),
            operator: CompareOp::Gt,
            value: 100.0, // "SUCCESS" 无法解析为数值，应失败
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(300));
    
    let slot = engine.get_slot(0).unwrap();
    let guard = slot.read();
    assert_eq!(guard.status(), SlotStatus::Completed);
    
    // 检查规则失败应导致步骤状态为 Failed 或 Error
    if let Some(result) = guard.step_results.get(0) {
        assert!(
            result.status == StepStatus::Failed || result.status == StepStatus::Error,
            "Expected Failed or Error, got {:?}",
            result.status
        );
    }
}

// ========== 测试：状态机 Running → Idle (Stop) ==========
#[test]
fn test_state_running_to_idle_stop() {
    use catalytic::model::status::SlotStatus;
    
    assert!(SlotStatus::Running.can_transition_to(SlotStatus::Idle));
    assert!(SlotStatus::Paused.can_transition_to(SlotStatus::Idle));
}

// ========== 测试：循环执行 ==========
#[test]
fn test_loop_execution() {
    use std::sync::Arc;
    use std::sync::atomic::{AtomicU32, Ordering};
    
    static CALL_COUNT: AtomicU32 = AtomicU32::new(0);
    
    extern "C" fn mock_counting_callback(
        slot_id: u32,
        task_id: u64,
        _device: *const std::ffi::c_char,
        _addr: *const std::ffi::c_char,
        _proto: *const std::ffi::c_char,
        _action: *const std::ffi::c_char,
        _payload: *const u8,
        _len: u32,
        _timeout: u32,
        user_data: *mut std::ffi::c_void,
    ) -> i32 {
        CALL_COUNT.fetch_add(1, Ordering::SeqCst);
        unsafe {
            let registry = &*(user_data as *const TaskRegistry);
            registry.submit(task_id, slot_id, TaskResult::Ok(b"ok".to_vec()));
        }
        0
    }
    
    CALL_COUNT.store(0, Ordering::SeqCst);
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_counting_callback, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    // 配置循环执行 3 次
    let step = TestStep {
        step_id: 1,
        step_name: "Loop_Step".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 1000,
            loop_max_iterations: Some(3),
            loop_delay_ms: Some(10),
            ..Default::default()
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(500));
    
    // 验证回调被调用了 3 次
    assert_eq!(CALL_COUNT.load(Ordering::SeqCst), 3, "Loop should execute 3 times");
}

// ========== 测试：next_on_fail 跳转 ==========
#[test]
fn test_next_on_fail_jump() {
    use std::sync::Arc;
    use std::sync::atomic::{AtomicU32, Ordering};
    
    static STEP_EXECUTED: AtomicU32 = AtomicU32::new(0);
    
    // Mock 回调：第一次返回失败，后续返回成功
    extern "C" fn mock_fail_then_success(
        slot_id: u32,
        task_id: u64,
        _device: *const std::ffi::c_char,
        _addr: *const std::ffi::c_char,
        _proto: *const std::ffi::c_char,
        _action: *const std::ffi::c_char,
        _payload: *const u8,
        _len: u32,
        _timeout: u32,
        user_data: *mut std::ffi::c_void,
    ) -> i32 {
        let count = STEP_EXECUTED.fetch_add(1, Ordering::SeqCst);
        unsafe {
            let registry = &*(user_data as *const TaskRegistry);
            if count == 0 {
                // 第一步返回错误数据，检查会失败
                registry.submit(task_id, slot_id, TaskResult::Ok(b"BAD".to_vec()));
            } else {
                // 后续步骤返回成功
                registry.submit(task_id, slot_id, TaskResult::Ok(b"OK".to_vec()));
            }
        }
        0
    }
    
    STEP_EXECUTED.store(0, Ordering::SeqCst);
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_fail_then_success, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    // Step 1: 检查失败，应跳转到 step 3 (由 next_on_fail 指定)
    let step1 = TestStep {
        step_id: 1,
        step_name: "Step1_WillFail".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 1000,
            parse_rule: Some(ParseRule::Regex { pattern: "(.+)".into(), group: 1 }), // 捕获整个响应
            ..Default::default()
        }),
        save_to: Some("result".into()),
        check_type: CheckType::Builtin,
        check_rule: Some(CheckRule::Contains { 
            variable: "result".into(), 
            substring: "GOOD".into() // "BAD" 不包含 "GOOD"，检查失败
        }),
        next_on_pass: Some(2),
        next_on_fail: Some(3), // 失败时跳转到 step 3
        ..Default::default()
    };
    
    // Step 2: 不应被执行
    let step2 = TestStep {
        step_id: 2,
        step_name: "Step2_ShouldSkip".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD2".to_vec(),
            timeout_ms: 1000,
            ..Default::default()
        }),
        ..Default::default()
    };
    
    // Step 3: 应被执行
    let step3 = TestStep {
        step_id: 3,
        step_name: "Step3_AfterFail".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD3".to_vec(),
            timeout_ms: 1000,
            ..Default::default()
        }),
        ..Default::default()
    };
    
    engine.add_test_step(step1).unwrap();
    engine.add_test_step(step2).unwrap();
    engine.add_test_step(step3).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(500));
    
    let slot = engine.get_slot(0).unwrap();
    let guard = slot.read();
    
    // 验证步骤执行顺序：Step1 失败 -> 跳过 Step2 -> 执行 Step3
    assert_eq!(guard.step_results.len(), 2, "Only 2 steps should execute (1 and 3)");
    assert_eq!(guard.step_results[0].step_id, 1);
    assert_eq!(guard.step_results[0].status, StepStatus::Failed);
    assert_eq!(guard.step_results[1].step_id, 3);
}

// ========== 测试：UIUpdateCallback 被正确触发 ==========
#[test]
fn test_ui_update_callback_triggered() {
    use std::sync::Arc;
    use std::sync::atomic::{AtomicU32, Ordering};
    
    static UI_UPDATE_COUNT: AtomicU32 = AtomicU32::new(0);
    
    extern "C" fn mock_ui_callback(
        _json: *const std::ffi::c_char,
        _json_len: u32,
        _user_data: *mut std::ffi::c_void,
    ) {
        UI_UPDATE_COUNT.fetch_add(1, Ordering::SeqCst);
    }
    
    UI_UPDATE_COUNT.store(0, Ordering::SeqCst);
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_engine_task_instant, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    engine.register_ui_callback(mock_ui_callback, std::ptr::null_mut());
    
    let step = TestStep {
        step_id: 1,
        step_name: "UIUpdate_Step".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 1000,
            ..Default::default()
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(300));
    
    // UIUpdateCallback 应至少被调用一次（步骤完成时）
    assert!(UI_UPDATE_COUNT.load(Ordering::SeqCst) >= 1, "UIUpdateCallback should be triggered");
}

// ========== 测试：Compare 检查规则 ==========
#[test]
fn test_compare_check_rule() {
    use std::sync::Arc;
    
    static CALL_INDEX: std::sync::atomic::AtomicU32 = std::sync::atomic::AtomicU32::new(0);
    
    extern "C" fn mock_values(
        slot_id: u32,
        task_id: u64,
        _device: *const std::ffi::c_char,
        _addr: *const std::ffi::c_char,
        _proto: *const std::ffi::c_char,
        _action: *const std::ffi::c_char,
        _payload: *const u8,
        _len: u32,
        _timeout: u32,
        user_data: *mut std::ffi::c_void,
    ) -> i32 {
        let idx = CALL_INDEX.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        unsafe {
            let registry = &*(user_data as *const TaskRegistry);
            match idx {
                0 => { registry.submit(task_id, slot_id, TaskResult::Ok(b"100".to_vec())); }, // var_a
                1 => { registry.submit(task_id, slot_id, TaskResult::Ok(b"50".to_vec())); },  // var_b
                _ => { registry.submit(task_id, slot_id, TaskResult::Ok(b"0".to_vec())); },
            }
        }
        0
    }
    
    CALL_INDEX.store(0, std::sync::atomic::Ordering::SeqCst);
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_values, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    // Step 1: 读取 var_a = 100
    let step1 = TestStep {
        step_id: 1,
        step_name: "Read_A".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"GET_A".to_vec(),
            timeout_ms: 1000,
            parse_rule: Some(ParseRule::Number), // 必须有解析规则才能存变量
            ..Default::default()
        }),
        save_to: Some("var_a".into()),
        next_on_pass: Some(2),
        ..Default::default()
    };
    
    // Step 2: 读取 var_b = 50, 然后比较 var_a > var_b (100 > 50 = true)
    let step2 = TestStep {
        step_id: 2,
        step_name: "Read_B_Compare".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDevice".into(),
            action_type: ActionType::Query,
            payload: b"GET_B".to_vec(),
            timeout_ms: 1000,
            parse_rule: Some(ParseRule::Number), // 必须有解析规则才能存变量
            ..Default::default()
        }),
        save_to: Some("var_b".into()),
        check_type: CheckType::Builtin,
        check_rule: Some(CheckRule::Compare {
            var_a: "var_a".into(),
            operator: CompareOp::Gt,
            var_b: "var_b".into(),
        }),
        ..Default::default()
    };
    
    engine.add_test_step(step1).unwrap();
    engine.add_test_step(step2).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    std::thread::sleep(Duration::from_millis(500));
    
    let slot = engine.get_slot(0).unwrap();
    let guard = slot.read();
    
    assert_eq!(guard.step_results.len(), 2);
    assert_eq!(guard.step_results[0].status, StepStatus::Passed, "Step1 should pass");
    assert_eq!(guard.step_results[1].status, StepStatus::Passed, "Step2 Compare check should pass (100 > 50)");
}

// ========== 测试：停止控制命令 ==========
#[test]
fn test_stop_slot_control() {
    use std::sync::Arc;
    use std::sync::atomic::{AtomicU32, Ordering};
    
    static STEPS_EXECUTED: AtomicU32 = AtomicU32::new(0);
    
    extern "C" fn mock_slow_callback(
        slot_id: u32,
        task_id: u64,
        _device: *const std::ffi::c_char,
        _addr: *const std::ffi::c_char,
        _proto: *const std::ffi::c_char,
        _action: *const std::ffi::c_char,
        _payload: *const u8,
        _len: u32,
        _timeout: u32,
        user_data: *mut std::ffi::c_void,
    ) -> i32 {
        std::thread::sleep(Duration::from_millis(100)); // 每步执行 100ms
        STEPS_EXECUTED.fetch_add(1, Ordering::SeqCst);
        unsafe {
            let registry = &*(user_data as *const TaskRegistry);
            registry.submit(task_id, slot_id, TaskResult::Ok(b"ok".to_vec()));
        }
        0
    }
    
    STEPS_EXECUTED.store(0, Ordering::SeqCst);
    
    let mut engine = create_test_engine();
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_slow_callback, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    
    // 添加 10 个步骤（总共需要 1 秒执行）
    for i in 1..=10 {
        let step = TestStep {
            step_id: i,
            step_name: format!("Step_{}", i),
            execution_mode: ExecutionMode::EngineControlled,
            engine_task: Some(EngineTask {
                target_device: "MockDevice".into(),
                action_type: ActionType::Query,
                payload: b"CMD".to_vec(),
                timeout_ms: 1000,
                ..Default::default()
            }),
            next_on_pass: if i < 10 { Some(i + 1) } else { None },
            ..Default::default()
        };
        engine.add_test_step(step).unwrap();
    }
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    // 等待 250ms（大约 2-3 个步骤执行后）
    std::thread::sleep(Duration::from_millis(250));
    
    // 发送停止信号
    let slot = engine.get_slot(0).unwrap();
    slot.read().send_control_blocking(catalytic::core::slot::ControlSignal::Stop);
    
    std::thread::sleep(Duration::from_millis(200));
    
    // 验证：不是所有 10 步都执行了
    let executed = STEPS_EXECUTED.load(Ordering::SeqCst);
    assert!(executed < 10, "Stop should prevent all 10 steps from executing, actual: {}", executed);
    assert!(executed >= 1, "At least 1 step should have executed before stop");
}
