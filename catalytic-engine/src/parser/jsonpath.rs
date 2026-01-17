//! JSON 路径解析器

use crate::error::{EngineError, Result};
use jsonpath_rust::JsonPathQuery;

/// 使用 JSON 路径提取数据
pub fn extract_json(text: &str, path: &str) -> Result<String> {
    let json: serde_json::Value = serde_json::from_str(text)
        .map_err(|e| EngineError::ParseError(format!("JSON 解析失败: {}", e)))?;
    
    let result = json.path(path)
        .map_err(|e| EngineError::ParseError(format!("JSON 路径查询失败: {}", e)))?;
    
    // 取第一个结果
    match result.as_array() {
        Some(arr) if !arr.is_empty() => {
            match &arr[0] {
                serde_json::Value::String(s) => Ok(s.clone()),
                serde_json::Value::Number(n) => Ok(n.to_string()),
                serde_json::Value::Bool(b) => Ok(b.to_string()),
                other => Ok(other.to_string()),
            }
        }
        _ => Err(EngineError::ParseError(format!(
            "JSON 路径 '{}' 未找到结果", path
        ))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_json() {
        let json = r#"{"measurement": {"voltage": 3.31, "unit": "V"}}"#;
        let result = extract_json(json, "$.measurement.voltage").unwrap();
        assert_eq!(result, "3.31");
    }

    #[test]
    fn test_extract_json_string() {
        let json = r#"{"device": {"name": "Device_A"}}"#;
        let result = extract_json(json, "$.device.name").unwrap();
        assert_eq!(result, "Device_A");
    }
}
