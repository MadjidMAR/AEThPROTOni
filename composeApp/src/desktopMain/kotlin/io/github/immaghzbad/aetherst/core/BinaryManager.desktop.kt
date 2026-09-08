package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext
import java.io.File

class DesktopBinaryManager : BinaryManager {
    override fun prepareBinary(name: String): String {
        val binName = if (System.getProperty("os.name").lowercase().contains("win")) {
            if (name.endsWith(".exe")) name else "$name.exe"
        } else {
            name
        }

        System.getProperty("compose.application.resources.dir")?.let { resourcesDir ->
            val candidates = listOf(
                File(resourcesDir, binName),
                File(resourcesDir, "bin/$binName"),
                File(File(resourcesDir, "app/resources"), binName),
                File(File(resourcesDir, "app/resources/bin"), binName)
            )
            for (file in candidates) {
                if (file.exists()) return file.absolutePath
            }
            val binSub = File(resourcesDir, "bin")
            if (binSub.exists()) {
                val fileInBin = File(binSub, binName)
                if (fileInBin.exists()) return fileInBin.absolutePath
            }
        }

        val userDir = File(System.getProperty("user.dir"))
        val devDirs = listOf(userDir, File(userDir, "bin"), File(userDir, "src/desktopMain/resources/bin"))
        for (dir in devDirs) {
            val file = File(dir, binName)
            if (file.exists()) return file.absolutePath
            val fileInBin = File(File(dir, "bin"), binName)
            if (fileInBin.exists()) return fileInBin.absolutePath
        }

        val appData = System.getenv("AppData") ?: System.getProperty("user.home")
        val targetDir = File(appData, "AetherST-Tunnel/bin")
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, binName)

        if (!targetFile.exists()) {
            try {
                val stream = getResourceStream(binName) ?: getResourceStream("bin/$binName")
                stream?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                    if (!System.getProperty("os.name").lowercase().contains("win")) targetFile.setExecutable(true)
                }
                if (binName.contains("hev-socks5-tunnel")) {
                    extractResource("wintun.dll", targetDir)
                    extractResource("msys-2.0.dll", targetDir)
                }
            } catch (_: Exception) {}
        } else {
            try {
                if (binName.contains("hev-socks5-tunnel")) {
                    extractResource("wintun.dll", targetDir)
                    extractResource("msys-2.0.dll", targetDir)
                }
                val expectedSize = getExpectedSize(binName)
                if (expectedSize > 0 && targetFile.length() < expectedSize * 0.8) {
                    targetFile.delete()
                    getResourceStream(binName)?.use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: getResourceStream("bin/$binName")?.use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } catch (_: Exception) {}
        }

        return if (targetFile.exists()) targetFile.absolutePath else binName
    }

    private fun getResourceStream(path: String): java.io.InputStream? {
        val clean = path.removePrefix("/")
        return javaClass.getResourceAsStream("/$clean")
            ?: javaClass.getResourceAsStream("/bin/$clean")
            ?: javaClass.classLoader.getResourceAsStream(clean)
            ?: javaClass.classLoader.getResourceAsStream("bin/$clean")
    }

    private fun getExpectedSize(name: String): Long {
        return try {
            getResourceStream(name)?.use { it.available().toLong() }
                ?: getResourceStream("bin/$name")?.use { it.available().toLong() } ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun extractResource(name: String, targetDir: File) {
        val targetFile = File(targetDir, name)
        if (!targetFile.exists() || targetFile.length() == 0L) {
            getResourceStream(name)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: getResourceStream("bin/$name")?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

actual fun getBinaryManager(context: PlatformContext): BinaryManager = DesktopBinaryManager()
