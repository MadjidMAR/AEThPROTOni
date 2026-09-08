package io.github.immaghzbad.aetherst.shared.data

import io.github.immaghzbad.aetherst.shared.model.LogEntry
import io.github.immaghzbad.aetherst.shared.model.LogLevel
import io.github.immaghzbad.aetherst.platform.Settings
import io.github.immaghzbad.aetherst.platform.getCurrentTimestamp
import io.github.immaghzbad.aetherst.shared.model.AetherLogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    @Volatile
    var currentAppLogLevel: AetherLogLevel = AetherLogLevel.INFO
    @Volatile
    var currentCoreLogLevel: AetherLogLevel = AetherLogLevel.INFO

    private val scope = CoroutineScope(Dispatchers.Default)
    private var settings: Settings? = null
    private var logIdCounter = 1L
    var fileLogWriter: ((LogLevel, String, String) -> Unit)? = null

    private val sensitivePatterns = listOf(
        "access_token", "cert_pem", "key_pem", "private_key",
        "wg_private_key", "wg_peer_public_key", "client_id",
        "Authorization", "Bearer"
    )

    fun initialize(settings: Settings) {
        if (this.settings != null) return
        this.settings = settings

        val savedLogsJson = settings.getString("saved_logs", "")
        if (savedLogsJson.isNotEmpty()) {
            try {
                val loadedLogs = Json.decodeFromString<List<LogEntry>>(savedLogsJson)
                _logs.value = loadedLogs
                logIdCounter = (loadedLogs.maxOfOrNull { it.id } ?: 0) + 1
            } catch (_: Exception) {}
        }
    }

    fun log(level: LogLevel, message: String, tag: String = "AetherSystem") {
        val sanitizedMessage = sanitize(message)
        runCatching { fileLogWriter?.invoke(level, tag, sanitizedMessage) }

        val isCore = tag == "AetherCore" || tag == "AetherRegistration"
        val configLevel = if (isCore) currentCoreLogLevel else currentAppLogLevel

        val shouldLog = when (configLevel) {
            AetherLogLevel.OFF -> false
            AetherLogLevel.ERROR -> level == LogLevel.ERROR
            AetherLogLevel.WARN -> level == LogLevel.WARN || level == LogLevel.ERROR
            AetherLogLevel.INFO -> level == LogLevel.INFO || level == LogLevel.WARN || level == LogLevel.ERROR
            AetherLogLevel.DEBUG -> true
        }

        if (!shouldLog) return

        val timestamp = getCurrentTimestamp()

        val entry = LogEntry(
            id = logIdCounter++,
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = sanitizedMessage
        )

        synchronized(this) {
            val current = _logs.value.toMutableList()
            if (current.size >= 1000) {
                current.removeAt(0)
            }
            current.add(entry)
            _logs.value = current
            saveLogs(current)
        }
    }

    private fun sanitize(input: String): String {
        var output = input
        for (pattern in sensitivePatterns) {
            if (output.contains(pattern, ignoreCase = true)) {
                output = output.replace(Regex("$pattern[:\\s=]+[^\\s,;]+", RegexOption.IGNORE_CASE), "$pattern: [REDACTED]")
            }
        }
        return output
    }

    private fun saveLogs(list: List<LogEntry>) {
        scope.launch {
            try {
                val json = Json.encodeToString(list)
                settings?.putString("saved_logs", json)
            } catch (_: Exception) {}
        }
    }

    fun i(message: String, tag: String = "AetherSystem") = log(LogLevel.INFO, message, tag)
    fun w(message: String, tag: String = "AetherSystem") = log(LogLevel.WARN, message, tag)
    fun e(message: String, tag: String = "AetherSystem") = log(LogLevel.ERROR, message, tag)
    fun d(message: String, tag: String = "AetherSystem") = log(LogLevel.DEBUG, message, tag)

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
            saveLogs(emptyList())
        }
    }
}
