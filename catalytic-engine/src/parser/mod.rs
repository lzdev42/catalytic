//! 数据解析器模块

pub mod number;
pub mod regex_parser;
pub mod jsonpath;

use crate::model::ParseRule;
use crate::error::Result;

/// 解析响应数据
pub fn parse_response(data: &[u8], rule: &ParseRule) -> Result<String> {
    let text = String::from_utf8_lossy(data);
    
    match rule {
        ParseRule::Number => number::extract_number(&text),
        ParseRule::Regex { pattern, group } => regex_parser::extract_regex(&text, pattern, *group),
        ParseRule::Json { path } => jsonpath::extract_json(&text, path),
    }
}
