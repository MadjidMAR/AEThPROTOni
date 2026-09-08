package io.github.immaghzbad.aetherst.shared.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

actual class PlatformProcess actual constructor() {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    actual suspend fun start(command: List<String>, directory: String, env: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(command)
            pb.directory(File(directory))
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc
            reader = BufferedReader(InputStreamReader(proc.inputStream))
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun readLine(): String? = withContext(Dispatchers.IO) {
        try { reader?.readLine() } catch (e: Exception) { null }
    }

    actual suspend fun writeLine(line: String): Unit = withContext(Dispatchers.IO) {
        try {
            writer?.write(line)
            writer?.newLine()
            writer?.flush()
        } catch (_: Exception) {}
    }

    actual fun waitFor(): Int = process?.waitFor() ?: -1
    actual fun destroy() {
        process?.destroy()
    }
}
