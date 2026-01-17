package io.github.lzdev42.catalyticui.model

/**
 * 设备类型 UI 状态
 * 用于设置页面的设备管理
 */
data class DeviceTypeUiState(
    val id: String,
    val name: String,
    val icon: String,
    val pluginId: String = "",  // 通讯插件 ID
    val devices: List<DeviceUiState>,
    val commands: List<CommandUiState> = emptyList(),
    val isExpanded: Boolean = false
)

/**
 * 设备命令 UI 状态
 */
data class CommandUiState(
    val id: String,
    val name: String,       // 命令名称 (如 "读取电压")
    val payload: String,    // 命令载荷 (如 "MEAS:VOLT:DC?")
    val parseRule: String? = null, // 解析规则
    val timeoutMs: Int = 1000
)

/**
 * 设备实例 UI 状态
 */
data class DeviceUiState(
    val id: String,
    val name: String,
    val address: String,
    val isOnline: Boolean = true
)
