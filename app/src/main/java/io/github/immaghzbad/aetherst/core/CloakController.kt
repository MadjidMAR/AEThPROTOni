package io.github.immaghzbad.aetherst.core

import android.content.Context
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import java.io.File
import java.net.ServerSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CloakController {
    @Volatile private var cloakPort: Int = 40443
    @Volatile private var running = false
    private val tailScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var tailJob: kotlinx.coroutines.Job? = null

    fun isSupported(config: AetherConfig): Boolean {
        return config.cloakEnabled && config.protocol == AetherProtocol.MASQUE && config.h2Mode
    }

    fun getCloakPort(): Int = cloakPort

    fun prepareConfig(context: Context, config: AetherConfig): String {
        val dir = File(context.filesDir, "cloak")
        if (!dir.exists()) dir.mkdirs()
        val confFile = File(dir, "cloak.conf")
        val statsFile = File(dir, "cloak.stats")
        val logFile = File(dir, "cloak.log")
        val port = findFreePort()
        cloakPort = port
        val sni = config.cloakSniList.ifEmpty { "www.hcaptcha.com,www.speedtest.net,www.bing.com" }
        val ttl = config.cloakTtlList.ifEmpty { "4,5,6,8" }
        val fallback = config.cloakFallbackPorts.ifEmpty { "443,2053,2083,2087,2096,8443" }
        val connectList = buildConnectList(context, config)
        val content = buildString {
            appendLine("listen_port = $port")
            appendLine("connect_list = $connectList")
            appendLine("fallback_ports = $fallback")
            appendLine("sni_list = $sni")
            appendLine("ttl_list = $ttl")
            appendLine("jitter_min_ms = ${config.cloakJitterMin}")
            appendLine("jitter_max_ms = ${config.cloakJitterMax}")
            appendLine("fragment = ${config.cloakFragment}")
            appendLine("adaptive = ${config.cloakAdaptive}")
            appendLine("randomize_sni_case = ${config.cloakRandomizeSniCase}")
            appendLine("log_level = ${config.cloakLogLevel}")
            appendLine("log_file = ${logFile.absolutePath}")
            appendLine("summary_interval_sec = 60")
        }
        confFile.writeText(content)
        try {
            confFile.setReadable(false, false)
            confFile.setReadable(true, true)
            confFile.setWritable(false, false)
            confFile.setWritable(true, true)
        } catch (_: Exception) {}
        runCatching {
            if (logFile.exists() && logFile.length() > 1024 * 1024) {
                logFile.writeText("")
            }
            logFile.setReadable(false, false)
            logFile.setReadable(true, true)
            logFile.setWritable(false, false)
            logFile.setWritable(true, true)
        }
        if (!statsFile.exists()) statsFile.writeText("")
        return confFile.absolutePath
    }

    private fun buildConnectList(context: Context, config: AetherConfig): String {
        val peer = config.peer.trim()
        if (peer.isNotEmpty()) {
            val host = peer.substringBefore(":").substringAfter("[").substringBefore("]")
            val portPart = if (peer.contains(":")) peer.substringAfterLast(":") else ""
            val port = portPart.toIntOrNull()
            if (host.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) || host.contains(":")) {
                return if (port != null) "$host:$port" else host
            }
        }
        val cached = getCachedGateway(context)
        if (cached != null) return cached
        return "162.159.198.79:443"
    }

    private fun getCachedGateway(context: Context): String? {
        return try {
            val dir = context.filesDir
            val files = dir.listFiles()?.filter { it.name.contains("lastconn") } ?: emptyList()
            val regex = Regex("""\d+\.\d+\.\d+\.\d+:\d+""")
            for (file in files) {
                val text = try { file.readText() } catch (e: Exception) {
                    LogRepository.w("read lastconn ${file.name} failed: ${e.message}", "Cloak")
                    continue
                }
                val match = regex.find(text)
                if (match != null) return match.value
            }
            val fallbackFiles = listOf(File(dir, "aether-masque.toml"), File(dir, "aether.toml"))
            for (file in fallbackFiles) {
                if (!file.exists()) continue
                val text = try { file.readText() } catch (e: Exception) {
                    LogRepository.w("read fallback ${file.name} failed: ${e.message}", "Cloak")
                    continue
                }
                val match = regex.find(text)
                if (match != null) return match.value
            }
            null
        } catch (e: Exception) {
            LogRepository.w("getCachedGateway failed: ${e.message}", "Cloak")
            null
        }
    }

    private fun findFreePort(): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (e: Exception) {
            LogRepository.w("findFreePort failed: ${e.message}, using 40443", "Cloak")
            40443
        }
    }

    fun start(context: Context, config: AetherConfig): Boolean {
        if (!isSupported(config)) return false
        if (!CloakNative.isAvailable()) {
            LogRepository.w("Cloak native library not available", "Cloak")
            return false
        }
        try {
            val level = when (config.cloakLogLevel) {
                "error" -> 0
                "warn" -> 1
                "info" -> 2
                else -> 3
            }
            CloakNative.setLogLevel(level)
            val confPath = prepareConfig(context, config)
            val rc = CloakNative.start(confPath)
            running = rc == 0
            if (running) {
                LogRepository.i("Cloak started on 127.0.0.1:$cloakPort conf=$confPath", "Cloak")
                startLogTail(context)
            } else {
                LogRepository.e("Cloak start failed rc=$rc", "Cloak")
            }
            return running
        } catch (e: Exception) {
            LogRepository.e("Cloak start exception: ${e.message}", "Cloak")
            return false
        }
    }

    fun stop() {
        if (!running) return
        try {
            CloakNative.stop()
            LogRepository.i("Cloak stopped", "Cloak")
        } catch (e: Exception) {
            LogRepository.w("Cloak stop failed: ${e.message}", "Cloak")
        }
        running = false
        tailJob?.cancel()
        tailJob = null
    }

    fun isRunning(): Boolean {
        return try { CloakNative.isRunning() != 0 } catch (e: Exception) {
            LogRepository.w("Cloak isRunning check failed: ${e.message}", "Cloak")
            running
        }
    }

    private fun startLogTail(context: Context) {
        try {
            val logFile = File(File(context.filesDir, "cloak"), "cloak.log")
            tailJob?.cancel()
            tailJob = tailScope.launch {
                var offset = logFile.length()
                while (isRunning()) {
                    try {
                        if (logFile.exists() && logFile.length() > offset) {
                            val raf = java.io.RandomAccessFile(logFile, "r")
                            raf.seek(offset)
                            val bytes = ByteArray((logFile.length() - offset).toInt())
                            raf.readFully(bytes)
                            offset = logFile.length()
                            raf.close()
                            val text = String(bytes)
                            text.lineSequence().filter { it.isNotBlank() }.forEach {
                                LogRepository.i(it, "CloakCore")
                            }
                        }
                    } catch (e: Exception) {
                        LogRepository.w("Cloak log tail read failed: ${e.message}", "Cloak")
                    }
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            LogRepository.w("startLogTail failed: ${e.message}", "Cloak")
        }
    }

    fun getEffectivePeer(config: AetherConfig): String {
        return if (isRunning()) "127.0.0.1:$cloakPort" else config.peer
    }
}
