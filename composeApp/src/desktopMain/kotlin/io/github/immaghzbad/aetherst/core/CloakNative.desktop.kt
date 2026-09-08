package io.github.immaghzbad.aetherst.core

object CloakNative {
    fun isAvailable(): Boolean = CloakController.isAvailable()
    fun start(jPath: String): Int = -1
    fun stop(): Int = -1
    fun isRunning(): Int = if (CloakController.isRunning()) 1 else 0
    fun setLogLevel(level: Int) {}
}
