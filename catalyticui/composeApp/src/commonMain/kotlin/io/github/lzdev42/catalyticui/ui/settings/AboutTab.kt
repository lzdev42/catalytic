package io.github.lzdev42.catalyticui.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme

/**
 * 关于标签页
 */
@Composable
fun AboutTab() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo / Title
        Text(
            text = "Catalytic",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "低代码电测自动化平台",
            style = MaterialTheme.typography.bodyMedium,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Version Info
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InfoRow("版本", "v1.2.0")
                InfoRow("构建", "2026.01.04")
                InfoRow("Compose", "1.7.0")
                InfoRow("Kotlin", "2.1.0")
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "© 2026 Aspect. All rights reserved.",
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
