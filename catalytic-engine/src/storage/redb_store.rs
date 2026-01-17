//! redb 数据库存储

use redb::{Database, TableDefinition};
use crate::error::{EngineError, Result};

// 定义表
const CONFIG_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("config");

/// 存储接口
pub struct Storage {
    db: Database,
}

impl Storage {
    /// 创建或打开数据库
    pub fn open(path: &str) -> Result<Self> {
        let db = Database::create(path)
            .map_err(|e| EngineError::StorageError(format!("打开数据库失败: {}", e)))?;
        
        // 确保表存在（首次创建）
        let write_txn = db.begin_write()
            .map_err(|e| EngineError::StorageError(format!("开始写事务失败: {}", e)))?;
        {
            let _ = write_txn.open_table(CONFIG_TABLE)
                .map_err(|e| EngineError::StorageError(format!("创建表失败: {}", e)))?;
        }
        write_txn.commit()
            .map_err(|e| EngineError::StorageError(format!("提交事务失败: {}", e)))?;
        
        Ok(Self { db })
    }

    /// 保存配置
    pub fn save_config(&self, key: &str, value: &[u8]) -> Result<()> {
        let write_txn = self.db.begin_write()
            .map_err(|e| EngineError::StorageError(format!("开始写事务失败: {}", e)))?;
        {
            let mut table = write_txn.open_table(CONFIG_TABLE)
                .map_err(|e| EngineError::StorageError(format!("打开表失败: {}", e)))?;
            table.insert(key, value)
                .map_err(|e| EngineError::StorageError(format!("插入数据失败: {}", e)))?;
        }
        write_txn.commit()
            .map_err(|e| EngineError::StorageError(format!("提交事务失败: {}", e)))?;
        Ok(())
    }

    /// 加载配置
    pub fn load_config(&self, key: &str) -> Result<Option<Vec<u8>>> {
        let read_txn = self.db.begin_read()
            .map_err(|e| EngineError::StorageError(format!("开始读事务失败: {}", e)))?;
        let table = read_txn.open_table(CONFIG_TABLE)
            .map_err(|e| EngineError::StorageError(format!("打开表失败: {}", e)))?;
        
        match table.get(key) {
            Ok(Some(value)) => Ok(Some(value.value().to_vec())),
            Ok(None) => Ok(None),
            Err(e) => Err(EngineError::StorageError(format!("读取数据失败: {}", e))),
        }
    }
}
