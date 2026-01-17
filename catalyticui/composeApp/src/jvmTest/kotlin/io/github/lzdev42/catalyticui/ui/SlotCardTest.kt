package io.github.lzdev42.catalyticui.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.SlotVariable
import io.github.lzdev42.catalyticui.ui.components.SlotCard
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * SlotCard 组件 UI 测试
 * 
 * 测试范围:
 * - 各状态下的 UI 显示
 * - 按钮点击回调
 * - 日志/变量显示
 */
class SlotCardTest {
    
    @get:Rule
    val rule = createComposeRule()
    
    // ============================================
    // 测试 1: IDLE 状态显示
    // ============================================
    @Test
    fun `slotCard_idleStatus_displaysCorrectly`() {
        val state = createIdleState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证卡片存在
        rule.onNodeWithTag("slot_card_0").assertExists()
        
        // 验证标题
        rule.onNodeWithTag("slot_title_0").assertTextContains("Slot 0")
        
        // 验证状态徽章显示 "空闲"
        rule.onNodeWithTag("slot_badge_0").assertExists()
        rule.onNodeWithText("空闲").assertExists()
        
        // 验证 SN 显示 "未扫描SN"
        rule.onNodeWithTag("slot_sn_0").assertTextEquals("未扫描SN")
        
        // 验证 Start 按钮存在
        rule.onNodeWithTag("btn_start_0").assertExists()
        rule.onNodeWithTag("btn_start_0").assertIsEnabled()
    }
    
    // ============================================
    // 测试 2: RUNNING 状态显示进度和按钮
    // ============================================
    @Test
    fun `slotCard_runningStatus_displaysProgressAndButtons`() {
        val state = createRunningState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证状态徽章显示 "运行中"
        rule.onNodeWithText("运行中").assertExists()
        
        // 验证 SN 显示
        rule.onNodeWithTag("slot_sn_1").assertTextEquals("SN: ABC123")
        
        // 验证进度文本
        rule.onNodeWithText("步骤 3/10", substring = true).assertExists()
        
        // 验证 Pause 和 Stop 按钮存在
        rule.onNodeWithTag("btn_pause_1").assertExists()
        rule.onNodeWithTag("btn_stop_1").assertExists()
        
        // 验证 Start 按钮不存在
        rule.onNodeWithTag("btn_start_1").assertDoesNotExist()
    }
    
    // ============================================
    // 测试 3: PAUSED 状态显示 Resume 按钮
    // ============================================
    @Test
    fun `slotCard_pausedStatus_showsResumeButton`() {
        val state = createPausedState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证状态徽章显示 "已暂停"
        rule.onNodeWithText("已暂停").assertExists()
        
        // 验证 Resume 和 Stop 按钮存在
        rule.onNodeWithTag("btn_resume_0").assertExists()
        rule.onNodeWithTag("btn_stop_0").assertExists()
    }
    
    // ============================================
    // 测试 4: PASS 状态显示结果
    // ============================================
    @Test
    fun `slotCard_passStatus_showsResultAndActions`() {
        val state = createPassState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证状态徽章显示 "通过"
        rule.onNodeWithText("通过").assertExists()
        
        // 验证耗时显示
        rule.onNodeWithText("耗时 1:23", substring = true).assertExists()
        
        // 验证 Restart 和 Report 按钮存在
        rule.onNodeWithTag("btn_restart_0").assertExists()
        rule.onNodeWithTag("btn_report_0").assertExists()
    }
    
    // ============================================
    // 测试 5: FAIL 状态显示错误指示
    // ============================================
    @Test
    fun `slotCard_failStatus_showsErrorIndicator`() {
        val state = createFailState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证状态徽章显示 "失败"
        rule.onNodeWithText("失败").assertExists()
        
        // 验证 Restart 和 Report 按钮存在
        rule.onNodeWithTag("btn_restart_0").assertExists()
        rule.onNodeWithTag("btn_report_0").assertExists()
    }
    
    // ============================================
    // 测试 6: Start 按钮点击回调
    // ============================================
    @Test
    fun `slotCard_startButtonClick_triggersCallback`() {
        var startClicked = false
        val state = createIdleState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = { startClicked = true },
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.onNodeWithTag("btn_start_0").performClick()
        
        assertTrue(startClicked, "Start 按钮回调未触发")
    }
    
    // ============================================
    // 测试 7: Pause 按钮点击回调
    // ============================================
    @Test
    fun `slotCard_pauseButtonClick_triggersCallback`() {
        var pauseClicked = false
        val state = createRunningState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = { pauseClicked = true },
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.onNodeWithTag("btn_pause_1").performClick()
        
        assertTrue(pauseClicked, "Pause 按钮回调未触发")
    }
    
    // ============================================
    // 测试 8: Stop 按钮点击回调
    // ============================================
    @Test
    fun `slotCard_stopButtonClick_triggersCallback`() {
        var stopClicked = false
        val state = createRunningState()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = { stopClicked = true },
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.onNodeWithTag("btn_stop_1").performClick()
        
        assertTrue(stopClicked, "Stop 按钮回调未触发")
    }
    
    // ============================================
    // 测试 9: 日志显示
    // ============================================
    @Test
    fun `slotCard_logsDisplay_showsLogEntries`() {
        val state = createStateWithLogs()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证日志标题显示数量
        rule.onNodeWithText("检测日志 (2)", substring = true).assertExists()
        
        // 验证日志内容
        rule.onNodeWithText("[10:00] 开始测试", substring = true).assertExists()
        rule.onNodeWithText("PASS", substring = true).assertExists()
    }
    
    // ============================================
    // 测试 10: 变量显示
    // ============================================
    @Test
    fun `slotCard_variablesDisplay_showsVariables`() {
        val state = createStateWithVariables()
        
        rule.setContent {
            CatalyticTheme {
                SlotCard(
                    state = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onRestart = {},
                    onViewReport = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证变量显示
        rule.onNodeWithText("V: 3.3V", substring = true).assertExists()
    }
    
    // ============================================
    // 辅助方法: 创建测试状态
    // ============================================
    
    private fun createIdleState() = SlotState(
        id = 0,
        sn = null,
        status = SlotStatus.IDLE,
        currentStep = 0,
        totalSteps = 0,
        currentStepName = null,
        currentStepValue = null,
        variables = emptyList(),
        logs = emptyList(),
        elapsedTime = null,
        deviceInfo = null
    )
    
    private fun createRunningState() = SlotState(
        id = 1,
        sn = "ABC123",
        status = SlotStatus.RUNNING,
        currentStep = 3,
        totalSteps = 10,
        currentStepName = "电压检测",
        currentStepValue = "3.31V",
        variables = emptyList(),
        logs = emptyList(),
        elapsedTime = null,
        deviceInfo = null
    )
    
    private fun createPausedState() = SlotState(
        id = 0,
        sn = "DEF456",
        status = SlotStatus.PAUSED,
        currentStep = 5,
        totalSteps = 10,
        currentStepName = "电流检测",
        currentStepValue = null,
        variables = emptyList(),
        logs = emptyList(),
        elapsedTime = null,
        deviceInfo = null
    )
    
    private fun createPassState() = SlotState(
        id = 0,
        sn = "GHI789",
        status = SlotStatus.PASS,
        currentStep = 5,
        totalSteps = 5,
        currentStepName = null,
        currentStepValue = null,
        variables = emptyList(),
        logs = emptyList(),
        elapsedTime = "1:23",
        deviceInfo = null
    )
    
    private fun createFailState() = SlotState(
        id = 0,
        sn = "JKL012",
        status = SlotStatus.FAIL,
        currentStep = 3,
        totalSteps = 5,
        currentStepName = null,
        currentStepValue = null,
        variables = emptyList(),
        logs = emptyList(),
        elapsedTime = "0:45",
        deviceInfo = null
    )
    
    private fun createStateWithLogs() = SlotState(
        id = 0,
        sn = null,
        status = SlotStatus.IDLE,
        currentStep = 0,
        totalSteps = 0,
        currentStepName = null,
        currentStepValue = null,
        variables = emptyList(),
        logs = listOf(
            "[10:00] 开始测试",
            "[10:01] PASS 电压检测"
        ),
        elapsedTime = null,
        deviceInfo = null
    )
    
    private fun createStateWithVariables() = SlotState(
        id = 0,
        sn = null,
        status = SlotStatus.RUNNING,
        currentStep = 1,
        totalSteps = 5,
        currentStepName = "电压检测",
        currentStepValue = null,
        variables = listOf(
            SlotVariable("V", "3.3V", true)
        ),
        logs = emptyList(),
        elapsedTime = null,
        deviceInfo = null
    )
}
