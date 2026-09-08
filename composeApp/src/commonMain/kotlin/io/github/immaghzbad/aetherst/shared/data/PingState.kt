package io.github.immaghzbad.aetherst.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class PingState(
    val ms: Long = -1,
    val isPinging: Boolean = false,
    val error: String? = null
)
