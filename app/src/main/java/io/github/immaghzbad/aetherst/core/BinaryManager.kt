package io.github.immaghzbad.aetherst.core

import android.content.Context
import android.os.Build
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import java.io.File
import java.io.IOException

object BinaryManager {

    data class ArchInfo(
        val abi: String,
        val targetName: String
    )

    fun detectArchitecture(): ArchInfo {
        val supportedAbis = Build.SUPPORTED_ABIS
        if (supportedAbis == null || supportedAbis.isEmpty()) {
            throw IOException("Device reports no supported ABIs.")
        }

        for (abi in supportedAbis) {
            when {
                abi.contains("x86_64", ignoreCase = true) -> return ArchInfo(abi, "x86_64")
                abi.contains("x86", ignoreCase = true) || abi.contains("i686", ignoreCase = true) || abi.contains("i386", ignoreCase = true) -> return ArchInfo(abi, "x86")
                abi.contains("arm64", ignoreCase = true) || abi.contains("aarch64", ignoreCase = true) -> return ArchInfo(abi, "arm64")
                abi.contains("v7", ignoreCase = true) || abi.contains("arm", ignoreCase = true) -> return ArchInfo(abi, "armv7")
            }
        }

        throw IOException("No compatible Aether core binary found for device ABIs: ${supportedAbis.joinToString(", ")}")
    }

    @Synchronized
    fun prepareBinary(context: Context): File {
        val archInfo = detectArchitecture()
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val bundledBinary = File(nativeLibDir, "libaether.so")

        if (bundledBinary.exists() && bundledBinary.isFile && bundledBinary.length() > 0) {
            if (bundledBinary.canExecute()) {
                return bundledBinary
            }

            try {
                makeExecutable(bundledBinary)
                if (bundledBinary.canExecute()) return bundledBinary
            } catch (e: Exception) {
                LogRepository.w("makeExecutable bundled failed: ${e.message}")
            }
        }

        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw IOException("Failed to create binary directory: ${binDir.absolutePath}")
        }

        val targetFile = File(binDir, "aether_core")
        val sourceFile = File(binDir, "aether-${archInfo.targetName}")

        if (sourceFile.exists() && sourceFile.isFile && sourceFile.length() > 0) {
            val tempFile = File(binDir, "aether_core.tmp")
            try {
                sourceFile.copyTo(tempFile, overwrite = true)
                makeExecutable(tempFile)
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename temporary binary to final destination.")
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

        if (targetFile.exists() && targetFile.isFile && targetFile.length() > 0) {
            if (targetFile.canExecute()) return targetFile
            makeExecutable(targetFile)
            if (targetFile.canExecute()) return targetFile
        }

        throw IOException("Aether core binary is missing or not executable for architecture: ${archInfo.abi}")
    }

    private fun makeExecutable(file: File) {
        try {
            val pb = ProcessBuilder("chmod", "700", file.absolutePath)
            val proc = pb.start()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                val err = runCatching { proc.errorStream.bufferedReader().readText() }.getOrNull()?.trim()
                LogRepository.w("chmod 700 failed for ${file.name} exit=$exitCode err=${err ?: "?"}")
            }
        } catch (e: Exception) {
            LogRepository.w("chmod 700 exception for ${file.name}: ${e.message}")
        }

        try {
            file.setExecutable(true, true)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (e: Exception) {
            LogRepository.w("setExecutable exception for ${file.name}: ${e.message}")
        }

        if (!file.canExecute()) {
            throw IOException("Failed to set executable permissions on ${file.name}")
        }
    }
}
