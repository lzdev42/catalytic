//! 步骤执行结果定义

use serde::{Deserialize, Serialize};
use crate::model::status::StepStatus;

/// 检查结果详情
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CheckResultDetail {
    /// 检查模板名
    pub template: String,
    /// 检查参数
    pub params: serde_json::Value,
    /// 实际值
    pub actual: serde_json::Value,
    /// 检查结果
    pub passed: bool,
}

/// 步骤执行结果
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StepResult {
    /// 步骤 ID
    pub step_id: u32,
    /// 步骤名称
    pub step_name: String,
    /// 步骤状态
    pub status: StepStatus,
    /// 执行耗时（毫秒）
    pub elapsed_ms: u32,
    /// 最终检查用的值
    #[serde(skip_serializing_if = "Option::is_none")]
    pub final_value: Option<serde_json::Value>,
    /// 检查结果详情
    #[serde(skip_serializing_if = "Option::is_none")]
    pub check_result: Option<CheckResultDetail>,
    /// 结果摘要
    pub result_summary: String,
    /// 错误信息
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_message: Option<String>,
}

impl StepResult {
    /// 创建成功结果
    pub fn passed(step_id: u32, step_name: String, elapsed_ms: u32, summary: String) -> Self {
        Self {
            step_id,
            step_name,
            status: StepStatus::Passed,
            elapsed_ms,
            final_value: None,
            check_result: None,
            result_summary: summary,
            error_message: None,
        }
    }

    /// 创建失败结果
    pub fn failed(step_id: u32, step_name: String, elapsed_ms: u32, summary: String, error: Option<String>) -> Self {
        Self {
            step_id,
            step_name,
            status: StepStatus::Failed,
            elapsed_ms,
            final_value: None,
            check_result: None,
            result_summary: summary,
            error_message: error,
        }
    }

    /// 创建超时结果
    pub fn timeout(step_id: u32, step_name: String, elapsed_ms: u32) -> Self {
        Self {
            step_id,
            step_name,
            status: StepStatus::Timeout,
            elapsed_ms,
            final_value: None,
            check_result: None,
            result_summary: "执行超时".to_string(),
            error_message: Some("任务超时".to_string()),
        }
    }

    /// 创建跳过结果
    pub fn skipped(step_id: u32, step_name: String) -> Self {
        Self {
            step_id,
            step_name,
            status: StepStatus::Skipped,
            elapsed_ms: 0,
            final_value: None,
            check_result: None,
            result_summary: "已跳过".to_string(),
            error_message: None,
        }
    }
}
