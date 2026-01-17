package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.ui.theme.MonoFontFamily

/**
 * 底部状态栏
 */
@Composable
fun StatusBar(
    systemInfo: String,
    version: String,
    flowName: String,
    passCount: Int,
    failCount: Int,
    currentTime: String,
    isConnected: Boolean,
    showLogs: Boolean = false,
    onToggleLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: System Info with connection indicator
        // Left: System Info with connection indicator
        val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isConnected) strings.connectionConnected else strings.connectionDisconnected,
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected) 
                    CatalyticTheme.extendedColors.success 
                else 
                    MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = systemInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        StatusItem(text = version)
        
        Spacer(Modifier.width(16.dp))
        
        StatusItem(text = "${io.github.lzdev42.catalyticui.i18n.LocalStrings.current.statusFlowLabel}: $flowName")
        
        Spacer(Modifier.weight(1f))
        
        // Log toggle button
        Text(
            text = if (showLogs) io.github.lzdev42.catalyticui.i18n.LocalStrings.current.statusHideLogs else io.github.lzdev42.catalyticui.i18n.LocalStrings.current.statusShowLogs,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onToggleLogs)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        Spacer(Modifier.width(16.dp))
        
        // Right: Stats + Time
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${io.github.lzdev42.catalyticui.i18n.LocalStrings.current.statusTodayStats}: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$passCount",
                style = MaterialTheme.typography.bodySmall,
                color = CatalyticTheme.extendedColors.success
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$failCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            
            val rate = if (passCount + failCount > 0) {
                (passCount * 100.0 / (passCount + failCount))
            } else 0.0
            
            Text(
                text = " (${((rate * 10).toInt() / 10.0)}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(Modifier.width(20.dp))
        
        Text(
            text = currentTime,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusItem(
    text: String,
    showDot: Boolean = false,
    dotColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
