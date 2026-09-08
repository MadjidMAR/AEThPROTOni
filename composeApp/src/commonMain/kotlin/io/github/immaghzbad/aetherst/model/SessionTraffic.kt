package io.github.immaghzbad.aetherst.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionTraffic(
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val uploadSpeedBps: Double = 0.0,
    val downloadSpeedBps: Double = 0.0
)
