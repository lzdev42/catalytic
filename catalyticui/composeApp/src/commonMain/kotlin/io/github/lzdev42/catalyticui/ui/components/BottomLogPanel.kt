package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.ui.theme.MonoFontFamily

/**
 * 底部可折叠日志面板
 */
@Composable
fun BottomLogPanel(
    logs: List<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "系统日志",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    TextButton(
                        onClick = { io.github.lzdev42.catalyticui.log.LogManager.clearSystemLogs() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("清空", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Text("✕", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            
            // Logs
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = when {
                                "✓" in log || "成功" in log -> CatalyticTheme.extendedColors.success
                                "失败" in log || "错误" in log || "Error" in log -> MaterialTheme.colorScheme.error
                                "警告" in log || "Warning" in log -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
