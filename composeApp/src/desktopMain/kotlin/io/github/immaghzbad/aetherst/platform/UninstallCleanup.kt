package io.github.immaghzbad.aetherst.platform

import java.io.File

object UninstallCleanup {

    private val appDataDir: File by lazy {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val baseDir = if (isWindows) {
            System.getenv("AppData") ?: System.getProperty("user.home")
        } else {
            System.getProperty("user.home") + "/.config"
        }
        File(baseDir, "AetherST-Tunnel")
    }

    private val markerFile: File by lazy { File(appDataDir, ".installed") }

    fun getInstallDir(): File? {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (!isWindows) return null

        val javaPath = System.getProperty("java.home") ?: return null
        val installDir = File(javaPath).parentFile ?: return null

        return if (File(installDir, "AetherST-Tunnel.exe").exists() ||
            File(installDir, "lib").exists()
        ) installDir else null
    }

    fun markInstalled() {
        try {
            if (!appDataDir.exists()) appDataDir.mkdirs()
            markerFile.writeText(System.currentTimeMillis().toString())
        } catch (_: Exception) {}
    }

    fun isInstalled(): Boolean = markerFile.exists()

    fun needsCleanup(): Boolean {
        val installDir = getInstallDir()
        return installDir == null && appDataDir.exists() && appDataDir.listFiles()?.isNotEmpty() == true
    }

    fun cleanAllData(): Boolean {
        return try {
            if (appDataDir.exists()) {
                appDataDir.walkBottomUp().forEach { it.delete() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun handleStartupCleanup() {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (!isWindows) return

        if (needsCleanup()) {
            cleanAllData()
        } else if (!isInstalled()) {
            markInstalled()
        }
    }

    fun performManualCleanup(): Boolean {
        val stopped = try {
            val ctrl = io.github.immaghzbad.aetherst.shared.core.ConnectionController.getImpl(PlatformContext())
            ctrl.stop()
            true
        } catch (_: Exception) { false }

        Thread.sleep(500)
        val cleaned = cleanAllData()

        if (cleaned && stopped) {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "AetherST")
            if (tempDir.exists()) {
                tempDir.walkBottomUp().forEach { it.delete() }
            }
        }

        return cleaned
    }
}
