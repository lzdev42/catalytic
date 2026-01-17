//! 端到端集成测试
//! 
//! 验证完整的测试执行流程：
//! 1. 配置设备和步骤
//! 2. 启动测试 (start_slot)
//! 3. Engine 通过 EngineTaskCallback 请求 Host 执行设备通讯
//! 4. Host 通过 submit_result 返回结果
//! 5. Engine 推送 UIUpdateCallback
//! 6. 验证结果

use std::sync::atomic::{AtomicU32, AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use catalytic::core::engine::CatEngine;
use catalytic::core::task::{TaskRegistry, TaskResult};
use catalytic::model::{
    TestStep, ExecutionMode, EngineTask, ActionType, ParseRule,
    CheckType, CheckRule, DeviceType, DeviceInstance, SlotStatus
};
use std::collections::HashMap;

// 计数器：记录回调触发次数
static TASK_CALLBACK_COUNT: AtomicU32 = AtomicU32::new(0);
static UI_UPDATE_COUNT: AtomicU32 = AtomicU32::new(0);
static TEST_COMPLETED: AtomicBool = AtomicBool::new(false);

/// Mock Engine Task 回调
/// 模拟 Host 接收 Engine 的设备执行请求
extern "C" fn mock_engine_task_callback(
    slot_id: u32,
    task_id: u64,
    _device: *const std::ffi::c_char,
    _addr: *const std::ffi::c_char,
    _plugin_id: *const std::ffi::c_char,
    _action: *const std::ffi::c_char,
    _payload: *const u8,
    _payload_len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    TASK_CALLBACK_COUNT.fetch_add(1, Ordering::SeqCst);
    println!("[E2E] EngineTaskCallback received: slot={}, task={}", slot_id, task_id);
    
    // 模拟 Host 处理并返回结果
    unsafe {
        let registry = &*(user_data as *const TaskRegistry);
        // 模拟设备返回电压值 "3.28"
        let response = b"3.28".to_vec();
        registry.submit(task_id, slot_id, TaskResult::Ok(response));
    }
    
    0 // 成功
}

/// Mock Host Task 回调 (HostControlled 模式)
extern "C" fn mock_host_task_callback(
    slot_id: u32,
    task_id: u64,
    _task_name: *const std::ffi::c_char,
    _params: *const u8,
    _params_len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    println!("[E2E] HostTaskCallback received: slot={}, task={}", slot_id, task_id);
    
    unsafe {
        let registry = &*(user_data as *const TaskRegistry);
        let response = b"host_task_result".to_vec();
        registry.submit(task_id, slot_id, TaskResult::Ok(response));
    }
    
    0
}

/// Mock UI Update 回调
extern "C" fn mock_ui_update_callback(
    json: *const std::ffi::c_char,
    _json_len: u32,
    _user_data: *mut std::ffi::c_void,
) {
    UI_UPDATE_COUNT.fetch_add(1, Ordering::SeqCst);
    
    unsafe {
        let c_str = std::ffi::CStr::from_ptr(json);
        if let Ok(json_str) = c_str.to_str() {
            println!("[E2E UIUpdate] {}", json_str);
            // 检查是否包含 "completed" 状态 (snake_case)
            if json_str.contains("\"status\":\"completed\"") {
                TEST_COMPLETED.store(true, Ordering::SeqCst);
                println!("[E2E] Test completed detected!");
            }
        }
    }
}

/// 端到端测试：完整测试执行流程
#[test]
fn test_e2e_full_test_execution() {
    // 重置计数器
    TASK_CALLBACK_COUNT.store(0, Ordering::SeqCst);
    UI_UPDATE_COUNT.store(0, Ordering::SeqCst);
    TEST_COMPLETED.store(false, Ordering::SeqCst);
    
    // 1. 创建 Engine
    let mut engine = CatEngine::new(1).expect("创建 Engine 失败");
    
    // 2. 注册回调
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;
    
    engine.register_engine_task_callback(mock_engine_task_callback, registry_ptr);
    engine.register_host_task_callback(mock_host_task_callback, registry_ptr);
    engine.register_ui_callback(mock_ui_update_callback, std::ptr::null_mut());
    
    // 3. 配置设备类型
    let device_type = DeviceType {
        type_name: "MockDMM".into(),
        name: "Mock 万用表".into(),
        plugin_id: "mock.dmm".into(),
        instances: vec![],
        commands: vec![],
    };
    engine.add_device_type("MockDMM".into(), device_type).unwrap();
    
    // 4. 添加设备实例
    let instance = DeviceInstance {
        id: "dmm1".into(),
        name: "DMM #1".into(),
        address: "COM3".into(),
        ..Default::default()
    };
    engine.add_device_instance("MockDMM", instance).unwrap();
    
    // 5. 绑定设备到槽位
    let mut bindings = HashMap::new();
    bindings.insert("MockDMM".to_string(), vec!["dmm1".to_string()]);
    engine.set_slot_binding(0, bindings).unwrap();
    
    // 6. 添加测试步骤 - 读取电压并检查范围
    let step1 = TestStep {
        step_id: 1,
        step_name: "读取电压".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "MockDMM".into(),
            action_type: ActionType::Query,
            payload: b"MEAS:VOLT:DC?".to_vec(),
            timeout_ms: 1000,
            parse_rule: Some(ParseRule::Number),
            ..Default::default()
        }),
        save_to: Some("voltage".into()),
        check_type: CheckType::Builtin,
        check_rule: Some(CheckRule::RangeCheck {
            variable: Some("voltage".into()),
            min: 3.0,
            max: 3.5,
            include_min: true,
            include_max: true,
        }),
        ..Default::default()
    };
    engine.add_test_step(step1).unwrap();
    
    // 7. 启动测试
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).expect("启动槽位失败");
    
    // 8. 等待测试完成 (最多 5 秒) - 直接轮询槽位状态
    let slot = engine.get_slot(0).unwrap();
    let start = std::time::Instant::now();
    loop {
        let status = slot.read().status();
        if status == SlotStatus::Completed || status == SlotStatus::Error {
            break;
        }
        if start.elapsed() > Duration::from_secs(5) {
            panic!("测试执行超时: 状态仍为 {:?}", status);
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    
    // 9. 验证结果
    let callback_count = TASK_CALLBACK_COUNT.load(Ordering::SeqCst);
    let ui_count = UI_UPDATE_COUNT.load(Ordering::SeqCst);
    let status = slot.read().status();
    
    println!("[E2E] 结果:");
    println!("  - EngineTaskCallback 调用次数: {}", callback_count);
    println!("  - UIUpdateCallback 调用次数: {}", ui_count);
    println!("  - 槽位状态: {:?}", status);
    
    // 验证回调被调用
    assert!(callback_count >= 1, "EngineTaskCallback 应该至少被调用 1 次");
    
    // 10. 验证槽位状态
    assert_eq!(status, SlotStatus::Completed, "槽位应该是 Completed 状态");
    
    // 11. 验证变量
    let slot_guard = slot.read();
    let voltage = slot_guard.variables.get("voltage");
    assert!(voltage.is_some(), "voltage 变量应该存在");
    if let Some(v) = voltage {
        let val = v.as_f64().expect("voltage 应该是数值");
        assert!((val - 3.28).abs() < 0.01, "voltage 值应该是 3.28，实际是 {}", val);
    }
    
    println!("[E2E] ✅ 端到端测试通过!");
}

/// 测试：超时处理
#[test]
fn test_e2e_timeout_handling() {
    // 不提交结果的回调，模拟 Host 超时
    extern "C" fn timeout_callback(
        _slot_id: u32,
        _task_id: u64,
        _device: *const std::ffi::c_char,
        _addr: *const std::ffi::c_char,
        _plugin_id: *const std::ffi::c_char,
        _action: *const std::ffi::c_char,
        _payload: *const u8,
        _payload_len: u32,
        _timeout: u32,
        _user_data: *mut std::ffi::c_void,
    ) -> i32 {
        println!("[E2E Timeout] Callback received, NOT submitting result (simulating timeout)");
        // 不调用 submit，Engine 将超时
        0
    }
    
    let mut engine = CatEngine::new(1).unwrap();
    engine.register_engine_task_callback(timeout_callback, std::ptr::null_mut());
    
    // 配置一个会超时的步骤
    let device_type = DeviceType {
        type_name: "TimeoutDevice".into(),
        name: "Timeout Device".into(),
        plugin_id: "test".into(),
        instances: vec![],
        commands: vec![],
    };
    engine.add_device_type("TimeoutDevice".into(), device_type).unwrap();
    
    let instance = DeviceInstance {
        id: "dev1".into(),
        name: "Dev #1".into(),
        address: "addr".into(),
        ..Default::default()
    };
    engine.add_device_instance("TimeoutDevice", instance).unwrap();
    let mut bindings = HashMap::new();
    bindings.insert("TimeoutDevice".to_string(), vec!["dev1".to_string()]);
    engine.set_slot_binding(0, bindings).unwrap();
    
    let step = TestStep {
        step_id: 1,
        step_name: "TimeoutStep".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "TimeoutDevice".into(),
            action_type: ActionType::Query,
            payload: b"CMD".to_vec(),
            timeout_ms: 200, // 200ms 超时
            ..Default::default()
        }),
        ..Default::default()
    };
    engine.add_test_step(step).unwrap();
    
    use catalytic::core::executor;
    executor::spawn_slot(&engine, 0).unwrap();
    
    // 等待超时 + 处理时间
    std::thread::sleep(Duration::from_millis(500));
    
    // 验证槽位状态 - 应该是 Error 或仍在等待
    let slot = engine.get_slot(0).unwrap();
    let status = slot.read().status();
    
    // 超时后槽位应该结束（Completed 或 Error）
    assert!(
        status == SlotStatus::Completed || status == SlotStatus::Error,
        "超时后槽位状态应该是 Completed 或 Error，实际是 {:?}", status
    );
    
    println!("[E2E Timeout] ✅ 超时处理测试通过，状态: {:?}", status);
}
