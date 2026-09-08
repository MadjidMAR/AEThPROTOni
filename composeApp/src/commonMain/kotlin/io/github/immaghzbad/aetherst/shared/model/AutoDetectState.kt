package io.github.immaghzbad.aetherst.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProbeStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

@Serializable
data class ProtocolProbeResult(
    val protocol: AetherProtocol,
    val status: ProbeStatus = ProbeStatus.IDLE,
    val latencyMs: Long = -1,
    val error: String? = null
)

@Serializable
data class MtuProbeResult(
    val discoveredMtu: Int = 0,
    val status: ProbeStatus = ProbeStatus.IDLE,
    val rawPathMtu: Int = 0
)

@Serializable
data class NoiseProbeResult(
    val noise: AetherNoise,
    val status: ProbeStatus = ProbeStatus.IDLE,
    val effective: Boolean = false
)

@Serializable
data class ScanModeProbeResult(
    val scanMode: AetherScanMode,
    val status: ProbeStatus = ProbeStatus.IDLE,
    val gatewayFound: Boolean = false
)

@Serializable
data class NetworkFingerprint(
    val networkType: String = "",
    val supportsDPI: Boolean = false,
    val supportsUDP: Boolean = true,
    val supportsIPv6: Boolean = false,
    val dnsServers: List<String> = emptyList(),
    val carrierOrIsp: String = "",
    val ipAddress: String = ""
)

@Serializable
data class AutoDetectResult(
    val recommendedProtocol: AetherProtocol = AetherProtocol.MASQUE,
    val recommendedNoise: AetherNoise = AetherNoise.FIREWALL,
    val recommendedScanMode: AetherScanMode = AetherScanMode.BALANCED,
    val recommendedMtu: Int = 1100,
    val recommendedIpMode: AetherIpMode = AetherIpMode.IPV4,
    val recommendedH2Mode: Boolean = true,
    val recommendedEch: Boolean = false,
    val recommendedFragment: Boolean = false,
    val recommendedNoDataCheck: Boolean = false,
    val confidence: Float = 0f,
    val networkFingerprint: NetworkFingerprint = NetworkFingerprint()
)

@Serializable
data class AutoDetectState(
    val phase: AutoDetectPhase = AutoDetectPhase.IDLE,
    val currentStep: String = "",
    val protocolResults: List<ProtocolProbeResult> = emptyList(),
    val mtuResult: MtuProbeResult = MtuProbeResult(),
    val noiseResults: List<NoiseProbeResult> = emptyList(),
    val scanModeResults: List<ScanModeProbeResult> = emptyList(),
    val finalResult: AutoDetectResult? = null,
    val liveFingerprint: NetworkFingerprint? = null,
    val error: String? = null,
    val progressPercent: Int = 0
)

@Serializable
enum class AutoDetectPhase {
    IDLE,
    FINGERPRINTING,
    PROTOCOL_SCAN,
    MTU_PROBE,
    NOISE_PROBE,
    SCAN_MODE_PROBE,
    ANALYZING,
    COMPLETE,
    ERROR
}
