use std::collections::HashMap;
use tokio::sync::mpsc;
use crate::model::{
    DeviceInstance, SlotStatus, StepResult, VariablePool,
};
use crate::core::state::StateMachine;

/// 控制信号
#[derive(Debug, Clone)]
pub enum ControlSignal {
    Pause,
    Resume,
    Stop,
    StepNext,
    SkipCurrent,
}

/// 槽位运行时上下文
pub struct SlotContext {
    pub slot_id: u32,
    pub sn: Option<String>,
    pub state_machine: StateMachine,
    pub device_bindings: HashMap<String, DeviceInstance>,
    pub current_step_index: usize,
    pub start_time: Option<u64>,
    pub end_time: Option<u64>,
    pub variables: VariablePool,
    pub step_results: Vec<StepResult>,
    control_tx: Option<mpsc::Sender<ControlSignal>>,
    control_rx: Option<mpsc::Receiver<ControlSignal>>,
    // [NEW] 最后一次发生的错误消息
    pub last_error: Option<String>,
}

impl SlotContext {
    pub fn new(slot_id: u32) -> Self {
        let (tx, rx) = mpsc::channel(16);
        Self {
            slot_id,
            sn: None,
            state_machine: StateMachine::new(),
            device_bindings: HashMap::new(),
            current_step_index: 0,
            start_time: None,
            end_time: None,
            variables: VariablePool::new(),
            step_results: Vec::new(),
            control_tx: Some(tx),
            control_rx: Some(rx),
            last_error: None,
        }
    }

    pub fn status(&self) -> SlotStatus {
        self.state_machine.current()
    }

    pub fn set_sn(&mut self, sn: String) {
        self.sn = Some(sn);
    }

    /// [Restored] 清除 SN
    pub fn clear_sn(&mut self) {
        self.sn = None;
    }
    
    pub fn set_device_binding(&mut self, device_name: String, instance: DeviceInstance) {
        self.device_bindings.insert(device_name, instance);
    }

    pub fn get_device_binding(&self, device_name: &str) -> Option<&DeviceInstance> {
        self.device_bindings.get(device_name)
    }

    pub fn reset(&mut self) {
        self.state_machine.reset();
        self.current_step_index = 0;
        self.start_time = None;
        self.end_time = None;
        self.variables.clear();
        self.step_results.clear();
        self.last_error = None; // [NEW] 重置时清空报错
    }
    
    // 控制信号相关方法
    pub fn get_control_tx(&self) -> Option<mpsc::Sender<ControlSignal>> {
        self.control_tx.clone()
    }
    
    pub fn take_control_rx(&mut self) -> Option<mpsc::Receiver<ControlSignal>> {
        self.control_rx.take()
    }
    
    // 恢复 rx，通常在执行器退出时调用
    pub fn set_control_rx(&mut self, rx: mpsc::Receiver<ControlSignal>) {
        self.control_rx = Some(rx);
    }

    /// [Restored] 重新初始化控制通道
    pub fn reinit_control_channel(&mut self) {
        let (tx, rx) = mpsc::channel(16);
        self.control_tx = Some(tx);
        self.control_rx = Some(rx);
    }

    /// [Restored] 阻塞发送控制信号（用于 FFI 调用）
    pub fn send_control_blocking(&self, signal: ControlSignal) -> bool {
        if let Some(tx) = &self.control_tx {
            match tx.blocking_send(signal) {
                Ok(_) => true,
                Err(_) => false, // 通道已关闭
            }
        } else {
            false
        }
    }

    /// [Restored] 标记开始测试
    pub fn mark_start(&mut self) {
        self.start_time = Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64
        );
        self.end_time = None;
    }

    /// [Restored] 标记结束测试
    pub fn mark_end(&mut self) {
        self.end_time = Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64
        );
    }

    pub fn set_error(&mut self, msg: String) {
        self.last_error = Some(msg);
    }

    pub fn add_step_result(&mut self, result: StepResult) {
        self.step_results.push(result);
    }

    pub fn elapsed_ms(&self) -> u64 {
        if let Some(start) = self.start_time {
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64;
            now.saturating_sub(start)
        } else {
            0
        }
    }
}
