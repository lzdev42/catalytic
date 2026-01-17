package io.github.lzdev42.catalyticui.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.lzdev42.catalyticui.model.DeviceTypeUiState
import io.github.lzdev42.catalyticui.ui.settings.DeviceManagementTab
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 设置页面 UI 测试
 * 
 * 测试范围:
 * - DEVICES Tab: 添加设备类型弹窗、列表显示
 * - SLOTS Tab: 槽位数设置、设备绑定
 * - FLOW Tab: 测试步骤管理
 */
class SettingsDialogTest {
    
    @get:Rule
    val rule = createComposeRule()
    
    // ============================================
    // DEVICES Tab Tests
    // ============================================
    
    @Test
    fun `deviceManagementTab_emptyState_displaysAddButton`() {
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = emptyList(),
                    onToggleExpand = {},
                    onAddType = { _, _ -> },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证空状态提示存在
        rule.onNodeWithText("暂无设备类型").assertExists()
        
        // 验证添加按钮存在
        rule.onNodeWithText("+ 添加设备类型").assertExists()
    }
    
    @Test
    fun `deviceManagementTab_clickAddButton_opensDialog`() {
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = emptyList(),
                    onToggleExpand = {},
                    onAddType = { _, _ -> },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 点击添加按钮
        rule.onNodeWithText("+ 添加设备类型").performClick()
        
        rule.waitForIdle()
        
        // 验证弹窗打开
        rule.onNodeWithText("添加设备类型").assertExists()
        rule.onNodeWithText("类型名称").assertExists()
        rule.onNodeWithText("传输协议").assertExists()
    }
    
    @Test
    fun `deviceManagementTab_addDeviceTypeDialog_inputDisabledWhenEmpty`() {
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = emptyList(),
                    onToggleExpand = {},
                    onAddType = { _, _ -> },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 打开弹窗
        rule.onNodeWithText("+ 添加设备类型").performClick()
        rule.waitForIdle()
        
        // 验证添加按钮默认禁用（因为名称为空）
        rule.onNodeWithText("添加").assertIsNotEnabled()
    }
    
    @Test
    fun `deviceManagementTab_addDeviceTypeDialog_inputEnablesButton`() {
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = emptyList(),
                    onToggleExpand = {},
                    onAddType = { _, _ -> },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 打开弹窗
        rule.onNodeWithText("+ 添加设备类型").performClick()
        rule.waitForIdle()
        
        // 输入名称
        rule.onNode(hasSetTextAction()).performTextInput("DUT")
        rule.waitForIdle()
        
        // 验证添加按钮启用
        rule.onNodeWithText("添加").assertIsEnabled()
    }
    
    @Test
    fun `deviceManagementTab_addDeviceType_callsCallback`() {
        var addedName: String? = null
        var addedTransport: String? = null
        
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = emptyList(),
                    onToggleExpand = {},
                    onAddType = { name, transport ->
                        addedName = name
                        addedTransport = transport
                    },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 打开弹窗
        rule.onNodeWithText("+ 添加设备类型").performClick()
        rule.waitForIdle()
        
        // 输入名称
        rule.onNode(hasSetTextAction()).performTextInput("DUT")
        rule.waitForIdle()
        
        // 选择TCP传输协议
        rule.onNodeWithText("TCP/IP").performClick()
        rule.waitForIdle()
        
        // 点击添加
        rule.onNodeWithText("添加").performClick()
        rule.waitForIdle()
        
        // 验证回调被调用
        assertEquals("DUT", addedName)
        assertEquals("tcp", addedTransport)
    }
    
    @Test
    fun `deviceManagementTab_withDeviceTypes_displaysList`() {
        val deviceTypes = listOf(
            DeviceTypeUiState(
                id = "dut",
                name = "被测设备",
                icon = "usb",
                transport = "serial",
                devices = emptyList()
            ),
            DeviceTypeUiState(
                id = "dmm",
                name = "万用表",
                icon = "ethernet",
                transport = "tcp",
                devices = emptyList()
            )
        )
        
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = deviceTypes,
                    onToggleExpand = {},
                    onAddType = { _, _ -> },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 验证设备类型显示在列表中
        rule.onNodeWithText("被测设备").assertExists()
        rule.onNodeWithText("万用表").assertExists()
        
        // 验证设备数量显示
        rule.onAllNodesWithText("0 台").assertCountEquals(2)
    }
    
    @Test
    fun `deviceManagementTab_clickDeviceType_togglesExpand`() {
        var expandedId: String? = null
        
        val deviceTypes = listOf(
            DeviceTypeUiState(
                id = "dut",
                name = "被测设备",
                icon = "usb",
                transport = "serial",
                devices = emptyList()
            )
        )
        
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = deviceTypes,
                    onToggleExpand = { expandedId = it },
                    onAddType = { _, _ -> },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 点击设备类型
        rule.onNodeWithText("被测设备").performClick()
        rule.waitForIdle()
        
        // 验证回调被调用
        assertEquals("dut", expandedId)
    }
    
    @Test
    fun `deviceManagementTab_cancelDialog_closesWithoutCallback`() {
        var callbackCalled = false
        
        rule.setContent {
            CatalyticTheme {
                DeviceManagementTab(
                    deviceTypes = emptyList(),
                    onToggleExpand = {},
                    onAddType = { _, _ -> callbackCalled = true },
                    onAddDevice = { _, _, _ -> },
                    onAddCommand = { _, _ -> }
                )
            }
        }
        
        rule.waitForIdle()
        
        // 打开弹窗
        rule.onNodeWithText("+ 添加设备类型").performClick()
        rule.waitForIdle()
        
        // 输入内容
        rule.onNode(hasSetTextAction()).performTextInput("Test")
        rule.waitForIdle()
        
        // 点击取消
        rule.onNodeWithText("取消").performClick()
        rule.waitForIdle()
        
        // 验证弹窗关闭
        rule.onNodeWithText("添加设备类型").assertDoesNotExist()
        
        // 验证回调没有被调用
        assertTrue(!callbackCalled)
    }
}
