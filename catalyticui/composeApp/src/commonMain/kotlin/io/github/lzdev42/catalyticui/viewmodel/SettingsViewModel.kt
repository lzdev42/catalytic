package io.github.lzdev42.catalyticui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import io.github.lzdev42.catalyticui.model.CommandUiState
import io.github.lzdev42.catalyticui.model.DeviceTypeUiState
import io.github.lzdev42.catalyticui.model.DeviceUiState
import io.github.lzdev42.catalyticui.model.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lzdev42.catalyticui.data.EngineRepository
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 设置页面 ViewModel
 * 
 * 职责：
 * - 管理设置页面的所有状态
 * - 处理用户交互的业务逻辑
 * - 提供数据给 UI 层（单向数据流）
 * 
 * 状态说明：
 * - 初始状态为空（真实生产环境）
 * - Mock 数据可通过 loadMockData() / clearMockData() 切换
 */
class SettingsViewModel(
    private val repository: EngineRepository? = null
) : ViewModel() {

    // ==========================================
    // State Definitions (Must be initialized before init block)
    // ==========================================
    
    // Mock Data Control
    private val _isMockDataLoaded = mutableStateOf(false)
    val isMockDataLoaded: State<Boolean> = _isMockDataLoaded
    
    // Device Management State
    private val _deviceTypes = mutableStateOf<List<DeviceTypeUiState>>(emptyList())
    val deviceTypes: State<List<DeviceTypeUiState>> = _deviceTypes
    
    // Plugin State (for device type creation)
    private val _plugins = mutableStateOf<List<PluginInfo>>(emptyList())
    val plugins: State<List<PluginInfo>> = _plugins
    
    // [DELETED] Role state removed
    
    private val _steps = mutableStateOf<List<StepUiState>>(emptyList())
    val steps: State<List<StepUiState>> = _steps
    
    // Slot Binding State
    private val _slotCount = mutableStateOf(4)
    val slotCount: State<Int> = _slotCount
    
    private val _slotBindings = mutableStateOf<Map<Int, Map<String, List<String>>>>(emptyMap())
    val slotBindings: State<Map<Int, Map<String, List<String>>>> = _slotBindings
    
    // Connection State
    private val _hostAddress = mutableStateOf("localhost")
    val hostAddress: State<String> = _hostAddress
    
    private val _hostPort = mutableStateOf("5000")
    val hostPort: State<String> = _hostPort
    
    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading
    
    // Appearance State
    private val _selectedLanguage = mutableStateOf("zh-CN")
    val selectedLanguage: State<String> = _selectedLanguage

    init {
        if (repository != null) {
            observeConnectionAndLoadData()
        }
    }

    /**
     * 观察连接状态，连接成功后加载数据
     */
    private fun observeConnectionAndLoadData() {
        viewModelScope.launch {
            // 先观察连接状态
            repository?.isConnected?.collect { connected ->
                _isConnected.value = connected
                if (connected) {
                    loadRealData()
                }
            }
        }
    }

    private suspend fun loadRealData() {
        try {
            // 加载配置数据
            val slotCount = repository?.getSlotCount() ?: 4
            val devices = repository?.getDeviceTypes() ?: emptyList()
            val stepList = repository?.getSteps() ?: emptyList()
            val bindings = repository?.getSlotBindings() ?: emptyMap()
            val pluginList = repository?.listPlugins() ?: emptyList()
            
            _slotCount.value = slotCount
            _deviceTypes.value = devices
            _steps.value = stepList
            _slotBindings.value = bindings
            _plugins.value = pluginList
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
        }
    }

    // ==========================================
    // Mock Data Functionality
    // ==========================================
    
    /**
     * 加载 Mock 数据（用于开发测试）
     */
    fun loadMockData() {
        if (_isMockDataLoaded.value) return
        
        _deviceTypes.value = createMockDeviceTypes()
        _steps.value = createMockSteps()
        _isMockDataLoaded.value = true
    }
    
    /**
     * 清除 Mock 数据，恢复空状态
     */
    fun clearMockData() {
        _deviceTypes.value = emptyList()
        _steps.value = emptyList()
        _slotBindings.value = emptyMap()
        _isMockDataLoaded.value = false
    }

    // ==========================================
    // Device Management Logic
    // ==========================================
    
    fun toggleDeviceTypeExpand(typeId: String) {
        _deviceTypes.value = _deviceTypes.value.map {
            if (it.id == typeId) it.copy(isExpanded = !it.isExpanded) else it
        }
    }
    
    fun addDeviceType(name: String, pluginId: String) {
        // 根据 pluginId 查找插件获取图标提示
        val plugin = _plugins.value.find { it.id == pluginId }
        val newType = DeviceTypeUiState(
            id = "type_${Random.nextLong()}",
            name = name,
            icon = when {
                pluginId.contains("serial") -> "usb"
                pluginId.contains("tcp") -> "ethernet"
                else -> "device_unknown"
            },
            pluginId = pluginId,
            devices = emptyList()
        )
        _deviceTypes.value = _deviceTypes.value + newType
        
        // 持久化到 Host
        viewModelScope.launch {
            try {
                repository?.createDeviceType(newType)
            } catch (e: Exception) {
                println("Error creating device type: ${e.message}")
            }
        }
    }
    
    fun deleteDeviceType(typeId: String) {
        val newList = _deviceTypes.value.filter { it.id != typeId }
        _deviceTypes.value = newList
        // DRAFT ONLY: No persistence call here
    }
    
    fun addDeviceToType(typeId: String, name: String, address: String) {
        val newDevice = DeviceUiState(
            id = "device_${Random.nextLong()}",
            name = name,
            address = address
        )
        _deviceTypes.value = _deviceTypes.value.map { type ->
            if (type.id == typeId) {
                type.copy(devices = type.devices + newDevice)
            } else type
        }
        // DRAFT ONLY: No persistence call here
    }

    fun addCommandToType(typeId: String, command: CommandUiState) {
        _deviceTypes.value = _deviceTypes.value.map { type ->
            if (type.id == typeId) {
                type.copy(commands = type.commands + command)
            } else type
        }
        // DRAFT ONLY: No persistence call here
    }

    // ==========================================
    // Flow Definition Logic
    // ==========================================
    
    // [DELETED] addRole and deleteRole methods removed
    
    fun toggleStepExpand(stepId: Int) {
        _steps.value = _steps.value.map {
            if (it.stepId == stepId) it.copy(isExpanded = !it.isExpanded) else it
        }
    }
    
    fun addStep() {
        val newId = (_steps.value.maxOfOrNull { it.stepId } ?: 0) + 1
        val newStep = StepUiState(
            stepId = newId,
            stepName = "New Step",
            executionMode = ExecutionMode.ENGINE_CONTROLLED,
            engineTask = EngineTaskUiState(deviceTypeId = ""),
            isExpanded = true
        )
        val newList = _steps.value + newStep
        _steps.value = newList
        // DRAFT ONLY
    }
    
    fun deleteStep(stepId: Int) {
        val newList = _steps.value.filter { it.stepId != stepId }
        _steps.value = newList
        // DRAFT ONLY
    }
    
    fun moveStepUp(stepId: Int) {
        val list = _steps.value.toMutableList()
        val i = list.indexOfFirst { it.stepId == stepId }
        if (i > 0) {
            val temp = list[i]
            list[i] = list[i - 1]
            list[i - 1] = temp
            _steps.value = list
            // DRAFT ONLY
        }
    }
    
    fun moveStepDown(stepId: Int) {
        val list = _steps.value.toMutableList()
        val i = list.indexOfFirst { it.stepId == stepId }
        if (i >= 0 && i < list.size - 1) {
            val temp = list[i]
            list[i] = list[i + 1]
            list[i + 1] = temp
            _steps.value = list
            // DRAFT ONLY
        }
    }
    
    fun updateStep(stepId: Int, transform: (StepUiState) -> StepUiState) {
        val newList = _steps.value.map {
            if (it.stepId == stepId) transform(it) else it
        }
        _steps.value = newList
        // DRAFT ONLY
    }
    
    fun updateStepExecutionMode(stepId: Int, mode: ExecutionMode) {
        updateStep(stepId) { step ->
            when (mode) {
                ExecutionMode.ENGINE_CONTROLLED -> step.copy(
                    executionMode = mode,
                    engineTask = step.engineTask ?: EngineTaskUiState(deviceTypeId = ""),
                    hostTask = null
                )
                ExecutionMode.HOST_CONTROLLED -> step.copy(
                    executionMode = mode,
                    hostTask = step.hostTask ?: HostTaskUiState(taskName = ""),
                    engineTask = null
                )
                ExecutionMode.CALCULATION -> step.copy(
                    executionMode = mode,
                    engineTask = null,
                    hostTask = null
                )
            }
        }
    }
    
    // ==========================================
    // Slot Binding Logic
    // ==========================================
    
    // Available device options (derived from deviceTypes)
    // [Obsolete] Properties dutOptions, dmmOptions, saOptions removed as we now support dynamic types.
    
    fun updateSlotCount(count: Int) {
        _slotCount.value = count.coerceIn(1, 16)
    }
    
    fun addSlotBinding(slotIndex: Int, deviceType: String, deviceId: String) {
        val currentBindings = _slotBindings.value.toMutableMap()
        val slotMap = currentBindings[slotIndex]?.toMutableMap() ?: mutableMapOf()
        val currentList = slotMap[deviceType]?.toMutableList() ?: mutableListOf()
        
        if (!currentList.contains(deviceId)) {
            currentList.add(deviceId)
            slotMap[deviceType] = currentList
            currentBindings[slotIndex] = slotMap
            _slotBindings.value = currentBindings
        }
    }

    fun removeSlotBinding(slotIndex: Int, deviceType: String, deviceId: String) {
        val currentBindings = _slotBindings.value.toMutableMap()
        val slotMap = currentBindings[slotIndex]?.toMutableMap() ?: mutableMapOf()
        val currentList = slotMap[deviceType]?.toMutableList() ?: mutableListOf()
        
        if (currentList.remove(deviceId)) {
            slotMap[deviceType] = currentList
            currentBindings[slotIndex] = slotMap
            _slotBindings.value = currentBindings
        }
    }
    
    fun getSlotBindingsForType(slotIndex: Int, deviceType: String): List<String> {
        return _slotBindings.value[slotIndex]?.get(deviceType) ?: emptyList()
    }
    
    // ==========================================
    // Connection Logic
    // ==========================================
    
    fun updateHostAddress(address: String) {
        _hostAddress.value = address
    }
    
    fun updateHostPort(port: String) {
        _hostPort.value = port
    }
    
    fun testConnection() {
        // TODO: Real connection test via Repository
        _isConnected.value = !_isConnected.value
    }
    
    // ==========================================
    // Save All Settings
    // ==========================================
    
    /**
     * 保存所有设置到 Host/Engine
     * 由"保存"按钮触发
     */
    fun saveAllSettings() {
        if (repository == null) return
        if (_isLoading.value) return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                println("Starting Transaction Commit...")
                
                // 1. Save Device Types (including instances)
                println("Committing Device Types (${_deviceTypes.value.size})...")
                repository.saveDeviceTypes(_deviceTypes.value)
                
                // [DELETED] saveRoles call removed
                
                // 2. Save Steps
                println("Committing Steps (${_steps.value.size})...")
                repository.saveSteps(_steps.value)
                
                // 4. Save Slot Count
                println("Committing Slot Count (${_slotCount.value})...")
                repository.setSlotCount(_slotCount.value)
                
                // 5. Save Slot Bindings
                println("Committing Slot Bindings (${_slotBindings.value.size})...")
                repository.saveSlotBindings(_slotBindings.value)
                
                println("Transaction Commit Successful.")
            } catch (e: Exception) {
                println("Transaction Commit FAILED: ${e.message}")
                e.printStackTrace()
                // TODO: Show User Feedback
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==========================================
    // Appearance Logic
    // ==========================================
    
    fun updateLanguage(language: String) {
        _selectedLanguage.value = language
    }
    
    // ==========================================
    // Mock Data Factory
    // ==========================================
    
    private fun createMockDeviceTypes(): List<DeviceTypeUiState> = listOf(
        DeviceTypeUiState(
            id = "dmm",
            name = "Digital Multimeter (数字万用表)",
            icon = "multimeter",
            devices = listOf(
                DeviceUiState("dmm_001", "DMM_Main", "192.168.1.101"),
                DeviceUiState("dmm_002", "DMM_Aux", "192.168.1.102", false)
            ),
            commands = listOf(
                CommandUiState("read_volt", "读取电压", "MEAS:VOLT:DC?", "NumberExtract"),
                CommandUiState("read_curr", "读取电流", "MEAS:CURR:DC?", "NumberExtract"),
                CommandUiState("reset", "复位", "*RST", null)
            )
        ),
        DeviceTypeUiState(
            id = "sa",
            name = "Spectrum Analyzer (频谱分析仪)",
            icon = "graphic_eq",
            devices = listOf(
                DeviceUiState("sa_001", "SA_Main", "GPIB0::14::INSTR")
            ),
            commands = listOf(
                CommandUiState("scan_freq", "扫频", "SENS:FREQ:SPAN 10MHz", null),
                CommandUiState("get_peak", "读取峰值", "CALC:MARK:MAX", "NumberExtract")
            )
        ),
        DeviceTypeUiState(
            id = "dut",
            name = "DUT (待测设备)",
            icon = "developer_board",
            devices = listOf(
                DeviceUiState("dut_a", "DUT_A", "COM3", true),
                DeviceUiState("dut_b", "DUT_B", "COM4", true),
                DeviceUiState("dut_c", "DUT_C", "COM5", false),
                DeviceUiState("dut_d", "DUT_D", "COM6", true)
            )
        )
    )
    
    // [DELETED] createMockRoles function removed
    
    private fun createMockSteps(): List<StepUiState> = listOf(
        StepUiState(
            stepId = 1,
            stepName = "设备上电 (Power On)",
            executionMode = ExecutionMode.HOST_CONTROLLED,
            hostTask = HostTaskUiState(
                taskName = "PowerOn",
                paramsJson = """{"voltage": 3.3, "current_limit": 0.5}""",
                timeoutMs = 5000
            )
        ),
        StepUiState(
            stepId = 2,
            stepName = "电压检测 (Check Voltage)",
            executionMode = ExecutionMode.ENGINE_CONTROLLED,
            engineTask = EngineTaskUiState(
                deviceTypeId = "dmm",
                commandId = "read_volt"
            ),
            variables = mapOf("voltage_a" to VariableType.NUMBER),
            checkRule = CheckRuleUiState.RangeCheck(variableName = "voltage_a", min = 3.0, max = 3.5),
            isExpanded = true
        ),
        StepUiState(
            stepId = 3,
            stepName = "电流检测 (Check Current)",
            executionMode = ExecutionMode.ENGINE_CONTROLLED,
            engineTask = EngineTaskUiState(
                deviceTypeId = "dmm",
                commandId = "read_curr"
            ),
            variables = mapOf("current_a" to VariableType.NUMBER),
            checkRule = CheckRuleUiState.Threshold(variableName = "current_a", operator = "<", value = 0.3)
        ),
        StepUiState(
            stepId = 4,
            stepName = "频谱仪扫频 (SA Scan)",
            executionMode = ExecutionMode.ENGINE_CONTROLLED,
            engineTask = EngineTaskUiState(
                deviceTypeId = "sa",
                commandId = "scan_freq"
            )
        ),
        StepUiState(
            stepId = 5,
            stepName = "读取峰值 (Get Peak)",
            executionMode = ExecutionMode.ENGINE_CONTROLLED,
            engineTask = EngineTaskUiState(
                deviceTypeId = "sa",
                commandId = "get_peak"
            ),
            variables = mapOf("peak_freq" to VariableType.NUMBER)
        )
    )
}
