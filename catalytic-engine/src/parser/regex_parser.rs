//! 正则解析器

use crate::error::{EngineError, Result};
use regex::Regex;

/// 使用正则表达式提取文本
pub fn extract_regex(text: &str, pattern: &str, group: usize) -> Result<String> {
    let re = Regex::new(pattern)
        .map_err(|e| EngineError::ParseError(format!("正则编译失败: {}", e)))?;
    
    match re.captures(text) {
        Some(caps) => {
            let matched = if group == 0 {
                caps.get(0)
            } else {
                caps.get(group)
            };
            
            match matched {
                Some(m) => Ok(m.as_str().to_string()),
                None => Err(EngineError::ParseError(format!(
                    "捕获组 {} 不存在", group
                ))),
            }
        }
        None => Err(EngineError::ParseError(format!(
            "正则 '{}' 未匹配: {}", pattern, text
        ))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_regex() {
        let result = extract_regex("VOLT: 3.31 V", r"VOLT:\s*([0-9.]+)", 1).unwrap();
        assert_eq!(result, "3.31");
    }

    #[test]
    fn test_extract_regex_group_0() {
        let result = extract_regex("Temperature: 25.5C", r"Temperature: [0-9.]+C", 0).unwrap();
        assert_eq!(result, "Temperature: 25.5C");
    }

    #[test]
    fn test_extract_regex_no_match() {
        let result = extract_regex("no match", r"VOLT:\s*([0-9.]+)", 1);
        assert!(result.is_err());
    }
}
