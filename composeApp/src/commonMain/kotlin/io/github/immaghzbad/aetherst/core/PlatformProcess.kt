package io.github.immaghzbad.aetherst.shared.core

expect class PlatformProcess() {
    suspend fun start(command: List<String>, directory: String, env: Map<String, String>): Boolean
    suspend fun readLine(): String?
    suspend fun writeLine(line: String)
    fun waitFor(): Int
    fun destroy()
}
