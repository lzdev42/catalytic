package io.github.lzdev42.catalyticui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lzdev42.catalyticui.data.EngineRepository
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.SlotVariable
import io.github.lzdev42.catalyticui.model.TestStepUiState
import io.github.lzdev42.catalyticui.model.SystemStatusUiState
import io.github.lzdev42.catalyticui.util.getCurrentTimeString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.lzdev42.catalyticui.ui.components.AlertState
import io.github.lzdev42.catalyticui.i18n.AppStrings

/**
 * 主界面 ViewModel
 */
class MainViewModel(
    private val repository: EngineRepository? = null
) : ViewModel() {
    
    // ========== UI State ==========
    
    private val _slots = MutableStateFlow<List<SlotState>>(emptyList())
    val slots: StateFlow<List<SlotState>> = _slots.asStateFlow()
    
    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()
    
    private val _testSteps = MutableStateFlow<List<TestStepUiState>>(emptyList())
    val testSteps: StateFlow<List<TestStepUiState>> = _testSteps.asStateFlow()
    
    private val _systemStatus = MutableStateFlow(SystemStatusUiState())
    val systemStatus: StateFlow<SystemStatusUiState> = _systemStatus.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _deviceConnections = MutableStateFlow<List<io.github.lzdev42.catalyticui.model.DeviceConnectionState>>(emptyList())
    val deviceConnections: StateFlow<List<io.github.lzdev42.catalyticui.model.DeviceConnectionState>> = _deviceConnections.asStateFlow()

    private val _allDevices = MutableStateFlow<List<io.github.lzdev42.catalyticui.model.DeviceUiState>>(emptyList())
    val allDevices: StateFlow<List<io.github.lzdev42.catalyticui.model.DeviceUiState>> = _allDevices.asStateFlow()
    
    // Alert dialog state
    private val _alertState = MutableStateFlow(AlertState.Hidden)
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()
    
    // Configuration state (updated from SettingsViewModel sync)
    private var hasDevices = false
    private var hasSteps = false
    
    // i18n strings (updated from UI layer)
    private var strings: AppStrings = AppStrings()
    
    fun updateStrings(newStrings: AppStrings) {
        this.strings = newStrings
    }
    
    fun updateConfigState(hasDevices: Boolean, hasSteps: Boolean) {
        this.hasDevices = hasDevices
        this.hasSteps = hasSteps
    }
    
    fun dismissAlert() {
        _alertState.value = AlertState.Hidden
    }
    
    init {
        // 如果注入了 Repository，则订阅数据
        if (repository != null) {
            observeRepository()
        }
    }
    
    private fun observeRepository() {
        if (repository == null) return
        
        viewModelScope.launch {
            repository.isConnected.collect { _isConnected.value = it }
        }
        
        viewModelScope.launch {
            repository.slotsFlow.collect { _slots.value = it }
        }
        
        viewModelScope.launch {
            repository.systemLogsFlow.collect { _systemLogs.value = it }
        }
        
        viewModelScope.launch {
            repository.deviceConnectionsFlow.collect { _deviceConnections.value = it }
        }
        
        viewModelScope.launch {
            repository.isConnected.collect { connected ->
                if (connected) loadDevices()
            }
        }
    }
    
    // ========== Actions ==========
    
    fun startTest(slotId: Int) {
        // 校验：设备、流程、SN
        val slot = _slots.value.find { it.id == slotId }
        
        if (!hasDevices) {
            _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertNoDevices)
            return
        }
        if (!hasSteps) {
            _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertNoSteps)
            return
        }
        if (slot?.sn.isNullOrBlank()) {
            _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertSlotNoSn.replace("%d", slotId.toString()))
            return
        }
        
        if (repository != null) {
            viewModelScope.launch { repository.startTest(slotId) }
        } else {
            updateSlot(slotId) { it.copy(status = SlotStatus.RUNNING) }
            addLog("Slot $slotId: 开始测试 (Mock)")
        }
    }
    
    fun pauseTest(slotId: Int) {
        if (repository != null) {
            viewModelScope.launch { repository.pauseTest(slotId) }
        } else {
            updateSlot(slotId) { it.copy(status = SlotStatus.PAUSED) }
            addLog("Slot $slotId: 暂停 (Mock)")
        }
    }
    
    fun resumeTest(slotId: Int) {
        if (repository != null) {
            viewModelScope.launch { repository.resumeTest(slotId) }
        } else {
            updateSlot(slotId) { it.copy(status = SlotStatus.RUNNING) }
            addLog("Slot $slotId: 继续 (Mock)")
        }
    }
    
    fun stopTest(slotId: Int) {
        if (repository != null) {
            viewModelScope.launch { repository.stopTest(slotId) }
        } else {
            updateSlot(slotId) { it.copy(status = SlotStatus.IDLE, currentStep = 0) }
            addLog("Slot $slotId: 停止 (Mock)")
        }
    }
    
    fun setSlotSn(slotId: Int, sn: String) {
        if (repository != null) {
            viewModelScope.launch { repository.setSlotSn(slotId, sn) }
        }
        // 本地更新 UI 状态
        updateSlot(slotId) { it.copy(sn = sn) }
        addLog("Slot $slotId: 设置 SN = $sn")
    }
    
    fun startAll() {
        // 校验：设备、流程
        if (!hasDevices) {
            _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertNoDevices)
            return
        }
        if (!hasSteps) {
            _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertNoSteps)
            return
        }
        
        // 校验：是否有可启动的 Slot（IDLE 且有 SN）
        val readySlots = _slots.value.filter { it.status == SlotStatus.IDLE && !it.sn.isNullOrBlank() }
        if (readySlots.isEmpty()) {
            val idleWithoutSn = _slots.value.filter { it.status == SlotStatus.IDLE && it.sn.isNullOrBlank() }
            if (idleWithoutSn.isNotEmpty()) {
                _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertAllSlotsNoSn)
            } else {
                _alertState.value = AlertState.show(strings.alertCannotStart, strings.alertNoReadySlots)
            }
            return
        }
        
        if (repository != null) {
            viewModelScope.launch { repository.startAll() }
        } else {
            readySlots.forEach { slot ->
                startTest(slot.id)
            }
        }
    }
    
    fun stopAll() {
        if (repository != null) {
            viewModelScope.launch { repository.stopAll() }
        } else {
            _slots.value.filter { it.status != SlotStatus.IDLE }.forEach { slot ->
                stopTest(slot.id)
            }
        }
    }

    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            repository?.connectDevice(deviceId)
                ?.onFailure { _alertState.value = AlertState.show("连接失败", it.message ?: "") }
        }
    }

    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            repository?.disconnectDevice(deviceId)
                ?.onFailure { _alertState.value = AlertState.show("断开失败", it.message ?: "") }
        }
    }
    
    // ========== Helper ==========
    
    private fun updateSlot(slotId: Int, transform: (SlotState) -> SlotState) {
        _slots.update { list ->
            list.map { if (it.id == slotId) transform(it) else it }
        }
    }
    
    private fun addLog(message: String) {
        val time = getCurrentTimeString()
        _systemLogs.update { (it + "$time $message").takeLast(500) }
    }

    private fun loadDevices() {
        if (repository == null) return
        viewModelScope.launch {
            val types = repository.getDeviceTypes()
            val flattened = types.flatMap { type -> type.devices }
            _allDevices.value = flattened
            hasDevices = flattened.isNotEmpty()
        }
    }
    
    // ==========================================================
    // Mock 数据控制
    // ==========================================================
    
    private val _isMockDataLoaded = MutableStateFlow(false)
    val isMockDataLoaded: StateFlow<Boolean> = _isMockDataLoaded.asStateFlow()
    
    /**
     * 加载 Mock 数据（用于开发测试）
     * 生产环境不调用此函数，改为调用 connectToHost()
     */
    fun loadMockData() {
        // if (repository != null) return // 允许手动加载 Mock 数据以便调试
        if (_isMockDataLoaded.value) return
        
        _isConnected.value = true
        _isMockDataLoaded.value = true
        addLog("程序启动，加载 Mock 数据")
        
        // Mock 测试步骤
        _testSteps.value = listOf(
            TestStepUiState(1, "设备上电", "初始化电源"),
            TestStepUiState(2, "电压检测", "3.0V - 3.5V"),
            TestStepUiState(3, "电流检测", "< 300mA"),
            TestStepUiState(4, "频谱仪配置", "中心频率 2.4GHz"),
            TestStepUiState(5, "射频功率", "-15 ~ -5 dBm"),
            TestStepUiState(6, "频率验证", "2.4GHz ± 100kHz"),
            TestStepUiState(7, "设备下电", "关闭电源")
        )
        
        // Mock 系统状态
        _systemStatus.value = SystemStatusUiState(
            systemInfo = "Windows 10",
            version = "Catalytic v1.2.0",
            flowName = "射频模块生产测试 v2.1",
            passCount = 128,
            failCount = 3
        )
        
        // Mock 日志
        addLog("连接设备: DMM_1 (192.168.1.101)")
        addLog("连接设备: SA_1 (192.168.1.102)")
        
        // Mock 槽位
        _slots.value = listOf(
            SlotState(
                id = 0,
                sn = "ABC123456789",
                status = SlotStatus.RUNNING,
                currentStep = 3,
                totalSteps = 7,
                currentStepName = "电压检测",
                currentStepValue = "3.31V",
                variables = listOf(
                    SlotVariable("V", "3.31V", true),
                    SlotVariable("I", "125mA", true)
                ),
                logs = listOf("Step 1: 设备上电 PASS", "Step 2: 初始化 PASS", "Step 3: 检测中..."),
                elapsedTime = null,
                deviceInfo = null
            ),
            SlotState(
                id = 1,
                sn = "DEF987654321",
                status = SlotStatus.PAUSED,
                currentStep = 4,
                totalSteps = 7,
                currentStepName = "频谱仪配置",
                currentStepValue = null,
                variables = listOf(SlotVariable("V", "3.28V", true), SlotVariable("I", "142mA", true)),
                logs = listOf("Step 1-3: PASS", "Step 4: 暂停"),
                elapsedTime = "00:45",
                deviceInfo = null
            ),
            SlotState(
                id = 2,
                sn = "GHI111222333",
                status = SlotStatus.PASS,
                currentStep = 7,
                totalSteps = 7,
                currentStepName = null,
                currentStepValue = null,
                variables = listOf(
                    SlotVariable("V", "3.30V", true),
                    SlotVariable("I", "128mA", true),
                    SlotVariable("RF", "-10.2dBm", true)
                ),
                logs = listOf("全部 7 步 PASS"),
                elapsedTime = "01:23",
                deviceInfo = null
            ),
            SlotState(
                id = 3,
                sn = null,
                status = SlotStatus.IDLE,
                currentStep = 0,
                totalSteps = 7,
                currentStepName = null,
                currentStepValue = null,
                variables = emptyList(),
                logs = emptyList(),
                elapsedTime = null,
                deviceInfo = "DMM: 192.168.1.101"
            )
        )
    }
    
    /**
     * 清除 Mock 数据，恢复空状态
     */
    fun clearMockData() {
        // if (repository != null) return
        
        _slots.value = emptyList()
        _systemLogs.value = emptyList()
        _testSteps.value = emptyList()
        _systemStatus.value = SystemStatusUiState()
        _isConnected.value = false
        _isMockDataLoaded.value = false
    }
}
