package io.github.lzdev42.catalyticui.model

/**
 * 测试步骤定义 UI 状态
 * 用于主界面测试项侧边栏
 */
data class TestStepUiState(
    val index: Int,
    val name: String,
    val description: String
)

/**
 * 系统状态 UI 模型
 * 用于状态栏显示
 */
data class SystemStatusUiState(
    val systemInfo: String = "",
    val version: String = "",
    val flowName: String = "",
    val passCount: Int = 0,
    val failCount: Int = 0
)
