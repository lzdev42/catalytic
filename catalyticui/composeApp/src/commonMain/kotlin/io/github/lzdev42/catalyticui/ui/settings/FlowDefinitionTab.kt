package io.github.lzdev42.catalyticui.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.model.*

/**
 * 测试流程定义标签页 (无状态 UI 组件)
 * 
 * 职责：纯渲染，不包含任何业务逻辑
 * 状态和逻辑由 SettingsViewModel 提供
 */
@Composable
fun FlowDefinitionTab(
    steps: List<StepUiState>,
    deviceTypes: List<DeviceTypeUiState>,
    onToggleStep: (Int) -> Unit,
    onAddStep: () -> Unit,
    onDeleteStep: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onUpdateStep: (Int, (StepUiState) -> StepUiState) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = "测试流程",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "定义测试步骤和逻辑",
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Steps List
        if (steps.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无测试步骤",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "点击下方按钮添加步骤",
                        style = MaterialTheme.typography.bodySmall,
                        color = CatalyticTheme.extendedColors.onSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(steps) { idx, step ->
                    // 收集前置步骤的所有变量名
                    val availableVariables = steps.take(idx).flatMap { it.variables.keys }
                    
                    StepItem(
                        step = step,
                        stepIndex = idx + 1,
                        deviceTypes = deviceTypes,
                        availableVariables = availableVariables,
                        onToggle = { onToggleStep(step.stepId) },
                        onDelete = { onDeleteStep(step.stepId) },
                        onMoveUp = { onMoveUp(step.stepId) },
                        onMoveDown = { onMoveDown(step.stepId) },
                        onUpdate = { transform -> onUpdateStep(step.stepId, transform) }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = onAddStep,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("+ 添加步骤")
        }
    }
}

@Composable
private fun StepItem(
    step: StepUiState,
    stepIndex: Int,
    deviceTypes: List<DeviceTypeUiState>,
    availableVariables: List<String>,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUpdate: ((StepUiState) -> StepUiState) -> Unit
) {
    val modeLabel = when (step.executionMode) {
        ExecutionMode.ENGINE_CONTROLLED -> "Engine"
        ExecutionMode.HOST_CONTROLLED -> "Host"
        ExecutionMode.CALCULATION -> "计算"
    }
    val modeColor = when (step.executionMode) {
        ExecutionMode.ENGINE_CONTROLLED -> MaterialTheme.colorScheme.primary
        ExecutionMode.HOST_CONTROLLED -> MaterialTheme.colorScheme.tertiary
        ExecutionMode.CALCULATION -> MaterialTheme.colorScheme.secondary
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Index Badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$stepIndex",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Spacer(Modifier.width(10.dp))
                
                // Title
                Text(
                    text = step.stepName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                // Mode Badge
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = modeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = modeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                
                Spacer(Modifier.width(8.dp))
                
                // Actions
                IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                    Text("↑", fontSize = 14.sp)
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                    Text("↓", fontSize = 14.sp)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Text("🗑", fontSize = 12.sp)
                }
            }
            
            // Body (Expanded Form)
            AnimatedVisibility(visible = step.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Step Name
                    LabeledRow("Name") {
                        OutlinedTextField(
                            value = step.stepName,
                            onValueChange = { newName ->
                                onUpdate { it.copy(stepName = newName) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    // Mode Selector
                    LabeledRow("Mode") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = step.executionMode == ExecutionMode.ENGINE_CONTROLLED,
                                onClick = {
                                    onUpdate { it.copy(
                                        executionMode = ExecutionMode.ENGINE_CONTROLLED,
                                        engineTask = it.engineTask ?: EngineTaskUiState()
                                    )}
                                },
                                label = { Text("Engine") }
                            )
                            FilterChip(
                                selected = step.executionMode == ExecutionMode.HOST_CONTROLLED,
                                onClick = {
                                    onUpdate { it.copy(
                                        executionMode = ExecutionMode.HOST_CONTROLLED,
                                        hostTask = it.hostTask ?: HostTaskUiState(taskName = "")
                                    )}
                                },
                                label = { Text("Host") }
                            )
                            FilterChip(
                                selected = step.executionMode == ExecutionMode.CALCULATION,
                                onClick = {
                                    onUpdate { it.copy(
                                        executionMode = ExecutionMode.CALCULATION,
                                        engineTask = null,
                                        hostTask = null
                                    )}
                                },
                                label = { Text("计算") }
                            )
                        }
                    }
                    
                    // Engine Mode Configuration
                    if (step.executionMode == ExecutionMode.ENGINE_CONTROLLED && step.engineTask != null) {
                        EngineTaskSection(
                            engineTask = step.engineTask,
                            deviceTypes = deviceTypes,
                            onUpdate = { newTask ->
                                onUpdate { it.copy(engineTask = newTask) }
                            }
                        )
                    }
                    
                    // Host Mode Configuration
                    if (step.executionMode == ExecutionMode.HOST_CONTROLLED && step.hostTask != null) {
                        HostTaskSection(
                            hostTask = step.hostTask,
                            onUpdate = { newTask ->
                                onUpdate { it.copy(hostTask = newTask) }
                            }
                        )
                    }
                    
                    // Calculation Mode Configuration
                    if (step.executionMode == ExecutionMode.CALCULATION) {
                        CalculationSection(
                            availableVariables = availableVariables,
                            selectedVariables = step.inputVariables,
                            onSelectionChange = { newSelection ->
                                onUpdate { it.copy(inputVariables = newSelection) }
                            }
                        )
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // Variables Section
                    VariablesSection(
                        variables = step.variables,
                        onUpdate = { newVars ->
                            onUpdate { it.copy(variables = newVars) }
                        }
                    )
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // Check Rule Section
                    CheckRuleSection(
                        checkRule = step.checkRule,
                        availableVariables = step.variables.keys.toList(),
                        onUpdate = { newRule ->
                            onUpdate { it.copy(checkRule = newRule) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineTaskSection(
    engineTask: EngineTaskUiState,
    deviceTypes: List<DeviceTypeUiState>,
    onUpdate: (EngineTaskUiState) -> Unit
) {
    val selectedType = deviceTypes.find { it.id == engineTask.deviceTypeId }
    val availableCommands = selectedType?.commands ?: emptyList()
    val selectedCommand = availableCommands.find { it.id == engineTask.commandId }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "设备与命令 (Device & Command)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Device Type Dropdown
        LabeledRow("设备类型") {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedType?.name ?: "选择设备类型...")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    deviceTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = {
                                onUpdate(engineTask.copy(deviceTypeId = type.id, commandId = null))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        
        // Command Dropdown
        LabeledRow("执行命令") {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedType != null
                ) {
                    Text(
                        selectedCommand?.let { "${it.name} (${it.payload})" } 
                            ?: if (selectedType != null) "选择命令..." else "先选择设备类型"
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    availableCommands.forEach { cmd ->
                        DropdownMenuItem(
                            text = { Text("${cmd.name} (${cmd.payload})") },
                            onClick = {
                                onUpdate(engineTask.copy(commandId = cmd.id))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        
        // [REMOVED] Device Index UI - 默认使用 Slot 绑定的第一个设备
        
        Spacer(Modifier.height(8.dp))
        
        // Loop Configuration
        Text(
            text = "循环配置 (可选)",
            style = MaterialTheme.typography.labelSmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("最大次数", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = engineTask.loopMaxIterations?.toString() ?: "",
                    onValueChange = { newVal ->
                        onUpdate(engineTask.copy(loopMaxIterations = newVal.toIntOrNull()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("空=执行1次") }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("间隔 (ms)", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = engineTask.loopDelayMs?.toString() ?: "",
                    onValueChange = { newVal ->
                        onUpdate(engineTask.copy(loopDelayMs = newVal.toIntOrNull()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        
        LabeledRow("跳出条件") {
            OutlinedTextField(
                value = engineTask.breakCondition ?: "",
                onValueChange = { newVal ->
                    onUpdate(engineTask.copy(breakCondition = newVal.ifBlank { null }))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如: voltage_a > 3.3") }
            )
        }
    }
}

@Composable
private fun HostTaskSection(
    hostTask: HostTaskUiState,
    onUpdate: (HostTaskUiState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Host 任务配置",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        LabeledRow("任务名称") {
            OutlinedTextField(
                value = hostTask.taskName,
                onValueChange = { onUpdate(hostTask.copy(taskName = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如: WaitDeviceReady") }
            )
        }
        
        LabeledRow("超时 (ms)") {
            OutlinedTextField(
                value = hostTask.timeoutMs.toString(),
                onValueChange = { newVal ->
                    newVal.toIntOrNull()?.let { onUpdate(hostTask.copy(timeoutMs = it)) }
                },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
        }
        
        LabeledRow("参数 (JSON)") {
            OutlinedTextField(
                value = hostTask.paramsJson,
                onValueChange = { onUpdate(hostTask.copy(paramsJson = it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
        }
    }
}

@Composable
private fun VariablesSection(
    variables: Map<String, VariableType>,
    onUpdate: (Map<String, VariableType>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "变量定义 (Variables)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Variable List
        variables.entries.forEachIndexed { index, (name, type) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        val newMap = variables.toMutableMap()
                        newMap.remove(name)
                        newMap[newName] = type
                        onUpdate(newMap)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("变量名") }
                )
                
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { typeExpanded = true }) {
                        Text(type.name)
                    }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        VariableType.entries.forEach { vt ->
                            DropdownMenuItem(
                                text = { Text(vt.name) },
                                onClick = {
                                    val newMap = variables.toMutableMap()
                                    newMap[name] = vt
                                    onUpdate(newMap)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = {
                        val newMap = variables.toMutableMap()
                        newMap.remove(name)
                        onUpdate(newMap)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🗑", fontSize = 12.sp)
                }
            }
        }
        
        // Add Variable Button
        OutlinedButton(
            onClick = {
                val newName = "var_${variables.size + 1}"
                val newMap = variables.toMutableMap()
                newMap[newName] = VariableType.NUMBER
                onUpdate(newMap)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ 添加变量")
        }
    }
}

@Composable
private fun CheckRuleSection(
    checkRule: CheckRuleUiState,
    availableVariables: List<String>,
    onUpdate: (CheckRuleUiState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "检查规则 (Check Rule)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Rule Type Selector
        var expanded by remember { mutableStateOf(false) }
        val ruleTypeLabel = when (checkRule) {
            is CheckRuleUiState.None -> "无检查"
            is CheckRuleUiState.RangeCheck -> "范围检查"
            is CheckRuleUiState.Threshold -> "阈值检查"
            is CheckRuleUiState.Contains -> "包含检查"
            is CheckRuleUiState.Expression -> "表达式"
        }
        
        LabeledRow("类型") {
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(ruleTypeLabel)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("无检查") },
                        onClick = {
                            onUpdate(CheckRuleUiState.None)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("范围检查 (min ≤ value ≤ max)") },
                        onClick = {
                            onUpdate(CheckRuleUiState.RangeCheck(
                                variableName = availableVariables.firstOrNull() ?: "",
                                min = 0.0,
                                max = 100.0
                            ))
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("阈值检查 (value op threshold)") },
                        onClick = {
                            onUpdate(CheckRuleUiState.Threshold(
                                variableName = availableVariables.firstOrNull() ?: "",
                                operator = ">",
                                value = 0.0
                            ))
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("包含检查") },
                        onClick = {
                            onUpdate(CheckRuleUiState.Contains(
                                variableName = availableVariables.firstOrNull() ?: "",
                                substring = ""
                            ))
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("自定义表达式") },
                        onClick = {
                            onUpdate(CheckRuleUiState.Expression(expr = ""))
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // Rule-specific configuration
        when (checkRule) {
            is CheckRuleUiState.None -> { /* 无配置 */ }
            
            is CheckRuleUiState.RangeCheck -> {
                LabeledRow("变量") {
                    VariableDropdown(
                        selectedVariable = checkRule.variableName,
                        availableVariables = availableVariables,
                        onSelect = { onUpdate(checkRule.copy(variableName = it)) }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("最小值", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = checkRule.min.toString(),
                            onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdate(checkRule.copy(min = it)) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("最大值", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = checkRule.max.toString(),
                            onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdate(checkRule.copy(max = it)) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
            
            is CheckRuleUiState.Threshold -> {
                LabeledRow("变量") {
                    VariableDropdown(
                        selectedVariable = checkRule.variableName,
                        availableVariables = availableVariables,
                        onSelect = { onUpdate(checkRule.copy(variableName = it)) }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("运算符", style = MaterialTheme.typography.labelSmall)
                        var opExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { opExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(checkRule.operator)
                            }
                            DropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
                                listOf(">", "<", ">=", "<=", "==", "!=").forEach { op ->
                                    DropdownMenuItem(
                                        text = { Text(op) },
                                        onClick = {
                                            onUpdate(checkRule.copy(operator = op))
                                            opExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("阈值", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = checkRule.value.toString(),
                            onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdate(checkRule.copy(value = it)) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
            
            is CheckRuleUiState.Contains -> {
                LabeledRow("变量") {
                    VariableDropdown(
                        selectedVariable = checkRule.variableName,
                        availableVariables = availableVariables,
                        onSelect = { onUpdate(checkRule.copy(variableName = it)) }
                    )
                }
                LabeledRow("包含") {
                    OutlinedTextField(
                        value = checkRule.substring,
                        onValueChange = { onUpdate(checkRule.copy(substring = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("检查字符串包含...") }
                    )
                }
            }
            
            is CheckRuleUiState.Expression -> {
                Text(
                    text = "自定义表达式（如: voltage_a + voltage_b > 100）",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted
                )
                OutlinedTextField(
                    value = checkRule.expr,
                    onValueChange = { onUpdate(checkRule.copy(expr = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如: (voltage_a + voltage_b) > 100") }
                )
            }
        }
    }
}

@Composable
private fun VariableDropdown(
    selectedVariable: String,
    availableVariables: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedVariable.isBlank()) "选择变量..." else selectedVariable)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (availableVariables.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("(请先定义变量)", color = CatalyticTheme.extendedColors.onSurfaceMuted) },
                    onClick = { expanded = false }
                )
            } else {
                availableVariables.forEach { varName ->
                    DropdownMenuItem(
                        text = { Text(varName) },
                        onClick = {
                            onSelect(varName)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculationSection(
    availableVariables: List<String>,
    selectedVariables: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "计算配置 (Calculation)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "此步骤不发送任何命令，仅对前置步骤的变量进行运算和判定。",
            style = MaterialTheme.typography.bodySmall,
            color = CatalyticTheme.extendedColors.onSurfaceMuted
        )
        
        // Input Variables Selection
        Text(
            text = "选择输入变量（来自前置步骤）",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
        
        if (availableVariables.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "暂无可用变量（请先在前置步骤中定义变量）",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatalyticTheme.extendedColors.onSurfaceMuted,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            // Display as clickable chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableVariables.forEach { varName ->
                    val isSelected = varName in selectedVariables
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newList = if (isSelected) {
                                selectedVariables - varName
                            } else {
                                selectedVariables + varName
                            }
                            onSelectionChange(newList)
                        },
                        label = { Text(varName) }
                    )
                }
            }
        }
        
        if (selectedVariables.isNotEmpty()) {
            Text(
                text = "已选择: ${selectedVariables.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LabeledRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(70.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
