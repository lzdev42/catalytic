//! 任务结果管理

use std::collections::HashMap;
use parking_lot::Mutex;
use tokio::sync::oneshot;

/// 任务结果类型
#[derive(Debug, Clone)]
pub enum TaskResult {
    /// 成功，包含数据
    Ok(Vec<u8>),
    /// 超时
    Timeout,
    /// 错误
    Error(String),
}

/// 待处理任务的发送端
type PendingSender = oneshot::Sender<TaskResult>;

/// 任务注册表（管理所有待处理任务）
#[derive(Default)]
pub struct TaskRegistry {

    // 存储 (SlotID, Sender)
    pending: Mutex<HashMap<u64, (u32, PendingSender)>>,
}

impl TaskRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    /// 注册一个新任务，返回接收端
    pub fn register(&self, task_id: u64, slot_id: u32) -> oneshot::Receiver<TaskResult> {
        let (tx, rx) = oneshot::channel();
        self.pending.lock().insert(task_id, (slot_id, tx));
        rx
    }

    /// 提交任务结果（由 FFI 调用）
    pub fn submit(&self, task_id: u64, slot_id: u32, result: TaskResult) -> bool {
        let mut guard = self.pending.lock();
        if let Some((registered_slot, sender)) = guard.remove(&task_id) {
            // 安全校验：防止 Host 端张冠李戴
            if registered_slot != slot_id {
                eprintln!("[TaskRegistry] Slot ID mismatch! Task {} registered for slot {}, but received submit from slot {}", 
                    task_id, registered_slot, slot_id);
                return false;
            }
            sender.send(result).is_ok()
        } else {
            false
        }
    }

    /// 取消任务（超时或停止时调用）
    pub fn cancel(&self, task_id: u64) {
        self.pending.lock().remove(&task_id);
    }
}

/// 全局任务 ID 生成器
static TASK_ID_COUNTER: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

/// 生成唯一任务 ID
pub fn generate_task_id() -> u64 {
    TASK_ID_COUNTER.fetch_add(1, std::sync::atomic::Ordering::SeqCst)
}
