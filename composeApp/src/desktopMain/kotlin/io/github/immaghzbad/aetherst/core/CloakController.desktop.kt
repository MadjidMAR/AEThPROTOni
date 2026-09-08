package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.shared.core.Elevation
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import java.io.File
import java.net.ServerSocket

object CloakController {
    private var cloakProcess: Process? = null
    @Volatile private var cloakPort: Int = 40443
    @Volatile private var running = false
    private var logTailThread: Thread? = null

    fun isAvailable(): Boolean = true

    fun isSupported(config: AetherConfig): Boolean {
        if (!config.cloakEnabled) return false
        if (config.protocol != AetherProtocol.MASQUE) return false
        if (!config.h2Mode) return false
        return true
    }

    fun getCloakPort(): Int = cloakPort

    fun prepareConfig(context: PlatformContext, config: AetherConfig): String {
        val baseDir = File(getSystemUtils(context).getFilesDir(), "cloak")
        if (!baseDir.exists()) baseDir.mkdirs()
        val confFile = File(baseDir, "cloak.conf")
        val statsFile = File(baseDir, "cloak.stats")
        val logFile = File(baseDir, "cloak.log")
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
            appendLine("log_file = ${logFile.absolutePath.replace("\\", "/")}")
            appendLine("summary_interval_sec = 60")
        }
        confFile.writeText(content)
        if (!statsFile.exists()) statsFile.writeText("")
        return confFile.absolutePath
    }

    private fun buildConnectList(context: PlatformContext, config: AetherConfig): String {
        val peer = config.peer.trim()
        if (peer.isNotEmpty()) {
            val host = peer.substringBefore(":").substringAfter("[").substringBefore("]")
            val portPart = if (peer.contains(":")) peer.substringAfterLast(":") else ""
            val port = portPart.toIntOrNull()
            if (host.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) || host.contains(":")) {
                return if (port != null) "$host:$port" else host
            }
        }
        getCachedGateway(context)?.let { return it }
        return "162.159.198.79:443"
    }

    private fun getCachedGateway(context: PlatformContext): String? {
        return try {
            val dir = File(getSystemUtils(context).getFilesDir())
            val files = dir.listFiles()?.filter { it.name.contains("lastconn") } ?: emptyList()
            val regex = Regex("""\d+\.\d+\.\d+\.\d+:\d+""")
            for (file in files) {
                val text = try { file.readText() } catch (_: Throwable) { continue }
                regex.find(text)?.let { return it.value }
            }
            val fallbackFiles = listOf(File(dir, "aether-masque.toml"), File(dir, "aether.toml"))
            for (file in fallbackFiles) {
                if (!file.exists()) continue
                val text = try { file.readText() } catch (_: Throwable) { continue }
                regex.find(text)?.let { return it.value }
            }
            null
        } catch (_: Throwable) { null }
    }

    private fun findFreePort(): Int {
        return try { ServerSocket(0).use { it.localPort } } catch (_: Throwable) { 40443 }
    }

    private fun findCloakBinary(): File? {
        return try {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val binName = if (isWindows) "cloak.exe" else "cloak"
            System.getProperty("compose.application.resources.dir")?.let { resourcesDir ->
                val candidates = listOf(
                    File(resourcesDir, binName),
                    File(resourcesDir, "bin/$binName"),
                    File(File(resourcesDir, "app/resources"), binName),
                    File(File(resourcesDir, "app/resources/bin"), binName)
                )
                for (f in candidates) if (f.exists()) return f
            }
            val appData = System.getenv("AppData") ?: System.getProperty("user.home")
            val target = File(appData, "AetherST-Tunnel/bin/$binName")
            if (target.exists()) return target
            val userDir = File(System.getProperty("user.dir"))
            val devCandidates = listOf(File(userDir, binName), File(userDir, "bin/$binName"), File(userDir, "src/desktopMain/resources/bin/$binName"))
            for (f in devCandidates) if (f.exists()) return f
            null
        } catch (_: Throwable) { null }
    }

    fun start(context: PlatformContext, config: AetherConfig): Boolean {
        if (!isSupported(config)) return false
        if (running) return true
        return try {
            val confPath = prepareConfig(context, config)
            val bin = findCloakBinary()
            if (bin != null && bin.exists()) {
                if (!Elevation.isElevated()) {
                    LogRepository.w("Cloak on Windows requires admin for raw TTL decoy; continuing", "Cloak")
                }
                val level = when (config.cloakLogLevel) {
                    "error" -> 0
                    "warn" -> 1
                    "info" -> 2
                    else -> 3
                }
                try { CloakNative.setLogLevel(level) } catch (_: Throwable) {}
                val baseDir = File(getSystemUtils(context).getFilesDir(), "cloak")
                val pb = ProcessBuilder(bin.absolutePath, confPath)
                pb.directory(baseDir)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                cloakProcess = proc
                Thread.sleep(800)
                val alive = proc.isAlive
                running = alive
                if (alive) {
                    LogRepository.i("Cloak native started on 127.0.0.1:$cloakPort conf=$confPath pid=${proc.pid()}", "Cloak")
                    startLogTail(context)
                    return true
                } else {
                    val out = try { proc.inputStream.bufferedReader().readText().take(500) } catch (_: Throwable) { "" }
                    LogRepository.w("Cloak native failed exit=${proc.exitValue()} out=$out, fallback to embedded", "Cloak")
                }
            }
            val embeddedPort = CloakEmbeddedServer.start(confPath)
            if (embeddedPort > 0) {
                cloakPort = embeddedPort
                running = true
                LogRepository.i("Cloak embedded started on 127.0.0.1:$cloakPort", "Cloak")
                return true
            }
            LogRepository.e("Cloak embedded start failed", "Cloak")
            false
        } catch (e: Throwable) {
            LogRepository.e("Cloak start exception: ${e.message}", "Cloak")
            false
        }
    }

    fun stop() {
        if (!running && cloakProcess == null && !CloakEmbeddedServer.isRunning()) return
        try {
            try { CloakNative.stop() } catch (_: Throwable) {}
            try { CloakEmbeddedServer.stop() } catch (_: Throwable) {}
            cloakProcess?.let { p ->
                try { if (p.isAlive) p.destroy() } catch (_: Throwable) {}
                try { if (p.isAlive) p.destroyForcibly() } catch (_: Throwable) {}
            }
            LogRepository.i("Cloak stopped", "Cloak")
        } catch (_: Throwable) {}
        running = false
        cloakProcess = null
        logTailThread?.interrupt()
        logTailThread = null
    }

    fun isRunning(): Boolean {
        return try {
            if (CloakEmbeddedServer.isRunning()) return true
            if (running && cloakProcess?.isAlive == true) return true
            try { CloakNative.isRunning() != 0 } catch (_: Throwable) { running && cloakProcess?.isAlive == true }
        } catch (_: Throwable) { false }
    }

    private fun startLogTail(context: PlatformContext) {
        try {
            val logFile = File(File(getSystemUtils(context).getFilesDir(), "cloak"), "cloak.log")
            logTailThread = Thread {
                var offset = try { logFile.length() } catch (_: Throwable) { 0L }
                while (isRunning()) {
                    try {
                        if (logFile.exists() && logFile.length() > offset) {
                            java.io.RandomAccessFile(logFile, "r").use { raf ->
                                raf.seek(offset)
                                val bytes = ByteArray((logFile.length() - offset).toInt())
                                raf.readFully(bytes)
                                offset = logFile.length()
                                String(bytes).lineSequence().filter { it.isNotBlank() }.forEach {
                                    LogRepository.i(it, "CloakCore")
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                    try { Thread.sleep(1000) } catch (_: Throwable) { break }
                }
            }.apply { isDaemon = true; start() }
        } catch (_: Throwable) {}
    }

    fun getEffectivePeer(config: AetherConfig): String {
        return if (isRunning()) "127.0.0.1:$cloakPort" else config.peer
    }
}
