package io.github.lzdev42.catalyticui.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.model.DeviceTypeUiState
import io.github.lzdev42.catalyticui.model.DeviceUiState
import io.github.lzdev42.catalyticui.model.CommandUiState
import io.github.lzdev42.catalyticui.model.PluginInfo
import io.github.lzdev42.catalyticui.i18n.LocalStrings

/**
 * 设备管理标签页 (无状态 UI 组件)
 * 
 * 职责：纯渲染，不包含任何业务逻辑
 * 状态和逻辑由 SettingsViewModel 提供
 */
@Composable
fun DeviceManagementTab(
    deviceTypes: List<DeviceTypeUiState>,
    plugins: List<PluginInfo>,  // 新增：可用插件列表
    onToggleExpand: (String) -> Unit,
    onAddType: (name: String, pluginId: String) -> Unit,
    onAddDevice: (typeId: String, name: String, address: String) -> Unit,
    onAddCommand: (typeId: String, command: CommandUiState) -> Unit
) {
    val strings = LocalStrings.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addDeviceToTypeId by remember { mutableStateOf<String?>(null) }
    var addCommandToTypeId by remember { mutableStateOf<String?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = strings.deviceManagementTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = strings.deviceManagementSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Device Types List or Empty State
        if (deviceTypes.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.deviceNoTypes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = strings.deviceClickToAdd,
                        style = MaterialTheme.typography.bodySmall,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(deviceTypes) { type ->
                    DeviceTypeGroup(
                        type = type,
                        onToggleExpand = { onToggleExpand(type.id) },
                        onAddDevice = { addDeviceToTypeId = type.id },
                        onAddCommand = { addCommandToTypeId = type.id }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Add Type Button
        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(strings.deviceAddTypeButton)
        }
    }
    
    // Add Device Type Dialog
    if (showAddDialog) {
        AddDeviceTypeDialog(
            plugins = plugins.filter { it.isCommunicator },  // 只显示通讯驱动插件
            onDismiss = { showAddDialog = false },
            onConfirm = { name, pluginId ->
                onAddType(name, pluginId)
                showAddDialog = false
            }
        )
    }
    
    // Add Device Dialog
    addDeviceToTypeId?.let { typeId ->
        AddDeviceDialog(
            typeName = deviceTypes.find { it.id == typeId }?.name ?: typeId,
            onDismiss = { addDeviceToTypeId = null },
            onConfirm = { name, address ->
                onAddDevice(typeId, name, address)
                addDeviceToTypeId = null
            }
        )
    }

    // Add Command Dialog
    addCommandToTypeId?.let { typeId ->
        AddCommandDialog(
            typeName = deviceTypes.find { it.id == typeId }?.name ?: typeId,
            onDismiss = { addCommandToTypeId = null },
            onConfirm = { cmd ->
                onAddCommand(typeId, cmd)
                addCommandToTypeId = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceTypeDialog(
    plugins: List<PluginInfo>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, pluginId: String) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var selectedPluginId by remember { mutableStateOf(plugins.firstOrNull()?.id ?: "") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deviceAddTypeDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.deviceTypeName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(strings.devicePlugin, style = MaterialTheme.typography.labelMedium)
                
                // Plugin Dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = plugins.find { it.id == selectedPluginId }?.name ?: strings.deviceSelectPlugin,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        plugins.forEach { plugin ->
                            DropdownMenuItem(
                                text = { Text(plugin.name) },
                                onClick = {
                                    selectedPluginId = plugin.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedPluginId) },
                enabled = name.isNotBlank() && selectedPluginId.isNotBlank()
            ) {
                Text(strings.commonAdd)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.commonCancel)
            }
        }
    )
}

@Composable
private fun AddDeviceDialog(
    typeName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deviceAddDeviceTo.replace("%s", typeName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.deviceName) },
                    placeholder = { Text(strings.devicePlaceholderName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(strings.deviceAddress) },
                    placeholder = { Text(strings.devicePlaceholderAddress) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, address) },
                enabled = name.isNotBlank() && address.isNotBlank()
            ) {
                Text(strings.commonAdd)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.commonCancel)
            }
        }
    )
}

@Composable
private fun AddCommandDialog(
    typeName: String,
    onDismiss: () -> Unit,
    onConfirm: (CommandUiState) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var parseRule by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deviceAddCommandTo.replace("%s", typeName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.deviceCommandName) },
                    placeholder = { Text(strings.devicePlaceholderCommandName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text(strings.deviceCommandPayload) },
                    placeholder = { Text(strings.devicePlaceholderPayload) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = parseRule,
                    onValueChange = { parseRule = it },
                    label = { Text(strings.deviceCommandParseRule) },
                    placeholder = { Text(strings.devicePlaceholderParseRule) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val randomId = "cmd_${(1..10000).random()}"
                    onConfirm(CommandUiState(
                        id = randomId,
                        name = name,
                        payload = payload,
                        parseRule = parseRule.ifBlank { null }
                    )) 
                },
                enabled = name.isNotBlank() && payload.isNotBlank()
            ) {
                Text(strings.commonAdd)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.commonCancel)
            }
        }
    )
}

@Composable
private fun DeviceTypeGroup(
    type: DeviceTypeUiState,
    onToggleExpand: () -> Unit,
    onAddDevice: () -> Unit,
    onAddCommand: () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand Icon
                Text(
                    text = "▶",
                    fontSize = 12.sp,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted,
                    modifier = Modifier.rotate(if (type.isExpanded) 90f else 0f)
                )
                
                Spacer(Modifier.width(8.dp))
                
                // Type Name
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                
                // Device Count
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = CatalyticTheme.extendedColors.surfaceDim
                ) {
                    Text(
                        text = strings.deviceCountSuffix.replace("%d", type.devices.size.toString()),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                
                Spacer(Modifier.width(8.dp))
                
                // Actions (Add Device & Edit Type)
                IconButton(onClick = onAddDevice, modifier = Modifier.size(28.dp)) {
                    Text("+", fontSize = 16.sp)
                }
            }
            
            // Body (Devices & Commands)
            AnimatedVisibility(visible = type.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Devices Section
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.deviceInstances.replace("%d", type.devices.size.toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        if (type.devices.isEmpty()) {
                            Text(
                                text = strings.deviceNoInstances,
                                style = MaterialTheme.typography.bodySmall,
                                color = CatalyticTheme.extendedColors.onSurfaceMuted,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            type.devices.forEach { device ->
                                DeviceItem(device = device, showStatus = type.id == "dut")
                            }
                        }
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // Commands Section
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.deviceCommands.replace("%d", type.commands.size.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = onAddCommand, modifier = Modifier.size(24.dp)) {
                                Text("+", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        if (type.commands.isEmpty()) {
                            Text(
                                text = strings.deviceNoCommands,
                                style = MaterialTheme.typography.bodySmall,
                                color = CatalyticTheme.extendedColors.onSurfaceMuted,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            type.commands.forEach { cmd ->
                                CommandItem(cmd)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: DeviceUiState,
    showStatus: Boolean = false
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = CatalyticTheme.extendedColors.onSurfaceMuted
            )
        }
        
        if (showStatus) {
            // Online/Offline Status
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (device.isOnline) {
                    CatalyticTheme.extendedColors.successContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Text(
                    text = if (device.isOnline) strings.deviceOnline else strings.deviceOffline,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (device.isOnline) {
                        CatalyticTheme.extendedColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        } else {
            // Action Buttons
            TextButton(onClick = { /* Test */ }) {
                Text(strings.deviceTest, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { /* Edit */ }) {
                Text(strings.deviceEdit, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CommandItem(cmd: CommandUiState) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cmd.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = cmd.payload,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = CatalyticTheme.extendedColors.onSurfaceMuted
            )
        }
        
        if (cmd.parseRule != null) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = strings.deviceParsePrefix.replace("%s", cmd.parseRule),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
