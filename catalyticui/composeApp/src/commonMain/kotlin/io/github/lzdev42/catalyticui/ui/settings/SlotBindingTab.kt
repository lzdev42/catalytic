package io.github.lzdev42.catalyticui.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.model.DeviceTypeUiState
import io.github.lzdev42.catalyticui.model.DeviceUiState
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme

/**
 * 槽位绑定标签页 (无状态 UI 组件 - 重构版)
 * 支持一个槽位绑定多个设备
 */
@Composable
fun SlotBindingTab(
    slotCount: Int,
    onSlotCountChange: (Int) -> Unit,
    deviceTypes: List<DeviceTypeUiState>,
    getBindings: (Int, String) -> List<String>,
    onAddBinding: (Int, String, String) -> Unit,
    onRemoveBinding: (Int, String, String) -> Unit
) {
    var showBindDialogForSlot by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = "槽位绑定",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "配置每个槽位使用的设备实例 (支持多设备绑定)",
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Slot Count
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("槽位数量", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = slotCount.toString(),
                onValueChange = { onSlotCountChange(it.toIntOrNull()?.coerceIn(1, 16) ?: 4) },
                modifier = Modifier.width(80.dp),
                singleLine = true
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Slot Bindings List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(slotCount) { index ->
                SlotBindingRow(
                    slotIndex = index,
                    deviceTypes = deviceTypes,
                    getBindings = { typeId -> getBindings(index, typeId) },
                    onAddClick = { showBindDialogForSlot = index },
                    onRemoveClick = { typeId, deviceId -> onRemoveBinding(index, typeId, deviceId) }
                )
            }
        }
    }
    
    // Binding Dialog
    showBindDialogForSlot?.let { slotIndex ->
        SlotBindingDialog(
            slotIndex = slotIndex,
            deviceTypes = deviceTypes,
            onDismiss = { showBindDialogForSlot = null },
            onConfirm = { typeId, deviceId ->
                onAddBinding(slotIndex, typeId, deviceId)
                showBindDialogForSlot = null
            }
        )
    }
}

@Composable
private fun SlotBindingRow(
    slotIndex: Int,
    deviceTypes: List<DeviceTypeUiState>,
    getBindings: (String) -> List<String>,
    onAddClick: () -> Unit,
    onRemoveClick: (String, String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slot Label
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Slot $slotIndex",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Bound Devices (FlowRow logic simplified with Row + weight for now, or custom layout)
            // Flatten bindings for display
            val boundDevices = deviceTypes.flatMap { type ->
                val deviceIds = getBindings(type.id)
                type.devices.filter { it.id in deviceIds }.map { device ->
                    type to device
                }
            }

            Row(
                modifier = Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (boundDevices.isEmpty()) {
                    Text(
                        text = "未绑定设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                } else {
                    boundDevices.forEach { (type, device) ->
                        BoundDeviceChip(type, device, onRemove = { onRemoveClick(type.id, device.id) })
                    }
                }
            }
            
            Spacer(Modifier.width(8.dp))
            
            // Add Button
            IconButton(onClick = onAddClick) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BoundDeviceChip(
    type: DeviceTypeUiState,
    device: DeviceUiState,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
        ) {
            Text(
                text = "${type.name}: ${device.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Text("×", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun SlotBindingDialog(
    slotIndex: Int,
    deviceTypes: List<DeviceTypeUiState>,
    onDismiss: () -> Unit,
    onConfirm: (typeId: String, deviceId: String) -> Unit
) {
    var selectedType by remember { mutableStateOf<DeviceTypeUiState?>(null) }
    var selectedDevice by remember { mutableStateOf<DeviceUiState?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定设备到 Slot $slotIndex") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Step 1: Select Type
                Column {
                    Text("设备类型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    
                    // Simple Type Selector Grid/Row
                    Row(
                        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        deviceTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { 
                                    selectedType = type 
                                    selectedDevice = null // Reset device when type changes
                                },
                                label = { Text(type.name) }
                            )
                        }
                    }
                }
                
                // Step 2: Select Device
                if (selectedType != null) {
                    Column {
                        Text("选择设备实例", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            selectedType!!.devices.forEach { device ->
                                val isSelected = selectedDevice == device
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDevice = device }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null // Handled by Surface click
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                            
                            if (selectedType!!.devices.isEmpty()) {
                                Text("该类型下无可用设备，请先去设备管理添加", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedType != null && selectedDevice != null) {
                        onConfirm(selectedType!!.id, selectedDevice!!.id)
                    }
                },
                enabled = selectedType != null && selectedDevice != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
