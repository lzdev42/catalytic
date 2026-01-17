package io.github.lzdev42.catalyticui.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.ui.components.AppHeader
import io.github.lzdev42.catalyticui.ui.components.SlotCard
import io.github.lzdev42.catalyticui.ui.components.SettingsDialog
import io.github.lzdev42.catalyticui.ui.components.StatusBar
import io.github.lzdev42.catalyticui.ui.components.BottomLogPanel
import io.github.lzdev42.catalyticui.ui.components.SimpleAlertDialog
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.SlotVariable
import io.github.lzdev42.catalyticui.model.TestStepUiState
import io.github.lzdev42.catalyticui.model.SystemStatusUiState
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.ui.theme.MonoFontFamily
import io.github.lzdev42.catalyticui.util.getCurrentTimeString
import io.github.lzdev42.catalyticui.viewmodel.MainViewModel
import io.github.lzdev42.catalyticui.viewmodel.SettingsViewModel

/**
 * 主界面布局
 * 三栏结构：[测试项侧边栏] | [槽位网格] | [系统日志]
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    isDarkTheme: Boolean,
    currentLanguage: String,
    onToggleTheme: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    var showSteps by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showBottomLogs by remember { mutableStateOf(false) }
    
    // Collect states
    val testSteps by viewModel.testSteps.collectAsState()
    val systemLogs by viewModel.systemLogs.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    
    // Current time
    var currentTime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTimeString()
            kotlinx.coroutines.delay(1000)
        }
    }
    
    // Sync config state for validation
    val deviceTypes by settingsViewModel.deviceTypes
    val steps by settingsViewModel.steps
    LaunchedEffect(deviceTypes, steps) {
        viewModel.updateConfigState(
            hasDevices = deviceTypes.isNotEmpty(),
            hasSteps = steps.isNotEmpty()
        )
    }
    
    val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current
    
    // Sync i18n strings to ViewModel for validation messages
    LaunchedEffect(strings) {
        viewModel.updateStrings(strings)
    }
    
    // Validation alert dialog
    SimpleAlertDialog(
        visible = alertState.visible,
        title = alertState.title,
        message = alertState.message,
        onDismiss = { viewModel.dismissAlert() }
    )
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        AppHeader(
            flowName = systemStatus.flowName.ifEmpty { "-" },
            isDarkTheme = isDarkTheme,
            currentLanguage = currentLanguage,
            onStartAll = { viewModel.startAll() },
            onStop = { viewModel.stopAll() },
            onToggleSteps = { showSteps = !showSteps },
            onToggleTheme = onToggleTheme,
            onOpenSettings = { showSettings = true },
            onLanguageChange = onLanguageChange
        )
        
        // Main Content
        Row(modifier = Modifier.weight(1f)) {
            // Left: Steps Sidebar
            AnimatedVisibility(
                visible = showSteps,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it }
            ) {
                StepsSidebar(steps = testSteps)
            }
            
            // Center: Slots Grid
            SlotsGrid(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
            
            // Right: Operation Log Panel (for future use, currently empty)
            // SystemLogPanel(logs = systemLogs)
        }
        
        // Bottom: Collapsible System Log Panel
        AnimatedVisibility(visible = showBottomLogs) {
            BottomLogPanel(
                logs = systemLogs,
                onClose = { showBottomLogs = false }
            )
        }
        
        // Status Bar with toggle button
        StatusBar(
            systemInfo = systemStatus.systemInfo.ifEmpty { "-" },
            version = systemStatus.version.ifEmpty { "Catalytic" },
            flowName = systemStatus.flowName.ifEmpty { "-" },
            passCount = systemStatus.passCount,
            failCount = systemStatus.failCount,
            currentTime = currentTime,
            isConnected = isConnected,
            showLogs = showBottomLogs,
            onToggleLogs = { showBottomLogs = !showBottomLogs }
        )
    }
    
    // Settings Dialog
    SettingsDialog(
        isOpen = showSettings,
        onDismiss = { showSettings = false },
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        viewModel = settingsViewModel
    )
}

/**
 * Test steps sidebar (stateless)
 */
@Composable
private fun StepsSidebar(steps: List<TestStepUiState>) {
    val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current
    
    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (steps.isEmpty()) strings.testSteps else "${strings.testSteps} (${steps.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            
            // Steps List or Empty State
            if (steps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noTestSteps,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(steps) { step ->
                        StepItem(step.index, step.name, step.description)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(number: Int, name: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Number Badge
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = "$number",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = CatalyticTheme.extendedColors.onSurfaceMuted
            )
        }
    }
}

/**
 * System log panel (stateless)
 */
@Composable
private fun SystemLogPanel(logs: List<String>) {
    val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current
    
    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.systemLogs,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            
            // Logs List or Empty State
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noLogs,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = when {
                                "PASS" in log -> CatalyticTheme.extendedColors.success
                                "FAIL" in log -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 槽位网格
 */
@Composable
private fun SlotsGrid(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val strings = io.github.lzdev42.catalyticui.i18n.LocalStrings.current
    val slots by viewModel.slots.collectAsState()
    
    if (slots.isEmpty()) {
        // Empty State - waiting for Host connection
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = strings.noSlotsData,
                    style = MaterialTheme.typography.headlineSmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted
                )
                Text(
                    text = strings.connectHostPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(slots, key = { it.id }) { slot ->
                SlotCard(
                    state = slot,
                    onStart = { viewModel.startTest(slot.id) },
                    onPause = { viewModel.pauseTest(slot.id) },
                    onResume = { viewModel.resumeTest(slot.id) },
                    onStop = { viewModel.stopTest(slot.id) },
                    onRestart = { viewModel.startTest(slot.id) },
                    onViewReport = { /* TODO */ },
                    onSetSn = { sn -> viewModel.setSlotSn(slot.id, sn) }
                )
            }
        }
    }
}

