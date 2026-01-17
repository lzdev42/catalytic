//! 状态机管理

use crate::model::status::SlotStatus;
use crate::error::{EngineError, Result};

/// 状态机
pub struct StateMachine {
    current: SlotStatus,
}

impl StateMachine {
    /// 创建新的状态机
    pub fn new() -> Self {
        Self {
            current: SlotStatus::Idle,
        }
    }

    /// 获取当前状态
    pub fn current(&self) -> SlotStatus {
        self.current
    }

    /// 尝试转换状态
    pub fn transition(&mut self, target: SlotStatus) -> Result<()> {
        if self.current.can_transition_to(target) {
            self.current = target;
            Ok(())
        } else {
            Err(EngineError::InvalidSlotState {
                current: self.current,
                expected: vec![target],
            })
        }
    }

    /// 强制设置状态（用于重置）
    pub fn reset(&mut self) {
        self.current = SlotStatus::Idle;
    }

    /// 强制设置特定状态（内部使用）
    pub fn force_state(&mut self, status: SlotStatus) {
        self.current = status;
    }

    /// 检查是否处于运行状态
    pub fn is_running(&self) -> bool {
        self.current == SlotStatus::Running
    }

    /// 检查是否处于暂停状态
    pub fn is_paused(&self) -> bool {
        self.current == SlotStatus::Paused
    }

    /// 检查是否处于空闲状态
    pub fn is_idle(&self) -> bool {
        self.current == SlotStatus::Idle
    }
}

impl Default for StateMachine {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_state_transitions() {
        let mut sm = StateMachine::new();
        assert!(sm.is_idle());

        // idle -> running
        sm.transition(SlotStatus::Running).unwrap();
        assert!(sm.is_running());

        // running -> paused
        sm.transition(SlotStatus::Paused).unwrap();
        assert!(sm.is_paused());

        // paused -> running
        sm.transition(SlotStatus::Running).unwrap();
        assert!(sm.is_running());

        // running -> completed
        sm.transition(SlotStatus::Completed).unwrap();
        assert_eq!(sm.current(), SlotStatus::Completed);
    }

    #[test]
    fn test_invalid_transition() {
        let mut sm = StateMachine::new();
        
        // idle -> completed (非法)
        let result = sm.transition(SlotStatus::Completed);
        assert!(result.is_err());
    }
}
