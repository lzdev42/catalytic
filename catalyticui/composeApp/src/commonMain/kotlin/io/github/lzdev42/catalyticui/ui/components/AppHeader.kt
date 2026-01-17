package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.i18n.LocalStrings
import io.github.lzdev42.catalyticui.i18n.StringsLoader
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme

/**
 * Top application header bar
 */
@Composable
fun AppHeader(
    flowName: String,
    isDarkTheme: Boolean,
    currentLanguage: String,
    onStartAll: () -> Unit,
    onStop: () -> Unit,
    onToggleSteps: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var languageMenuExpanded by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Logo + Flow Name
            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.width(16.dp))
            
            VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(Modifier.width(16.dp))
            
            Text(
                text = flowName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.weight(1f))
            
            // Center: Actions
            Button(
                onClick = onStartAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CatalyticTheme.extendedColors.success
                )
            ) {
                Text(strings.headerStartAll)
            }
            
            Spacer(Modifier.width(8.dp))
            
            OutlinedButton(onClick = onStop) {
                Text(strings.headerStop)
            }
            
            Spacer(Modifier.width(16.dp))
            
            IconButton(onClick = onToggleSteps) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = strings.testSteps)
            }
            
            Spacer(Modifier.width(8.dp))
            
            // Language Selector
            Box {
                IconButton(onClick = { languageMenuExpanded = true }) {
                    Icon(Icons.Default.Language, contentDescription = strings.language)
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    StringsLoader.supportedLanguages().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onLanguageChange(option.code)
                                languageMenuExpanded = false
                            },
                            leadingIcon = {
                                if (option.code == currentLanguage) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
            
            // Right: Utils
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = null
                )
            }
            
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = strings.settings)
            }
        }
    }
}
