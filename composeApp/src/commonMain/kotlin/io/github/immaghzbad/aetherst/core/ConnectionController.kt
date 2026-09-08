package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.SessionTraffic
import kotlinx.coroutines.flow.StateFlow

expect object ConnectionController {
    val status: StateFlow<ConnectionStatus>
    val elapsedSeconds: StateFlow<Long>
    val sessionTraffic: StateFlow<SessionTraffic>
    val isWaitingForCode: StateFlow<Boolean>
    fun getInstance(context: PlatformContext)
    fun markStatus(status: ConnectionStatus)
}
