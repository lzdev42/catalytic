package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.SlotVariable
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.ui.theme.MonoFontFamily

/**
 * 槽位卡片组件
 */
@Composable
fun SlotCard(
    state: SlotState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onViewReport: () -> Unit,
    onSetSn: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (state.status) {
        SlotStatus.RUNNING -> CatalyticTheme.extendedColors.success
        SlotStatus.PAUSED -> CatalyticTheme.extendedColors.warning
        SlotStatus.PASS -> CatalyticTheme.extendedColors.success
        SlotStatus.FAIL -> MaterialTheme.colorScheme.error
        SlotStatus.IDLE -> MaterialTheme.colorScheme.outlineVariant
    }
    
    Surface(
        modifier = modifier
            .testTag("slot_card_${state.id}")
            .fillMaxWidth()
            .height(320.dp),  // 固定高度
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Slot ID + Status Badge
            SlotHeader(state)
            
            // SN 输入/显示
            SnRow(
                slotId = state.id,
                sn = state.sn,
                isEditable = state.status == SlotStatus.IDLE,
                onSetSn = onSetSn
            )
            
            // Content based on status
            when (state.status) {
                SlotStatus.IDLE -> IdleContent(state)
                SlotStatus.RUNNING, SlotStatus.PAUSED -> RunningContent(state)
                SlotStatus.PASS, SlotStatus.FAIL -> ResultContent(state)
            }
            
            // Variables (if any)
            if (state.variables.isNotEmpty()) {
                VariablesRow(state.variables)
            }
            
            // Log Section (固定高度，可滚动)
            LogSection(
                logs = state.logs,
                modifier = Modifier.weight(1f)  // 占用剩余空间
            )
            
            // Actions
            ActionsRow(
                slotId = state.id,
                status = state.status,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onRestart = onRestart,
                onViewReport = onViewReport
            )
        }
    }
}

@Composable
private fun SlotHeader(state: SlotState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Slot ${state.id}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("slot_title_${state.id}")
        )
        
        StatusBadge(slotId = state.id, status = state.status)
    }
}

@Composable
private fun SnRow(
    slotId: Int,
    sn: String?,
    isEditable: Boolean,
    onSetSn: (String) -> Unit
) {
    if (isEditable) {
        // IDLE 状态：显示输入框
        var inputSn by remember { mutableStateOf(sn ?: "") }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputSn,
                onValueChange = { inputSn = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("slot_sn_input_$slotId"),
                placeholder = { Text("输入 SN", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            
            FilledTonalButton(
                onClick = { if (inputSn.isNotBlank()) onSetSn(inputSn) },
                enabled = inputSn.isNotBlank(),
                modifier = Modifier.height(48.dp).testTag("btn_set_sn_$slotId"),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "确认 SN",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        // 其他状态：只读显示
        Text(
            text = if (sn != null) "SN: $sn" else "未扫描SN",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            color = if (sn != null) 
                MaterialTheme.colorScheme.onSurfaceVariant 
            else 
                CatalyticTheme.extendedColors.onSurfaceMuted,
            modifier = Modifier.testTag("slot_sn_$slotId")
        )
    }
}

@Composable
private fun StatusBadge(slotId: Int, status: SlotStatus) {
    val (text, containerColor, contentColor) = when (status) {
        SlotStatus.IDLE -> Triple("空闲", 
            MaterialTheme.colorScheme.surfaceContainerHigh,
            CatalyticTheme.extendedColors.onSurfaceMuted)
        SlotStatus.RUNNING -> Triple("运行中",
            CatalyticTheme.extendedColors.successContainer,
            CatalyticTheme.extendedColors.success)
        SlotStatus.PAUSED -> Triple("已暂停",
            CatalyticTheme.extendedColors.warningContainer,
            CatalyticTheme.extendedColors.warning)
        SlotStatus.PASS -> Triple("通过",
            CatalyticTheme.extendedColors.successContainer,
            CatalyticTheme.extendedColors.success)
        SlotStatus.FAIL -> Triple("失败",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error)
    }
    
    Surface(
        modifier = Modifier.testTag("slot_badge_${slotId}"),
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
private fun IdleContent(state: SlotState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        Text(
            text = "等待扫描",
            style = MaterialTheme.typography.bodyMedium,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        if (state.deviceInfo != null) {
            Text(
                text = state.deviceInfo,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = CatalyticTheme.extendedColors.onSurfaceMuted
            )
        }
    }
}

@Composable
private fun RunningContent(state: SlotState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "步骤 ${state.currentStep}/${state.totalSteps}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${state.progressPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        val animatedProgress by animateFloatAsState(targetValue = state.progress)
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.small),
            color = if (state.status == SlotStatus.PAUSED) 
                CatalyticTheme.extendedColors.warning 
            else 
                CatalyticTheme.extendedColors.success
        )
        
        // Current Step
        if (state.currentStepName != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = CatalyticTheme.extendedColors.surfaceDim
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = state.currentStepName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.currentStepValue != null) {
                        Text(
                            text = state.currentStepValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (state.status == SlotStatus.PAUSED) {
                        Text(
                            text = "暂停",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CatalyticTheme.extendedColors.onSurfaceMuted,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultContent(state: SlotState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (state.status == SlotStatus.PASS) 
                Icons.Filled.CheckCircle 
            else 
                Icons.Filled.Cancel,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (state.status == SlotStatus.PASS) 
                CatalyticTheme.extendedColors.success 
            else 
                MaterialTheme.colorScheme.error
        )
        
        Column {
            Text(
                text = "${state.totalSteps} 通过 / 0 失败", // 简化
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.elapsedTime != null) {
                Text(
                    text = "耗时 ${state.elapsedTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun VariablesRow(variables: List<SlotVariable>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        variables.forEach { variable ->
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = CatalyticTheme.extendedColors.surfaceDim
            ) {
                Text(
                    text = "${variable.name}: ${variable.value}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = if (variable.isPassing) 
                        CatalyticTheme.extendedColors.success 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogSection(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = CatalyticTheme.extendedColors.onSurfaceMuted
            )
            Text(
                text = "检测日志 (${logs.size})",
                style = MaterialTheme.typography.labelSmall,
                color = CatalyticTheme.extendedColors.onSurfaceMuted
            )
        }
        
        // 可滚动日志区域
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                logs.forEach { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = when {
                            "PASS" in log -> CatalyticTheme.extendedColors.success
                            "暂停" in log || "WARN" in log -> CatalyticTheme.extendedColors.warning
                            "FAIL" in log -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsRow(
    slotId: Int,
    status: SlotStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onViewReport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (status) {
            SlotStatus.IDLE -> {
                ActionButton(
                    icon = Icons.Filled.PlayArrow,
                    onClick = onStart,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).testTag("btn_start_$slotId")
                )
            }
            SlotStatus.RUNNING -> {
                ActionButton(
                    icon = Icons.Filled.Pause,
                    onClick = onPause,
                    containerColor = CatalyticTheme.extendedColors.warningContainer,
                    contentColor = CatalyticTheme.extendedColors.warning,
                    modifier = Modifier.weight(1f).testTag("btn_pause_$slotId")
                )
                ActionButton(
                    icon = Icons.Filled.Stop,
                    onClick = onStop,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).testTag("btn_stop_$slotId")
                )
            }
            SlotStatus.PAUSED -> {
                ActionButton(
                    icon = Icons.Filled.PlayArrow,
                    onClick = onResume,
                    containerColor = CatalyticTheme.extendedColors.successContainer,
                    contentColor = CatalyticTheme.extendedColors.success,
                    modifier = Modifier.weight(1f).testTag("btn_resume_$slotId")
                )
                ActionButton(
                    icon = Icons.Filled.Stop,
                    onClick = onStop,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).testTag("btn_stop_$slotId")
                )
            }
            SlotStatus.PASS, SlotStatus.FAIL -> {
                ActionButton(
                    icon = Icons.Filled.Replay,
                    onClick = onRestart,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).testTag("btn_restart_$slotId")
                )
                ActionButton(
                    icon = Icons.Filled.Description,
                    onClick = onViewReport,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("btn_report_$slotId")
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}
