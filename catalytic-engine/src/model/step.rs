//! 测试步骤定义

use serde::{Deserialize, Serialize};

/// serde 默认值辅助函数
fn default_true() -> bool { true }

/// 执行模式
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum ExecutionMode {
    /// 引擎控制模式
    #[default]
    EngineControlled,
    /// Host 控制模式
    HostControlled,
}

/// 动作类型
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum ActionType {
    /// 仅发送
    #[default]
    Send,
    /// 查询响应
    Query,
    /// 等待事件
    Wait,
    /// 循环
    Loop,
}

impl ActionType {
    pub fn as_str(&self) -> &'static str {
        match self {
            ActionType::Send => "send",
            ActionType::Query => "query",
            ActionType::Wait => "wait",
            ActionType::Loop => "loop",
        }
    }
}

/// 检查类型
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum CheckType {
    /// 无检查
    None,
    /// 内置检查
    Builtin,
    /// 外部检查
    External,
}

impl Default for CheckType {
    fn default() -> Self {
        CheckType::None
    }
}

/// 数据解析规则
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ParseRule {
    /// 数字提取
    Number,
    /// 正则提取
    Regex {
        pattern: String,
        #[serde(default)]
        group: usize,
    },
    /// JSON 路径
    Json { path: String },
}

/// 比较运算符
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum CompareOp {
    #[serde(rename = ">")]
    Gt,
    #[serde(rename = "<")]
    Lt,
    #[serde(rename = ">=")]
    Gte,
    #[serde(rename = "<=")]
    Lte,
    #[serde(rename = "==")]
    Eq,
    #[serde(rename = "!=")]
    Ne,
}

impl CompareOp {
    /// 获取运算符字符串表示
    pub fn as_str(&self) -> &'static str {
        match self {
            CompareOp::Gt => ">",
            CompareOp::Lt => "<",
            CompareOp::Gte => ">=",
            CompareOp::Lte => "<=",
            CompareOp::Eq => "==",
            CompareOp::Ne => "!=",
        }
    }

    /// 执行比较
    pub fn compare(&self, left: f64, right: f64) -> bool {
        match self {
            CompareOp::Gt => left > right,
            CompareOp::Lt => left < right,
            CompareOp::Gte => left >= right,
            CompareOp::Lte => left <= right,
            CompareOp::Eq => (left - right).abs() < f64::EPSILON,
            CompareOp::Ne => (left - right).abs() >= f64::EPSILON,
        }
    }
}

/// 检查规则
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "template", rename_all = "snake_case")]
pub enum CheckRule {
    /// 范围检查
    RangeCheck {
        #[serde(skip_serializing_if = "Option::is_none")]
        variable: Option<String>,
        min: f64,
        max: f64,
        /// 是否包含最小值（默认 true，即 >=）
        #[serde(default = "default_true")]
        include_min: bool,
        /// 是否包含最大值（默认 true，即 <=）
        #[serde(default = "default_true")]
        include_max: bool,
    },
    /// 双变量比较
    Compare {
        var_a: String,
        operator: CompareOp,
        var_b: String,
    },
    /// 阈值检查
    Threshold {
        variable: String,
        operator: CompareOp,
        value: f64,
    },
    /// 字符串包含
    Contains {
        variable: String,
        substring: String,
    },
    /// 位检查
    BitCheck {
        variable: String,
        bit: u8,
        value: u8,
    },
    /// 表达式检查
    Expression { expr: String },
}

/// 自定义 payload 反序列化：支持字符串或字节数组
fn deserialize_payload<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    use serde::de::{self, Visitor};
    
    struct PayloadVisitor;
    
    impl<'de> Visitor<'de> for PayloadVisitor {
        type Value = Vec<u8>;
        
        fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
            formatter.write_str("a string or byte array")
        }
        
        // 支持字符串输入 (如 "*IDN?")
        fn visit_str<E>(self, v: &str) -> Result<Self::Value, E>
        where
            E: de::Error,
        {
            Ok(v.as_bytes().to_vec())
        }
        
        // 支持字节数组输入 (如 [42, 73, 68, 78, 63])
        fn visit_seq<A>(self, mut seq: A) -> Result<Self::Value, A::Error>
        where
            A: de::SeqAccess<'de>,
        {
            let mut bytes = Vec::new();
            while let Some(byte) = seq.next_element::<u8>()? {
                bytes.push(byte);
            }
            Ok(bytes)
        }
    }
    
    deserializer.deserialize_any(PayloadVisitor)
}

/// 引擎控制任务
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct EngineTask {
    /// 目标设备类型名
    pub target_device: String,
    /// 动作类型
    pub action_type: ActionType,
    /// 发送载荷 - 支持字符串 "*IDN?" 或字节数组 [42, 73, ...]
    #[serde(default, deserialize_with = "deserialize_payload")]
    pub payload: Vec<u8>,
    /// 超时时间（毫秒）
    pub timeout_ms: u32,
    /// 数据解析规则
    #[serde(skip_serializing_if = "Option::is_none")]
    pub parse_rule: Option<ParseRule>,
    /// 最大循环次数
    #[serde(skip_serializing_if = "Option::is_none")]
    pub loop_max_iterations: Option<u32>,
    /// 循环间隔（毫秒）
    #[serde(skip_serializing_if = "Option::is_none")]
    pub loop_delay_ms: Option<u32>,
}

/// Host 控制任务
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HostTask {
    /// 任务名称
    pub task_name: String,
    /// 超时时间（毫秒）
    pub timeout_ms: u32,
    /// 参数（JSON 格式）
    #[serde(default)]
    pub params: serde_json::Value,
}

/// 测试步骤
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct TestStep {
    /// 步骤 ID
    pub step_id: u32,
    /// 步骤名称
    pub step_name: String,
    /// 执行模式
    pub execution_mode: ExecutionMode,
    /// 引擎控制任务数据
    #[serde(skip_serializing_if = "Option::is_none")]
    pub engine_task: Option<EngineTask>,
    /// Host 控制任务数据
    #[serde(skip_serializing_if = "Option::is_none")]
    pub host_task: Option<HostTask>,
    /// 存储结果的变量名
    #[serde(skip_serializing_if = "Option::is_none")]
    pub save_to: Option<String>,
    /// 检查类型
    #[serde(default)]
    pub check_type: CheckType,
    /// 检查规则
    #[serde(skip_serializing_if = "Option::is_none")]
    pub check_rule: Option<CheckRule>,
    /// 成功后跳转
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_on_pass: Option<u32>,
    /// 失败后跳转
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_on_fail: Option<u32>,
    /// 超时后跳转
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_on_timeout: Option<u32>,
    /// 异常后跳转
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_on_error: Option<u32>,
    /// 预设跳过（true 表示始终跳过此步骤）
    #[serde(default)]
    pub skip: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_engine_controlled_step_deserialization() {
        let json = r#"{
            "step_id": 1,
            "step_name": "电压检测",
            "execution_mode": "engine_controlled",
            "engine_task": {
                "target_device": "dut",
                "action_type": "query",
                "payload": [34, 241, 144],
                "timeout_ms": 1000,
                "parse_rule": {
                    "type": "regex",
                    "pattern": "VOLT:\\s*([0-9.]+)",
                    "group": 1
                }
            },
            "save_to": "voltage_read",
            "check_type": "builtin",
            "check_rule": {
                "template": "range_check",
                "min": 3.0,
                "max": 3.5
            },
            "next_on_pass": 2,
            "next_on_fail": 999
        }"#;

        let step: TestStep = serde_json::from_str(json).unwrap();
        assert_eq!(step.step_id, 1);
        assert_eq!(step.step_name, "电压检测");
        assert_eq!(step.execution_mode, ExecutionMode::EngineControlled);
        assert!(step.engine_task.is_some());
    }

    #[test]
    fn test_host_controlled_step_deserialization() {
        let json = r#"{
            "step_id": 2,
            "step_name": "自定义初始化",
            "execution_mode": "host_controlled",
            "host_task": {
                "task_name": "WaitDeviceReady",
                "timeout_ms": 5000,
                "params": {
                    "retry_interval": 500,
                    "check_command": "IDN?"
                }
            },
            "next_on_pass": 3,
            "next_on_error": 999
        }"#;

        let step: TestStep = serde_json::from_str(json).unwrap();
        assert_eq!(step.step_id, 2);
        assert_eq!(step.execution_mode, ExecutionMode::HostControlled);
        assert!(step.host_task.is_some());
    }

    #[test]
    fn test_compare_op() {
        assert!(CompareOp::Gt.compare(5.0, 3.0));
        assert!(CompareOp::Lt.compare(3.0, 5.0));
        assert!(CompareOp::Eq.compare(3.0, 3.0));
    }
}
