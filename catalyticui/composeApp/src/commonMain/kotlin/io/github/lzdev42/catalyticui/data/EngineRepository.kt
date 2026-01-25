package io.github.lzdev42.catalyticui.data

import io.github.lzdev42.catalyticui.model.DeviceTypeUiState
import io.github.lzdev42.catalyticui.model.PluginInfo
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.StepUiState
import kotlinx.coroutines.flow.Flow

/**
 * 引擎数据仓库接口
 * 
 * 定义 UI 与 Host/Engine 之间的数据契约。
 * 实现类可以是：
 * - MockEngineRepository: 用于 UI 开发和测试
 * - GrpcEngineRepository: 真实 gRPC 通信（后续实现）
 */
interface EngineRepository {
    
    /**
     * 槽位状态流（实时推送）
     */
    val slotsFlow: Flow<List<SlotState>>
    
    /**
     * 系统日志流
     */
    val systemLogsFlow: Flow<List<String>>
    
    /**
     * 连接状态
     */
    val isConnected: Flow<Boolean>
    
    /**
     * 启动指定槽位测试
     */
    suspend fun startTest(slotId: Int)
    
    /**
     * 暂停指定槽位测试
     */
    suspend fun pauseTest(slotId: Int)
    
    /**
     * 恢复指定槽位测试
     */
    suspend fun resumeTest(slotId: Int)
    
    /**
     * 停止指定槽位测试
     */
    suspend fun stopTest(slotId: Int)
    
    /**
     * 启动所有槽位
     */
    suspend fun startAll()
    
    /**
     * 停止所有槽位
     */
    suspend fun stopAll()
    
    /**
     * 获取 Engine 的槽位数量
     */
    suspend fun getSlotCount(): Int
    
    /**
     * 设置 Engine 的槽位数量
     */
    suspend fun setSlotCount(count: Int)

    // ==========================================
    // Configuration Management
    // ==========================================

    /**
     * 获取设备类型配置
     */
    suspend fun getDeviceTypes(): List<DeviceTypeUiState>

    /**
     * 保存设备类型配置 (批量)
     */
    suspend fun saveDeviceTypes(types: List<DeviceTypeUiState>)
    
    /**
     * 创建单个设备类型
     */
    suspend fun createDeviceType(type: DeviceTypeUiState)
    
    /**
     * 创建单个设备实例
     */
    suspend fun createDevice(typeId: String, device: io.github.lzdev42.catalyticui.model.DeviceUiState)

    // [DELETED] getRoles/saveRoles removed

    /**
     * 获取测试步骤配置
     */
    suspend fun getSteps(): List<StepUiState>

    /**
     * 保存测试步骤配置
     */
    suspend fun saveSteps(steps: List<StepUiState>)

    /**
     * 获取槽位绑定配置
     * 返回: Map<SlotId, Map<DeviceTypeId, List<DeviceId>>>
     */
    suspend fun getSlotBindings(): Map<Int, Map<String, List<String>>>

    /**
     * 保存槽位绑定配置
     * bindings: Map<SlotId, Map<DeviceTypeId, List<DeviceId>>>
     */
    suspend fun saveSlotBindings(bindings: Map<Int, Map<String, List<String>>>)
    
    // ==========================================
    // Plugin Management
    // ==========================================
    
    /**
     * 获取可用插件列表
     * 用于设备类型创建时选择通讯插件
     */
    suspend fun listPlugins(): List<PluginInfo>
    
    /**
     * 设置槽位 SN
     */
    suspend fun setSlotSn(slotId: Int, sn: String)
    
    // ==========================================
    // Device Connection Management
    // ==========================================
    
    /**
     * 设备连接状态流
     */
    val deviceConnectionsFlow: Flow<List<io.github.lzdev42.catalyticui.model.DeviceConnectionState>>
    
    /**
     * 连接设备
     */
    suspend fun connectDevice(deviceId: String): Result<Unit>
    
    /**
     * 断开设备连接
     */
    suspend fun disconnectDevice(deviceId: String): Result<Unit>
}
