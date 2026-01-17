package io.github.lzdev42.catalyticui.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.SlotVariable
import io.github.lzdev42.catalyticui.ui.components.SlotCard
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 截图测试
 * 
 * 捕获各状态的 SlotCard 截图用于视觉回归验证
 */
class ScreenshotTest {
    
    @get:Rule
    val rule = createComposeRule()
    
    // ============================================
    // 测试 14: 捕获各状态截图
    // ============================================
    @Test
    fun `screenshot_slotCard_allStates`() {
        val screenshotDir = "screenshots"
        File(screenshotDir).mkdirs()
        
        // IDLE 状态
        captureSlotCardScreenshot(
            state = createState(SlotStatus.IDLE),
            filename = "$screenshotDir/slot_card_idle.png"
        )
        
        // RUNNING 状态
        captureSlotCardScreenshot(
            state = createState(SlotStatus.RUNNING),
            filename = "$screenshotDir/slot_card_running.png"
        )
        
        // PAUSED 状态
        captureSlotCardScreenshot(
            state = createState(SlotStatus.PAUSED),
            filename = "$screenshotDir/slot_card_paused.png"
        )
        
        // PASS 状态
        captureSlotCardScreenshot(
            state = createState(SlotStatus.PASS),
            filename = "$screenshotDir/slot_card_pass.png"
        )
        
        // FAIL 状态
        captureSlotCardScreenshot(
            state = createState(SlotStatus.FAIL),
            filename = "$screenshotDir/slot_card_fail.png"
        )
        
        // 验证截图已生成
        assert(File("$screenshotDir/slot_card_idle.png").exists()) { "IDLE 截图未生成" }
        assert(File("$screenshotDir/slot_card_running.png").exists()) { "RUNNING 截图未生成" }
        assert(File("$screenshotDir/slot_card_paused.png").exists()) { "PAUSED 截图未生成" }
        assert(File("$screenshotDir/slot_card_pass.png").exists()) { "PASS 截图未生成" }
        assert(File("$screenshotDir/slot_card_fail.png").exists()) { "FAIL 截图未生成" }
        
        println("All screenshots saved to: ${File(screenshotDir).absolutePath}")
    }
    
    private fun captureSlotCardScreenshot(state: SlotState, filename: String) {
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
        
        val image = rule.onRoot().captureToImage()
        ScreenshotUtil.saveScreenshot(image, File(filename).name, File(filename).parent)
    }
    
    private fun createState(status: SlotStatus): SlotState {
        return when (status) {
            SlotStatus.IDLE -> SlotState(
                id = 0,
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
            SlotStatus.RUNNING -> SlotState(
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
            )
            SlotStatus.PAUSED -> SlotState(
                id = 0,
                sn = "DEF987654321",
                status = SlotStatus.PAUSED,
                currentStep = 4,
                totalSteps = 7,
                currentStepName = "频谱仪配置",
                currentStepValue = null,
                variables = listOf(SlotVariable("V", "3.28V", true)),
                logs = listOf("Step 1-3: PASS", "Step 4: 暂停"),
                elapsedTime = "00:45",
                deviceInfo = null
            )
            SlotStatus.PASS -> SlotState(
                id = 0,
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
            )
            SlotStatus.FAIL -> SlotState(
                id = 0,
                sn = "JKL444555666",
                status = SlotStatus.FAIL,
                currentStep = 5,
                totalSteps = 7,
                currentStepName = null,
                currentStepValue = null,
                variables = listOf(
                    SlotVariable("V", "2.85V", false),
                    SlotVariable("I", "350mA", false)
                ),
                logs = listOf("Step 1-4: PASS", "Step 5: 电压检测 FAIL (2.85V < 3.0V)"),
                elapsedTime = "00:58",
                deviceInfo = null
            )
        }
    }
}
