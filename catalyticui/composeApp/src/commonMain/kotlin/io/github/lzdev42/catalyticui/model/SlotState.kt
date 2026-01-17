package io.github.lzdev42.catalyticui.model

/**
 * 槽位状态枚举
 */
enum class SlotStatus {
    IDLE,       // 空闲
    RUNNING,    // 运行中
    PAUSED,     // 已暂停
    PASS,       // 通过
    FAIL        // 失败
}

/**
 * 槽位变量
 */
data class SlotVariable(
    val name: String,      // "V", "I", "RF"
    val value: String,     // "3.31V", "125mA"
    val isPassing: Boolean // 是否通过检查
)

/**
 * 槽位状态数据
 */
data class SlotState(
    val id: Int,
    val sn: String?,                       // 可能未扫描
    val status: SlotStatus,
    val currentStep: Int,
    val totalSteps: Int,
    val currentStepName: String?,
    val currentStepValue: String?,         // 当前测量值
    val variables: List<SlotVariable>,     // 变量列表
    val logs: List<String>,                // 检测日志
    val elapsedTime: String?,              // 耗时
    val deviceInfo: String?                // 设备信息 (空闲时显示)
) {
    val progress: Float
        get() = if (totalSteps > 0) currentStep.toFloat() / totalSteps else 0f
    
    val progressPercent: Int
        get() = (progress * 100).toInt()
}
