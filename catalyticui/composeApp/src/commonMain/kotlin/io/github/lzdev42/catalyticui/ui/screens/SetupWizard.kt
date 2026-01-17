package io.github.lzdev42.catalyticui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.i18n.LocalStrings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.launch

/**
 * First-launch setup wizard.
 * Forces user to select a working directory before proceeding.
 */
@Composable
fun SetupWizard(
    onDirectorySelected: (String) -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Text(
                text = "Catalytic",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = strings.selectWorkingDirectory,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Directory selection card
            OutlinedCard(
                modifier = Modifier.widthIn(max = 500.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = selectedPath ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val directory = FileKit.openDirectoryPicker(
                                    title = strings.selectWorkingDirectory
                                )
                                directory?.let {
                                    selectedPath = it.toString()
                                    showError = false
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(strings.selectWorkingDirectory)
                        }
                    }
                    
                    if (showError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Please select a directory",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Continue button
            Button(
                onClick = {
                    if (selectedPath != null) {
                        onDirectorySelected(selectedPath!!)
                    } else {
                        showError = true
                    }
                },
                enabled = selectedPath != null
            ) {
                Text("Continue")
            }
        }
    }
}
