package io.github.lzdev42.catalyticui.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.lzdev42.catalyticui.ui.screens.MainScreen
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.viewmodel.MainViewModel
import io.github.lzdev42.catalyticui.viewmodel.SettingsViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * MainScreen 集成测试
 * 
 * 测试范围:
 * - 槽位网格渲染
 * - 空状态显示
 * - Mock 数据加载
 */
class MainScreenTest {
    
    @get:Rule
    val rule = createComposeRule()
    
    // ============================================
    // 测试 11: 槽位网格显示
    // ============================================
    @Test
    fun `mainScreen_slotsGrid_displaysAllSlots`() {
        val mainViewModel = MainViewModel()
        val settingsViewModel = SettingsViewModel()
        var isDarkTheme by mutableStateOf(true)
        
        // 加载 Mock 数据
        mainViewModel.loadMockData()
        
        rule.setContent {
            CatalyticTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    viewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    isDarkTheme = isDarkTheme,
                    currentLanguage = "en",
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onLanguageChange = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证至少前 2 个槽位存在 (LazyGrid 在小视口可能不渲染全部)
        rule.onNodeWithTag("slot_card_0").assertExists()
        rule.onNodeWithTag("slot_card_1").assertExists()
        
        // 验证槽位标题
        rule.onNodeWithText("Slot 0").assertExists()
        rule.onNodeWithText("Slot 1").assertExists()
    }
    
    // ============================================
    // 测试 12: 空状态显示
    // ============================================
    @Test
    fun `mainScreen_emptyState_showsPlaceholder`() {
        val mainViewModel = MainViewModel()
        val settingsViewModel = SettingsViewModel()
        var isDarkTheme by mutableStateOf(true)
        
        // 不加载任何数据
        
        rule.setContent {
            CatalyticTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    viewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    isDarkTheme = isDarkTheme,
                    currentLanguage = "en",
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onLanguageChange = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证空状态文本
        rule.onNodeWithText("No slot data available").assertExists()
        
        // 验证加载 Mock 数据按钮
        rule.onNodeWithText("Load Mock Data (Dev)").assertExists()
    }
    
    // ============================================
    // 测试 13: 加载 Mock 数据按钮
    // ============================================
    @Test
    fun `mainScreen_loadMockButton_loadsSlots`() {
        val mainViewModel = MainViewModel()
        val settingsViewModel = SettingsViewModel()
        var isDarkTheme by mutableStateOf(true)
        
        rule.setContent {
            CatalyticTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    viewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    isDarkTheme = isDarkTheme,
                    currentLanguage = "en",
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onLanguageChange = {}
                )
            }
        }
        
        rule.waitForIdle()
        
        // 初始空状态
        rule.onNodeWithText("No slot data available").assertExists()
        
        // 点击加载按钮
        rule.onNodeWithText("Load Mock Data (Dev)").performClick()
        
        rule.waitForIdle()
        
        // 验证槽位已加载
        rule.onNodeWithTag("slot_card_0").assertExists()
        
        // 验证空状态文本消失
        rule.onNodeWithText("No slot data available").assertDoesNotExist()
    }
}
