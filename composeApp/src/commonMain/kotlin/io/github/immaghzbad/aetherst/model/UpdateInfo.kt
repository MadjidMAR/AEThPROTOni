package io.github.immaghzbad.aetherst.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    @SerialName("version") val version: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("is_beta") val isBeta: Boolean,
    @SerialName("changelog") val changelog: String,
    @SerialName("release_url") val releaseUrl: String
)
