//! 统一错误类型定义

use thiserror::Error;
use crate::model::status::SlotStatus;

/// 引擎错误类型
#[derive(Debug, Error)]
pub enum EngineError {
    #[error("无效的槽位 ID: {0}")]
    InvalidSlotId(u32),

    #[error("槽位状态不允许此操作: 当前状态 {current:?}, 期望状态 {expected:?}")]
    InvalidSlotState {
        current: SlotStatus,
        expected: Vec<SlotStatus>,
    },

    #[error("配置解析失败: {0}")]
    ConfigParseError(#[from] serde_json::Error),

    #[error("设备类型不存在: {0}")]
    DeviceTypeNotFound(String),

    #[error("设备实例不存在: {0}")]
    DeviceInstanceNotFound(String),

    #[error("步骤 ID 不存在: {0}")]
    StepNotFound(u32),

    #[error("解析失败: {0}")]
    ParseError(String),

    #[error("检查失败: {0}")]
    CheckError(String),

    #[error("表达式求值错误: {0}")]
    ExpressionError(String),

    #[error("回调未注册")]
    CallbackNotRegistered,

    #[error("任务超时")]
    TaskTimeout,

    #[error("存储错误: {0}")]
    StorageError(String),

    #[error("内部错误: {0}")]
    InternalError(String),

    #[error("执行错误: {0}")]
    ExecutionError(String),

    #[error("任务中断")]
    Interrupted,

    #[error("任务超时 ({0} ms)")]
    Timeout(u64),
}

/// FFI 返回码
pub const SUCCESS: i32 = 0;
pub const ERR_INVALID_STATE: i32 = -1;
pub const ERR_INVALID_PARAM: i32 = -2;
pub const ERR_INTERNAL: i32 = -3;

/// 将 EngineError 转换为 FFI 返回码
impl From<&EngineError> for i32 {
    fn from(err: &EngineError) -> Self {
        match err {
            EngineError::InvalidSlotId(_) => ERR_INVALID_PARAM,
            EngineError::InvalidSlotState { .. } => ERR_INVALID_STATE,
            EngineError::ConfigParseError(_) => ERR_INVALID_PARAM,
            EngineError::DeviceTypeNotFound(_) => ERR_INVALID_PARAM,
            EngineError::DeviceInstanceNotFound(_) => ERR_INVALID_PARAM,
            EngineError::StepNotFound(_) => ERR_INVALID_PARAM,
            EngineError::ParseError(_) => ERR_INTERNAL,
            EngineError::CheckError(_) => ERR_INTERNAL,
            EngineError::ExpressionError(_) => ERR_INTERNAL,
            EngineError::CallbackNotRegistered => ERR_INVALID_STATE,
            EngineError::TaskTimeout => ERR_INTERNAL,
            EngineError::StorageError(_) => ERR_INTERNAL,
            EngineError::InternalError(_) => ERR_INTERNAL,
            EngineError::ExecutionError(_) => ERR_INTERNAL,
            EngineError::Interrupted => ERR_INTERNAL, // 或其他特定码
            EngineError::Timeout(_) => ERR_INTERNAL,
        }
    }
}

pub type Result<T> = std::result::Result<T, EngineError>;
