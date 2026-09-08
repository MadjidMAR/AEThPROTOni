package io.github.immaghzbad.aetherst.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class IpInfo(
    val ip: String = "",
    val country: String = "",
    val countryCode: String = "",
    val flagEmoji: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
