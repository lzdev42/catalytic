package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.model.ConnectionStatus
import io.github.lzdev42.catalyticui.model.DeviceConnectionState
import io.github.lzdev42.catalyticui.model.DeviceUiState
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme

@Composable
fun DeviceConnectionPanel(
    isConnected: Boolean, // Host connected?
    deviceConnections: List<DeviceConnectionState>,
    allDevices: List<DeviceUiState>,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.panelDeviceConnections,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Summary Badge
                    val connectedCount = deviceConnections.count { it.status == ConnectionStatus.CONNECTED }
                    val totalCount = allDevices.size
                    Badge(
                        containerColor = if (connectedCount == totalCount && totalCount > 0) 
                            CatalyticTheme.extendedColors.successContainer 
                        else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = strings.panelConnectedFormat
                                .replace("%d", "$connectedCount", ignoreCase = true)
                                .replaceFirst("$connectedCount", "$connectedCount") // A bit hacky, better to assume order: connected / total
                                .let { 
                                    // Fallback to simpler format if regex fails or simply construct string manually for now
                                    // Given KMP limitations on String.format, manual construction is safer if we want full control
                                    // BUT, to respect the localization string, let's just do a manual replace of placeholders
                                    var result = strings.panelConnectedFormat
                                    result = result.replaceFirst("%d", "$connectedCount")
                                    result = result.replaceFirst("%d", "$totalCount")
                                    result
                                },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = if (connectedCount == totalCount && totalCount > 0)
                            MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.panelConnectHostFirst, color = MaterialTheme.colorScheme.error)
                    }
                } else if (allDevices.isEmpty()) {
                     Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.panelNoDevicesConfigured, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        allDevices.forEach { device ->
                            val connectionState = deviceConnections.find { it.deviceId == device.id }
                            DeviceConnectionRow(
                                device = device,
                                connectionState = connectionState,
                                onConnect = { onConnect(device.id) },
                                onDisconnect = { onDisconnect(device.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceConnectionRow(
    device: DeviceUiState,
    connectionState: DeviceConnectionState?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current
    val status = connectionState?.status ?: ConnectionStatus.DISCONNECTED
    val errorMessage = connectionState?.errorMessage ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Device Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            StatusIndicator(status)
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                 Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> strings.connectionConnected
                        ConnectionStatus.CONNECTING -> strings.statusConnecting
                        ConnectionStatus.DISCONNECTED -> strings.connectionDisconnected
                        ConnectionStatus.ERROR -> strings.statusError
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = getStatusColor(status)
                )
                if (status == ConnectionStatus.ERROR && errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }
        }
        
        // Actions
        when (status) {
            ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.actionConnect)
                }
            }
            ConnectionStatus.CONNECTING -> {
                 CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            ConnectionStatus.CONNECTED -> {
                OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PowerOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.actionDisconnect)
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: ConnectionStatus) {
    val color = getStatusColor(status)
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
private fun getStatusColor(status: ConnectionStatus): Color {
    return when (status) {
        ConnectionStatus.CONNECTED -> CatalyticTheme.extendedColors.success
        ConnectionStatus.CONNECTING -> CatalyticTheme.extendedColors.warning
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
}
