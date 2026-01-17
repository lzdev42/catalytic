use std::collections::HashMap;

use std::time::Duration;
use catalytic::core::engine::CatEngine;
use catalytic::core::task::{TaskRegistry, TaskResult};
use catalytic::model::{TestStep, ExecutionMode, EngineTask, ActionType, ParseRule, CheckType, CheckRule, CompareOp, DeviceType, DeviceInstance, StepStatus};


// IMPORTANT: This test assumes the instrument returns identical data 
// for consecutive READ? commands. In production:
// - Option A: Plugin caches the result of first READ? 
// - Option B: Engine supports multi-variable extraction from single query
static MOCK_DATA_RETURN: &str = "1.00E-1, 2.50E+2, 3.33E-3, 4.44E0, 5.55E1";

// --- Mock Engine Task Callback ---
extern "C" fn mock_engine_task(
    slot_id: u32,
    task_id: u64,
    _device: *const std::ffi::c_char,
    _addr: *const std::ffi::c_char,
    _proto: *const std::ffi::c_char,
    cmd: *const std::ffi::c_char,
    _payload: *const u8,
    _len: u32,
    _timeout: u32,
    user_data: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        // Log received command
        let c_str = std::ffi::CStr::from_ptr(cmd);
        let cmd_str = c_str.to_str().unwrap();
        println!("[MockHost] Slot {} Task {} START Cmd: {}", slot_id, task_id, cmd_str);

        // [Delay] Verify Concurrency: Sleep to ensure slots overlap if concurrent
        std::thread::sleep(Duration::from_millis(100));
        
        // Retrieve TaskRegistry from user_data
        let registry = &*(user_data as *const TaskRegistry);
        
        // Simulate immediate response
        let response_data = MOCK_DATA_RETURN.as_bytes().to_vec();
        
        println!("[MockHost] Slot {} Task {} END", slot_id, task_id);
        
        // Submit Result
        registry.submit(task_id, slot_id, TaskResult::Ok(response_data));
    }
    0 // OK
}

// --- Mock Host Task Callback (Empty) ---
extern "C" fn mock_host_task(
    _slot_id: u32,
    _task_id: u64,
    _name: *const std::ffi::c_char,
    _params: *const u8,
    _len: u32,
    _timeout: u32,
    _user_data: *mut std::ffi::c_void,
) -> i32 {
    0
}

#[test]
fn test_full_flow_simulation() {
    // 1. 初始化引擎 (2 Slots)
    let mut engine = CatEngine::new(2).unwrap();
    println!("[Test] Engine initialized with 2 slots.");

    // 2. 注册 Mock 回调
    use std::sync::Arc;
    let registry = engine.task_registry();
    let registry_ptr = Arc::as_ptr(&registry) as *mut std::ffi::c_void;

    engine.register_engine_task_callback(mock_engine_task, registry_ptr);
    engine.register_host_task_callback(mock_host_task, std::ptr::null_mut());
    println!("[Test] Callbacks registered.");

    // 3. 配置设备类型 "SimulatedDMM" (SCPI)
    let dmm_type = DeviceType {
        type_name: "SimulatedDMM".into(),
        name: "Simulated DMM".into(),
        plugin_id: "catalytic.scpi".into(),
        instances: vec![], // 初始为空，稍后添加
        commands: vec![],
    };
    engine.add_device_type("SimulatedDMM".into(), dmm_type).unwrap();

    // 4. 添加设备实例 & 绑定
    let inst_a = DeviceInstance { 
        id: "inst_0".into(), 
        name: "DMM1".into(),
        address: "GPIB0::1::INSTR".into(), 
        ..Default::default() 
    };
    let inst_b = DeviceInstance { 
        id: "inst_1".into(), 
        name: "DMM2".into(),
        address: "GPIB0::2::INSTR".into(), 
        ..Default::default() 
    };
    engine.add_device_instance("SimulatedDMM", inst_a).unwrap();
    engine.add_device_instance("SimulatedDMM", inst_b).unwrap();

    // 绑定 Slot 0 -> inst_0, Slot 1 -> inst_1
    let mut map_0 = HashMap::new(); map_0.insert("SimulatedDMM".into(), vec!["inst_0".into()]);
    engine.set_slot_binding(0, map_0).unwrap();

    let mut map_1 = HashMap::new(); map_1.insert("SimulatedDMM".into(), vec!["inst_1".into()]);
    engine.set_slot_binding(1, map_1).unwrap();
    println!("[Test] Devices bound.");

    // 5. 定义步骤
    // Step 1
    let step1 = TestStep {
        step_id: 1,
        step_name: "Read_Voltage".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "SimulatedDMM".into(),
            action_type: ActionType::Query,
            payload: "READ?".as_bytes().to_vec(),
            timeout_ms: 1000,
            parse_rule: Some(ParseRule::Regex { 
                pattern: r"([^,]+),\s*([^,]+)".into(), 
                group: 1 
            }),
            ..Default::default()
        }),
        save_to: Some("voltage".into()),
        check_type: CheckType::Builtin,
        check_rule: Some(CheckRule::RangeCheck {
            variable: None, min: 0.09, max: 0.11, include_min: true, include_max: true
        }),
        next_on_pass: Some(2),
        ..Default::default()
    };
    engine.add_test_step(step1).unwrap();

    // Step 2
    let step2 = TestStep {
        step_id: 2,
        step_name: "Read_Current".into(),
        execution_mode: ExecutionMode::EngineControlled,
        engine_task: Some(EngineTask {
            target_device: "SimulatedDMM".into(),
            action_type: ActionType::Query,
            payload: "READ?".as_bytes().to_vec(),
            timeout_ms: 1000,
            parse_rule: Some(ParseRule::Regex { 
                pattern: r"([^,]+),\s*([^,]+)".into(), 
                group: 2 
            }),
            ..Default::default()
        }),
        save_to: Some("current".into()),
        check_type: CheckType::Builtin,
        check_rule: Some(CheckRule::Threshold {
            variable: "current".into(), operator: CompareOp::Gt, value: 200.0
        }),
        ..Default::default()
    };
    engine.add_test_step(step2).unwrap();
    println!("[Test] Steps configured.");

    // 6. 启动测试
    use catalytic::core::executor;
    println!("[Test] Starting Slot 0 and Slot 1...");
    executor::spawn_slot(&engine, 0).unwrap();
    executor::spawn_slot(&engine, 1).unwrap();

    // 7. 轮询等待结束
    let start_time = std::time::Instant::now();
    loop {
        let s0 = engine.get_slot(0).unwrap().read().status();
        let s1 = engine.get_slot(1).unwrap().read().status();
        
        if start_time.elapsed().as_secs() > 5 {
            panic!("[Test] Timeout! Slots did not finish in 5s. Status: S0={:?}, S1={:?}", s0, s1);
        }

        if s0 == catalytic::model::SlotStatus::Completed && s1 == catalytic::model::SlotStatus::Completed {
            break;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    println!("[Test] Both slots completed.");

    // 8. 验证结果
    println!("[Test] Verifying results...");

    // 验证 Slot 0
    // API 修复: get_slot().read().variables.get()
    let slot0 = engine.get_slot(0).unwrap();
    let guard0 = slot0.read();
    
    let v0_volt = guard0.variables.get("voltage").unwrap();
    let v0_curr = guard0.variables.get("current").unwrap();
    println!("Slot 0 Voltage: {:?}, Current: {:?}", v0_volt, v0_curr);
    
    // float 0.1, 250.0
    assert!((v0_volt.as_f64().unwrap() - 0.1).abs() < 1e-6);
    assert!((v0_curr.as_f64().unwrap() - 250.0).abs() < 1e-6);

    // 验证 Step Result (API 修复: step_results)
    {
        let r1 = guard0.step_results.get(0).unwrap();
        let r2 = guard0.step_results.get(1).unwrap();
        
        println!("S0 Step 1: {:?}", r1);
        println!("S0 Step 2: {:?}", r2);

        assert_eq!(r1.status, StepStatus::Passed);
        assert_eq!(r2.status, StepStatus::Passed);
    }
    
    // 验证 Slot 1
    {
        let slot1 = engine.get_slot(1).unwrap();
        let guard1 = slot1.read();
        assert_eq!(guard1.step_results.len(), 2);
        assert_eq!(guard1.step_results[0].status, StepStatus::Passed);
    }

    println!("[Test] All verifications passed!");
}
