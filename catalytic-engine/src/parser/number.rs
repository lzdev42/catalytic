//! 数字提取器

use crate::error::{EngineError, Result};
use regex::Regex;

/// 从文本中提取第一个数字（支持科学计数法）
pub fn extract_number(text: &str) -> Result<String> {
    // 匹配整数、浮点数、科学计数法（包括负数）
    // 例如: 3.3, -12.5, 2.4E+09, 1.25E-03, 2400000000
    let re = Regex::new(r"-?\d+\.?\d*(?:[eE][-+]?\d+)?")
        .map_err(|e| EngineError::ParseError(format!("正则编译失败: {}", e)))?;
    
    match re.find(text.trim()) {
        Some(m) => Ok(m.as_str().to_string()),
        None => Err(EngineError::ParseError(format!("未找到数字: {}", text))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_number() {
        // 基本数字
        assert_eq!(extract_number("3.3V").unwrap(), "3.3");
        assert_eq!(extract_number("VOLT: 3.31").unwrap(), "3.31");
        assert_eq!(extract_number("-12.5 degrees").unwrap(), "-12.5");
        assert_eq!(extract_number("Count: 42").unwrap(), "42");
        
        // 科学计数法
        assert_eq!(extract_number("2.400000E+09").unwrap(), "2.400000E+09");
        assert_eq!(extract_number("1.25E-03").unwrap(), "1.25E-03");
        assert_eq!(extract_number("-10.5\n").unwrap(), "-10.5"); // 带换行
    }

    #[test]
    fn test_extract_number_no_match() {
        assert!(extract_number("no numbers here").is_err());
    }
}
