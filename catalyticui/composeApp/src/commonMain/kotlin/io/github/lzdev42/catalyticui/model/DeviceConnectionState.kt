package io.github.lzdev42.catalyticui.model

/**
 * 设备连接状态枚举
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 设备连接状态
 * 用于连接管理面板
 */
data class DeviceConnectionState(
    val deviceId: String,
    val status: ConnectionStatus,
    val errorMessage: String = ""
)
