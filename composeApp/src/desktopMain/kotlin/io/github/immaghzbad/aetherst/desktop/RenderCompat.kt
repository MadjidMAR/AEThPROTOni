package io.github.immaghzbad.aetherst.desktop

import io.github.immaghzbad.aetherst.shared.desktop.DesktopLogger
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object RenderCompat {

    private const val SOFTWARE_API = "SOFTWARE_FAST"
    private const val MAIN_CLASS = "io.github.immaghzbad.aetherst.MainKt"

    private val BARE_INTEL_HD = Regex("""intel\(r\)\s*hd\s*graphics\s*$""", RegexOption.IGNORE_CASE)
    private val YEAR_IN_TEXT = Regex("""(19|20)\d{2}""")

    enum class Decision {
        HARDWARE,
        HARDWARE_USER,
        ENV_OVERRIDE,
        SOFTWARE_USER,
        SOFTWARE_INCOMPATIBLE,
        SOFTWARE_DETECTION_FAILED
    }

    @Volatile
    var decision: Decision = Decision.HARDWARE
        private set

    @Volatile
    var decisionReason: String = "default hardware rendering"
        private set

    @Volatile
    var isRelaunching: Boolean = false
        private set

    private val relaunchState = AtomicBoolean(false)

    val isSoftwareRendering: Boolean
        get() = decision == Decision.SOFTWARE_USER ||
            decision == Decision.SOFTWARE_INCOMPATIBLE ||
            decision == Decision.SOFTWARE_DETECTION_FAILED

    val preferTransparentWindow: Boolean
        get() = !isSoftwareRendering

    fun apply(args: Array<String>) {
        try {
            logStartupEnvironment()

            val envRenderApi = System.getenv("SKIKO_RENDER_API")
            if (!envRenderApi.isNullOrBlank()) {
                decision = Decision.ENV_OVERRIDE
                decisionReason = "SKIKO_RENDER_API='$envRenderApi' from environment"
                log("$decisionReason -> Skiko reads this env var with the highest priority, renderer left untouched")
                return
            }
            if (args.any { it.equals("--force-software", true) || it.equals("--software-render", true) }) {
                decision = Decision.SOFTWARE_USER
                forceSoftware("CLI --force-software override")
                return
            }
            if (args.any { it.equals("--force-gpu", true) || it.equals("--hardware-render", true) }) {
                decision = Decision.HARDWARE_USER
                decisionReason = "CLI --force-gpu override"
                log("CLI --force-gpu -> hardware renderer kept (Skiko built-in fallback queue still protects at runtime)")
                return
            }

            val isWindows = System.getProperty("os.name")?.contains("win", true) == true
            if (!isWindows) {
                decision = Decision.HARDWARE
                decisionReason = "non-Windows OS default"
                log("Non-Windows OS -> leaving default renderer")
                return
            }

            val adapters = try {
                enumerateGpus()
            } catch (t: Throwable) {
                log("GPU enumeration threw ${t::class.simpleName}: ${t.message}")
                emptyList()
            }

            if (adapters.isEmpty()) {
                decision = Decision.SOFTWARE_DETECTION_FAILED
                forceSoftware("GPU detection unavailable (no CIM/WMIC data) -> startup-safe software renderer instead of blind hardware")
                return
            }

            adapters.forEach {
                log("GPU: name='${it.name}' driver=${it.driverVersion ?: "?"} date=${it.driverDate?.year ?: "?"}")
            }

            if (evaluate(adapters)) {
                decision = Decision.SOFTWARE_INCOMPATIBLE
                forceSoftware("Incompatible/legacy/virtual GPU: " + adapters.joinToString("; ") { it.name })
            } else {
                decision = Decision.HARDWARE
                decisionReason = "compatible GPU(s): " + adapters.joinToString("; ") { it.name }
                log("GPUs compatible -> hardware renderer (Skiko runtime fallback queue: ANGLE -> DIRECT3D -> OPENGL -> SOFTWARE_FAST -> SOFTWARE_COMPAT)")
            }
        } catch (t: Throwable) {
            decision = Decision.SOFTWARE_DETECTION_FAILED
            forceSoftware("RenderCompat failure (${t::class.simpleName}: ${t.message}) -> startup-safe software renderer")
        }
    }

    private fun forceSoftware(reason: String) {
        decisionReason = reason
        runCatching { System.setProperty("skiko.renderApi", SOFTWARE_API) }
        log("Software renderer selected: skiko.renderApi=$SOFTWARE_API (Skiko queue ends at SOFTWARE_COMPAT) | reason=$reason")
    }

    private fun logStartupEnvironment() {
        log("os=${System.getProperty("os.name")} ${System.getProperty("os.version")} arch=${System.getProperty("os.arch")}")
        log("java=${System.getProperty("java.version")} vendor=${System.getProperty("java.vendor") ?: "unknown"}")
        val skikoVersion = runCatching { org.jetbrains.skiko.Version.skiko }.getOrDefault("unknown")
        val skiaVersion = runCatching { org.jetbrains.skiko.Version.skia }.getOrDefault("unknown")
        log("skiko=$skikoVersion skia=$skiaVersion compose=${composeRuntimeJar()}")
    }

    private fun composeRuntimeJar(): String = runCatching {
        val url = RenderCompat::class.java.classLoader.getResource("androidx/compose/runtime/MonotonicFrameClock.class")?.toString() ?: return "unknown"
        url.substringBeforeLast('/').substringAfterLast('/').removeSuffix("!")
    }.getOrDefault("unknown")

    private fun evaluate(adapters: List<GpuAdapter>): Boolean {
        val names = adapters.map { it.name.lowercase() }
        if (names.none { !isVirtualAdapter(it) }) {
            log("Only virtual/remote display adapters detected -> treating as VM/RDP environment")
            return true
        }
        for (i in names.indices) {
            if (isVirtualAdapter(names[i])) continue
            if (isLegacyNvidia(names[i]) || isLegacyIntel(names[i])) return true
            val year = adapters[i].driverDate?.year
            if (year != null && year < 2013) return true
        }
        val hasIntel = names.any { it.contains("intel") && !isVirtualAdapter(it) }
        val hasNvidia = names.any { it.contains("nvidia") && !isVirtualAdapter(it) }
        if (hasIntel && hasNvidia) {
            val intelDate = adapters.firstOrNull { it.name.contains("intel", true) && !isVirtualAdapter(it.name.lowercase()) }?.driverDate
            if (intelDate != null && intelDate.year < 2016) return true
        }
        return false
    }

    private fun isLegacyNvidia(n: String): Boolean =
        n.contains("nvs") || n.contains("quadro nvs")

    private fun isLegacyIntel(n: String): Boolean {
        if (n.contains("hd graphics 2000") || n.contains("hd graphics 2500") ||
            n.contains("hd graphics 3000") || n.contains("hd graphics 4000")
        ) return true
        if (n.contains("intel(r) hd graphics family")) return true
        if (n.contains("gma ")) return true
        if (BARE_INTEL_HD.containsMatchIn(n)) return true
        return false
    }

    private fun isVirtualAdapter(n: String): Boolean =
        n.contains("basic render") || n.contains("basic display") || n.contains("remote display") ||
            n.contains("indirect display") || n.contains("microsoft remote") || n.contains("rdp") ||
            n.contains("vmware") || n.contains("virtualbox") || n.contains("vbox") ||
            n.contains("hyper-v") || n.contains("hyperv") || n.contains("parallels") ||
            n.contains("qemu") || n.contains("qxl") || n.contains("virtio") || n.contains("citrix")

    private data class GpuAdapter(
        val name: String,
        val driverDate: DriverDate?,
        val driverVersion: String?
    )

    private data class DriverDate(val year: Int)

    private fun enumerateGpus(): List<GpuAdapter> {
        val cim = runCommand(
            listOf(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Get-CimInstance -ClassName Win32_VideoController | Select-Object -Property Name,DriverVersion,DriverDate | ConvertTo-Csv -NoTypeInformation"
            ),
            10
        )
        if (cim.isNotEmpty()) {
            val parsed = parsePowershell(cim)
            if (parsed.isNotEmpty()) return parsed
            log("CIM returned data but parse produced no adapters -> trying WMIC as secondary source")
        } else {
            log("PowerShell/CIM unavailable -> trying WMIC as secondary source")
        }
        val wmic = runCommand(
            listOf("wmic", "path", "Win32_VideoController", "get", "Name,DriverDate,DriverVersion", "/value"),
            8
        )
        if (wmic.isEmpty()) return emptyList()
        return parseWmic(wmic)
    }

    private fun parseWmic(lines: List<String>): List<GpuAdapter> {
        val result = mutableListOf<GpuAdapter>()
        var name: String? = null
        var date: String? = null
        var ver: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) {
                if (!name.isNullOrBlank()) result += GpuAdapter(name, parseDriverDate(date ?: ""), ver)
                name = null
                date = null
                ver = null
                continue
            }
            val eq = line.indexOf('=')
            if (eq < 0) continue
            val key = line.substring(0, eq).trim().lowercase()
            val value = line.substring(eq + 1).trim()
            when (key) {
                "name" -> name = value
                "driverdate" -> date = value
                "driverversion" -> ver = value.ifEmpty { null }
            }
        }
        if (!name.isNullOrBlank()) result += GpuAdapter(name, parseDriverDate(date ?: ""), ver)
        return result.filter { it.name.isNotBlank() }
    }

    private fun parsePowershell(lines: List<String>): List<GpuAdapter> {
        val rows = parseCsvLines(lines)
        return rows.mapNotNull { row ->
            if (row.isEmpty()) return@mapNotNull null
            val name = row[0].trim().removeSurrounding("\"").trim()
            if (name.isEmpty() || name.equals("Name", true)) return@mapNotNull null
            GpuAdapter(
                name = name,
                driverDate = parseDriverDate(row.getOrNull(2)?.trim()?.removeSurrounding("\"").orEmpty()),
                driverVersion = row.getOrNull(1)?.trim()?.removeSurrounding("\"")
                    ?.takeIf { it.isNotEmpty() && !it.equals("DriverVersion", true) }
            )
        }
    }

    private fun parseCsvLines(lines: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        for (raw in lines) {
            val row = mutableListOf<String>()
            val cur = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    c == '"' -> {
                        if (inQuotes && i + 1 < raw.length && raw[i + 1] == '"') {
                            cur.append('"')
                            i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    c == ',' && !inQuotes -> {
                        row.add(cur.toString().trim())
                        cur.clear()
                    }
                    else -> cur.append(c)
                }
                i++
            }
            row.add(cur.toString().trim())
            result.add(row)
        }
        return result
    }

    private fun parseDriverDate(raw: String): DriverDate? {
        if (raw.isEmpty()) return null
        val m = YEAR_IN_TEXT.find(raw) ?: return null
        val y = m.value.toIntOrNull()
        if (y != null && y in 1990..2100) return DriverDate(y)
        return null
    }

    private fun runCommand(command: List<String>, timeoutSeconds: Long): List<String> {
        return try {
            val proc = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return emptyList()
            }
            proc.inputStream.reader(Charsets.UTF_8).readText().lineSequence()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter { it.isNotEmpty() }
                .toList()
        } catch (t: Throwable) {
            log("runCommand failed (${command.firstOrNull()}): ${t.message}")
            emptyList()
        }
    }

    private fun log(msg: String) {
        try { DesktopLogger.i("RenderCompat", msg) } catch (_: Throwable) {}
        try {
            File(System.getProperty("java.io.tmpdir"), "AetherST/aetherst-boot.log")
                .appendText("[RENDER] $msg\n")
        } catch (_: Throwable) {}
    }

    fun attemptSoftwareRelaunch(reason: String) {
        if (!relaunchState.compareAndSet(false, true)) return
        isRelaunching = true
        decision = Decision.SOFTWARE_USER
        decisionReason = reason
        log("Attempting software-renderer relaunch: $reason")
        try {
            val base = currentCommandLine()
            val cmd = if (base != null) {
                base.toMutableList()
            } else {
                val javaExe = File(System.getProperty("java.home"), "bin/java" + if (isWindowsOs()) ".exe" else "").absolutePath
                mutableListOf(
                    javaExe,
                    "-cp", System.getProperty("java.class.path", ""),
                    MAIN_CLASS
                )
            }
            cmd.removeAll { it.equals("--force-gpu", true) || it.equals("--hardware-render", true) }
            cmd.removeAll { it.startsWith("-Dskiko.renderApi=") }
            if (cmd.none { it.equals("--force-software", true) }) cmd += "--force-software"
            if (cmd.none { it.startsWith("-Dskiko.renderApi=") }) cmd += "-Dskiko.renderApi=$SOFTWARE_API"
            log("Relaunch command: " + cmd.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it })
            ProcessBuilder(cmd).inheritIO().start()
        } catch (t: Throwable) {
            log("Relaunch failed: ${t::class.simpleName}: ${t.message}; continuing in current process")
            isRelaunching = false
        }
    }

    private fun currentCommandLine(): List<String>? = runCatching {
        val info = ProcessHandle.current().info()
        val cl = info.commandLine().orElse(null)
        if (cl == null || cl.isBlank()) return null
        splitCommandLine(cl)
    }.getOrNull()

    private fun splitCommandLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ' ' && !inQuotes -> {
                    if (cur.isNotEmpty()) { result.add(cur.toString()); cur.clear() }
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) result.add(cur.toString())
        return result
    }

    private fun isWindowsOs(): Boolean = System.getProperty("os.name")?.contains("win", true) == true
}
