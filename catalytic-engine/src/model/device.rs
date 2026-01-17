//! 设备模型定义

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// 设备命令
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct Command {
    pub id: String,
    pub name: String,
    pub payload: String,
    pub timeout_ms: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub parse_rule: Option<String>,
}

/// 设备实例
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct DeviceInstance {
    /// 实例 ID (UUID)
    pub id: String,
    /// 显示名称
    pub name: String,
    /// 地址（如 "COM3", "192.168.1.10"）
    pub address: String,
    /// 实例标签（废弃，为了兼容旧代码暂留，映射到 name）
    #[serde(skip, default)]
    pub label: String,
}

/// 设备类型（模板）
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct DeviceType {
    /// 类型标识符（如 "dut", "scope"）— 作为 key 使用
    #[serde(skip)]
    pub type_name: String,
    /// 显示名称
    pub name: String,
    /// 通讯插件 ID（如 "catalytic.serial"）
    #[serde(default)]
    pub plugin_id: String,
    /// 设备实例列表
    #[serde(default, alias = "devices")]
    pub instances: Vec<DeviceInstance>,
    /// 命令列表
    #[serde(default)]
    pub commands: Vec<Command>,
}

impl DeviceType {
    /// 根据 ID 查找实例
    pub fn find_instance(&self, id: &str) -> Option<&DeviceInstance> {
        self.instances.iter().find(|i| i.id == id)
    }

    /// 添加实例
    pub fn add_instance(&mut self, instance: DeviceInstance) {
        // 如果存在同名 ID 则覆盖
        if let Some(pos) = self.instances.iter().position(|i| i.id == instance.id) {
            self.instances[pos] = instance;
        } else {
            self.instances.push(instance);
        }
    }

    /// 移除实例
    pub fn remove_instance(&mut self, id: &str) -> Option<DeviceInstance> {
        if let Some(idx) = self.instances.iter().position(|i| i.id == id) {
            Some(self.instances.remove(idx))
        } else {
            None
        }
    }
}

/// 槽位绑定
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SlotBinding {
    pub slot_id: u32,
    /// 设备类型名 -> 实例 ID 列表
    pub devices: HashMap<String, Vec<String>>,
}

/// 设备绑定信息（用于 UI 显示）
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceBindingInfo {
    pub name: String,
    pub address: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_device_type_serialization() {
        let device_type = DeviceType {
            type_name: "dut".to_string(),
            name: "待测设备".to_string(),
            plugin_id: "catalytic.serial".to_string(),
            instances: vec![
                DeviceInstance {
                    id: "dev-01".to_string(),
                    name: "DUT_A".to_string(),
                    address: "COM3".to_string(),
                    label: String::new(),
                },
            ],
            commands: vec![
                Command {
                    id: "cmd-01".to_string(),
                    name: "Read ID".to_string(),
                    payload: "*IDN?".to_string(),
                    timeout_ms: 1000,
                    parse_rule: None,
                }
            ],
        };

        let json = serde_json::to_string_pretty(&device_type).unwrap();
        println!("{}", json);
        assert!(json.contains("DUT_A"));
        assert!(json.contains("Read ID"));
    }
}
