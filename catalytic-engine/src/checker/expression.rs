//! 表达式检查

use crate::checker::CheckOutput;
use crate::model::VariablePool;
use crate::error::{EngineError, Result};
use evalexpr::*;

/// 表达式检查
pub fn check(
    expr: &str,
    variables: &VariablePool,
) -> Result<CheckOutput> {
    // 创建上下文并填充变量
    let mut context = HashMapContext::new();
    
    for name in variables.keys() {
        if let Some(var) = variables.get(name) {
            if let Some(val) = var.as_f64() {
                context.set_value(name.clone(), Value::Float(val))
                    .map_err(|e| EngineError::ExpressionError(format!("设置变量失败: {}", e)))?;
            }
        }
    }

    // 求值
    let result = eval_boolean_with_context(expr, &context)
        .map_err(|e| EngineError::ExpressionError(format!("表达式求值失败: {}", e)))?;

    let summary = if result {
        format!("{} → PASS", expr)
    } else {
        format!("{} → FAIL", expr)
    };

    Ok(CheckOutput {
        passed: result,
        template: "expression".to_string(),
        params: serde_json::json!({"expr": expr}),
        actual: serde_json::json!(result),
        summary,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::Variable;

    #[test]
    fn test_expression_check() {
        let mut pool = VariablePool::new();
        pool.set("voltage", Variable::Float(3.31));
        pool.set("threshold", Variable::Float(3.0));

        let result = check("voltage > threshold", &pool).unwrap();
        assert!(result.passed);
    }

    #[test]
    fn test_complex_expression() {
        let mut pool = VariablePool::new();
        pool.set("a", Variable::Float(10.0));
        pool.set("b", Variable::Float(20.0));

        let result = check("(a + b) > 25", &pool).unwrap();
        assert!(result.passed);
    }
}
