package io.github.lzdev42.catalyticui.model

/**
 * 插件信息 UI 状态
 * 用于设备类型创建时选择通讯插件
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String = "",
    val protocols: List<String> = emptyList(),
    val types: List<String> = emptyList()  // "protocol_driver", "task_handler"
) {
    /**
     * 是否为通讯器插件
     */
    val isCommunicator: Boolean
        get() = types.contains("communicator")
    
    /**
     * 是否为处理器插件
     */
    val isProcessor: Boolean
        get() = types.contains("processor")
}
