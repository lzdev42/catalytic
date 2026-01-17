//! 变量系统定义

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// 变量类型
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "value", rename_all = "snake_case")]
pub enum Variable {
    /// 整数
    Int(i64),
    /// 浮点数
    Float(f64),
    /// 字节数组
    Bytes(Vec<u8>),
    /// 浮点数组（波形等）
    FloatArray(Vec<f64>),
}

impl Variable {
    /// 尝试获取浮点数值（用于检查规则）
    pub fn as_f64(&self) -> Option<f64> {
        match self {
            Variable::Int(v) => Some(*v as f64),
            Variable::Float(v) => Some(*v),
            _ => None,
        }
    }

    /// 尝试获取整数值
    pub fn as_i64(&self) -> Option<i64> {
        match self {
            Variable::Int(v) => Some(*v),
            Variable::Float(v) => Some(*v as i64),
            _ => None,
        }
    }

    /// 尝试获取字符串表示
    pub fn as_string(&self) -> String {
        match self {
            Variable::Int(v) => v.to_string(),
            Variable::Float(v) => v.to_string(),
            Variable::Bytes(v) => format!("{:?}", v),
            Variable::FloatArray(v) => format!("{:?}", v),
        }
    }

    /// 从字符串解析变量（自动推断类型）
    pub fn from_string(s: &str) -> Variable {
        if let Ok(f) = s.parse::<f64>() {
            Variable::Float(f)
        } else if let Ok(i) = s.parse::<i64>() {
            Variable::Int(i)
        } else {
            Variable::Bytes(s.as_bytes().to_vec())
        }
    }
}

/// 变量显示信息（用于 UI）
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VariableDisplay {
    pub value: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub unit: Option<String>,
    #[serde(rename = "type")]
    pub var_type: String,
}

impl From<&Variable> for VariableDisplay {
    fn from(var: &Variable) -> Self {
        let (value, var_type) = match var {
            Variable::Int(v) => (v.to_string(), "int"),
            Variable::Float(v) => (format!("{:.6}", v), "float"),
            Variable::Bytes(v) => (format!("{} bytes", v.len()), "bytes"),
            Variable::FloatArray(v) => (format!("{} points", v.len()), "float_array"),
        };
        VariableDisplay {
            value,
            unit: None,
            var_type: var_type.to_string(),
        }
    }
}

/// 变量池
#[derive(Debug, Default)]
pub struct VariablePool {
    variables: HashMap<String, Variable>,
}

impl VariablePool {
    /// 创建新的变量池
    pub fn new() -> Self {
        Self::default()
    }

    /// 设置变量
    pub fn set(&mut self, name: &str, value: Variable) {
        self.variables.insert(name.to_string(), value);
    }

    /// 获取变量
    pub fn get(&self, name: &str) -> Option<&Variable> {
        self.variables.get(name)
    }

    /// 移除变量
    pub fn remove(&mut self, name: &str) -> Option<Variable> {
        self.variables.remove(name)
    }

    /// 清空所有变量
    pub fn clear(&mut self) {
        self.variables.clear();
    }

    /// 获取所有变量名
    pub fn keys(&self) -> impl Iterator<Item = &String> {
        self.variables.keys()
    }

    /// 转换为 JSON 值
    pub fn to_json(&self) -> serde_json::Value {
        let map: HashMap<String, VariableDisplay> = self
            .variables
            .iter()
            .map(|(k, v)| (k.clone(), VariableDisplay::from(v)))
            .collect();
        serde_json::to_value(map).unwrap_or(serde_json::Value::Object(Default::default()))
    }

    /// 转换为 HashMap<String, VariableDisplay>
    pub fn to_display_map(&self) -> HashMap<String, VariableDisplay> {
        self.variables
            .iter()
            .map(|(k, v)| (k.clone(), VariableDisplay::from(v)))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_variable_pool() {
        let mut pool = VariablePool::new();
        pool.set("voltage", Variable::Float(3.31));
        pool.set("count", Variable::Int(42));

        assert_eq!(pool.get("voltage").unwrap().as_f64(), Some(3.31));
        assert_eq!(pool.get("count").unwrap().as_i64(), Some(42));
    }

    #[test]
    fn test_variable_serialization() {
        let var = Variable::Float(3.31);
        let json = serde_json::to_string(&var).unwrap();
        assert!(json.contains("float"));
        assert!(json.contains("3.31"));
    }
}
