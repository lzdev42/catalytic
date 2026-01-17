//! 状态枚举定义

use serde::{Deserialize, Serialize};

/// 槽位状态
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SlotStatus {
    /// 空闲（未开始或已重置）
    Idle,
    /// 运行中
    Running,
    /// 已暂停
    Paused,
    /// 已完成（成功或失败）
    Completed,
    /// 发生错误
    Error,
}

impl Default for SlotStatus {
    fn default() -> Self {
        SlotStatus::Idle
    }
}

impl SlotStatus {
    /// 检查是否可以转换到目标状态
    pub fn can_transition_to(&self, target: SlotStatus) -> bool {
        match (self, target) {
            // idle -> running: start_slot()
            (SlotStatus::Idle, SlotStatus::Running) => true,
            // running -> paused: pause_slot()
            (SlotStatus::Running, SlotStatus::Paused) => true,
            // running -> completed: 所有步骤执行完成
            (SlotStatus::Running, SlotStatus::Completed) => true,
            // running -> error: 发生不可恢复错误
            (SlotStatus::Running, SlotStatus::Error) => true,
            // running -> idle: stop_slot()
            (SlotStatus::Running, SlotStatus::Idle) => true,
            // paused -> running: resume_slot()
            (SlotStatus::Paused, SlotStatus::Running) => true,
            // paused -> idle: stop_slot()
            (SlotStatus::Paused, SlotStatus::Idle) => true,
            // completed -> idle: 重置槽位
            (SlotStatus::Completed, SlotStatus::Idle) => true,
            // error -> idle: 重置槽位
            (SlotStatus::Error, SlotStatus::Idle) => true,
            // 其他都是非法转换
            _ => false,
        }
    }
}

/// 步骤状态
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum StepStatus {
    /// 等待执行
    Waiting,
    /// 正在执行
    Executing,
    /// 执行成功
    Passed,
    /// 执行失败
    Failed,
    /// 执行超时
    Timeout,
    /// 已跳过
    Skipped,
    /// 系统错误（非测试失败）
    Error,
}

impl Default for StepStatus {
    fn default() -> Self {
        StepStatus::Waiting
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_slot_status_transitions() {
        assert!(SlotStatus::Idle.can_transition_to(SlotStatus::Running));
        assert!(SlotStatus::Running.can_transition_to(SlotStatus::Paused));
        assert!(SlotStatus::Paused.can_transition_to(SlotStatus::Running));
        
        // 非法转换
        assert!(!SlotStatus::Idle.can_transition_to(SlotStatus::Completed));
        assert!(!SlotStatus::Completed.can_transition_to(SlotStatus::Running));
    }

    #[test]
    fn test_status_serialization() {
        let status = SlotStatus::Running;
        let json = serde_json::to_string(&status).unwrap();
        assert_eq!(json, "\"running\"");
    }
}
