package io.github.immaghzbad.aetherst.core

object CloakNative {
    private var loaded = false
    init {
        try {
            System.loadLibrary("cloak")
            loaded = true
        } catch (_: Throwable) {
            loaded = false
        }
    }
    fun isAvailable(): Boolean = loaded
    external fun start(jPath: String): Int
    external fun stop(): Int
    external fun isRunning(): Int
    external fun setLogLevel(level: Int)
}
