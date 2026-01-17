package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lzdev42.catalyticui.ui.settings.*
import io.github.lzdev42.catalyticui.viewmodel.SettingsViewModel

/**
 * 设置标签页枚举
 */
enum class SettingsTab(val title: String, val icon: String) {
    DEVICES("设备管理", "speed"),
    SLOTS("槽位绑定", "view_module"),
    FLOW("测试流程", "account_tree"),
    PLUGINS("插件管理", "extension"),
    CONNECTION("连接设置", "wifi"),
    APPEARANCE("外观", "palette"),
    ABOUT("关于", "info")
}

/**
 * 设置弹窗主组件
 * 
 * 职责：作为 View 层的容器，连接 ViewModel 和各个 Tab 组件
 */
@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: SettingsViewModel
) {
    if (!isOpen) return
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        var selectedTab by remember { mutableStateOf(SettingsTab.DEVICES) }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                // Header
                SettingsHeader(onDismiss = onDismiss)
                
                // Body: Nav + Content
                Row(modifier = Modifier.weight(1f)) {
                    // Left Navigation
                    SettingsNav(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                    
                    // Divider
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    // Content
                    SettingsContent(
                        selectedTab = selectedTab,
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                
                // Footer
                // Footer
                val isLoading by viewModel.isLoading
                SettingsFooter(
                    onDismiss = onDismiss,
                    onSave = {
                        viewModel.saveAllSettings()
                        // Don't dismiss immediately if we want to show loading.
                        // But per current design, we dismiss. 
                        // To show loading, we might need to keep dialog open.
                        // Let's keep it simple for now: Dismiss implies "Background Save".
                        // Use notify_user if we want to change this behavior.
                        // For Strict Transaction, usually we wait.
                        // But since I cannot easily change the flow to wait without callback, I'll update it to check isLoading.
                        onDismiss() 
                    },
                    enabled = !isLoading
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Text("✕", fontSize = 18.sp)
        }
    }
}

@Composable
private fun SettingsNav(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        SettingsTab.entries.forEach { tab ->
            SettingsNavItem(
                tab = tab,
                isSelected = tab == selectedTab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun SettingsNavItem(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tab.title,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsContent(
    selectedTab: SettingsTab,
    viewModel: SettingsViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        when (selectedTab) {
            SettingsTab.DEVICES -> {
                val deviceTypes by viewModel.deviceTypes
                val plugins by viewModel.plugins
                DeviceManagementTab(
                    deviceTypes = deviceTypes,
                    plugins = plugins,
                    onToggleExpand = { viewModel.toggleDeviceTypeExpand(it) },
                    onAddType = viewModel::addDeviceType,
                    onAddDevice = viewModel::addDeviceToType,
                    onAddCommand = viewModel::addCommandToType
                )
            }
            SettingsTab.SLOTS -> {
                val slotCount by viewModel.slotCount
                val deviceTypes by viewModel.deviceTypes
                val slotBindings by viewModel.slotBindings // Observe change to trigger recomposition
                SlotBindingTab(
                    slotCount = slotCount,
                    onSlotCountChange = { viewModel.updateSlotCount(it) },
                    deviceTypes = deviceTypes,
                    getBindings = { slot, type -> viewModel.getSlotBindingsForType(slot, type) },
                    onAddBinding = { slot, type, id -> viewModel.addSlotBinding(slot, type, id) },
                    onRemoveBinding = { slot, type, id -> viewModel.removeSlotBinding(slot, type, id) }
                )
            }
            SettingsTab.FLOW -> {
                val steps by viewModel.steps
                val deviceTypes by viewModel.deviceTypes
                FlowDefinitionTab(
                    steps = steps,
                    deviceTypes = deviceTypes,
                    onToggleStep = viewModel::toggleStepExpand,
                    onAddStep = viewModel::addStep,
                    onDeleteStep = viewModel::deleteStep,
                    onMoveUp = viewModel::moveStepUp,
                    onMoveDown = viewModel::moveStepDown,
                    onUpdateStep = viewModel::updateStep
                )
            }
            SettingsTab.PLUGINS -> {
                val plugins by viewModel.plugins
                PluginManagerTab(plugins = plugins)
            }
            SettingsTab.CONNECTION -> {
                val hostAddress by viewModel.hostAddress
                val hostPort by viewModel.hostPort
                val isConnected by viewModel.isConnected
                ConnectionTab(
                    hostAddress = hostAddress,
                    hostPort = hostPort,
                    isConnected = isConnected,
                    onHostAddressChange = viewModel::updateHostAddress,
                    onHostPortChange = viewModel::updateHostPort,
                    onTestConnection = viewModel::testConnection
                )
            }
            SettingsTab.APPEARANCE -> {
                val selectedLanguage by viewModel.selectedLanguage
                AppearanceTab(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    selectedLanguage = selectedLanguage,
                    onLanguageChange = viewModel::updateLanguage
                )
            }
            SettingsTab.ABOUT -> AboutTab()
        }
    }
}

@Composable
private fun SettingsFooter(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(onClick = onDismiss) {
            Text("取消")
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onSave,
            enabled = enabled
        ) {
            Text("保存")
        }
    }
}
