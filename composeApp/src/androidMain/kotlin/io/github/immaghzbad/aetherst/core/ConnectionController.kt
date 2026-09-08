package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.SessionTraffic
import io.github.immaghzbad.aetherst.shared.platform.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual object ConnectionController {
    private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
    actual val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    actual val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sessionTraffic = MutableStateFlow(SessionTraffic())
    actual val sessionTraffic: StateFlow<SessionTraffic> = _sessionTraffic.asStateFlow()

    private val _isWaitingForCode = MutableStateFlow(false)
    actual val isWaitingForCode: StateFlow<Boolean> = _isWaitingForCode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            Bridge.statusOverride.collect { s ->
                if (s != null) {
                    _status.value = s
                    if (s == ConnectionStatus.STOPPED || s == ConnectionStatus.ERROR || s == ConnectionStatus.FAILED) {
                        _elapsedSeconds.value = 0L
                        _sessionTraffic.value = SessionTraffic()
                    }
                }
            }
        }
        scope.launch {
            Bridge.elapsedOverride.collect { e ->
                if (e != null) {
                    _elapsedSeconds.value = e
                }
            }
        }
        scope.launch {
            Bridge.trafficOverride.collect { t ->
                if (t != null) {
                    _sessionTraffic.value = t
                }
            }
        }
        scope.launch {
            Bridge.isWaitingForCode.collect { waiting ->
                if (waiting != null) {
                    _isWaitingForCode.value = waiting
                }
            }
        }
    }

    actual fun getInstance(context: PlatformContext) {}

    actual fun markStatus(status: ConnectionStatus) {
        _status.value = status
        if (status == ConnectionStatus.STOPPED || status == ConnectionStatus.ERROR || status == ConnectionStatus.FAILED) {
            _elapsedSeconds.value = 0L
            _sessionTraffic.value = SessionTraffic()
            Bridge.elapsedOverride.value = 0L
            Bridge.trafficOverride.value = SessionTraffic()
        }
    }
}
