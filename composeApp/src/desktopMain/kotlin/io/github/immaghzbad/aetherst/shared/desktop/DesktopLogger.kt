package io.github.immaghzbad.aetherst.shared.desktop

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DesktopLogger {
    private val lock = ReentrantLock()
    private var logFile: File? = null
    private var fallbackFile: File? = null
    @Volatile private var initialized = false
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L
    private const val MAX_ROTATED_FILES = 3

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            var primary: File? = null
            var fallback: File? = null
            try {
                val baseDir = resolveBaseDir()
                val logsDir = File(baseDir, "logs")
                logsDir.mkdirs()
                primary = File(logsDir, "aetherst.log")
                if (!primary.exists()) primary.createNewFile()
                if (primary.length() > 0) {
                    try {
                        val lastByte = java.io.RandomAccessFile(primary, "r").use { raf ->
                            if (raf.length() == 0L) 0 else { raf.seek(raf.length() - 1); raf.read() }
                        }
                        if (lastByte != '\n'.code && lastByte != '\r'.code) {
                            FileOutputStream(primary, true).use { it.write(System.lineSeparator().toByteArray()) }
                        }
                    } catch (_: Exception) {}
                }
                logFile = primary
            } catch (e: Throwable) {
                System.err.println("[DesktopLogger] primary init failed: ${e.message}")
                e.printStackTrace()
            }
            try {
                val tmp = File(System.getProperty("java.io.tmpdir"), "AetherST")
                tmp.mkdirs()
                fallback = File(tmp, "aetherst-boot.log")
                if (!fallback.exists()) fallback.createNewFile()
                fallbackFile = fallback
            } catch (_: Exception) {}
            initialized = true
            val target = logFile ?: fallbackFile
            if (target != null) {
                try {
                    rawWriteLocked("INFO", "DesktopLogger", "Logger initialized primary=${primary?.absolutePath} fallback=${fallback?.absolutePath} | OS=${System.getProperty("os.name")} ${System.getProperty("os.version")} | Arch=${System.getProperty("os.arch")} | Java=${System.getProperty("java.version")}", target)
                    if (logFile != null && fallbackFile != null && logFile != fallbackFile) {
                        rawWriteLocked("INFO", "DesktopLogger", "Logger initialized fallback=${fallback?.absolutePath}", fallbackFile!!)
                    }
                } catch (_: Exception) {}
            }
            try { System.err.println("[DesktopLogger] initialized primary=${primary?.absolutePath} fallback=${fallback?.absolutePath}") } catch (_: Exception) {}
        }
    }

    fun getLogFile(): File? {
        if (!initialized) init()
        return logFile ?: fallbackFile
    }

    fun getLogFilePath(): String? = getLogFile()?.absolutePath

    fun log(level: String, tag: String, message: String) {
        if (!initialized) init()
        rawWrite(level, tag, message)
    }

    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String) = log("ERROR", tag, message)
    fun d(tag: String, message: String) = log("DEBUG", tag, message)

    private fun rawWrite(level: String, tag: String, message: String) {
        val file = logFile
        val fallback = fallbackFile
        lock.withLock {
            if (file != null) rawWriteLocked(level, tag, message, file)
            if (fallback != null && fallback != file) {
                try { rawWriteLocked(level, tag, message, fallback) } catch (_: Exception) {}
            }
            if (file == null && fallback == null) {
                try { System.err.println("[$level] [$tag] $message") } catch (_: Exception) {}
            }
        }
    }

    private fun rawWriteLocked(level: String, tag: String, message: String, file: File) {
        try {
            rotateIfNeededLocked(file)
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val cleanMessage = message.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
            val line = "[$ts] [$level] [$tag] $cleanMessage${System.lineSeparator()}"
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8)).use { w ->
                w.write(line)
                w.flush()
            }
        } catch (e: Throwable) {
            try { System.err.println("[$level] [$tag] $message | writeFailed=${e.message}") } catch (_: Exception) {}
        }
    }

    private fun rotateIfNeededLocked(file: File) {
        try {
            if (!file.exists() || file.length() < MAX_FILE_SIZE_BYTES) return
            for (i in MAX_ROTATED_FILES downTo 1) {
                val src = if (i == 1) file else File(file.parentFile, "aetherst.log.${i - 1}")
                val dst = File(file.parentFile, "aetherst.log.$i")
                if (src.exists()) {
                    if (dst.exists()) dst.delete()
                    src.renameTo(dst)
                }
            }
            FileOutputStream(file, false).close()
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "[$ts] [INFO] [DesktopLogger] Log rotated due to size limit${System.lineSeparator()}"
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8)).use { w ->
                w.write(line)
                w.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun resolveBaseDir(): File {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        return if (isWindows) {
            val appData = System.getenv("APPDATA")
            if (!appData.isNullOrBlank()) File(appData, "AetherST-Tunnel")
            else File(System.getProperty("user.home"), "AetherST-Tunnel")
        } else {
            File(System.getProperty("user.home"), ".aetherst")
        }
    }

    fun openInEditor(): Boolean {
        val file = getLogFile() ?: return false
        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
                rawWrite("INFO", "DesktopLogger", "Log file created for editor open")
            }
        } catch (_: Exception) {
        }
        return openWithDefaultEditor(file)
    }

    private fun openWithDefaultEditor(file: File): Boolean {
        try {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            if (isWindows) {
                val editors = listOf(
                    System.getenv("EDITOR"),
                    System.getenv("VISUAL")
                ).filterNotNull().filter { it.isNotBlank() }
                for (editor in editors) {
                    try {
                        ProcessBuilder(editor, file.absolutePath).start()
                        i("DesktopLogger", "Opened log with EDITOR=$editor")
                        return true
                    } catch (_: Exception) {
                    }
                }
                try {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                        java.awt.Desktop.getDesktop().open(file)
                        i("DesktopLogger", "Opened log via Desktop.open")
                        return true
                    }
                } catch (e: Exception) {
                    w("DesktopLogger", "Desktop.open failed: ${e.message}")
                }
                try {
                    ProcessBuilder("cmd", "/c", "start", "\"\"", "\"${file.absolutePath}\"").start()
                    i("DesktopLogger", "Opened log via cmd start")
                    return true
                } catch (_: Exception) {
                }
                try {
                    ProcessBuilder("notepad.exe", file.absolutePath).start()
                    i("DesktopLogger", "Opened log via notepad fallback")
                    return true
                } catch (e: Exception) {
                    e("DesktopLogger", "All open methods failed: ${e.message}")
                }
                return false
            } else {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(file)
                    return true
                }
                val opener = if (System.getProperty("os.name").lowercase().contains("mac")) "open" else "xdg-open"
                ProcessBuilder(opener, file.absolutePath).start()
                return true
            }
        } catch (e: Exception) {
            e("DesktopLogger", "openWithDefaultEditor failed: ${e.message}")
            return false
        }
    }
}
