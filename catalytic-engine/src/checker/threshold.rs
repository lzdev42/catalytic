//! 阈值检查

use crate::checker::CheckOutput;
use crate::model::{CompareOp, VariablePool};
use crate::error::{EngineError, Result};

/// 阈值检查
pub fn check(
    variable: &str,
    variables: &VariablePool,
    operator: &CompareOp,
    threshold: f64,
) -> Result<CheckOutput> {
    let val = variables.get(variable)
        .ok_or_else(|| EngineError::CheckError(format!("变量 '{}' 不存在", variable)))?
        .as_f64()
        .ok_or_else(|| EngineError::CheckError("变量无法转换为数值".to_string()))?;

    let passed = operator.compare(val, threshold);
    let op_str = operator.as_str();

    let summary = if passed {
        format!("{:.2} {} {:.2} → PASS", val, op_str, threshold)
    } else {
        format!("{:.2} {} {:.2} → FAIL", val, op_str, threshold)
    };

    Ok(CheckOutput {
        passed,
        template: "threshold".to_string(),
        params: serde_json::json!({"variable": variable, "operator": op_str, "value": threshold}),
        actual: serde_json::json!(val),
        summary,
    })
}
