package io.github.lzdev42.catalyticui.data.mock

import io.github.lzdev42.catalyticui.data.EngineRepository
import io.github.lzdev42.catalyticui.data.grpc.DeviceTypeMappers
import io.github.lzdev42.catalyticui.data.grpc.SlotBindingMappers
import io.github.lzdev42.catalyticui.data.grpc.StepMappers
import io.github.lzdev42.catalyticui.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Mock Repository for pure UI Verification
 * Acts as a "Standard Gauge" to verify Data Contracts
 */
class MockEngineRepository : EngineRepository {


    // Internal State (Simulating Backend Storage)
    private var slotCount = 4
    private var deviceTypes = mutableListOf<DeviceTypeUiState>()
    // [DELETED] roles list removed
    private var steps = mutableListOf<StepUiState>()
    private var slotBindings = mutableMapOf<Int, Map<String, List<String>>>()
    
    // Flows
    private val _slotsFlow = MutableStateFlow<List<SlotState>>(emptyList())
    override val slotsFlow: Flow<List<SlotState>> = _slotsFlow.asStateFlow()

    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    override val systemLogsFlow: Flow<List<String>> = _logsFlow.asStateFlow()

    override val isConnected: Flow<Boolean> = flow { 
        emit(true) // Always connected in Mock mode
    }

    init {
        println("[MockRepo] Initialized. Ready for Verification.")
        updateSlots()
    }

    private fun updateSlots() {
        val list = List(slotCount) { index ->
            SlotState(
                id = index,
                sn = "MOCK-SN-${index}",
                status = SlotStatus.IDLE,
                currentStep = 0,
                totalSteps = steps.size,
                currentStepName = null,
                currentStepValue = null,
                variables = emptyList(),
                logs = emptyList(),
                elapsedTime = null,
                deviceInfo = "Bound: ${slotBindings[index]?.size ?: 0}"
            )
        }
        _slotsFlow.value = list
    }

    private fun log(action: String, data: Any? = null) {
        val msg = if (data != null) {
            try {
                // Use reflection or toString for simplicity if Serializable not available for generic Any
                // But our models are likely Serializable or data classes
                "[MockRepo] ACTION: $action\nDATA:\n${data}" 
            } catch (e: Exception) {
                "[MockRepo] ACTION: $action (Serialize Failed: ${e.message})"
            }
        } else {
            "[MockRepo] ACTION: $action"
        }
        println("\n==================================================")
        println(msg)
        println("==================================================\n")
        
        val newLogs = _logsFlow.value.toMutableList()
        newLogs.add(0, "$action")
        if (newLogs.size > 100) newLogs.removeLast()
        _logsFlow.value = newLogs
    }

    override suspend fun startTest(slotId: Int) { log("startTest($slotId)") }
    override suspend fun pauseTest(slotId: Int) { log("pauseTest($slotId)") }
    override suspend fun resumeTest(slotId: Int) { log("resumeTest($slotId)") }
    override suspend fun stopTest(slotId: Int) { log("stopTest($slotId)") }
    override suspend fun startAll() { log("startAll") }
    override suspend fun stopAll() { log("stopAll") }

    override suspend fun getSlotCount(): Int {
        log("getSlotCount returning $slotCount")
        return slotCount
    }

    override suspend fun setSlotCount(count: Int) {
        log("setSlotCount($count)")
        slotCount = count
        updateSlots()
    }

    override suspend fun getDeviceTypes(): List<DeviceTypeUiState> {
        return deviceTypes
    }

    override suspend fun saveDeviceTypes(types: List<DeviceTypeUiState>) {
        val json = DeviceTypeMappers.toJsonMap(types)
        log("saveDeviceTypes", json)
        deviceTypes.clear()
        deviceTypes.addAll(types)
    }

    override suspend fun createDeviceType(type: DeviceTypeUiState) {
        log("createDeviceType - DEPRECATED in Transaction Mode", type)
        deviceTypes.add(type)
    }

    override suspend fun createDevice(typeId: String, device: DeviceUiState) {
        log("createDevice - DEPRECATED in Transaction Mode", device)
    }

    // [DELETED] getRoles/saveRoles removed

    override suspend fun getSteps(): List<StepUiState> = steps

    override suspend fun saveSteps(steps: List<StepUiState>) {
        val json = steps.map { StepMappers.toEngineJson(it) }
        log("saveSteps", json.joinToString(",\n", "[\n", "\n]"))
        this.steps.clear()
        this.steps.addAll(steps)
        updateSlots()
    }

    override suspend fun getSlotBindings(): Map<Int, Map<String, List<String>>> = slotBindings

    override suspend fun saveSlotBindings(bindings: Map<Int, Map<String, List<String>>>) {
        val json = SlotBindingMappers.toJson(bindings)
        log("saveSlotBindings", json)
        slotBindings.clear()
        slotBindings.putAll(bindings)
        updateSlots()
    }

    // Mock Connection Management (to satisfy main.kt usage)
    suspend fun connect(host: String, port: Int): Result<Unit> {
        log("connect($host, $port)")
        return Result.success(Unit)
    }

    suspend fun shutdown() {
        log("shutdown")
    }
    
    // Plugin Management
    override suspend fun listPlugins(): List<PluginInfo> {
        log("listPlugins()")
        // Mock 插件数据用于 UI 开发验证
        return listOf(
            PluginInfo(
                id = "catalytic.serial",
                name = "Serial Port Driver",
                version = "1.0.0",
                protocols = listOf("serial"),
                types = listOf("communicator")
            ),
            PluginInfo(
                id = "catalytic.tcp", 
                name = "TCP/IP Driver",
                version = "1.0.0",
                protocols = listOf("tcp"),
                types = listOf("communicator")
            ),
            PluginInfo(
                id = "catalytic.scpi",
                name = "SCPI Protocol Driver",
                version = "1.0.0", 
                protocols = listOf("scpi"),
                types = listOf("communicator")
            )
        )
    }
    
    override suspend fun setSlotSn(slotId: Int, sn: String) {
        log("setSlotSn(slotId=$slotId, sn=$sn)")
    }
}
