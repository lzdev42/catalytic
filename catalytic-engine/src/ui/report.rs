//! 测试报告

use serde::Serialize;
use std::collections::HashMap;
use crate::model::{DeviceBindingInfo, StepResult};

/// 测试报告
#[derive(Debug, Serialize)]
pub struct TestReport {
    #[serde(rename = "type")]
    pub msg_type: String,
    pub slot_id: u32,
    pub sn: Option<String>,
    pub device_bindings: HashMap<String, DeviceBindingInfo>,
    pub overall_status: String,
    pub total_steps: u32,
    pub passed: u32,
    pub failed: u32,
    pub skipped: u32,
    pub elapsed_ms: u64,
    pub start_time: u64,
    pub end_time: u64,
    pub steps: Vec<StepResult>,
}

impl TestReport {
    pub fn from_results(
        slot_id: u32,
        sn: Option<String>,
        device_bindings: HashMap<String, DeviceBindingInfo>,
        steps: Vec<StepResult>,
        start_time: u64,
        end_time: u64,
    ) -> Self {
        let total_steps = steps.len() as u32;
        let passed = steps.iter().filter(|s| s.status == crate::model::StepStatus::Passed).count() as u32;
        let failed = steps.iter().filter(|s| s.status == crate::model::StepStatus::Failed).count() as u32;
        let skipped = steps.iter().filter(|s| s.status == crate::model::StepStatus::Skipped).count() as u32;

        let overall_status = if failed > 0 {
            "failed".to_string()
        } else {
            "passed".to_string()
        };

        Self {
            msg_type: "test_report".to_string(),
            slot_id,
            sn,
            device_bindings,
            overall_status,
            total_steps,
            passed,
            failed,
            skipped,
            elapsed_ms: end_time.saturating_sub(start_time),
            start_time,
            end_time,
            steps,
        }
    }
}
