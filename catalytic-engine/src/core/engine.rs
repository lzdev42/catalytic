//! CatEngine 主结构

use std::collections::HashMap;
use std::ffi::c_void;
use std::sync::Arc;
use parking_lot::RwLock;
use tokio::runtime::Runtime;

use crate::core::slot::SlotContext;
use crate::model::{DeviceType, TestStep, SlotBinding, DeviceInstance};
use crate::ffi::callback::{EngineTaskCallback, HostTaskCallback, UIUpdateCallback, LogCallback};
use crate::error::{EngineError, Result};

/// 回调函数集合
pub struct Callbacks {
    pub engine_task: Option<EngineTaskCallback>,
    pub engine_task_user_data: *mut c_void,

    pub host_task: Option<HostTaskCallback>,
    pub host_task_user_data: *mut c_void,

    pub ui_update: Option<UIUpdateCallback>,
    pub ui_update_user_data: *mut c_void,
    
    // [NEW] 日志回调接口
    pub log: Option<LogCallback>,
    pub log_user_data: *mut c_void,
}

// Callbacks 需要 Send + Sync
unsafe impl Send for Callbacks {}
unsafe impl Sync for Callbacks {}

impl Default for Callbacks {
    fn default() -> Self {
        Self {
            engine_task: None,
            engine_task_user_data: std::ptr::null_mut(),
            host_task: None,
            host_task_user_data: std::ptr::null_mut(),
            ui_update: None,
            ui_update_user_data: std::ptr::null_mut(),
            log: None,
            log_user_data: std::ptr::null_mut(),
        }
    }
}

impl Callbacks {
    /// 调用 EngineTask 回调
    pub fn call_engine_task(
        &self,
        slot_id: u32,
        task_id: u64,
        device_type: &str,
        device_address: &str,
        protocol: &str,
        action_type: &str,
        payload: &[u8],
        timeout_ms: u32,
    ) -> i32 {
        use std::ffi::CString;
        
        let callback = match self.engine_task {
            Some(cb) => cb,
            None => return -1,
        };

        let device_type_c = CString::new(device_type).unwrap_or_default();
        let device_address_c = CString::new(device_address).unwrap_or_default();
        let protocol_c = CString::new(protocol).unwrap_or_default();
        let action_type_c = CString::new(action_type).unwrap_or_default();

        callback(
            slot_id,
            task_id,
            device_type_c.as_ptr(),
            device_address_c.as_ptr(),
            protocol_c.as_ptr(),
            action_type_c.as_ptr(),
            payload.as_ptr(),
            payload.len() as u32,
            timeout_ms,
            self.engine_task_user_data,
        )
    }

    /// 调用 HostTask 回调
    pub fn call_host_task(
        &self,
        slot_id: u32,
        task_id: u64,
        task_name: &str,
        params: &[u8],
        timeout_ms: u32,
    ) -> i32 {
        use std::ffi::CString;

        let callback = match self.host_task {
            Some(cb) => cb,
            None => return -1,
        };

        let task_name_c = CString::new(task_name).unwrap_or_default();

        callback(
            slot_id,
            task_id,
            task_name_c.as_ptr(),
            params.as_ptr(),
            params.len() as u32,
            timeout_ms,
            self.host_task_user_data,
        )
    }

    /// 调用 UI 更新回调
    pub fn call_ui_update(&self, json: &str) {
        use std::ffi::CString;

        if let Some(callback) = self.ui_update {
            if let Ok(json_c) = CString::new(json) {
                callback(
                    json_c.as_ptr(),
                    json.len() as u32,
                    self.ui_update_user_data,
                );
            }
        }
    }
}

/// Catalytic Engine 主结构
pub struct CatEngine {
    /// 槽位列表
    slots: Vec<Arc<RwLock<SlotContext>>>,

    /// 设备类型配置 {type_name: DeviceType}
    device_types: HashMap<String, DeviceType>,

    /// 测试步骤列表
    test_steps: Vec<TestStep>,

    /// 槽位绑定配置
    pub slot_bindings: Vec<SlotBinding>,

    /// 回调函数
    callbacks: Arc<RwLock<Callbacks>>,

    /// 任务注册表（用于等待 submit_result）
    task_registry: Arc<crate::core::task::TaskRegistry>,

    /// 异步运行时
    runtime: Runtime,
    
    /// 数据存储（设置 data_path 后初始化）
    storage: Option<crate::storage::Storage>,
    
    /// 数据目录路径
    data_path: Option<String>,
}

impl CatEngine {
    /// 创建引擎实例
    pub fn new(slot_count: u32) -> Result<Self> {
        let runtime = Runtime::new()
            .map_err(|e| EngineError::InternalError(format!("创建 Tokio 运行时失败: {}", e)))?;

        let mut slots = Vec::with_capacity(slot_count as usize);
        for i in 0..slot_count {
            slots.push(Arc::new(RwLock::new(SlotContext::new(i))));
        }

        Ok(Self {
            slots,
            device_types: HashMap::new(),
            test_steps: Vec::new(),
            slot_bindings: Vec::new(),
            callbacks: Arc::new(RwLock::new(Callbacks::default())),
            task_registry: Arc::new(crate::core::task::TaskRegistry::new()),
            runtime,
            storage: None,
            data_path: None,
        })
    }

    /// 设置数据存储路径
    /// 
    /// 调用此方法后，Engine 会在指定路径打开（或创建）redb 数据库，并自动加载已保存的配置
    pub fn set_data_path(&mut self, path: &str) -> Result<()> {
        use std::fs;
        use std::path::Path;
        
        // 确保目录存在
        let path_obj = Path::new(path);
        if !path_obj.exists() {
            fs::create_dir_all(path_obj)
                .map_err(|e| EngineError::InternalError(format!("创建数据目录失败: {}", e)))?;
        }
        
        // 打开 redb 数据库
        let db_path = path_obj.join("engine.db");
        let storage = crate::storage::Storage::open(db_path.to_str().unwrap_or(path))?;
        
        self.storage = Some(storage);
        self.data_path = Some(path.to_string());
        
        // 自动加载已保存的配置
        self.load_from_storage()?;
        
        Ok(())
    }
    
    /// 获取数据存储路径
    pub fn get_data_path(&self) -> Option<&str> {
        self.data_path.as_deref()
    }
    
    /// 将当前配置保存到存储
    fn save_to_storage(&self) -> Result<()> {
        if let Some(storage) = &self.storage {
            // 构建完整配置 JSON（包含 slot_count）
            let config = serde_json::json!({
                "slot_count": self.slots.len(),
                "device_types": &self.device_types,
                "test_steps": &self.test_steps,
                "slot_bindings": &self.slot_bindings,
            });
            
            let bytes = serde_json::to_vec(&config)
                .map_err(|e| EngineError::InternalError(format!("序列化配置失败: {}", e)))?;
            
            storage.save_config("full_config", &bytes)?;
        }
        Ok(())
    }
    
    /// 从存储加载配置
    fn load_from_storage(&mut self) -> Result<()> {
        eprintln!("[Engine] load_from_storage called");
        if let Some(storage) = &self.storage {
            eprintln!("[Engine] storage exists, attempting to load full_config");
            if let Some(bytes) = storage.load_config("full_config")? {
                eprintln!("[Engine] loaded {} bytes from full_config", bytes.len());
                // 反序列化配置
                #[derive(serde::Deserialize)]
                struct StoredConfig {
                    #[serde(default)]
                    slot_count: Option<usize>,
                    #[serde(default)]
                    device_types: HashMap<String, DeviceType>,
                    #[serde(default)]
                    test_steps: Vec<TestStep>,
                    #[serde(default)]
                    slot_bindings: Vec<SlotBinding>,
                }
                
                let config: StoredConfig = serde_json::from_slice(&bytes)
                    .map_err(|e| {
                        eprintln!("[Engine] deserialization failed: {}", e);
                        EngineError::InternalError(format!("反序列化配置失败: {}", e))
                    })?;
                
                eprintln!("[Engine] deserialization success, {} device types", config.device_types.len());
                
                // 恢复槽位数量（如果已保存，且大于 0）
                if let Some(saved_slot_count) = config.slot_count {
                    if saved_slot_count > 0 && saved_slot_count != self.slots.len() {
                        eprintln!("[Engine] restoring slot count from {} to {}", self.slots.len(), saved_slot_count);
                        // 直接调整 slots 向量大小
                        let current = self.slots.len();
                        if saved_slot_count > current {
                            for i in current..saved_slot_count {
                                self.slots.push(std::sync::Arc::new(parking_lot::RwLock::new(SlotContext::new(i as u32))));
                            }
                        } else if saved_slot_count < current {
                            self.slots.truncate(saved_slot_count);
                        }
                    }
                }
                
                // 恢复设备类型（需要手动设置 type_name，因为它被 skip 了）
                for (name, mut device_type) in config.device_types {
                    device_type.type_name = name.clone();
                    self.device_types.insert(name, device_type);
                }
                
                // 恢复测试步骤
                self.test_steps = config.test_steps;
                
                // 恢复槽位绑定
                self.slot_bindings = config.slot_bindings;

                // 强制同步：将恢复的绑定应用到槽位运行时
                for slot_id in 0..self.slots.len() {
                    if let Err(e) = self.apply_binding_to_slot(slot_id as u32) {
                        eprintln!("[Engine] failed to apply binding for slot {}: {}", slot_id, e);
                    }
                }
                
                eprintln!("[Engine] loaded {} device types, {} slots into memory", self.device_types.len(), self.slots.len());
            } else {
                eprintln!("[Engine] no full_config found in storage");
            }
        } else {
            eprintln!("[Engine] no storage available");
        }
        Ok(())
    }

    /// 获取槽位数量
    pub fn slot_count(&self) -> u32 {
        self.slots.len() as u32
    }

    /// 获取槽位（只读）
    pub fn get_slot(&self, slot_id: u32) -> Result<Arc<RwLock<SlotContext>>> {
        self.slots
            .get(slot_id as usize)
            .cloned()
            .ok_or(EngineError::InvalidSlotId(slot_id))
    }

    /// 设置槽位数量
    pub fn set_slot_count(&mut self, new_count: u32) -> Result<()> {
        // 槽位数量必须大于 0
        if new_count == 0 {
            return Err(EngineError::InternalError("槽位数量必须大于 0".to_string()));
        }
        
        // 检查是否有槽位正在运行
        for slot in &self.slots {
            let slot_guard = slot.read();
            if slot_guard.state_machine.is_running() || slot_guard.state_machine.is_paused() {
                return Err(EngineError::InvalidSlotState {
                    current: slot_guard.status(),
                    expected: vec![crate::model::SlotStatus::Idle],
                });
            }
        }

        let current = self.slots.len();
        let new = new_count as usize;

        if new > current {
            for i in current..new {
                self.slots.push(Arc::new(RwLock::new(SlotContext::new(i as u32))));
            }
        } else if new < current {
            self.slots.truncate(new);
        }
        
        // 保存到持久化存储
        self.save_to_storage()?;

        Ok(())
    }

    // ========== 设备管理 ==========

    /// 添加设备类型
    pub fn add_device_type(&mut self, type_name: String, mut device_type: DeviceType) -> Result<()> {
        device_type.type_name = type_name.clone();
        self.device_types.insert(type_name, device_type);
        self.save_to_storage()?;
        Ok(())
    }

    /// 获取设备类型
    pub fn get_device_type(&self, type_name: &str) -> Option<&DeviceType> {
        self.device_types.get(type_name)
    }

    /// 获取设备类型 HashMap（克隆，用于 executor）
    pub fn get_device_types_map(&self) -> HashMap<String, DeviceType> {
        self.device_types.clone()
    }

    /// 添加设备实例
    pub fn add_device_instance(&mut self, type_name: &str, instance: DeviceInstance) -> Result<()> {
        let device_type = self.device_types
            .get_mut(type_name)
            .ok_or_else(|| EngineError::DeviceTypeNotFound(type_name.to_string()))?;
        device_type.add_instance(instance);
        self.save_to_storage()?;
        Ok(())
    }

    /// 移除设备实例
    pub fn remove_device_instance(&mut self, type_name: &str, instance_id: &str) -> Result<()> {
        let device_type = self.device_types
            .get_mut(type_name)
            .ok_or_else(|| EngineError::DeviceTypeNotFound(type_name.to_string()))?;
        device_type.remove_instance(instance_id)
            .ok_or_else(|| EngineError::DeviceInstanceNotFound(instance_id.to_string()))?;
        self.save_to_storage()?;
        Ok(())
    }

    // ========== 测试步骤管理 ==========

    /// 获取所有测试步骤
    pub fn get_test_steps(&self) -> &[TestStep] {
        &self.test_steps
    }

    /// 添加测试步骤
    pub fn add_test_step(&mut self, step: TestStep) -> Result<()> {
        self.test_steps.push(step);
        self.save_to_storage()?;
        Ok(())
    }

    /// 更新测试步骤
    pub fn update_test_step(&mut self, step_id: u32, new_step: TestStep) -> Result<()> {
        let idx = self.test_steps
            .iter()
            .position(|s| s.step_id == step_id)
            .ok_or(EngineError::StepNotFound(step_id))?;
        self.test_steps[idx] = new_step;
        self.save_to_storage()?;
        Ok(())
    }

    /// 移除测试步骤
    pub fn remove_test_step(&mut self, step_id: u32) -> Result<()> {
        let idx = self.test_steps
            .iter()
            .position(|s| s.step_id == step_id)
            .ok_or(EngineError::StepNotFound(step_id))?;
        self.test_steps.remove(idx);
        self.save_to_storage()?;
        Ok(())
    }

    /// 重新排序测试步骤
    pub fn reorder_steps(&mut self, step_ids: &[u32]) -> Result<()> {
        let mut new_steps = Vec::with_capacity(step_ids.len());
        for id in step_ids {
            let step = self.test_steps
                .iter()
                .find(|s| s.step_id == *id)
                .ok_or(EngineError::StepNotFound(*id))?
                .clone();
            new_steps.push(step);
        }
        self.test_steps = new_steps;
        self.save_to_storage()?;
        Ok(())
    }

    // ========== 槽位绑定 ==========

    /// 设置槽位绑定（支持多设备）
    pub fn set_slot_binding(&mut self, slot_id: u32, devices: HashMap<String, Vec<String>>) -> Result<()> {
        // 验证槽位存在
        if slot_id >= self.slots.len() as u32 {
            return Err(EngineError::InvalidSlotId(slot_id));
        }

        // 查找或创建绑定
        if let Some(binding) = self.slot_bindings.iter_mut().find(|b| b.slot_id == slot_id) {
            binding.devices = devices;
        } else {
            self.slot_bindings.push(SlotBinding { slot_id, devices });
        }

        // 更新槽位上下文中的设备实例
        self.apply_binding_to_slot(slot_id)?;
        
        // 保存到持久化存储
        self.save_to_storage()?;

        Ok(())
    }

    /// 获取槽位绑定
    pub fn get_slot_binding(&self, slot_id: u32) -> Option<&SlotBinding> {
        self.slot_bindings.iter().find(|b| b.slot_id == slot_id)
    }

    /// 应用绑定到槽位
    fn apply_binding_to_slot(&self, slot_id: u32) -> Result<()> {
        let binding = self.slot_bindings
            .iter()
            .find(|b| b.slot_id == slot_id);

        if let Some(binding) = binding {
            let slot = self.get_slot(slot_id)?;
            let mut slot_guard = slot.write();

            // 多设备绑定：每个设备类型可能有多个实例，这里只取第一个（当前设计）
            for (device_type_name, instance_labels) in &binding.devices {
                if let Some(device_type) = self.device_types.get(device_type_name) {
                    // 取第一个设备实例用于执行（后续可扩展为多设备并发）
                    if let Some(first_label) = instance_labels.first() {
                        if let Some(instance) = device_type.find_instance(first_label) {
                            slot_guard.set_device_binding(device_type_name.clone(), instance.clone());
                        }
                    }
                }
            }
        }

        Ok(())
    }

    // ========== 回调注册 ==========

    /// 注册 EngineTask 回调
    pub fn register_engine_task_callback(&self, callback: EngineTaskCallback, user_data: *mut c_void) {
        let mut callbacks = self.callbacks.write();
        callbacks.engine_task = Some(callback);
        callbacks.engine_task_user_data = user_data;
    }

    /// 注册 HostTask 回调
    pub fn register_host_task_callback(&self, callback: HostTaskCallback, user_data: *mut c_void) {
        let mut callbacks = self.callbacks.write();
        callbacks.host_task = Some(callback);
        callbacks.host_task_user_data = user_data;
    }

    /// 注册 UI 更新回调
    pub fn register_ui_callback(&self, callback: UIUpdateCallback, user_data: *mut c_void) {
        let mut callbacks = self.callbacks.write();
        callbacks.ui_update = Some(callback);
        callbacks.ui_update_user_data = user_data;
    }

    /// 注册日志回调 (新增)
    pub fn register_log_callback(
        &self, 
        callback: LogCallback, 
        user_data: *mut std::ffi::c_void
    ) {
        let mut callbacks = self.callbacks.write();
        callbacks.log = Some(callback);
        callbacks.log_user_data = user_data;
    }

    /// 获取回调（用于执行器）
    pub fn callbacks(&self) -> Arc<RwLock<Callbacks>> {
        Arc::clone(&self.callbacks)
    }

    /// 获取运行时引用
    pub fn runtime(&self) -> &Runtime {
        &self.runtime
    }

    /// 获取任务注册表
    pub fn task_registry(&self) -> Arc<crate::core::task::TaskRegistry> {
        Arc::clone(&self.task_registry)
    }
}
