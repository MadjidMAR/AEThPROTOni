package io.github.immaghzbad.aetherst.shared.core

import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

object Elevation {

    fun isElevated(): Boolean {
        return runCatching {
            val proc = ProcessBuilder("net", "session")
                .redirectErrorStream(true)
                .start()
            proc.waitFor() == 0
        }.getOrDefault(false)
    }

    fun relaunchElevatedAndExit(): Boolean {
        SingleInstanceLock.release()
        runCatching { java.net.ServerSocket(18195).use { } } 
        val launcher = System.getProperty("jpackage.app-path")
        if (!launcher.isNullOrBlank() && File(launcher).exists()) {
            return runCatching {
                val escaped = launcher.replace("'", "''")
                val command = arrayOf(
                    "powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                    "Start-Process -FilePath '$escaped' -Verb RunAs -WindowStyle Hidden"
                )
                ProcessBuilder(*command).start()
                Thread.sleep(1300)
                exitProcess(0)
                true
            }.getOrDefault(false)
        }

        return runCatching {
            val info = ProcessHandle.current().info()
            val cmd = info.command().orElse(null)
            val args = info.arguments().orElse(emptyArray())

            if (cmd.isNullOrBlank()) return false

            val ps1 = Files.createTempFile("aether-relaunch-", ".ps1")
            val script = buildString {
                appendLine("\$proc = Start-Process -FilePath '$cmd'")
                append(" -ArgumentList '")
                append(args.joinToString("' '") { it.replace("'", "''") })
                appendLine("'")
                appendLine(" -Verb RunAs -PassThru -WindowStyle Hidden")
                appendLine("if (\$proc) { exit }")
            }
            Files.writeString(ps1, script)

            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", ps1.toAbsolutePath().toString()
            )
            pb.start()
            Thread.sleep(1300)
            exitProcess(0)
            true
        }.getOrDefault(false)
    }
}
