package io.github.lzdev42.catalyticui.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme

/**
 * 连接设置标签页 (无状态 UI 组件)
 * 
 * 职责：纯渲染，不包含任何业务逻辑
 * 状态和逻辑由 SettingsViewModel 提供
 */
@Composable
fun ConnectionTab(
    hostAddress: String,
    hostPort: String,
    isConnected: Boolean,
    onHostAddressChange: (String) -> Unit,
    onHostPortChange: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = "连接设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "配置与 Host 服务的 gRPC 连接",
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Host Address
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Host 地址",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(100.dp)
            )
            OutlinedTextField(
                value = hostAddress,
                onValueChange = onHostAddressChange,
                modifier = Modifier.width(200.dp),
                singleLine = true,
                placeholder = { Text("localhost") }
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Host Port
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "端口",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(100.dp)
            )
            OutlinedTextField(
                value = hostPort,
                onValueChange = onHostPortChange,
                modifier = Modifier.width(100.dp),
                singleLine = true,
                placeholder = { Text("5000") }
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Connection Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "状态",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(100.dp)
            )
            Surface(
                color = if (isConnected) {
                    CatalyticTheme.extendedColors.successContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) {
                        CatalyticTheme.extendedColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Test Connection Button
        Button(onClick = onTestConnection) {
            Text("测试连接")
        }
    }
}
