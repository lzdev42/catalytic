//! 配置管理 FFI

use std::ffi::{c_char, CStr};
use crate::core::CatEngine;
use crate::model::{DeviceType, TestStep, SlotBinding};
use crate::ffi::helpers::to_cstring_ptr;
use crate::error::{SUCCESS, ERR_INVALID_PARAM, ERR_INTERNAL};

/// 全局配置结构
#[derive(serde::Deserialize)]
struct GlobalConfig {
    #[serde(default)]
    device_types: std::collections::HashMap<String, DeviceType>,
    #[serde(default)]
    test_steps: Vec<TestStep>,
    #[serde(default)]
    slot_bindings: Vec<SlotBinding>,
}

/// 加载 JSON 配置
///
/// # Safety
/// engine 和 config_json 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_load_config(
    engine: *mut CatEngine,
    config_json: *const c_char,
) -> i32 {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() || config_json.is_null() {
            return ERR_INVALID_PARAM;
        }
    
        let engine = &mut *engine;
        let config_str = match CStr::from_ptr(config_json).to_str() {
            Ok(s) => s,
            Err(_) => return ERR_INVALID_PARAM,
        };
    
        let config: GlobalConfig = match serde_json::from_str(config_str) {
            Ok(c) => c,
            Err(_) => return ERR_INVALID_PARAM,
        };
    
        // 加载设备类型
        for (name, device_type) in config.device_types {
            if engine.add_device_type(name, device_type).is_err() {
                return ERR_INTERNAL;
            }
        }
    
        // 加载测试步骤
        for step in config.test_steps {
            if engine.add_test_step(step).is_err() {
                return ERR_INTERNAL;
            }
        }
    
        // 加载槽位绑定
        for binding in config.slot_bindings {
            if engine.set_slot_binding(binding.slot_id, binding.devices).is_err() {
                return ERR_INTERNAL;
            }
        }
    
        SUCCESS
    })
}

/// 获取配置 JSON（返回的字符串需要调用 cat_engine_free_json 释放）
///
/// # Safety
/// engine 必须是有效指针
#[no_mangle]
pub unsafe extern "C" fn cat_engine_get_config_json(
    engine: *const CatEngine,
) -> *mut c_char {
    use crate::ffi_guard;
    ffi_guard!({
        if engine.is_null() {
            return std::ptr::null_mut();
        }
    
        let engine = &*engine;
        let device_types_map = engine.get_device_types_map();
        
        // 构建 device_types 数组，手动添加 id 字段（因为 type_name 被 #[serde(skip)]）
        let device_types_array: Vec<serde_json::Value> = device_types_map.iter()
            .map(|(id, dt)| {
                serde_json::json!({
                    "id": id,
                    "name": dt.name,
                    "plugin_id": dt.plugin_id,
                    "instances": dt.instances,
                    "commands": dt.commands,
                })
            })
            .collect();
        
        // 构建 devices map: type_id -> [instances]（Host ListDevices 需要这个格式，保留兼容性）
        // 注意：UI 现在主要使用 device_types 里的 nested instances
        let devices_map: std::collections::HashMap<&String, &Vec<crate::model::DeviceInstance>> = 
            device_types_map.iter()
                .map(|(id, dt)| (id, &dt.instances))
                .collect();
        
        // 构建完整配置对象
        let config = serde_json::json!({
            "slot_count": engine.slot_count(),
            "device_types": device_types_array,
            "devices": devices_map,
            "test_steps": engine.get_test_steps(),
            "slot_bindings": &engine.slot_bindings,
        });
    
    
    
        to_cstring_ptr(&config)
    }, std::ptr::null_mut())
}
