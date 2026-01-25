package io.github.lzdev42.catalyticui.data.grpc

import com.catalytic.grpc.*
import io.github.lzdev42.catalyticui.data.EngineRepository
import io.github.lzdev42.catalyticui.model.DeviceTypeUiState
import io.github.lzdev42.catalyticui.model.DeviceUiState
import io.github.lzdev42.catalyticui.model.PluginInfo
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.StepUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * gRPC 实现的 EngineRepository
 * 
 * 职责：
 * - 实现 EngineRepository 接口
 * - 订阅 Host 的事件流
 * - 将 gRPC 数据转换为 UI 模型
 */
class GrpcRepository(
    private val clientManager: GrpcClientManager,
    private val scope: CoroutineScope
) : EngineRepository {
    
    // ========== 内部状态 ==========
    
    private val _slotsMap = MutableStateFlow<Map<Int, SlotState>>(emptyMap())

    private val _logsBuffer = MutableStateFlow<List<String>>(emptyList())
    private val _deviceConnections = MutableStateFlow<List<io.github.lzdev42.catalyticui.model.DeviceConnectionState>>(emptyList())
    
    private var subscriptionJob: Job? = null
    private var statusPollingJob: Job? = null
    
    // ========== EngineRepository 实现 ==========
    
    override val slotsFlow: Flow<List<SlotState>>
        get() = _slotsMap.map { it.values.sortedBy { slot -> slot.id } }
    
    override val systemLogsFlow: Flow<List<String>>
        get() = io.github.lzdev42.catalyticui.log.LogManager.systemLogs
    
    override val isConnected: Flow<Boolean>
        get() = clientManager.connectionState.map { it is ConnectionState.Connected }
    
    override suspend fun startTest(slotId: Int) {
        executeRpc("StartTest") {
            it.StartTest().execute(StartTestRequest(slot_id = slotId))
        }
    }
    
    override suspend fun pauseTest(slotId: Int) {
        executeRpc("PauseTest") {
            it.PauseTest().execute(SlotId(id = slotId))
        }
    }
    
    override suspend fun resumeTest(slotId: Int) {
        executeRpc("ResumeTest") {
            it.ResumeTest().execute(SlotId(id = slotId))
        }
    }
    
    override suspend fun stopTest(slotId: Int) {
        executeRpc("StopTest") {
            it.StopTest().execute(SlotId(id = slotId))
        }
    }
    
    override suspend fun startAll() {
        val idleSlots = _slotsMap.value.values
            .filter { it.status == SlotStatus.IDLE && it.sn != null }
        
        idleSlots.forEach { slot ->
            startTest(slot.id)
        }
    }
    
    override suspend fun stopAll() {
        val runningSlots = _slotsMap.value.values
            .filter { it.status != SlotStatus.IDLE }
        
        runningSlots.forEach { slot ->
            stopTest(slot.id)
        }
    }
    
    override suspend fun getSlotCount(): Int {
        return executeRpc("getSlotCount") { client ->
            val systemInfo = client.GetSystemInfo().execute(Empty())
            systemInfo.slot_count
        }.getOrElse {
            addLog("getSlotCount 失败: ${it.message}")
            4 // Default fallback
        }
    }
    
    override suspend fun setSlotCount(count: Int) {
        executeRpc("setSlotCount") { client ->
            val result = client.SetSlotCount().execute(SlotCountRequest(count = count))
            if (!result.success) {
                addLog("setSlotCount 失败: ${result.error}")
            } else {
                addLog("setSlotCount 成功: $count")
            }
        }.onFailure { addLog("setSlotCount 失败: ${it.message}") }
    }
    
    // ========== Configuration Management ==========
    // Note: These are placeholder implementations.
    // The actual gRPC API for configuration management needs to be defined in the proto files.
    
    override suspend fun getDeviceTypes(): List<DeviceTypeUiState> {
        return executeRpc("getDeviceTypes") { client ->
            val typesResult = client.ListDeviceTypes().execute(Empty())
            addLog("[ListDeviceTypes] 收到 ${typesResult.items.size} 个设备类型")
            
            // Nested structure allows direct mapping without separate ListDevices call
            typesResult.items.map { grpcType ->
                // Devices are now nested inside grpcType
                Mappers.mapDeviceType(grpcType, grpcType.devices)
            }
        }.getOrElse { 
            addLog("getDeviceTypes 失败: ${it.message}")
            emptyList() 
        }
    }
    
    override suspend fun saveDeviceTypes(types: List<DeviceTypeUiState>) {
        executeRpc("saveDeviceTypes") { client ->
                // [Refactored] Use UpdateDeviceType with Nested Structure (Batch Save)
            for (type in types) {
                val typeJson = DeviceTypeMappers.toJson(type)
                addLog("[UpdateDeviceType] 发送数据: $typeJson")
                
                // Mappers fully support nested structure now
                val grpcType = Mappers.toGrpcDeviceType(type)
                
                val typeResult = client.UpdateDeviceType().execute(grpcType)
                addLog("[UpdateDeviceType] 结果: success=${typeResult.success}, error=${typeResult.error}")
            }
            addLog("saveDeviceTypes: 已保存 ${types.size} 个设备类型")
        }.onFailure { addLog("saveDeviceTypes 失败: ${it.message}") }
    }
    
    override suspend fun createDeviceType(type: DeviceTypeUiState) {
        executeRpc("createDeviceType") { client ->
            val typeJson = DeviceTypeMappers.toJson(type)
            addLog("[CreateDeviceType] 发送数据: $typeJson")
            val grpcType = Mappers.toGrpcDeviceType(type)
            val result = client.CreateDeviceType().execute(grpcType)
            addLog("[CreateDeviceType] 结果: success=${result.success}, error=${result.error}")
        }.onFailure { addLog("createDeviceType 失败: ${it.message}") }
    }
    
    override suspend fun createDevice(typeId: String, device: DeviceUiState) {
        executeRpc("createDevice") { client ->
            val deviceJson = """{"id": "${device.id}", "device_type_id": "$typeId", "name": "${device.name}", "address": "${device.address}"}"""
            addLog("[CreateDevice] 发送数据: $deviceJson")
            val grpcDevice = Mappers.toGrpcDevice(device, typeId)
            val result = client.CreateDevice().execute(grpcDevice)
            addLog("[CreateDevice] 结果: success=${result.success}, error=${result.error}")
        }.onFailure { addLog("createDevice 失败: ${it.message}") }
    }
    
    // [DELETED] getRoles/saveRoles methods removed
    
    override suspend fun getSteps(): List<StepUiState> {
        return executeRpc("getSteps") { client ->
            val script = client.GetCurrentScript().execute(Empty())
            addLog("[GetCurrentScript] 收到数据: ${script.json_content}")
            if (script.json_content.isBlank()) {
                addLog("[GetCurrentScript] 脚本为空")
                emptyList()
            } else {
                try {
                    val steps = StepMappers.fromEngineJsonArray(script.json_content)
                    addLog("[GetCurrentScript] 解析成功: ${steps.size} 个步骤")
                    steps
                } catch (e: Exception) {
                    addLog("getSteps 解析失败: ${e.message}")
                    emptyList()
                }
            }
        }.getOrElse { 
            addLog("getSteps 失败: ${it.message}")
            emptyList() 
        }
    }
    
    override suspend fun saveSteps(steps: List<StepUiState>) {
        executeRpc("saveSteps") { client ->
            // 逐个添加测试步骤
            for (step in steps) {
                val json = StepMappers.toEngineJson(step)
                addLog("[AddTestStep] 发送数据: $json")
                val result = client.AddTestStep().execute(TestStepPayload(json_content = json))
                if (!result.success) {
                    addLog("saveSteps: 步骤 ${step.stepId} 失败: ${result.error}")
                }
            }
            addLog("saveSteps: 已保存 ${steps.size} 个步骤")
        }.onFailure { addLog("saveSteps 失败: ${it.message}") }
    }
    
    override suspend fun getSlotBindings(): Map<Int, Map<String, List<String>>> {
        return executeRpc("getSlotBindings") { client ->
            val slotList = client.ListSlots().execute(Empty())
            addLog("[ListSlots] 收到 ${slotList.items.size} 个槽位绑定")
            val result = slotList.items.associate { binding ->
                binding.slot_id to binding.device_bindings.mapValues { (_, list) ->
                    list.device_ids
                }
            }
            addLog("[ListSlots] 绑定详情: $result")
            result
        }.getOrElse {
            addLog("getSlotBindings 失败: ${it.message}")
            emptyMap()
        }
    }
    
    override suspend fun saveSlotBindings(bindings: Map<Int, Map<String, List<String>>>) {
        executeRpc("saveSlotBindings") { client ->
            for ((slotId, typeBindings) in bindings) {
                val bindingJson = SlotBindingMappers.toJson(mapOf(slotId to typeBindings))
                addLog("[SetSlotBinding] 发送数据: $bindingJson")
                val protoBindings = typeBindings.mapValues { (_, deviceIds) ->
                    DeviceBindingList(device_ids = deviceIds)
                }
                val request = SlotBinding(
                    slot_id = slotId,
                    device_bindings = protoBindings
                )
                val result = client.SetSlotBinding().execute(request)
                addLog("[SetSlotBinding] 结果: success=${result.success}, error=${result.error}")
            }
            addLog("saveSlotBindings: 已保存 ${bindings.size} 个槽位绑定")
        }.onFailure { addLog("saveSlotBindings 失败: ${it.message}") }
    }
    
    // ========== 连接管理 ==========
    
    override val deviceConnectionsFlow: Flow<List<io.github.lzdev42.catalyticui.model.DeviceConnectionState>>
        get() = _deviceConnections.asStateFlow()

    override suspend fun connectDevice(deviceId: String): kotlin.Result<Unit> {
        return executeRpc("ConnectDevice") {
            val result = it.ConnectDevice().execute(DeviceId(id = deviceId))
            if (!result.success) throw Exception(result.error)
            // Immediately refresh status to feel responsive
            refreshDeviceStatus(it)
        }
    }

    override suspend fun disconnectDevice(deviceId: String): kotlin.Result<Unit> {
        return executeRpc("DisconnectDevice") {
            val result = it.DisconnectDevice().execute(DeviceId(id = deviceId))
            if (!result.success) throw Exception(result.error)
            refreshDeviceStatus(it)
        }
    }

    private suspend fun refreshDeviceStatus(client: HostServiceClient) {
         try {
             val result = client.ListDeviceConnectionStatus().execute(Empty())
             val states = result.items.map { Mappers.mapConnectionStatus(it) }
             _deviceConnections.value = states
         } catch (e: Exception) {
             addLog("刷新设备状态失败: ${e.message}")
         }
    }

    // ========== 连接管理 ==========
    
    /**
     * 连接到 Host 并开始订阅事件
     */
    suspend fun connect(host: String, port: Int): kotlin.Result<Unit> {
        val result = clientManager.connect(host, port)
        
        return result.fold(
            onSuccess = { client ->
                startEventSubscription(client)
                startStatusPolling(client)
                loadInitialData(client)
                kotlin.Result.success(Unit)
            },
            onFailure = { kotlin.Result.failure(it) }
        )
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        subscriptionJob?.cancel()
        statusPollingJob?.cancel()
        clientManager.disconnect()
        _slotsMap.value = emptyMap()
        _logsBuffer.value = emptyList()
        _deviceConnections.value = emptyList()
    }
    
    /**
     * 发送 Shutdown 命令关闭 Host
     */
    suspend fun shutdown() {
        executeRpc("Shutdown") {
            it.Shutdown().execute(Empty())
        }
        disconnect()
    }
    
    // ========== 内部方法 ==========
    
    private suspend fun loadInitialData(client: HostServiceClient) {
        try {
            withContext(Dispatchers.IO) {
                // 获取槽位绑定配置并初始化状态
                val slotList = client.ListSlots().execute(Empty())
                val slots = slotList.items.associate { binding ->
                    binding.slot_id to SlotState(
                        id = binding.slot_id,
                        sn = null,
                        status = SlotStatus.IDLE,
                        currentStep = 0,
                        totalSteps = 0,
                        currentStepName = null,
                        currentStepValue = null,
                        variables = emptyList(),
                        logs = emptyList(),
                        elapsedTime = null,
                        deviceInfo = binding.device_bindings.entries.firstOrNull()?.let { 
                            "${it.key}: ${it.value}" 
                        }
                    )
                }
                _slotsMap.value = slots
            }
        } catch (e: Exception) {
            addLog("加载初始数据失败: ${e.message}")
        }
    }
    
    private fun startEventSubscription(client: HostServiceClient) {
        subscriptionJob?.cancel()
        subscriptionJob = scope.launch {
            try {
                val call = client.Subscribe()
                val (requestChannel, responseChannel) = call.executeIn(this)
                
                // 发送订阅请求
                requestChannel.send(SubscribeRequest(topics = listOf("slot_update", "log")))
                requestChannel.close()
                
                // 处理事件流
                for (event in responseChannel) {
                    handleEvent(event)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    addLog("事件订阅中断: ${e.message}")
                    // 尝试重连
                    delay(3000)
                    clientManager.getClient()?.let { startEventSubscription(it) }
                }
            }
        }
    }

    private fun startStatusPolling(client: HostServiceClient) {
        statusPollingJob?.cancel()
        statusPollingJob = scope.launch {
            while (isActive) {
                delay(2000) // Poll every 2 seconds
                refreshDeviceStatus(client)
            }
        }
    }
    
    private fun handleEvent(event: Event) {
        when (event.type) {
            "slot_update" -> {
                event.slot_update?.let { slotEvent ->
                    updateSlot(slotEvent)
                }
            }
            "log" -> {
                event.log?.let { logEvent ->
                    addLog(Mappers.mapLogEvent(logEvent))
                }
            }
        }
    }
    
    private fun updateSlot(slotEvent: SlotUpdateEvent) {
        val status = slotEvent.status ?: return
        _slotsMap.update { map ->
            val existing = map[status.slot_id]
            val updated = Mappers.mapSlotStatus(
                status,
                existingLogs = existing?.logs ?: emptyList(),
                sn = existing?.sn
            )
            map + (status.slot_id to updated)
        }
    }
    
    private fun addLog(message: String) {
        print("UILog $message")
        io.github.lzdev42.catalyticui.log.LogManager.addLog("UILog $message")
    }
    
    private suspend fun <T> executeRpc(
        name: String,
        block: suspend (HostServiceClient) -> T
    ): kotlin.Result<T> {
        val client = clientManager.getClient()
            ?: return kotlin.Result.failure(IllegalStateException("未连接到 Host"))
        
        return try {
            addLog("[$name] 开始执行")
            withContext(Dispatchers.IO) {
                addLog("[$name] 执行成功")
                kotlin.Result.success(block(client))
            }
        } catch (e: Exception) {
            addLog("$name 失败: ${e.message}")
            kotlin.Result.failure(e)
        }
    }
    
    // ========== Plugin Management ==========
    
    override suspend fun listPlugins(): List<PluginInfo> {
        val result = executeRpc("ListPlugins") {
            it.ListPlugins().execute(Empty())
        }
        return result.getOrNull()?.items?.map { plugin ->
            PluginInfo(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                protocols = plugin.protocols.toList(),
                types = plugin.types.toList()
            )
        } ?: emptyList()
    }
    
    override suspend fun setSlotSn(slotId: Int, sn: String) {
        executeRpc("SetSlotSn") { client ->
            val result = client.SetSlotSn().execute(SetSlotSnRequest(slot_id = slotId, sn = sn))
            if (!result.success) {
                addLog("SetSlotSn 失败: ${result.error}")
            } else {
                addLog("SetSlotSn 成功: slot=$slotId, sn=$sn")
            }
        }.onFailure { addLog("SetSlotSn 失败: ${it.message}") }
    }
}
