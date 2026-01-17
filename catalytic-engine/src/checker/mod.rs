//! 检查规则模块

pub mod range;
pub mod threshold;
pub mod compare;
pub mod contains;
pub mod bit;
pub mod expression;

use crate::model::{CheckRule, Variable, VariablePool};
use crate::error::Result;

/// 检查结果
pub struct CheckOutput {
    pub passed: bool,
    pub template: String,
    pub params: serde_json::Value,
    pub actual: serde_json::Value,
    pub summary: String,
}

/// 执行检查
pub fn execute_check(
    rule: &CheckRule,
    current_value: Option<&Variable>,
    variables: &VariablePool,
) -> Result<CheckOutput> {
    match rule {
        CheckRule::RangeCheck { variable, min, max, include_min, include_max } => {
            range::check(variable.as_deref(), current_value, variables, *min, *max, *include_min, *include_max)
        }
        CheckRule::Threshold { variable, operator, value } => {
            threshold::check(variable, variables, operator, *value)
        }
        CheckRule::Compare { var_a, operator, var_b } => {
            compare::check(var_a, var_b, operator, variables)
        }
        CheckRule::Contains { variable, substring } => {
            contains::check(variable, substring, variables)
        }
        CheckRule::BitCheck { variable, bit, value } => {
            bit::check(variable, *bit, *value, variables)
        }
        CheckRule::Expression { expr } => {
            expression::check(expr, variables)
        }
    }
}
