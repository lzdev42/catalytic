//! 全局 UI 快照

use serde::Serialize;
use std::collections::HashMap;
use crate::model::{SlotStatus, StepStatus, DeviceBindingInfo, VariableDisplay};

/// 全局 UI 快照
#[derive(Debug, Serialize)]
pub struct UISnapshot {
    #[serde(rename = "type")]
    pub msg_type: String,
    pub timestamp: u64,
    pub slots: Vec<SlotSnapshot>,
}

impl UISnapshot {
    pub fn new(slots: Vec<SlotSnapshot>) -> Self {
        Self {
            msg_type: "ui_snapshot".to_string(),
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64,
            slots,
        }
    }
}

/// 单槽位快照
#[derive(Debug, Serialize)]
pub struct SlotSnapshot {
    pub slot_id: u32,
    pub sn: Option<String>,
    pub device_bindings: HashMap<String, DeviceBindingInfo>,
    pub status: SlotStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub progress: Option<ProgressInfo>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub current_step: Option<CurrentStepInfo>,
    pub variables: HashMap<String, VariableDisplay>,
}

/// 进度信息
#[derive(Debug, Serialize)]
pub struct ProgressInfo {
    pub current_step: u32,
    pub total_steps: u32,
    pub percent: u8,
    pub elapsed_ms: u64,
    pub start_time: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub end_time: Option<u64>,
}

/// 当前步骤信息
#[derive(Debug, Serialize)]
pub struct CurrentStepInfo {
    pub step_id: u32,
    pub step_index: u32,
    pub step_name: String,
    pub status: StepStatus,
    pub description: String,
    pub elapsed_ms: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_message: Option<String>,
}
