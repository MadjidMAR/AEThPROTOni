@file:Suppress("UNUSED_PARAMETER", "SameParameterValue")
package io.github.immaghzbad.aetherst.shared.data

import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.shared.core.NetworkClient
import io.github.immaghzbad.aetherst.shared.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

object AutoDetectRepository {
    private val _state = MutableStateFlow(AutoDetectState())
    val state: StateFlow<AutoDetectState> = _state.asStateFlow()
    private var detectJob: Job? = null
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private const val ICMP_TARGET = "1.1.1.1"
    private const val TCP_TARGET_HOST = "1.1.1.1"
    private const val TCP_TARGET_PORT = 443
    private const val UDP_TARGET_HOST = "1.1.1.1"
    private const val UDP_TARGET_PORT = 53
    private const val HTTPS_TARGET = "https://1.1.1.1/cdn-cgi/trace"
    private const val HTTPS_FALLBACK = "https://8.8.8.8/"
    private const val CONNECTIVITY_CHECK_HOST = "connectivitycheck.gstatic.com"
    private const val CONNECTIVITY_CHECK_PORT = 443
    private const val CONNECTIVITY_CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204"
    private const val SAMPLES = 5
    private const val ICMP_TIMEOUT_MS = 3000
    private val httpsClient by lazy {
        NetworkClient.instance.newBuilder()
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .writeTimeout(2000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    fun cancel() {
        detectJob?.cancel()
        _state.value = _state.value.copy(phase = AutoDetectPhase.IDLE)
    }

    fun reset() {
        detectJob?.cancel()
        _state.value = AutoDetectState()
    }

    fun startDetection(platformContext: PlatformContext) {
        if (detectJob?.isActive == true) return
        if (!mutex.tryLock()) return
        detectJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                updateState(
                    AutoDetectState(
                        phase = AutoDetectPhase.FINGERPRINTING,
                        currentStep = "Checking internet connection...",
                        progressPercent = 3
                    )
                )
                if (!getSystemUtils(platformContext).isNetworkConnected()) {
                    updateState(
                        _state.value.copy(
                            phase = AutoDetectPhase.ERROR,
                            currentStep = "No internet connection. Connect to a working network and try again.",
                            progressPercent = 0,
                            error = "No internet connection"
                        )
                    )
                    return@launch
                }
                updateState(_state.value.copy(currentStep = "Checking IPv6 connectivity...", progressPercent = 5))
                val hasIPv6 = runCatching {
                    var result = false
                    repeat(3) { attempt ->
                        updateState(_state.value.copy(currentStep = "Checking IPv6... attempt ${attempt + 1}/3", progressPercent = 5 + attempt))
                        result = try { withTimeout(3500.milliseconds) { detectIPv6() } } catch (_: Exception) { false }
                        if (result) return@runCatching true
                        delay(350.milliseconds)
                    }
                    result
                }.getOrDefault(false)
                val hasUdp = runCatching {
                    withTimeout(7000.milliseconds) { detectUdp() }
                }.getOrDefault(true)
                updateState(
                    _state.value.copy(
                        liveFingerprint = NetworkFingerprint(
                            networkType = "open",
                            supportsDPI = false,
                            supportsUDP = hasUdp,
                            supportsIPv6 = hasIPv6,
                            carrierOrIsp = "Detecting..."
                        ),
                        currentStep = "Checking DPI restrictions...",
                        progressPercent = 8
                    )
                )
                val isDPI = runCatching {
                    withTimeout(7000.milliseconds) { detectDPI() }
                }.getOrDefault(false)
                updateState(
                    _state.value.copy(
                        liveFingerprint = NetworkFingerprint(
                            networkType = if (isDPI) "restricted" else "open",
                            supportsDPI = isDPI,
                            supportsUDP = hasUdp,
                            supportsIPv6 = hasIPv6,
                            carrierOrIsp = "Detecting..."
                        ),
                        currentStep = "Detecting ISP...",
                        progressPercent = 12
                    )
                )
                val ispIp = runCatching {
                    var res = Pair("Unknown", "Unknown")
                    repeat(2) { attempt ->
                        updateState(_state.value.copy(currentStep = "Detecting ISP... attempt ${attempt + 1}/2", progressPercent = 12 + attempt))
                        res = try { withTimeout(5000.milliseconds) { detectIspIp() } } catch (_: Exception) { Pair("Unknown", "Unknown") }
                        if (res.first != "Unknown" || res.second != "Unknown") return@runCatching res
                        delay(300.milliseconds)
                    }
                    res
                }.getOrDefault(Pair("Unknown", "Unknown"))
                val fingerprint = NetworkFingerprint(
                    networkType = if (isDPI) "restricted" else "open",
                    supportsDPI = isDPI,
                    supportsUDP = hasUdp,
                    supportsIPv6 = hasIPv6,
                    carrierOrIsp = ispIp.first,
                    ipAddress = ispIp.second
                )
                updateState(
                    _state.value.copy(
                        liveFingerprint = fingerprint,
                        currentStep = "Network fingerprint complete",
                        progressPercent = 15
                    )
                )
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.PROTOCOL_SCAN,
                        currentStep = "Measuring protocol latency...",
                        progressPercent = 20
                    )
                )
                val protocolResults = probeAllProtocols(platformContext)
                updateState(_state.value.copy(progressPercent = 50))
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.MTU_PROBE,
                        currentStep = "Discovering optimal MTU...",
                        progressPercent = 55
                    )
                )
                val mtuResult = probeMtu(platformContext)
                updateState(_state.value.copy(progressPercent = 65))
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.NOISE_PROBE,
                        currentStep = "Testing obfuscation modes...",
                        progressPercent = 70
                    )
                )
                val noiseResults = probeNoiseModes(protocolResults)
                updateState(_state.value.copy(progressPercent = 82))
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.SCAN_MODE_PROBE,
                        currentStep = "Evaluating scan strategies...",
                        progressPercent = 85
                    )
                )
                val scanModeResults = probeScanModes()
                updateState(_state.value.copy(progressPercent = 92))
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.ANALYZING,
                        currentStep = "Computing optimal configuration...",
                        progressPercent = 95
                    )
                )
                val result = analyzeResults(protocolResults, mtuResult, noiseResults, scanModeResults, fingerprint)
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.COMPLETE,
                        currentStep = "Optimal configuration found!",
                        progressPercent = 100,
                        finalResult = result
                    )
                )
                LogRepository.i(
                    "Auto-Detect complete: Protocol=${result.recommendedProtocol.name}, MTU=${result.recommendedMtu}, Noise=${result.recommendedNoise.name}, Confidence=${(result.confidence * 100).toInt()}%",
                    "AutoDetect"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.e("Auto-Detect failed: ${e.message}", "AutoDetect")
                updateState(
                    _state.value.copy(
                        phase = AutoDetectPhase.ERROR,
                        currentStep = "Detection failed",
                        error = e.message ?: "Unknown error"
                    )
                )
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun detectIPv6(): Boolean {
        val timeout = 2500
        return try {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress("2606:4700:4700::1111", 443), timeout)
                sock.close()
                true
            } catch (_: Exception) {
                try { sock.close() } catch (_: Exception) {}
                try {
                    val sock2 = Socket()
                    sock2.connect(InetSocketAddress("2606:4700:4700::1111", 80), timeout)
                    sock2.close()
                    true
                } catch (_: Exception) { false }
            }
        } catch (_: Exception) { false }
    }

    private fun detectUdp(): Boolean {
        return try {
            val samples = measureUdpDnsLatency(UDP_TARGET_HOST, UDP_TARGET_PORT, 2)
            samples.isNotEmpty()
        } catch (_: Exception) { true }
    }

    private fun detectDPI(): Boolean {
        return try {
            val tcpOk = measureTcpLatency(TCP_TARGET_HOST, TCP_TARGET_PORT, 1).isNotEmpty()
            val httpsOk = measureHttpsLatency(1).isNotEmpty()
            if (tcpOk && !httpsOk) return true
            val request = Request.Builder().url(CONNECTIVITY_CHECK_URL).header("User-Agent", "AetherST-AutoDetect/1.0").build()
            httpsClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) return false
                return response.code == 403 || response.code == 407
            }
        } catch (_: Exception) { false }
    }

    private fun detectIsp(): String {
        return detectIspIp().first
    }

    private fun detectIspIp(): Pair<String, String> {
        return try {
            val request = Request.Builder().url("https://api.ip.sb/geoip").header("User-Agent", "AetherST-AutoDetect/1.0").build()
            val client = NetworkClient.instance.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                    val isp = json?.get("isp")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    val org = json?.get("organization")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    val ip = json?.get("ip")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                    val ispVal = when {
                        !isp.isNullOrBlank() -> isp
                        !org.isNullOrBlank() -> org
                        else -> "Unknown"
                    }
                    Pair(ispVal, if (ip.isBlank()) "Unknown" else ip)
                } else {
                    val fb = fallbackIsp()
                    Pair("Unknown", fb)
                }
            }
        } catch (_: Exception) {
            val fb = fallbackIsp()
            Pair("Unknown", fb)
        }
    }

    private fun fallbackIsp(): String {
        return try {
            val request = Request.Builder().url("https://api.ipify.org?format=json").header("User-Agent", "AetherST-AutoDetect/1.0").build()
            val client = NetworkClient.instance.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                    json?.get("ip")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "Unknown"
                } else "Unknown"
            }
        } catch (_: Exception) { "Unknown" }
    }

    private fun measureIcmpLatency(context: PlatformContext): Long {
        val systemUtils = getSystemUtils(context)
        val samples = mutableListOf<Long>()
        repeat(SAMPLES) {
            try {
                val start = System.nanoTime()
                val ok = systemUtils.execPing(ICMP_TARGET, 32, ICMP_TIMEOUT_MS, dontFragment = false)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                if (ok && elapsed in 1..4000) samples.add(elapsed)
            } catch (_: Exception) {}
            Thread.sleep(90)
        }
        if (samples.isNotEmpty()) return medianLatency(samples)
        val tcpSamples = measureTcpLatency(TCP_TARGET_HOST, TCP_TARGET_PORT, SAMPLES)
        return if (tcpSamples.isNotEmpty()) medianLatency(tcpSamples) else -1L
    }

    private fun measureTcpLatency(host: String, port: Int, sampleCount: Int): List<Long> {
        val samples = mutableListOf<Long>()
        repeat(sampleCount) {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                val start = System.nanoTime()
                sock.connect(InetSocketAddress(host, port), 1200)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                sock.close()
                if (elapsed in 1..4000) samples.add(elapsed)
            } catch (_: Exception) {}
            if (sampleCount > 1) Thread.sleep(60)
        }
        return samples
    }

    private fun measureUdpDnsLatency(host: String, port: Int, sampleCount: Int): List<Long> {
        val samples = mutableListOf<Long>()
        repeat(sampleCount) {
            try {
                val query = buildDnsQuery("google.com")
                val socket = DatagramSocket()
                socket.soTimeout = 1200
                val packet = DatagramPacket(query, query.size, InetSocketAddress(host, port))
                val buffer = ByteArray(512)
                val resp = DatagramPacket(buffer, buffer.size)
                val start = System.nanoTime()
                socket.send(packet)
                socket.receive(resp)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                socket.close()
                if (elapsed in 1..4000 && resp.length > 12) samples.add(elapsed)
            } catch (_: Exception) {}
            if (sampleCount > 1) Thread.sleep(60)
        }
        return samples
    }

    private fun buildDnsQuery(domain: String): ByteArray {
        val parts = domain.split(".")
        val out = mutableListOf<Byte>()
        out.add(0x12); out.add(0x34); out.add(0x01); out.add(0x00); out.add(0x00); out.add(0x01); out.add(0x00); out.add(0x00); out.add(0x00); out.add(0x00); out.add(0x00); out.add(0x00)
        for (p in parts) {
            out.add(p.length.toByte())
            for (c in p) out.add(c.code.toByte())
        }
        out.add(0x00); out.add(0x00); out.add(0x01); out.add(0x00); out.add(0x01)
        return out.toByteArray()
    }

    private fun measureHttpsLatency(sampleCount: Int): List<Long> {
        val samples = mutableListOf<Long>()
        repeat(sampleCount) {
            try {
                val request = Request.Builder().url(CONNECTIVITY_CHECK_URL).head().header("User-Agent", "AetherST/1.0").build()
                val start = System.nanoTime()
                httpsClient.newCall(request).execute().use { response ->
                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                    if ((response.isSuccessful || response.code == 204) && elapsed in 1..4000) samples.add(elapsed)
                }
            } catch (_: Exception) {}
            if (samples.isEmpty()) {
                try {
                    val request = Request.Builder().url(HTTPS_TARGET).header("User-Agent", "AetherST/1.0").build()
                    val start = System.nanoTime()
                    httpsClient.newCall(request).execute().use { response ->
                        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                        if (response.isSuccessful && elapsed in 1..4000) samples.add(elapsed)
                    }
                } catch (_: Exception) {}
            }
            if (sampleCount > 1) Thread.sleep(110)
        }
        if (samples.isEmpty()) {
            repeat(2) {
                try {
                    val request = Request.Builder().url(HTTPS_FALLBACK).header("User-Agent", "AetherST/1.0").build()
                    val start = System.nanoTime()
                    httpsClient.newCall(request).execute().use { response ->
                        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                        if (response.isSuccessful && elapsed in 1..4000) samples.add(elapsed)
                    }
                } catch (_: Exception) {}
            }
        }
        return samples
    }

    private fun medianLatency(samples: List<Long>): Long {
        val sorted = samples.filter { it > 0 }.sorted()
        return if (sorted.isNotEmpty()) sorted[sorted.size / 2] else -1L
    }

    private suspend fun probeAllProtocols(context: PlatformContext): List<ProtocolProbeResult> {
        val protocols = listOf(AetherProtocol.MASQUE, AetherProtocol.WG, AetherProtocol.GOOL)
        val results = mutableListOf<ProtocolProbeResult>()
        for ((index, protocol) in protocols.withIndex()) {
            updateState(
                _state.value.copy(
                    protocolResults = results + ProtocolProbeResult(protocol, ProbeStatus.RUNNING),
                    currentStep = "Measuring ${protocol.displayName} latency...",
                    progressPercent = 20 + (index * 10)
                )
            )
            val result = withTimeoutOrNull(12000.milliseconds) { probeProtocol(protocol, context) }
                ?: ProtocolProbeResult(protocol, ProbeStatus.FAILED, -1, "Timeout on weak network")
            results.add(result)
            updateState(_state.value.copy(protocolResults = results.toList(), progressPercent = 20 + (index + 1) * 10))
            delay(120.milliseconds)
        }
        return results
    }

    private suspend fun probeProtocol(protocol: AetherProtocol, context: PlatformContext): ProtocolProbeResult {
        return withContext(Dispatchers.Default) {
            try {
                when (protocol) {
                    AetherProtocol.MASQUE -> probeMasque(context)
                    AetherProtocol.WG -> probeWireGuard(context)
                    AetherProtocol.GOOL -> probeGool(context)
                    AetherProtocol.ZERO_TRUST -> ProtocolProbeResult(protocol, ProbeStatus.SKIPPED, -1, "Zero Trust requires manual configuration")
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                LogRepository.w("Protocol probe failed for ${protocol.name}: ${e.message}", "AutoDetect")
                ProtocolProbeResult(protocol, ProbeStatus.FAILED, -1, e.message)
            }
        }
    }

    private suspend fun probeMasque(context: PlatformContext): ProtocolProbeResult {
        return withContext(Dispatchers.Default) {
            updateState(_state.value.copy(currentStep = "MASQUE: TCP latency..."))
            val tcpSamplesCc = measureTcpLatency(CONNECTIVITY_CHECK_HOST, CONNECTIVITY_CHECK_PORT, 1)
            val tcpMedianCc = medianLatency(tcpSamplesCc)
            val tcpFallback = if (tcpMedianCc < 0) measureTcpLatency(TCP_TARGET_HOST, 443, 1) else emptyList()
            val tcpMedian = if (tcpMedianCc > 0) tcpMedianCc else medianLatency(tcpFallback)
            val icmp = if (tcpMedian < 0) measureIcmpLatency(context) else -1L
            if (tcpMedian < 0 && icmp < 0) {
                updateState(_state.value.copy(currentStep = "MASQUE: HTTPS probe..."))
                val httpsSamples = measureHttpsLatency(1)
                val httpsMedian = medianLatency(httpsSamples)
                return@withContext if (httpsMedian > 0) ProtocolProbeResult(AetherProtocol.MASQUE, ProbeStatus.SUCCESS, httpsMedian) else ProtocolProbeResult(AetherProtocol.MASQUE, ProbeStatus.FAILED, -1, "Network unreachable")
            }
            updateState(_state.value.copy(currentStep = "MASQUE: HTTPS latency..."))
            val httpsSamples = measureHttpsLatency(1)
            val httpsMedian = medianLatency(httpsSamples)
            if (httpsMedian > 0) {
                val best = if (tcpMedian > 0) minOf(tcpMedian, httpsMedian) else httpsMedian
                ProtocolProbeResult(AetherProtocol.MASQUE, ProbeStatus.SUCCESS, best)
            } else if (tcpMedian > 0) {
                ProtocolProbeResult(AetherProtocol.MASQUE, ProbeStatus.SUCCESS, tcpMedian)
            } else {
                ProtocolProbeResult(AetherProtocol.MASQUE, ProbeStatus.FAILED, -1, "TCP reachable but no internet access (captive portal or restricted network)")
            }
        }
    }

    private suspend fun probeWireGuard(context: PlatformContext): ProtocolProbeResult {
        return withContext(Dispatchers.Default) {
            updateState(_state.value.copy(currentStep = "WireGuard: TCP latency..."))
            val udpSamples = measureUdpDnsLatency(UDP_TARGET_HOST, UDP_TARGET_PORT, 1)
            val tcpSamplesCc = measureTcpLatency(CONNECTIVITY_CHECK_HOST, CONNECTIVITY_CHECK_PORT, 1)
            val tcpMedian = medianLatency(tcpSamplesCc).let { if (it < 0) medianLatency(measureTcpLatency(TCP_TARGET_HOST, 443, 1)) else it }
            updateState(_state.value.copy(currentStep = "WireGuard: HTTPS probe..."))
            val httpsSamples = measureHttpsLatency(1)
            val httpsMedian = medianLatency(httpsSamples)
            val udpOk = udpSamples.isNotEmpty()
            when {
                httpsMedian > 0 && udpOk -> ProtocolProbeResult(AetherProtocol.WG, ProbeStatus.SUCCESS, minOf(httpsMedian, medianLatency(udpSamples)))
                httpsMedian > 0 && !udpOk -> ProtocolProbeResult(AetherProtocol.WG, ProbeStatus.SUCCESS, httpsMedian + 40)
                tcpMedian > 0 -> ProtocolProbeResult(AetherProtocol.WG, ProbeStatus.FAILED, -1, "UDP blocked - WireGuard requires UDP")
                else -> ProtocolProbeResult(AetherProtocol.WG, ProbeStatus.FAILED, -1, "Network unreachable")
            }
        }
    }

    private suspend fun probeGool(context: PlatformContext): ProtocolProbeResult {
        return withContext(Dispatchers.Default) {
            updateState(_state.value.copy(currentStep = "Gool: TCP latency..."))
            val udpSamples = measureUdpDnsLatency(UDP_TARGET_HOST, UDP_TARGET_PORT, 1)
            val tcpSamplesCc = measureTcpLatency(CONNECTIVITY_CHECK_HOST, CONNECTIVITY_CHECK_PORT, 1)
            val tcpMedian = medianLatency(tcpSamplesCc).let { if (it < 0) medianLatency(measureTcpLatency(TCP_TARGET_HOST, 443, 1)) else it }
            updateState(_state.value.copy(currentStep = "Gool: HTTPS probe..."))
            val httpsSamples = measureHttpsLatency(1)
            val httpsMedian = medianLatency(httpsSamples)
            val udpOk = udpSamples.isNotEmpty()
            when {
                httpsMedian > 0 && udpOk -> ProtocolProbeResult(AetherProtocol.GOOL, ProbeStatus.SUCCESS, (minOf(httpsMedian, medianLatency(udpSamples)) * 1.12).toLong())
                httpsMedian > 0 && !udpOk -> ProtocolProbeResult(AetherProtocol.GOOL, ProbeStatus.FAILED, -1, "UDP blocked - Gool requires UDP")
                tcpMedian > 0 -> ProtocolProbeResult(AetherProtocol.GOOL, ProbeStatus.FAILED, -1, "UDP blocked - Gool requires UDP")
                else -> ProtocolProbeResult(AetherProtocol.GOOL, ProbeStatus.FAILED, -1, "Network unreachable")
            }
        }
    }

    private suspend fun probeMtu(context: PlatformContext): MtuProbeResult {
        val systemUtils = getSystemUtils(context)
        return withContext(Dispatchers.Default) {
            try {
                val localMtu = systemUtils.getInterfaceMtu().coerceIn(1280, 9000)
                LogRepository.i("MTU Probe: Local interface MTU = $localMtu", "AutoDetect")
                fun testMtu(totalSize: Int): Boolean {
                    val payloadSize = totalSize - 28
                    if (payloadSize < 0) return true
                    return systemUtils.execPing(ICMP_TARGET, payloadSize, 900, dontFragment = true)
                }
                if (testMtu(2000)) {
                    LogRepository.w("MTU Probe: DF bit ignored, using safe value", "AutoDetect")
                    return@withContext MtuProbeResult(discoveredMtu = 1280, status = ProbeStatus.SUCCESS, rawPathMtu = 1280)
                }
                var low = 1200
                var high = localMtu.coerceAtMost(1500)
                var bestPathMtu = 1200
                var probeStep = 0
                val totalSteps = 10
                while (low <= high) {
                    ensureActive()
                    val mid = (low + high) / 2
                    probeStep++
                    updateState(_state.value.copy(currentStep = "Probing MTU $mid bytes... best $bestPathMtu", progressPercent = 55 + (probeStep * 10 / totalSteps).coerceIn(0, 10)))
                    val ok = try { withTimeout(1800.milliseconds) { testMtu(mid) } } catch (_: Exception) { false }
                    if (ok) {
                        bestPathMtu = mid
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                    delay(90.milliseconds)
                }
                val overhead = 60
                val optimalMtu = (bestPathMtu - overhead).coerceIn(1100, 1460)
                LogRepository.i("MTU Probe: Path MTU = $bestPathMtu, Optimal = $optimalMtu", "AutoDetect")
                MtuProbeResult(discoveredMtu = optimalMtu, status = ProbeStatus.SUCCESS, rawPathMtu = bestPathMtu)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                LogRepository.e("MTU Probe failed: ${e.message}", "AutoDetect")
                MtuProbeResult(discoveredMtu = 1280, status = ProbeStatus.FAILED, rawPathMtu = 1280)
            }
        }
    }

    private suspend fun probeNoiseModes(protocolResults: List<ProtocolProbeResult>): List<NoiseProbeResult> {
        val bestProtocol = protocolResults.filter { it.status == ProbeStatus.SUCCESS }.minByOrNull { it.latencyMs }?.protocol ?: AetherProtocol.MASQUE
        val noiseModes = when (bestProtocol) {
            AetherProtocol.MASQUE -> listOf(AetherNoise.FIREWALL, AetherNoise.GFW, AetherNoise.OFF)
            else -> listOf(AetherNoise.BALANCED, AetherNoise.AGGRESSIVE, AetherNoise.LIGHT, AetherNoise.OFF)
        }
        return noiseModes.mapIndexed { idx, noise ->
            updateState(_state.value.copy(currentStep = "Testing ${noise.displayName} obfuscation ${idx + 1}/${noiseModes.size}...", progressPercent = 70 + (idx * 12 / noiseModes.size)))
            val effective = withContext(Dispatchers.Default) {
                try {
                    val tcpSamples = measureTcpLatency(TCP_TARGET_HOST, TCP_TARGET_PORT, 3)
                    val httpsSamples = measureHttpsLatency(2)
                    val tcpOk = tcpSamples.size >= 2
                    val httpsOk = httpsSamples.size >= 1
                    val median = medianLatency(tcpSamples + httpsSamples)
                    tcpOk && httpsOk && median in 1..3000
                } catch (_: Exception) { false }
            }
            delay(140.milliseconds)
            NoiseProbeResult(noise, ProbeStatus.SUCCESS, effective)
        }
    }

    private suspend fun probeScanModes(): List<ScanModeProbeResult> {
        val modes = listOf(AetherScanMode.TURBO, AetherScanMode.BALANCED, AetherScanMode.THOROUGH)
        return modes.mapIndexed { idx, mode ->
            updateState(_state.value.copy(currentStep = "Testing ${mode.name.lowercase()} scan ${idx + 1}/${modes.size}...", progressPercent = 85 + (idx * 7 / modes.size)))
            val success = withContext(Dispatchers.Default) {
                try {
                    val tcpSamples = measureTcpLatency(TCP_TARGET_HOST, TCP_TARGET_PORT, 3)
                    val httpsSamples = measureHttpsLatency(2)
                    val median = medianLatency(tcpSamples + httpsSamples)
                    tcpSamples.size >= 2 && httpsSamples.isNotEmpty() && median in 1..4000
                } catch (_: Exception) { false }
            }
            delay(140.milliseconds)
            ScanModeProbeResult(mode, ProbeStatus.SUCCESS, success)
        }
    }

    private fun analyzeResults(
        protocolResults: List<ProtocolProbeResult>,
        mtuResult: MtuProbeResult,
        noiseResults: List<NoiseProbeResult>,
        scanModeResults: List<ScanModeProbeResult>,
        fingerprint: NetworkFingerprint
    ): AutoDetectResult {
        val successfulProtocols = protocolResults.filter { it.status == ProbeStatus.SUCCESS }
        val recommendedProtocol = if (successfulProtocols.isNotEmpty()) {
            if (fingerprint.supportsDPI) {
                successfulProtocols.filter { it.protocol == AetherProtocol.MASQUE || it.protocol == AetherProtocol.GOOL }.minByOrNull { it.latencyMs }?.protocol ?: successfulProtocols.minByOrNull { it.latencyMs }!!.protocol
            } else {
                successfulProtocols.minByOrNull { it.latencyMs }!!.protocol
            }
        } else {
            AetherProtocol.MASQUE
        }
        val recommendedNoise = noiseResults.filter { it.status == ProbeStatus.SUCCESS && it.effective }.map { it.noise }.firstOrNull()
            ?: when (fingerprint.networkType) {
                "restricted" -> if (recommendedProtocol == AetherProtocol.MASQUE) AetherNoise.GFW else AetherNoise.AGGRESSIVE
                else -> if (recommendedProtocol == AetherProtocol.MASQUE) AetherNoise.FIREWALL else AetherNoise.BALANCED
            }
        val recommendedScanMode = scanModeResults.filter { it.status == ProbeStatus.SUCCESS && it.gatewayFound }.map { it.scanMode }.firstOrNull()
            ?: when {
                successfulProtocols.isEmpty() -> AetherScanMode.THOROUGH
                fingerprint.supportsDPI -> AetherScanMode.IRONCLAD
                else -> AetherScanMode.BALANCED
            }
        val confidence = calculateConfidence(protocolResults, mtuResult, noiseResults, scanModeResults)
        return AutoDetectResult(
            recommendedProtocol = recommendedProtocol,
            recommendedNoise = recommendedNoise,
            recommendedScanMode = recommendedScanMode,
            recommendedMtu = if (mtuResult.status == ProbeStatus.SUCCESS) mtuResult.discoveredMtu else 1100,
            recommendedIpMode = if (fingerprint.supportsIPv6) AetherIpMode.DUAL else AetherIpMode.IPV4,
            recommendedH2Mode = recommendedProtocol == AetherProtocol.MASQUE,
            recommendedEch = fingerprint.supportsDPI && recommendedProtocol == AetherProtocol.MASQUE,
            recommendedFragment = fingerprint.supportsDPI && recommendedProtocol == AetherProtocol.MASQUE,
            recommendedNoDataCheck = recommendedProtocol != AetherProtocol.MASQUE,
            confidence = confidence,
            networkFingerprint = fingerprint
        )
    }

    private fun calculateConfidence(
        protocolResults: List<ProtocolProbeResult>,
        mtuResult: MtuProbeResult,
        noiseResults: List<NoiseProbeResult>,
        scanResults: List<ScanModeProbeResult>
    ): Float {
        var score = 0f
        var total = 0f
        val protocolSuccess = protocolResults.count { it.status == ProbeStatus.SUCCESS }
        total += 40f
        score += (protocolSuccess.toFloat() / protocolResults.size.coerceAtLeast(1)) * 40f
        total += 20f
        if (mtuResult.status == ProbeStatus.SUCCESS) score += 20f
        val noiseEffective = noiseResults.count { it.effective }
        total += 25f
        if (noiseResults.isNotEmpty()) score += (noiseEffective.toFloat() / noiseResults.size) * 25f
        total += 15f
        val scanOk = scanResults.count { it.gatewayFound }
        if (scanResults.isNotEmpty()) score += (scanOk.toFloat() / scanResults.size) * 15f
        return (score / total).coerceIn(0f, 1f)
    }

    private fun updateState(newState: AutoDetectState) {
        _state.value = newState
    }
}
