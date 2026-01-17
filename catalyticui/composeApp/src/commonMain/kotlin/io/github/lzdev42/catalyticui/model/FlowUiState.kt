package io.github.lzdev42.catalyticui.model

// =============================================================================
// 执行模式
// =============================================================================

/**
 * 步骤执行模式
 */
enum class ExecutionMode {
    ENGINE_CONTROLLED,  // 引擎控制：发送指令 → 解析 → 检查
    HOST_CONTROLLED,    // Host 控制：调用自定义任务
    CALCULATION         // 计算步骤：不发送命令，仅对已有变量进行运算和判定
}

// [DELETED] ActionType 枚举已删除
// Engine 根据 CommandUiState.parseRule 是否存在自动推断是 Send 还是 Query

/**
 * 变量类型
 */
enum class VariableType {
    NUMBER,    // 数值（用于加减乘除运算）
    STRING     // 字符串（用于包含判断）
}

// =============================================================================
// 测试步骤
// =============================================================================

/**
 * 完整测试步骤 UI 状态
 * 对应 Engine 的 TestStep 结构
 */
data class StepUiState(
    val stepId: Int,
    val stepName: String,
    val executionMode: ExecutionMode,
    
    // Engine 模式配置 (executionMode == ENGINE_CONTROLLED 时使用)
    val engineTask: EngineTaskUiState? = null,
    
    // Host 模式配置 (executionMode == HOST_CONTROLLED 时使用)
    val hostTask: HostTaskUiState? = null,
    
    // ⭐ 输入变量（引用前置步骤的变量名，用于计算步骤）
    val inputVariables: List<String> = emptyList(),
    
    // ⭐ 输出变量定义（本步骤产生的新变量）
    val variables: Map<String, VariableType> = emptyMap(),
    
    // 检查规则（可引用 variables 中定义的变量）
    val checkRule: CheckRuleUiState = CheckRuleUiState.None,
    
    // 跳转逻辑 (null = 下一步)
    val nextOnPass: Int? = null,
    val nextOnFail: Int? = null,
    val nextOnTimeout: Int? = null,
    val nextOnError: Int? = null,
    
    // UI 状态
    val isExpanded: Boolean = false
)

/**
 * Engine 模式任务配置
 * 通过 commandId 引用 DeviceTypeUiState 中定义的 Command
 */
data class EngineTaskUiState(
    val deviceTypeId: String = "",        // 设备类型 ID
    val commandId: String? = null,        // ⭐ 引用命令 ID（非命令内容！）
    // [REMOVED] deviceIndex - 默认使用 Slot 绑定的第一个设备
    
    // 循环配置（可选）
    val loopMaxIterations: Int? = null,   // null = 不循环，执行1次
    val loopDelayMs: Int? = null,         // 每次循环间隔 (ms)
    val breakCondition: String? = null,   // 跳出条件表达式 (如 "voltage > 3.3")
)

/**
 * Host 模式任务配置
 */
data class HostTaskUiState(
    val taskName: String,             // 如 "WaitDeviceReady"
    val paramsJson: String = "{}",    // JSON 格式参数
    val timeoutMs: Int = 5000
)

// =============================================================================
// 检查规则
// =============================================================================

/**
 * 检查规则 - 验证步骤结果
 */
sealed class CheckRuleUiState {
    /** 无检查 */
    object None : CheckRuleUiState()
    
    /** 范围检查: min <= value <= max */
    data class RangeCheck(
        val variableName: String,  // 引用 variables 中的变量名
        val min: Double,
        val max: Double
    ) : CheckRuleUiState()
    
    /** 阈值检查: value <op> threshold */
    data class Threshold(
        val variableName: String,  // 引用 variables 中的变量名
        val operator: String,      // "<", ">", "<=", ">=", "==", "!="
        val value: Double
    ) : CheckRuleUiState()
    
    /** 字符串包含检查 */
    data class Contains(
        val variableName: String,  // 引用 variables 中的变量名
        val substring: String
    ) : CheckRuleUiState()
    
    /** 自定义表达式（开发中） */
    data class Expression(
        val expr: String  // 如 "(voltage_a + voltage_b) > 100"
    ) : CheckRuleUiState()
}
