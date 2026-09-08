package io.github.immaghzbad.aetherst.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETE,
    ERROR,
    CANCELLED
}

@Serializable
enum class SpeedTestServer(
    val displayName: String,
    val baseUrl: String,
    val description: String
) {
    CLOUDFLARE(
        "Cloudflare",
        "https://speed.cloudflare.com",
        "Cloudflare Global Network"
    ),
    OFAKIN(
        "Ofakino",
        "https://ofakino.pishtazan.dev",
        "Ofakino Mirror"
    ),
    CUSTOM(
        "Custom Server",
        "",
        "Use your own endpoint"
    )
}

@Serializable
enum class SpeedUnit(val label: String, val factor: Double) {
    BPS("B/s", 1.0),
    KBPS("KB/s", 1024.0),
    MBPS("MB/s", 1024.0 * 1024.0),
    GBPS("GB/s", 1024.0 * 1024.0 * 1024.0);

    companion object {
        fun autoSelect(bytesPerSec: Double): SpeedUnit {
            return when {
                bytesPerSec >= 1024.0 * 1024.0 * 1024.0 -> GBPS
                bytesPerSec >= 1024.0 * 1024.0 -> MBPS
                bytesPerSec >= 1024.0 -> KBPS
                else -> BPS
            }
        }
    }
}

@Serializable
enum class SpeedDisplayMode(val label: String, val shortLabel: String) {
    BYTES("Bytes per second", "B/s"),
    BITS("Bits per second", "b/s")
}

@Serializable
data class SpeedTestResult(
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadBps: Double = 0.0,
    val uploadBps: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingSamples: List<Long> = emptyList(),
    val serverName: String = ""
)

@Serializable
data class SpeedTestConfig(
    val selectedServer: SpeedTestServer = SpeedTestServer.CLOUDFLARE,
    val displayMode: SpeedDisplayMode = SpeedDisplayMode.BYTES,
    val downloadSizeMb: Int = 10,
    val uploadSizeMb: Int = 10,
    val pingSamples: Int = 20,
    val customServerUrl: String = "",
    val showBits: Boolean = false,
    val downloadStreams: Int = 3,
    val pingWarmup: Int = 1,
    val autoUnit: Boolean = true
)

@Serializable
data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val config: SpeedTestConfig = SpeedTestConfig(),
    val result: SpeedTestResult = SpeedTestResult(),
    val progress: Float = 0f,
    val currentStep: String = "",
    val error: String? = null,
    val downloadSpeedHistory: List<Double> = emptyList(),
    val uploadSpeedHistory: List<Double> = emptyList(),
    val livePingMs: Long = -1,
    val livePingMin: Long = -1,
    val livePingMax: Long = -1,
    val livePingAvg: Double = -1.0,
    val livePingCount: Int = 0,
    val liveDownloadBps: Double = 0.0,
    val liveDownloadTotal: Long = 0,
    val liveUploadBps: Double = 0.0,
    val liveUploadTotal: Long = 0,
    val livePhaseElapsed: Long = 0
)
