package io.github.lzdev42.catalyticui.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.time.Clock

/**
 * 日志管理器 - 纯 Kotlin 实现（使用 Okio）
 */
object LogManager {

    private const val MAX_UI_LOGS = 500

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    private val _operationLogs = MutableStateFlow<List<String>>(emptyList())
    val operationLogs: StateFlow<List<String>> = _operationLogs.asStateFlow()

    private val fileSystem = FileSystem.SYSTEM
    private var systemLogFile: okio.Path? = null
    private var operationLogFile: okio.Path? = null

    /**
     * 初始化日志目录
     */
    fun init(logsDirectory: String) {
        val logDir = logsDirectory.toPath()
        fileSystem.createDirectories(logDir)

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

        systemLogFile = logDir / "system_$today.log"
        operationLogFile = logDir / "operation_$today.log"
    }

    /**
     * 添加系统日志
     */
    fun addLog(message: String) {
        val timestamp = formatTimestamp()
        val entry = "[$timestamp] $message"

        _systemLogs.update { (it + entry).takeLast(MAX_UI_LOGS) }
        appendToFile(systemLogFile, entry)
    }

    /**
     * 添加操作日志
     */
    fun addOpLog(message: String) {
        val timestamp = formatTimestamp()
        val entry = "[$timestamp] $message"

        _operationLogs.update { (it + entry).takeLast(MAX_UI_LOGS) }
        appendToFile(operationLogFile, entry)
    }

    fun clearSystemLogs() {
        _systemLogs.value = emptyList()
    }

    fun clearOperationLogs() {
        _operationLogs.value = emptyList()
    }

    fun clearAll() {
        clearSystemLogs()
        clearOperationLogs()
    }

    private fun appendToFile(file: okio.Path?, line: String) {
        try {
            file?.let {
                // 使用 appendingSink 追加写入
                fileSystem.appendingSink(it).buffer().use { sink ->
                    sink.writeUtf8(line)
                    sink.writeUtf8("\n")
                }
            }
        } catch (e: Exception) {
            // 静默处理文件写入错误
        }
    }

    private fun formatTimestamp(): String {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localDateTime.year}-${pad(localDateTime.month.number)}-${pad(localDateTime.day)} " +
                "${pad(localDateTime.hour)}:${pad(localDateTime.minute)}:${pad(localDateTime.second)}"
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')
}