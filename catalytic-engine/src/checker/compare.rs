//! 双变量比较

use crate::checker::CheckOutput;
use crate::model::{CompareOp, VariablePool};
use crate::error::{EngineError, Result};

/// 双变量比较
pub fn check(
    var_a: &str,
    var_b: &str,
    operator: &CompareOp,
    variables: &VariablePool,
) -> Result<CheckOutput> {
    let val_a = variables.get(var_a)
        .ok_or_else(|| EngineError::CheckError(format!("变量 '{}' 不存在", var_a)))?
        .as_f64()
        .ok_or_else(|| EngineError::CheckError("变量无法转换为数值".to_string()))?;

    let val_b = variables.get(var_b)
        .ok_or_else(|| EngineError::CheckError(format!("变量 '{}' 不存在", var_b)))?
        .as_f64()
        .ok_or_else(|| EngineError::CheckError("变量无法转换为数值".to_string()))?;

    let passed = operator.compare(val_a, val_b);
    let op_str = operator.as_str();

    let summary = if passed {
        format!("{} ({:.2}) {} {} ({:.2}) → PASS", var_a, val_a, op_str, var_b, val_b)
    } else {
        format!("{} ({:.2}) {} {} ({:.2}) → FAIL", var_a, val_a, op_str, var_b, val_b)
    };

    Ok(CheckOutput {
        passed,
        template: "compare".to_string(),
        params: serde_json::json!({"var_a": var_a, "operator": op_str, "var_b": var_b}),
        actual: serde_json::json!({"a": val_a, "b": val_b}),
        summary,
    })
}
