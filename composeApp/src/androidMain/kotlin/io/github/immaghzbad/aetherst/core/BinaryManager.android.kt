package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext
import java.io.File

class AndroidBinaryManager(private val context: PlatformContext) : BinaryManager {
    override fun prepareBinary(name: String): String {
        val binaryFile = File(context.context.filesDir, name)
        if (!binaryFile.exists()) {
            try {
                context.context.assets.open(name).use { input ->
                    binaryFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                binaryFile.setExecutable(true)
            } catch (e: Exception) {
                return name
            }
        }
        return binaryFile.absolutePath
    }
}

actual fun getBinaryManager(context: PlatformContext): BinaryManager = AndroidBinaryManager(context)
