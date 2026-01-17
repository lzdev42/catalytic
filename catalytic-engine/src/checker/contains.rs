//! 字符串包含检查

use crate::checker::CheckOutput;
use crate::model::VariablePool;
use crate::error::{EngineError, Result};

/// 字符串包含检查
pub fn check(
    variable: &str,
    substring: &str,
    variables: &VariablePool,
) -> Result<CheckOutput> {
    let val = variables.get(variable)
        .ok_or_else(|| EngineError::CheckError(format!("变量 '{}' 不存在", variable)))?
        .as_string();

    let passed = val.contains(substring);

    let summary = if passed {
        format!("'{}' 包含 '{}' → PASS", val, substring)
    } else {
        format!("'{}' 不包含 '{}' → FAIL", val, substring)
    };

    Ok(CheckOutput {
        passed,
        template: "contains".to_string(),
        params: serde_json::json!({
            "variable": variable,
            "substring": substring
        }),
        actual: serde_json::json!(val),
        summary,
    })
}
