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
 * 外观设置标签页 (无状态 UI 组件)
 */
@Composable
fun AppearanceTab(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = "外观",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "自定义界面外观",
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Theme Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "深色主题",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "切换深色/浅色模式",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted
                )
            }
            Switch(
                checked = isDarkTheme,
                onCheckedChange = { onToggleTheme() }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        
        // Language Selection
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "语言",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "界面显示语言",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted
                )
            }
            
            Row {
                FilterChip(
                    selected = selectedLanguage == "zh-CN",
                    onClick = { onLanguageChange("zh-CN") },
                    label = { Text("中文") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = selectedLanguage == "en-US",
                    onClick = { onLanguageChange("en-US") },
                    label = { Text("English") }
                )
            }
        }
    }
}
