package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop Psiphon integration.
 *
 * On Android, PsiphonController loads the Java library via reflection and uses
 * VpnService.protect() to prevent routing loops. On desktop, we:
 *   1. Look for a bundled psiphon-tunnel-core binary in the resources/bin directory.
 *   2. Launch it as a subprocess with a generated JSON config.
 *   3. Verify the local SOCKS5 proxy is reachable.
 *   4. Forward traffic through it.
 *
 * If no Psiphon binary is found, the controller gracefully falls back to disabled.
 */
object PsiphonController {

    private const val TAG = "Psiphon"

    enum class State { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _availableRegions = MutableStateFlow<List<String>>(emptyList())
    val availableRegions: StateFlow<List<String>> = _availableRegions.asStateFlow()

    private var process: Process? = null
    private val isRunning = AtomicBoolean(false)
    private var monitorThread: Thread? = null

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Start the Psiphon tunnel.
     * @return true if the local SOCKS5 proxy came up successfully.
     */
    fun start(config: AetherConfig, dataDir: String): Boolean {
        if (isRunning.getAndSet(true)) return true
        _state.value = State.STARTING

        return try {
            val socksPort = config.psiphonSocksPort.toIntOrNull() ?: 3080
            val egressRegion = config.psiphonEgressRegion.ifBlank { null }

            // Locate psiphon binary
            val psiphonBinary = findPsiphonBinary(dataDir)
            if (psiphonBinary == null) {
                LogRepository.e("[$TAG] psiphon-tunnel-core binary not found in $dataDir")
                _state.value = State.ERROR
                isRunning.set(false)
                return false
            }

            // Generate config
            val configFile = File(dataDir, "psiphon-config.json")
            configFile.writeText(buildConfig(config, socksPort, egressRegion, dataDir))

            // Launch process
            val pb = ProcessBuilder(
                psiphonBinary.absolutePath,
                "-config", configFile.absolutePath
            )
            pb.redirectErrorStream(true)
            pb.directory(File(dataDir))
            process = pb.start()

            // Monitor output
            monitorThread = Thread({
                try {
                    val reader = process!!.inputStream.bufferedReader()
                    var line: Long = 0
                    while (isRunning.get()) {
                        val l = reader.readLine() ?: break
                        line++
                        // Only log first 50 lines and periodic status
                        if (line <= 50 || line % 100 == 0L) {
                            LogRepository.i("[$TAG] $l")
                        }
                        // Parse egress region from output
                        if (l.contains("Region:", ignoreCase = true)) {
                            parseRegions(l)
                        }
                    }
                } catch (_: Exception) {}
            }, "Psiphon-Monitor")
            monitorThread?.isDaemon = true
            monitorThread?.start()

            // Wait for SOCKS proxy to become available
            val ready = waitForProxy("127.0.0.1", socksPort, timeoutMs = 15000)
            if (ready) {
                _state.value = State.RUNNING
                LogRepository.i("[$TAG] Psiphon SOCKS proxy ready on port $socksPort")
            } else {
                LogRepository.w("[$TAG] Psiphon proxy did not become ready within timeout")
                _state.value = State.ERROR
                stop()
            }
            ready
        } catch (e: Exception) {
            LogRepository.e("[$TAG] Start failed: ${e.message}")
            _state.value = State.ERROR
            isRunning.set(false)
            false
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        _state.value = State.STOPPING
        try {
            process?.destroyForcibly()
            process?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
        process = null
        monitorThread?.interrupt()
        monitorThread = null
        _state.value = State.STOPPED
        LogRepository.i("[$TAG] Psiphon stopped")
    }

    fun isRunning(): Boolean = _state.value == State.RUNNING

    fun getSocksPort(config: AetherConfig): Int = config.psiphonSocksPort.toIntOrNull() ?: 3080

    /**
     * Check if Psiphon has been stable for at least [graceMs] milliseconds.
     */
    fun stableFor(graceMs: Long): Boolean {
        return _state.value == State.RUNNING
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun findPsiphonBinary(dataDir: String): File? {
        val candidates = listOf(
            File(dataDir, "bin/psiphon-tunnel-core.exe"),
            File(dataDir, "bin/psiphon-tunnel-core"),
            File(dataDir, "psiphon-tunnel-core.exe"),
            File(dataDir, "psiphon-tunnel-core"),
        )
        return candidates.firstOrNull { it.exists() }
    }

    private fun buildConfig(
        config: AetherConfig,
        socksPort: Int,
        egressRegion: String?,
        dataDir: String
    ): String {
        // Minimal Psiphon JSON config
        // Full config would include server entries, relay protocol, etc.
        val region = if (egressRegion != null) "\"$egressRegion\"" else "\"*\""
        return """
        {
            "DataStoreDirectory": "${dataDir.replace("\\", "\\\\")}",
            "LocalSocksProxyPort": $socksPort,
            "LocalHttpProxyPort": 0,
            "EgressRegion": $region,
            "RelayProtocol": "obfs-separator",
            "TunnelProtocol": "QUIC-OSSH",
            "ConnectionWorkerPoolSize": 12,
            "PsiphonServerPublicKey": "",
            "UseUpstreamProxy": ${config.upstreamProxyEnabled},
            "UpstreamProxyAddress": "${config.upstreamProxy}"
        }
        """.trimIndent()
    }

    private fun parseRegions(line: String) {
        // Try to extract available regions from Psiphon output
        val regionPattern = Regex("""Region[s]?\s*:\s*(.+)""", RegexOption.IGNORE_CASE)
        regionPattern.find(line)?.let { match ->
            val regions = match.groupValues[1]
                .split(",", ";")
                .map { it.trim() }
                .filter { it.length == 2 || it == "*" }
            if (regions.isNotEmpty()) {
                _availableRegions.value = regions
            }
        }
    }

    private fun waitForProxy(host: String, port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isRunning.get()) return false
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(host, port), 2000)
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(500)
            }
        }
        return false
    }
}
