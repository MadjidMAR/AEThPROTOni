package io.github.immaghzbad.aetherst.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    DEBUG
}

@Serializable
data class LogEntry(
    val id: Long = 0,
    val timestamp: String,
    val level: LogLevel,
    val tag: String = "AetherCore",
    val message: String
)
