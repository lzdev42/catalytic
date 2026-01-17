//! 范围检查

use crate::checker::CheckOutput;
use crate::model::{Variable, VariablePool};
use crate::error::{EngineError, Result};

/// 范围检查
/// - include_min: true 表示 >=，false 表示 >
/// - include_max: true 表示 <=，false 表示 <
pub fn check(
    variable_name: Option<&str>,
    current_value: Option<&Variable>,
    variables: &VariablePool,
    min: f64,
    max: f64,
    include_min: bool,
    include_max: bool,
) -> Result<CheckOutput> {
    // 获取要检查的值
    let value = if let Some(name) = variable_name {
        variables.get(name)
    } else {
        current_value
    };

    let val = value
        .ok_or_else(|| EngineError::CheckError("变量不存在".to_string()))?
        .as_f64()
        .ok_or_else(|| EngineError::CheckError("变量无法转换为数值".to_string()))?;

    // 根据配置判断边界
    let min_ok = if include_min { val >= min } else { val > min };
    let max_ok = if include_max { val <= max } else { val < max };
    let passed = min_ok && max_ok;

    // 构造比较符号
    let min_op = if include_min { ">=" } else { ">" };
    let max_op = if include_max { "<=" } else { "<" };

    let summary = if passed {
        format!("{:.4} ({}{:.4} && {}{:.4}) → PASS", val, min_op, min, max_op, max)
    } else {
        format!("{:.4} ({}{:.4} && {}{:.4}) → FAIL", val, min_op, min, max_op, max)
    };

    Ok(CheckOutput {
        passed,
        template: "range_check".to_string(),
        params: serde_json::json!({
            "min": min, 
            "max": max,
            "include_min": include_min,
            "include_max": include_max
        }),
        actual: serde_json::json!(val),
        summary,
    })
}
