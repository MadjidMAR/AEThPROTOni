package io.github.immaghzbad.aetherst.shared.data

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.core.NetworkClient
import io.github.immaghzbad.aetherst.shared.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

object SpeedTestRepository {
    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    private var testJob: Job? = null
    private val isCancelled = AtomicBoolean(false)
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var settings: io.github.immaghzbad.aetherst.platform.Settings? = null

    private var socksHost: String = "127.0.0.1"
    private var socksPort: Int = 1819
    private var useProxy: Boolean = false

    private const val TAG = "SpeedTest"
    private const val PREFIX = "speed_test_"

    fun initialize(settings: io.github.immaghzbad.aetherst.platform.Settings) {
        this.settings = settings
        val savedConfig = loadConfig(settings)
        _state.value = SpeedTestState(config = savedConfig)
    }

    private fun loadConfig(s: io.github.immaghzbad.aetherst.platform.Settings): SpeedTestConfig {
        return SpeedTestConfig(
            selectedServer = runCatching { SpeedTestServer.valueOf(s.getString("${PREFIX}server", SpeedTestServer.CLOUDFLARE.name)) }.getOrDefault(SpeedTestServer.CLOUDFLARE),
            showBits = s.getBoolean("${PREFIX}show_bits", false),
            downloadSizeMb = s.getInt("${PREFIX}download_size", 10),
            uploadSizeMb = s.getInt("${PREFIX}upload_size", 10),
            pingSamples = s.getInt("${PREFIX}ping_samples", 20),
            customServerUrl = s.getString("${PREFIX}custom_url", ""),
            downloadStreams = s.getInt("${PREFIX}download_streams", 3).coerceIn(1, 6),
            pingWarmup = s.getInt("${PREFIX}ping_warmup", 1).coerceIn(0, 5),
            autoUnit = s.getBoolean("${PREFIX}auto_unit", true)
        )
    }

    private fun saveConfig(config: SpeedTestConfig) {
        val s = settings ?: return
        s.putString("${PREFIX}server", config.selectedServer.name)
        s.putBoolean("${PREFIX}show_bits", config.showBits)
        s.putInt("${PREFIX}download_size", config.downloadSizeMb)
        s.putInt("${PREFIX}upload_size", config.uploadSizeMb)
        s.putInt("${PREFIX}ping_samples", config.pingSamples)
        s.putString("${PREFIX}custom_url", config.customServerUrl)
        s.putInt("${PREFIX}download_streams", config.downloadStreams)
        s.putInt("${PREFIX}ping_warmup", config.pingWarmup)
        s.putBoolean("${PREFIX}auto_unit", config.autoUnit)
    }


    fun startTest(proxyHost: String = "127.0.0.1", proxyPort: Int = 1819, throughProxy: Boolean = false) {
        if (testJob?.isActive == true) return
        if (!mutex.tryLock()) return

        socksHost = proxyHost
        socksPort = proxyPort
        useProxy = throughProxy

        isCancelled.set(false)
        val preservedConfig = _state.value.config
        testJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val config = preservedConfig
                val server = config.selectedServer
                val serverUrl = resolveServerUrl(server, config)

                if (server == SpeedTestServer.CUSTOM && config.customServerUrl.isBlank()) {
                    throw IllegalStateException("Custom server URL is empty")
                }

                LogRepository.i("Speed test started: Server=${server.displayName}, URL=$serverUrl, Proxy=${if (useProxy) "SOCKS($socksHost:$socksPort)" else "Direct"}", TAG)
                _state.update { it.copy(
                    config = preservedConfig,
                    phase = SpeedTestPhase.PING,
                    currentStep = "Measuring ping & jitter...",
                    progress = 0f,
                    error = null,
                    result = SpeedTestResult(),
                    downloadSpeedHistory = emptyList(),
                    uploadSpeedHistory = emptyList()
                ) }

                val pingResult = measurePingAndJitter(serverUrl, config.pingSamples, config.pingWarmup)
                if (isCancelled.get()) return@launch

                if (pingResult.first < 0) {
                    throw IllegalStateException("Ping failed — server unreachable")
                }

                _state.update { it.copy(
                    result = it.result.copy(
                        pingMs = pingResult.first,
                        jitterMs = pingResult.second,
                        pingSamples = pingResult.third,
                        serverName = server.displayName
                    ),
                    progress = 0.25f,
                    currentStep = "Ping: ${"%.1f".format(pingResult.first)}ms | Jitter: ${"%.1f".format(pingResult.second)}ms"
                ) }

                _state.update { it.copy(
                    phase = SpeedTestPhase.DOWNLOAD,
                    currentStep = "Testing download speed (${config.downloadStreams} stream${if (config.downloadStreams > 1) "s" else ""})...",
                    progress = 0.30f
                ) }
                val dlResult = measureDownload(serverUrl, config.downloadSizeMb, config.downloadStreams)
                if (isCancelled.get()) return@launch

                if (dlResult.first <= 0.0) {
                    throw IllegalStateException("Download test failed — no data received")
                }

                _state.update { it.copy(
                    result = it.result.copy(
                        downloadBps = dlResult.first,
                        downloadMbps = dlResult.first * 8.0 / (1024.0 * 1024.0)
                    ),
                    downloadSpeedHistory = dlResult.second,
                    progress = 0.65f,
                    currentStep = "Download: ${formatSpeed(dlResult.first, config)}"
                ) }

                _state.update { it.copy(
                    phase = SpeedTestPhase.UPLOAD,
                    currentStep = "Testing upload speed...",
                    progress = 0.70f
                ) }
                val ulResult = measureUpload(serverUrl, config.uploadSizeMb)
                if (isCancelled.get()) return@launch
                _state.update { it.copy(
                    result = it.result.copy(
                        uploadBps = ulResult.first,
                        uploadMbps = ulResult.first * 8.0 / (1024.0 * 1024.0)
                    ),
                    uploadSpeedHistory = ulResult.second,
                    progress = 1.0f
                ) }

                val finalResult = _state.value.result

                _state.update { it.copy(
                    phase = SpeedTestPhase.COMPLETE,
                    currentStep = "Test complete",
                    progress = 1.0f
                ) }

                LogRepository.i(
                    "Speed test complete: Ping=${"%.1f".format(finalResult.pingMs)}ms " +
                    "Jitter=${"%.1f".format(finalResult.jitterMs)}ms " +
                    "DL=${"%.2f".format(finalResult.downloadMbps)}Mbps " +
                    "UL=${"%.2f".format(finalResult.uploadMbps)}Mbps",
                    TAG
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.e("Speed test failed: ${e.message}", TAG)
                _state.update { it.copy(
                    phase = SpeedTestPhase.ERROR,
                    currentStep = "Test failed",
                    error = e.message ?: "Unknown error"
                ) }
            } finally {
                mutex.unlock()
            }
        }
    }

    fun cancelTest() {
        val activePhases = setOf(SpeedTestPhase.PING, SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD)
        if (_state.value.phase !in activePhases) return
        isCancelled.set(true)
        testJob?.cancel()
        testJob = null
        _state.update { it.copy(
            phase = SpeedTestPhase.CANCELLED,
            currentStep = "Test cancelled",
            progress = 0f
        ) }
    }

    fun reset() {
        testJob?.cancel()
        testJob = null
        isCancelled.set(false)
        val preservedConfig = _state.value.config
        _state.value = SpeedTestState(config = preservedConfig)
    }

    fun updateConfig(config: SpeedTestConfig) {
        _state.update { it.copy(config = config) }
        saveConfig(config)
    }


    private fun resolveServerUrl(server: SpeedTestServer, config: SpeedTestConfig): String {
        return when (server) {
            SpeedTestServer.CLOUDFLARE -> "https://speed.cloudflare.com"
            SpeedTestServer.OFAKIN -> "https://ofakino.pishtazan.dev"
            SpeedTestServer.CUSTOM -> config.customServerUrl.trim().removeSuffix("/")
        }
    }

    private fun getProxy(): Proxy {
        return if (useProxy) {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
        } else {
            Proxy.NO_PROXY
        }
    }

    private fun openProxyConnection(urlString: String): HttpURLConnection {
        val url = java.net.URI(urlString).toURL()
        return url.openConnection(getProxy()) as HttpURLConnection
    }


    private suspend fun measurePingAndJitter(
        baseUrl: String,
        sampleCount: Int,
        warmupCount: Int
    ): Triple<Double, Double, List<Long>> {
        return withContext(Dispatchers.IO) {
            val allSamples = mutableListOf<Long>()
            val pingUrl = "$baseUrl/__down?bytes=0"

            val phaseStart = System.currentTimeMillis()

            repeat(sampleCount) { i ->
                if (isCancelled.get()) return@withContext Triple(-1.0, -1.0, emptyList())

                try {
                    val startTime = System.nanoTime()
                    val conn = openProxyConnection(pingUrl)
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    conn.connect()

                    val code = conn.responseCode
                    conn.disconnect()

                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
                    if (code == 200 || code == 206) {
                        allSamples.add(elapsed)

                        val sortedSoFar = allSamples.sorted()
                        val keptSoFar = allSamples.drop(warmupCount.coerceAtMost(allSamples.size - 1))
                        val avgSoFar = if (keptSoFar.isEmpty()) allSamples.average() else keptSoFar.average()
                        _state.update { it.copy(
                            progress = ((i + 1).toFloat() / sampleCount) * 0.25f,
                            currentStep = "Ping sample ${i + 1}/$sampleCount",
                            livePingMs = elapsed,
                            livePingMin = sortedSoFar.first(),
                            livePingMax = sortedSoFar.last(),
                            livePingAvg = avgSoFar,
                            livePingCount = allSamples.size,
                            livePhaseElapsed = (System.currentTimeMillis() - phaseStart) / 1000
                        ) }
                    }
                } catch (_: Exception) {
                }

                delay(80)
            }

            val samples = allSamples.drop(warmupCount)
            if (samples.isEmpty()) {
                return@withContext Triple(-1.0, -1.0, allSamples.toList())
            }

            val sorted = samples.sorted()
            val medianPing = sorted[sorted.size / 2].toDouble()

            var jitterSum = 0.0
            if (samples.size > 1) {
                for (i in 1 until samples.size) {
                    jitterSum += kotlin.math.abs(samples[i] - samples[i - 1]).toDouble()
                }
                jitterSum /= (samples.size - 1)
            }
            val jitter = jitterSum

            Triple(medianPing, jitter, allSamples.toList())
        }
    }


    private suspend fun measureDownload(
        baseUrl: String,
        sizeMb: Int,
        streams: Int
    ): Pair<Double, List<Double>> {
        return withContext(Dispatchers.IO) {
            val history = Collections.synchronizedList(mutableListOf<Double>())
            val totalBytes = sizeMb.toLong() * 1024L * 1024L
            val chunkSize = 1024L * 1024L
            val totalRead = AtomicLong(0)
            val downloadStartTime = System.currentTimeMillis()
            val startNanos = System.nanoTime()

            coroutineScope {
                repeat(streams.coerceIn(1, 6)) {
                    launch(Dispatchers.IO) {
                        val buffer = ByteArray(64 * 1024)
                        while (totalRead.get() < totalBytes && !isCancelled.get()) {
                            try {
                                val conn = openProxyConnection("$baseUrl/__down?bytes=$chunkSize")
                                conn.connectTimeout = 10000
                                conn.readTimeout = 10000
                                conn.requestMethod = "GET"
                                conn.connect()

                                if (conn.responseCode != 200 && conn.responseCode != 206) {
                                    conn.disconnect()
                                    break
                                }

                                val inputStream: InputStream = conn.inputStream
                                val chunkStartNanos = System.nanoTime()
                                var chunkBytes = 0L
                                var bytesRead: Int

                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    chunkBytes += bytesRead
                                    totalRead.addAndGet(bytesRead.toLong())
                                    if (totalRead.get() >= totalBytes) break
                                }
                                inputStream.close()
                                conn.disconnect()

                                val chunkElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - chunkStartNanos)
                                if (chunkBytes > 0 && chunkElapsedMs > 0) {
                                    history.add(chunkBytes.toDouble() / (chunkElapsedMs / 1000.0))
                                }

                                val progress = (totalRead.get().toDouble() / totalBytes).coerceIn(0.0, 1.0)
                                val elapsedSec = (System.currentTimeMillis() - downloadStartTime) / 1000.0
                                val cumulativeSpeed = if (elapsedSec > 0.3) totalRead.get().toDouble() / elapsedSec else 0.0
                                _state.update { it.copy(
                                    progress = 0.30f + (progress * 0.35f).toFloat(),
                                    currentStep = "Downloading ${formatBytes(totalRead.get())} / ${formatBytes(totalBytes)}",
                                    liveDownloadBps = cumulativeSpeed,
                                    liveDownloadTotal = totalRead.get(),
                                    livePhaseElapsed = elapsedSec.toLong()
                                ) }

                                delay(20)
                            } catch (e: Exception) {
                                LogRepository.w("Download chunk error: ${e.message}", TAG)
                            }
                        }
                    }
                }
            }

            val totalElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) / 1000.0
            val avgSpeed = if (totalElapsed > 0) totalRead.get().toDouble() / totalElapsed else 0.0

            Pair(avgSpeed, history.toList())
        }
    }


    private suspend fun measureUpload(
        baseUrl: String,
        sizeMb: Int
    ): Pair<Double, List<Double>> {
        return withContext(Dispatchers.IO) {
            val history = mutableListOf<Double>()
            val totalBytes = sizeMb.toLong() * 1024L * 1024L
            val chunkSize = 512 * 1024
            var totalWritten = 0L

            val uploadUrl = "$baseUrl/__up"
            val uploadData = ByteArray(chunkSize) { (it % 256).toByte() }

            val startNanos = System.nanoTime()
            val uploadStartTime = System.currentTimeMillis()

            try {
                while (totalWritten < totalBytes && !isCancelled.get()) {
                    val conn = openProxyConnection(uploadUrl)
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/octet-stream")
                    conn.setRequestProperty("Content-Length", uploadData.size.toString())
                    conn.connect()

                    val chunkStartNanos = System.nanoTime()
                    val outputStream = conn.outputStream
                    outputStream.write(uploadData)
                    outputStream.flush()
                    outputStream.close()

                    val code = conn.responseCode
                    conn.disconnect()

                    val chunkElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - chunkStartNanos)
                    if ((code == 200 || code == 204 || code == 201) && chunkElapsedMs > 0) {
                        totalWritten += chunkSize
                        history.add(chunkSize.toDouble() / (chunkElapsedMs / 1000.0))
                    } else if (code != 200 && code != 204 && code != 201) {
                        LogRepository.w("Upload chunk rejected by server (code $code)", TAG)
                        break
                    }

                    val progress = (totalWritten.toDouble() / totalBytes).coerceIn(0.0, 1.0)
                    val elapsedSec = (System.currentTimeMillis() - uploadStartTime) / 1000.0
                    val cumulativeSpeed = if (elapsedSec > 0.3) totalWritten.toDouble() / elapsedSec else 0.0
                    _state.update { it.copy(
                        progress = 0.70f + (progress * 0.30f).toFloat(),
                        currentStep = "Uploading ${formatBytes(totalWritten)} / ${formatBytes(totalBytes)}",
                        uploadSpeedHistory = history.toList(),
                        liveUploadBps = cumulativeSpeed,
                        liveUploadTotal = totalWritten,
                        livePhaseElapsed = elapsedSec.toLong()
                    ) }

                    delay(50)
                }
            } catch (e: Exception) {
                LogRepository.w("Upload chunk error: ${e.message}", TAG)
            }

            val totalElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) / 1000.0
            val avgSpeed = if (totalElapsed > 0) totalWritten.toDouble() / totalElapsed else 0.0

            Pair(avgSpeed, history)
        }
    }


    private fun formatSpeed(bytesPerSec: Double, config: SpeedTestConfig): String {
        return if (config.showBits) formatBitsPerSecond(bytesPerSec) else formatBytesPerSecond(bytesPerSec)
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L * 1024L * 1024L -> "${smartFormat(bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0))} PB"
            bytes >= 1024L * 1024L * 1024L * 1024L -> "${smartFormat(bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0))} TB"
            bytes >= 1024L * 1024L * 1024L -> "${smartFormat(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024L * 1024L -> "${smartFormat(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024L -> "${smartFormat(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }

    fun formatBitsPerSecond(bytesPerSec: Double): String {
        val bps = bytesPerSec * 8.0
        return when {
            bps >= 1e15 -> "${"%.2f".format(bps / 1e15)} Pb/s"
            bps >= 1e12 -> "${"%.2f".format(bps / 1e12)} Tb/s"
            bps >= 1e9 -> "${"%.2f".format(bps / 1e9)} Gb/s"
            bps >= 1e6 -> "${"%.2f".format(bps / 1e6)} Mb/s"
            bps >= 1e3 -> "${"%.1f".format(bps / 1e3)} Kb/s"
            else -> "${"%.0f".format(bps)} b/s"
        }
    }

    fun formatBytesPerSecond(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 -> "${"%.2f".format(bytesPerSec / (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0))} PB/s"
            bytesPerSec >= 1024.0 * 1024.0 * 1024.0 * 1024.0 -> "${"%.2f".format(bytesPerSec / (1024.0 * 1024.0 * 1024.0 * 1024.0))} TB/s"
            bytesPerSec >= 1024.0 * 1024.0 * 1024.0 -> "${"%.2f".format(bytesPerSec / (1024.0 * 1024.0 * 1024.0))} GB/s"
            bytesPerSec >= 1024.0 * 1024.0 -> "${"%.2f".format(bytesPerSec / (1024.0 * 1024.0))} MB/s"
            bytesPerSec >= 1024.0 -> "${"%.1f".format(bytesPerSec / 1024.0)} KB/s"
            else -> "${"%.0f".format(bytesPerSec)} B/s"
        }
    }

    private fun smartFormat(value: Double): String {
        val formatted = "%.2f".format(value)
        return formatted.trimEnd('0').trimEnd('.')
    }

    fun checkServerReachable(server: SpeedTestServer, config: SpeedTestConfig, proxyHost: String = "127.0.0.1", proxyPort: Int = 1819, throughProxy: Boolean = false): Boolean {
        return try {
            val url = resolveServerUrl(server, config)
            if (url.isBlank()) return false
            val proxy = if (throughProxy) Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort)) else Proxy.NO_PROXY
            val conn = java.net.URI(url).toURL().openConnection(proxy) as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "HEAD"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }
}
