//! 位检查

use crate::checker::CheckOutput;
use crate::model::VariablePool;
use crate::error::{EngineError, Result};

/// 位检查
pub fn check(
    variable: &str,
    bit: u8,
    expected_value: u8,
    variables: &VariablePool,
) -> Result<CheckOutput> {
    let val = variables.get(variable)
        .ok_or_else(|| EngineError::CheckError(format!("变量 '{}' 不存在", variable)))?
        .as_i64()
        .ok_or_else(|| EngineError::CheckError("变量无法转换为整数".to_string()))?;

    let bit_value = ((val >> bit) & 1) as u8;
    let passed = bit_value == expected_value;

    let summary = if passed {
        format!("{}[bit {}] = {} → PASS", variable, bit, bit_value)
    } else {
        format!("{}[bit {}] = {} (期望 {}) → FAIL", variable, bit, bit_value, expected_value)
    };

    Ok(CheckOutput {
        passed,
        template: "bit_check".to_string(),
        params: serde_json::json!({
            "variable": variable,
            "bit": bit,
            "value": expected_value
        }),
        actual: serde_json::json!(bit_value),
        summary,
    })
}
