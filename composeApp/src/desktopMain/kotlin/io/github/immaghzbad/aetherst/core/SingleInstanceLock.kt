package io.github.immaghzbad.aetherst.shared.core

import java.net.ServerSocket

object SingleInstanceLock {
    @Volatile
    var socket: ServerSocket? = null
    fun release() {
        runCatching { socket?.close() }
        socket = null
    }
}
