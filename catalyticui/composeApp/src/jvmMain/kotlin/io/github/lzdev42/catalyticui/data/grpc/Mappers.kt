package io.github.lzdev42.catalyticui.data.grpc

import com.catalytic.grpc.LogEvent
import io.github.lzdev42.catalyticui.model.SlotState
import io.github.lzdev42.catalyticui.model.SlotStatus
import io.github.lzdev42.catalyticui.model.SlotVariable
import io.github.lzdev42.catalyticui.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.catalytic.grpc.SlotStatus as GrpcSlotStatus

/**
 * gRPC 数据 → UI 模型 映射器
 */
object Mappers {
    
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    
    /**
     * gRPC SlotStatus → UI SlotStatus 枚举
     */
    fun mapSlotStatus(grpcStatus: String): SlotStatus {
        return when (grpcStatus.lowercase()) {
            "idle" -> SlotStatus.IDLE
            "running" -> SlotStatus.RUNNING
            "paused" -> SlotStatus.PAUSED
            "completed", "pass" -> SlotStatus.PASS
            "error", "fail" -> SlotStatus.FAIL
            else -> SlotStatus.IDLE
        }
    }
    
    /**
     * gRPC SlotStatus 消息 → UI SlotState
     * 
     * 注意：gRPC 消息只包含状态快照，logs/variables 需要从其他来源累积
     */
    fun mapSlotStatus(
        grpc: GrpcSlotStatus,
        existingLogs: List<String> = emptyList(),
        sn: String? = null
    ): SlotState {
        val elapsedMs = grpc.elapsed_ms
        val elapsedTimeStr = if (elapsedMs > 0) {
            val seconds = elapsedMs / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            "${minutes}m ${secs}s"
        } else null
        
        // 映射变量列表 (新 Proto 字段)
        val variables = grpc.variables.map { grpcVar ->
            SlotVariable(
                name = grpcVar.name,
                value = grpcVar.value_ + (grpcVar.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
                isPassing = grpcVar.is_passing
            )
        }
        
        return SlotState(
            id = grpc.slot_id,
            sn = grpc.sn.takeIf { it.isNotBlank() } ?: sn,
            status = mapSlotStatus(grpc.status),
            currentStep = grpc.current_step_index,
            totalSteps = grpc.total_steps,
            currentStepName = grpc.current_step_name.takeIf { it.isNotBlank() },
            currentStepValue = grpc.current_step_desc.takeIf { it.isNotBlank() },
            variables = variables,
            logs = existingLogs,
            elapsedTime = elapsedTimeStr,
            deviceInfo = null
        )
    }
    
    /**
     * gRPC LogEvent → 格式化日志字符串
     */
    fun mapLogEvent(log: LogEvent): String {
        val time = try {
            timeFormatter.format(Instant.ofEpochMilli(log.timestamp))
        } catch (e: Exception) {
            "??:??:??"
        }
        val level = log.level.uppercase().take(1)
        val source = log.source.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
        return "$time [$level] $source${log.message}"
    }

    // ========== DeviceType / Device Mapping ==========

    /**
     * gRPC DeviceType + DeviceList → UI DeviceTypeUiState
     * 
     * 由于 gRPC API 返回扁平的 DeviceType 和 Device 列表，
     * 需要在调用侧组装成嵌套结构。
     */
    fun mapDeviceType(
        grpc: com.catalytic.grpc.DeviceType,
        devices: List<com.catalytic.grpc.Device>
    ): io.github.lzdev42.catalyticui.model.DeviceTypeUiState {
        // devices arg is kept for compatibility but in nested mode it should be grpc.devices
        // If caller passes grpc.devices, filter is redundant but safe if IDs match.
        val targetDevices = if (devices.isNotEmpty()) devices else grpc.devices
        val filtered = targetDevices.filter { it.device_type_id == grpc.id || it.device_type_id.isEmpty() } 
        // Note: Engine might not populate device_type_id in nested child if redundant. 
        // Relaxing filter to allow empty device_type_id if it came from the nested list.
        
        return io.github.lzdev42.catalyticui.model.DeviceTypeUiState(
            id = grpc.id,
            name = grpc.name.ifBlank { grpc.id },
            icon = mapPluginToIcon(grpc.plugin_id),
            pluginId = grpc.plugin_id,
            devices = filtered.map { mapDevice(it) },
            commands = grpc.commands.map { mapCommand(it) }, // [NEW] Map Nested Commands
            isExpanded = false
        )
    }

    fun mapDevice(grpc: com.catalytic.grpc.Device): io.github.lzdev42.catalyticui.model.DeviceUiState {
        return io.github.lzdev42.catalyticui.model.DeviceUiState(
            id = grpc.id,
            name = grpc.name.ifBlank { grpc.id },
            address = grpc.address,
            isOnline = true
        )
    }

    fun mapCommand(grpc: com.catalytic.grpc.Command): io.github.lzdev42.catalyticui.model.CommandUiState {
        return io.github.lzdev42.catalyticui.model.CommandUiState(
            id = grpc.id,
            name = grpc.name,
            payload = grpc.payload,
            parseRule = grpc.parse_rule.takeIf { it.isNotEmpty() },
            timeoutMs = grpc.timeout_ms
        )
    }

    /**
     * UI → gRPC DeviceType (Nested)
     */
    fun toGrpcDeviceType(ui: io.github.lzdev42.catalyticui.model.DeviceTypeUiState): com.catalytic.grpc.DeviceType {
        return com.catalytic.grpc.DeviceType(
            id = ui.id,
            name = ui.name,
            plugin_id = ui.pluginId,
            devices = ui.devices.map { toGrpcDevice(it, ui.id) },
            commands = ui.commands.map { toGrpcCommand(it, ui.id) }
        )
    }

    fun toGrpcDevice(ui: io.github.lzdev42.catalyticui.model.DeviceUiState, typeId: String): com.catalytic.grpc.Device {
        return com.catalytic.grpc.Device(
            id = ui.id,
            device_type_id = typeId,
            name = ui.name,
            address = ui.address
        )
    }

    fun toGrpcCommand(ui: io.github.lzdev42.catalyticui.model.CommandUiState, typeId: String): com.catalytic.grpc.Command {
        return com.catalytic.grpc.Command(
            id = ui.id,
            device_type_id = typeId,
            name = ui.name,
            payload = ui.payload,
            parse_rule = ui.parseRule ?: "",
            timeout_ms = ui.timeoutMs
        )
    }

    private fun mapPluginToIcon(pluginId: String): String {
        return when {
            pluginId.contains("serial") -> "🔌"
            pluginId.contains("tcp") || pluginId.contains("ethernet") -> "🌐"
            pluginId.contains("usb") -> "🔗"
            else -> "📟"
        }
    }
}

// =============================================================================
// DeviceType Mappers: DeviceTypeUiState → Engine JSON
// =============================================================================

/**
 * DeviceTypeMappers: 按契约格式序列化 DeviceType
 */
object DeviceTypeMappers {
    
    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    
    /**
     * 单个 DeviceType → JSON
     */
    fun toJson(type: DeviceTypeUiState): String {
        val devicesJson = type.devices.joinToString(", ") { dev ->
            """{"id": "${escapeJson(dev.id)}", "name": "${escapeJson(dev.name)}", "address": "${escapeJson(dev.address)}"}"""
        }
        
        val commandsJson = type.commands.joinToString(", ") { cmd ->
            val parts = mutableListOf<String>()
            parts.add(""""id": "${escapeJson(cmd.id)}"""")
            parts.add(""""name": "${escapeJson(cmd.name)}"""")
            parts.add(""""payload": "${escapeJson(cmd.payload)}"""")
            cmd.parseRule?.let { parts.add(""""parse_rule": "${escapeJson(it)}"""") }
            parts.add(""""timeout_ms": ${cmd.timeoutMs}""")
            "{${parts.joinToString(", ")}}"
        }
        
        return """{
  "id": "${escapeJson(type.id)}",
  "name": "${escapeJson(type.name)}",
  "plugin_id": "${escapeJson(type.pluginId)}",
  "devices": [$devicesJson],
  "commands": [$commandsJson]
}"""
    }
    
    /**
     * 全部 DeviceTypes → JSON Map
     */
    fun toJsonMap(types: List<DeviceTypeUiState>): String {
        val entries = types.joinToString(",\n") { type ->
            """"${type.id}": ${toJson(type).prependIndent("  ").trimStart()}"""
        }
        return "{\n  \"device_types\": {\n$entries\n  }\n}"
    }
}

// =============================================================================
// SlotBinding Mappers
// =============================================================================

/**
 * SlotBindingMappers: 按契约格式序列化 SlotBindings
 */
object SlotBindingMappers {
    
    /**
     * Map<Int, Map<String, List<String>>> → JSON
     */
    fun toJson(bindings: Map<Int, Map<String, List<String>>>): String {
        val entries = bindings.entries.sortedBy { it.key }.joinToString(",\n") { (slotId, typeBindings) ->
            val typeEntries = typeBindings.entries.joinToString(", ") { (typeId, deviceIds) ->
                val ids = deviceIds.joinToString(", ") { "\"$it\"" }
                """"$typeId": [$ids]"""
            }
            """"$slotId": {$typeEntries}"""
        }
        return "{\n  \"slot_bindings\": {\n    $entries\n  }\n}"
    }
}

// =============================================================================
// Step Mappers: StepUiState ↔ Engine TestStep JSON
// =============================================================================

/**
 * StepMappers: 手动实现 StepUiState ↔ JSON 映射
 * 
 * 注意：由于项目未配置 kotlinx.serialization，使用手动字符串构建。
 * 未来可考虑添加依赖后重构为类型安全的序列化。
 */
object StepMappers {
    
    /**
     * UI → Engine JSON (序列化)
     */
    fun toEngineJson(step: StepUiState): String {
        val parts = mutableListOf<String>()
        
        parts.add(""""step_id": ${step.stepId}""")
        parts.add(""""step_name": "${escapeJson(step.stepName)}"""")
        parts.add(""""execution_mode": "${when (step.executionMode) {
            ExecutionMode.ENGINE_CONTROLLED -> "engine_controlled"
            ExecutionMode.HOST_CONTROLLED -> "host_controlled"
            ExecutionMode.CALCULATION -> "calculation"
        }}"""")
        
        step.engineTask?.let { task ->
            val engineParts = mutableListOf<String>()
            engineParts.add(""""device_type_id": "${escapeJson(task.deviceTypeId)}"""")
            task.commandId?.let { engineParts.add(""""command_id": "${escapeJson(it)}"""" ) }
            // [REMOVED] device_index - 默认使用 Slot 绑定的第一个设备
            task.loopMaxIterations?.let { engineParts.add(""""loop_max_iterations": $it""") }
            task.loopDelayMs?.let { engineParts.add(""""loop_delay_ms": $it""") }
            task.breakCondition?.let { engineParts.add(""""break_condition": "${escapeJson(it)}"""" ) }
            parts.add(""""engine_task": {${engineParts.joinToString(", ")}}""")
        }
        
        step.hostTask?.let { task ->
            val hostParts = mutableListOf<String>()
            hostParts.add(""""task_name": "${escapeJson(task.taskName)}"""")
            hostParts.add(""""params": ${task.paramsJson}""")
            hostParts.add(""""timeout_ms": ${task.timeoutMs}""")
            parts.add(""""host_task": {${hostParts.joinToString(", ")}}""")
        }
        
        // Encode input variables (for calculation steps)
        if (step.inputVariables.isNotEmpty()) {
            val inputVars = step.inputVariables.joinToString(", ") { "\"${escapeJson(it)}\"" }
            parts.add(""""input_variables": [$inputVars]""")
        }
        
        // Encode variables map
        if (step.variables.isNotEmpty()) {
            val varParts = step.variables.entries.joinToString(", ") { (k, v) ->
                """"${escapeJson(k)}": "${v.name}""""
            }
            parts.add(""""variables": {$varParts}""")
        }
        
        parts.add(encodeCheckRule(step.checkRule))
        
        step.nextOnPass?.let { parts.add(""""next_on_pass": $it""") }
        step.nextOnFail?.let { parts.add(""""next_on_fail": $it""") }
        step.nextOnTimeout?.let { parts.add(""""next_on_timeout": $it""") }
        step.nextOnError?.let { parts.add(""""next_on_error": $it""") }
        
        return "{${parts.joinToString(", ")}}"
    }
    
    // [DELETED] encodeParseRule function removed - ParseRuleUiState no longer exists
    
    private fun encodeCheckRule(rule: CheckRuleUiState): String {
        return when (rule) {
            CheckRuleUiState.None -> """"check_type": "none""""
            is CheckRuleUiState.RangeCheck ->
                """"check_type": "builtin", "check_rule": {"template": "range", "variable": "${escapeJson(rule.variableName)}", "min": ${rule.min}, "max": ${rule.max}}"""
            is CheckRuleUiState.Threshold ->
                """"check_type": "builtin", "check_rule": {"template": "threshold", "variable": "${escapeJson(rule.variableName)}", "operator": "${escapeJson(rule.operator)}", "value": ${rule.value}}"""
            is CheckRuleUiState.Contains ->
                """"check_type": "builtin", "check_rule": {"template": "contains", "variable": "${escapeJson(rule.variableName)}", "substring": "${escapeJson(rule.substring)}"}"""
            is CheckRuleUiState.Expression ->
                """"check_type": "builtin", "check_rule": {"template": "expression", "expr": "${escapeJson(rule.expr)}"}"""
        }
    }
    
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    /**
     * Engine JSON → UI (反序列化)
     * 
     * 简易实现，使用正则提取。生产环境应使用正式 JSON 库。
     */
    fun fromEngineJson(json: String): StepUiState {
        val stepId = extractInt(json, "step_id") ?: 0
        val stepName = extractString(json, "step_name") ?: ""
        val executionModeStr = extractString(json, "execution_mode")
        val executionMode = when (executionModeStr) {
            "host_controlled" -> ExecutionMode.HOST_CONTROLLED
            else -> ExecutionMode.ENGINE_CONTROLLED
        }
        
        val engineTask = if (json.contains("\"engine_task\"")) {
            val taskJson = extractObject(json, "engine_task") ?: "{}"
            EngineTaskUiState(
                deviceTypeId = extractString(taskJson, "device_type_id") ?: "",
                commandId = extractString(taskJson, "command_id"),
                // [REMOVED] deviceIndex - 已移除
                loopMaxIterations = extractInt(taskJson, "loop_max_iterations"),
                loopDelayMs = extractInt(taskJson, "loop_delay_ms"),
                breakCondition = extractString(taskJson, "break_condition")
            )
        } else null
        
        val hostTask = if (json.contains("\"host_task\"")) {
            val taskJson = extractObject(json, "host_task") ?: "{}"
            HostTaskUiState(
                taskName = extractString(taskJson, "task_name") ?: "",
                paramsJson = extractObject(taskJson, "params") ?: "{}",
                timeoutMs = extractInt(taskJson, "timeout_ms") ?: 5000
            )
        } else null
        
        // Decode variables map
        val variables = mutableMapOf<String, VariableType>()
        extractObject(json, "variables")?.let { varJson ->
            // Simple approach: look for patterns like "var_name": "NUMBER"
            val varPattern = "\"([^\"]+)\"\\s*:\\s*\"(NUMBER|STRING)\"".toRegex()
            varPattern.findAll(varJson).forEach { match ->
                val name = match.groupValues[1]
                val type = when (match.groupValues[2]) {
                    "STRING" -> VariableType.STRING
                    else -> VariableType.NUMBER
                }
                variables[name] = type
            }
        }
        
        return StepUiState(
            stepId = stepId,
            stepName = stepName,
            executionMode = executionMode,
            engineTask = engineTask,
            hostTask = hostTask,
            variables = variables,
            checkRule = decodeCheckRule(json),
            nextOnPass = extractInt(json, "next_on_pass"),
            nextOnFail = extractInt(json, "next_on_fail"),
            nextOnTimeout = extractInt(json, "next_on_timeout"),
            nextOnError = extractInt(json, "next_on_error")
        )
    }
    
    // [DELETED] decodeParseRule function removed - ParseRuleUiState no longer exists
    
    private fun decodeCheckRule(json: String): CheckRuleUiState {
        if (extractString(json, "check_type") == "none") return CheckRuleUiState.None
        
        val ruleJson = extractObject(json, "check_rule") ?: return CheckRuleUiState.None
        return when (extractString(ruleJson, "template")) {
            "range" -> CheckRuleUiState.RangeCheck(
                variableName = extractString(ruleJson, "variable") ?: "",
                min = extractDouble(ruleJson, "min") ?: 0.0,
                max = extractDouble(ruleJson, "max") ?: 100.0
            )
            "threshold" -> CheckRuleUiState.Threshold(
                variableName = extractString(ruleJson, "variable") ?: "",
                operator = extractString(ruleJson, "operator") ?: ">",
                value = extractDouble(ruleJson, "value") ?: 0.0
            )
            "contains" -> CheckRuleUiState.Contains(
                variableName = extractString(ruleJson, "variable") ?: "",
                substring = extractString(ruleJson, "substring") ?: ""
            )
            "expression" -> CheckRuleUiState.Expression(
                expr = extractString(ruleJson, "expr") ?: ""
            )
            else -> CheckRuleUiState.None
        }
    }
    
    
    // Simple JSON extraction helpers (not robust, for basic use only)
    private fun extractString(json: String, key: String): String? {
        val regex = """"$key"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
    }
    
    private fun extractInt(json: String, key: String): Int? {
        val regex = """"$key"\s*:\s*(-?\d+)""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
    
    private fun extractDouble(json: String, key: String): Double? {
        val regex = """"$key"\s*:\s*(-?\d+\.?\d*)""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }
    
    private fun extractObject(json: String, key: String): String? {
        val startPattern = """"$key"\s*:\s*\{"""
        val startMatch = startPattern.toRegex().find(json) ?: return null
        var depth = 1
        var idx = startMatch.range.last + 1
        val sb = StringBuilder("{")
        while (idx < json.length && depth > 0) {
            val c = json[idx]
            sb.append(c)
            when (c) {
                '{' -> depth++
                '}' -> depth--
            }
            idx++
        }
        return if (depth == 0) sb.toString() else null
    }
    
    private fun unescapeJson(s: String): String {
        return s.replace("\\\\", "\u0000")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\u0000", "\\")
    }
    
    /**
     * 批量转换
     */
    fun toEngineJsonArray(steps: List<StepUiState>): String {
        return "[${steps.joinToString(", ") { toEngineJson(it) }}]"
    }
    
    fun fromEngineJsonArray(jsonStr: String): List<StepUiState> {
        // Simple array parsing - splits on closing brace followed by comma
        return try {
            val content = jsonStr.trim().removePrefix("[").removeSuffix("]")
            if (content.isBlank()) return emptyList()
            
            val results = mutableListOf<StepUiState>()
            var depth = 0
            var start = 0
            for (i in content.indices) {
                when (content[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            results.add(fromEngineJson(content.substring(start, i + 1)))
                            start = i + 1
                            // Skip comma and whitespace
                            while (start < content.length && content[start] in " ,\n\r\t") start++
                        }
                    }
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }
}
