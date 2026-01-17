//! 日志消息

use serde::Serialize;

/// 日志级别
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum LogLevel {
    Debug,
    Info,
    Warning,
    Error,
}

/// 日志消息
#[derive(Debug, Serialize)]
pub struct LogMessage {
    #[serde(rename = "type")]
    pub msg_type: String,
    pub slot_id: u32,
    pub level: LogLevel,
    pub message: String,
    pub timestamp: u64,
}

impl LogMessage {
    pub fn new(slot_id: u32, level: LogLevel, message: String) -> Self {
        Self {
            msg_type: "log".to_string(),
            slot_id,
            level,
            message,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64,
        }
    }

    pub fn debug(slot_id: u32, message: impl Into<String>) -> Self {
        Self::new(slot_id, LogLevel::Debug, message.into())
    }

    pub fn info(slot_id: u32, message: impl Into<String>) -> Self {
        Self::new(slot_id, LogLevel::Info, message.into())
    }

    pub fn warning(slot_id: u32, message: impl Into<String>) -> Self {
        Self::new(slot_id, LogLevel::Warning, message.into())
    }

    pub fn error(slot_id: u32, message: impl Into<String>) -> Self {
        Self::new(slot_id, LogLevel::Error, message.into())
    }
}
