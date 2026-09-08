package io.github.immaghzbad.aetherst.core

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Process
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.shared.data.ActiveProxyProvider
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.SessionTraffic
import io.github.immaghzbad.aetherst.shared.model.*
import io.github.immaghzbad.aetherst.shared.platform.Bridge
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object SocksGate {
    private val _readiness = MutableStateFlow(SocksReadiness.NOT_READY)
    val readiness: StateFlow<SocksReadiness> = _readiness.asStateFlow()
    fun setReady(v: SocksReadiness) { _readiness.value = v }
}

interface ConnectionControl {
    suspend fun start()
    suspend fun stop()
    fun submitLoginCode(code: String)
}

class ConnectionController private constructor(context: Context) : ConnectionControl {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner = AetherProcessRunner(appContext)
    private val mutex = Mutex()
    private val activeAttemptId = AtomicLong(0)
    private val loginCodeChannel = Channel<String>(Channel.CONFLATED)

    private var timerJob: Job? = null
    private var durationSeconds = 0L
    private var baseTx = 0L
    private var baseRx = 0L
    private var accumulatedTx = 0L
    private var accumulatedRx = 0L
    private var lastEngineTx = 0L
    private var lastEngineRx = 0L
    private var prevTotalTx = 0L
    private var prevTotalRx = 0L
    private var hasManualTraffic = false
    private var useTrafficStatsFallback = true

    companion object {
        const val ACTION_STATUS_CHANGED = "io.github.immaghzbad.aetherst.STATUS_CHANGED"

        @Volatile
        private var INSTANCE: ConnectionController? = null

        private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
        val status: StateFlow<ConnectionStatus> = _status.asStateFlow()
        
        @Volatile
        var lastKnownStatus: ConnectionStatus = ConnectionStatus.STOPPED
            private set

        @Volatile
        var psiphonChaining: Boolean = false
            private set

        private val _isWaitingForCode = MutableStateFlow(false)
        val isWaitingForCode: StateFlow<Boolean> = _isWaitingForCode.asStateFlow()

        private fun notifyStatusChanged(context: Context, newStatus: ConnectionStatus) {
            lastKnownStatus = newStatus
            _status.value = newStatus
            Bridge.statusOverride.value = newStatus
            val intent = Intent(ACTION_STATUS_CHANGED)
            intent.putExtra("status", newStatus.name)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        }

        fun getInstance(context: Context): ConnectionController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionController(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun updateIsWaitingForCode(waiting: Boolean) {
            _isWaitingForCode.value = waiting
            Bridge.isWaitingForCode.value = waiting
        }
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            runner.connectionStatus.drop(1).collect { coreStatus ->
                handleCoreStatus(coreStatus)
            }
        }
    }


    override fun submitLoginCode(code: String) {
        updateIsWaitingForCode(false)
        loginCodeChannel.trySend(code)
    }

    fun markReconnecting() {
        if (_status.value == ConnectionStatus.RUNNING || _status.value == ConnectionStatus.TUN_ACTIVE) {
            notifyStatusChanged(appContext, ConnectionStatus.RECONNECTING)
        }
    }

    override suspend fun start() {
        val attemptId: Long
        mutex.withLock {
            if (_status.value == ConnectionStatus.RUNNING || _status.value == ConnectionStatus.VALIDATING) return
            attemptId = System.currentTimeMillis()
            activeAttemptId.set(attemptId)
            notifyStatusChanged(appContext, ConnectionStatus.STARTING)
        }
        try {
            val config = AetherConfigRepository.getInstance(getSettings(PlatformContext(appContext))).config.value.effectiveZeroTrustConfig()
            var effectiveConfig = config
            if (CloakController.isSupported(config)) {
                val cloakStarted = runNativeBounded(30000L, "Cloak.start") { CloakController.start(appContext, config) } == true
                if (cloakStarted && CloakController.isRunning()) {
                    effectiveConfig = config.copy(peer = CloakController.getEffectivePeer(config))
                    LogRepository.i("[Controller] Cloak active, routing MASQUE via ${effectiveConfig.peer}")
                }
            }
            val psiphonSupported = PsiphonController.isSupported(effectiveConfig)
            val bindHost = if (effectiveConfig.shareHotspot) "0.0.0.0" else "127.0.0.1"
            val bindAddress = "$bindHost:${effectiveConfig.socksPort}"
            if (bindHost != "127.0.0.1") {
                LogRepository.w("[Controller] SOCKS5 listener bound to $bindAddress - not loopback, anyone who can reach this address can use the tunnel")
            }
            if (effectiveConfig.httpProxyEnabled && bindHost != "127.0.0.1") {
                LogRepository.w("[Controller] HTTP listener bound to $bindHost:${effectiveConfig.httpPort} - not loopback, anyone who can reach this address can use the tunnel")
            }
            hasManualTraffic = false
            accumulatedTx = 0L
            accumulatedRx = 0L
            lastEngineTx = 0L
            lastEngineRx = 0L
            prevTotalTx = 0L
            prevTotalRx = 0L
            useTrafficStatsFallback = effectiveConfig.connectionMode != ConnectionMode.TUNNEL
            val rawTx = TrafficStats.getUidTxBytes(Process.myUid())
            val rawRx = TrafficStats.getUidRxBytes(Process.myUid())
            baseTx = if (rawTx == TrafficStats.UNSUPPORTED.toLong() || rawTx < 0) 0L else rawTx
            baseRx = if (rawRx == TrafficStats.UNSUPPORTED.toLong() || rawRx < 0) 0L else rawRx
            if (psiphonSupported) {
                psiphonChaining = true
                if (effectiveConfig.protocol == AetherProtocol.MASQUE) {
                    val masqueOrder = effectiveConfig.psiphonMasqueOrder.lowercase().trim()
                    if (masqueOrder == "psiphon_first") {
                        LogRepository.i("[Controller] Psiphon MASQUE order=psiphon-first -> starting Psiphon direct first")
                        runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }) }
                        if (PsiphonController.isRunning()) {
                            if (awaitPsiphonStable()) {
                                LogRepository.i("[Controller] Psiphon settled (stable), proceeding to chain core")
                                effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                LogRepository.i("[Controller] Psiphon active, chaining via ${effectiveConfig.upstreamProxy} outer=${effectiveConfig.protocol}")
                                if (status.value == ConnectionStatus.RUNNING) {
                                    notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                                }
                                psiphonChaining = false
                                if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                    throw IllegalStateException("Core failed via psiphon chain")
                                }
                            } else {
                                LogRepository.e("[Controller] Psiphon not connected/stable over MASQUE - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                PsiphonController.stop()
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon not connected/stable over MASQUE")
                            }
                        } else {
                            LogRepository.e("[Controller] Psiphon failed to start over MASQUE - chain requires Psiphon, aborting")
                            ActiveProxyProvider.psiphonProxyUrl = null
                            psiphonChaining = false
                            runCatching { runner.stop() }
                            throw IllegalStateException("Psiphon failed to start over MASQUE")
                        }
                        return
                    }
                    if (masqueOrder == "auto") {
                        LogRepository.i("[Controller] Psiphon MASQUE order=auto -> racing MASQUE direct against Psiphon direct, winner completes the chain")
                        val raceWinner = coroutineScope {
                            val masqueLeg = async {
                                try {
                                    startAetherInternal(effectiveConfig, bindAddress, attemptId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            val psiphonLeg = async {
                                try {
                                    val started = runNativeBounded<Unit>(30000L, "Psiphon.race") { PsiphonController.start(appContext, effectiveConfig, upstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }) } != null && PsiphonController.isRunning()
                                    if (!started) return@async false
                                    awaitPsiphonStable()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            var masqueOk = false
                            var psiphonOk = false
                            var masqueDone = false
                            var psiphonDone = false
                            while (!masqueOk && !psiphonOk && (!masqueDone || !psiphonDone)) {
                                if (activeAttemptId.get() != attemptId) {
                                    masqueLeg.cancel()
                                    psiphonLeg.cancel()
                                    return@coroutineScope "stale"
                                }
                                if (!masqueDone && masqueLeg.isCompleted) {
                                    masqueDone = true
                                    masqueOk = runCatching { masqueLeg.await() }.getOrDefault(false)
                                }
                                if (!psiphonDone && psiphonLeg.isCompleted) {
                                    psiphonDone = true
                                    psiphonOk = runCatching { psiphonLeg.await() }.getOrDefault(false)
                                }
                                if (!masqueOk && !psiphonOk) delay(500.milliseconds)
                            }
                            masqueLeg.cancel()
                            psiphonLeg.cancel()
                            when {
                                masqueOk -> "masque"
                                psiphonOk -> "psiphon"
                                else -> null
                            }
                        }
                        if (raceWinner == "stale") return
                        if (raceWinner == "masque") {
                            LogRepository.i("[Controller] Race won by MASQUE direct, completing masque-first chain")
                            runCatching { PsiphonController.stop() }
                            delay(500.milliseconds)
                            if (status.value == ConnectionStatus.RUNNING) {
                                notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                            }
                            val httpUpstream = "http://127.0.0.1:${effectiveConfig.httpPort}"
                            runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = httpUpstream) }
                            if (PsiphonController.isRunning()) {
                                if (awaitPsiphonStable()) {
                                    ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                    LogRepository.i("[Controller] Psiphon over MASQUE ready via ${ActiveProxyProvider.psiphonProxyUrl}")
                                    try { val intent = Intent().setClassName(appContext.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService").apply { action = "io.github.immaghzbad.aetherst.SWITCH_HEV"; putExtra("host", "127.0.0.1"); putExtra("port", 3080) }; appContext.startService(intent) } catch (_: Exception) {}
                                    notifyStatusChanged(appContext, ConnectionStatus.RUNNING)
                                } else {
                                    LogRepository.e("[Controller] Psiphon not connected over MASQUE via http - chain requires Psiphon, aborting")
                                    ActiveProxyProvider.psiphonProxyUrl = null
                                    PsiphonController.stop()
                                    psiphonChaining = false
                                    runCatching { runner.stop() }
                                    throw IllegalStateException("Psiphon not connected over MASQUE")
                                }
                            } else {
                                LogRepository.e("[Controller] Psiphon failed to start over MASQUE - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon failed to start over MASQUE")
                            }
                            return
                        }
                        if (raceWinner == "psiphon") {
                            LogRepository.i("[Controller] Race won by Psiphon direct, completing psiphon-first chain")
                            runCatching { runner.stop() }
                            delay(500.milliseconds)
                            effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                            ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                            LogRepository.i("[Controller] Psiphon active, chaining via ${effectiveConfig.upstreamProxy} outer=${effectiveConfig.protocol}")
                            if (status.value == ConnectionStatus.RUNNING) {
                                notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                            }
                            psiphonChaining = false
                            if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                throw IllegalStateException("Core failed via psiphon chain")
                            }
                            return
                        }
                        LogRepository.e("[Controller] MASQUE auto race failed on both legs - chain requires a winner, aborting")
                        ActiveProxyProvider.psiphonProxyUrl = null
                        PsiphonController.stop()
                        psiphonChaining = false
                        runCatching { runner.stop() }
                        throw IllegalStateException("MASQUE auto race failed on both legs")
                    }
                    LogRepository.i("[Controller] Psiphon MASQUE order=masque-first (chainMode=${effectiveConfig.psiphonChainMode} order=${effectiveConfig.psiphonMasqueOrder})")
                    if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                        throw IllegalStateException("Core failed direct MASQUE")
                    }
                    if (status.value == ConnectionStatus.RUNNING) {
                        notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                    }
                    val httpUpstream = "http://127.0.0.1:${effectiveConfig.httpPort}"
                    runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = httpUpstream) }
                    if (PsiphonController.isRunning()) {
                        if (awaitPsiphonStable()) {
                            ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                            LogRepository.i("[Controller] Psiphon over MASQUE ready via ${ActiveProxyProvider.psiphonProxyUrl}")
                            try { val intent = Intent().setClassName(appContext.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService").apply { action = "io.github.immaghzbad.aetherst.SWITCH_HEV"; putExtra("host", "127.0.0.1"); putExtra("port", 3080) }; appContext.startService(intent) } catch (_: Exception) {}
                            notifyStatusChanged(appContext, ConnectionStatus.RUNNING)
                        } else {
                            LogRepository.e("[Controller] Psiphon not connected over MASQUE via http - chain requires Psiphon, aborting")
                            ActiveProxyProvider.psiphonProxyUrl = null
                            PsiphonController.stop()
                            psiphonChaining = false
                            runCatching { runner.stop() }
                            throw IllegalStateException("Psiphon not connected over MASQUE")
                        }
                    } else {
                        LogRepository.e("[Controller] Psiphon failed to start over MASQUE - chain requires Psiphon, aborting")
                        ActiveProxyProvider.psiphonProxyUrl = null
                        psiphonChaining = false
                        runCatching { runner.stop() }
                        throw IllegalStateException("Psiphon failed to start over MASQUE")
                    }
                    return
                }
                when (effectiveConfig.psiphonChainMode) {
                    PsiphonChainMode.ALWAYS -> {
                        val isWireGuardFamily = effectiveConfig.protocol == AetherProtocol.WG || effectiveConfig.protocol == AetherProtocol.GOOL
                        val masqueOrder = effectiveConfig.psiphonMasqueOrder
                        val shouldCoreFirst = isWireGuardFamily || (effectiveConfig.protocol == AetherProtocol.MASQUE && (masqueOrder == "masque_first" || masqueOrder == "auto"))
                        if (shouldCoreFirst) {
                            val modeDesc = if (isWireGuardFamily) "Psiphon over ${effectiveConfig.protocol} via http" else "MASQUE first -> Psiphon via http ($masqueOrder)"
                            LogRepository.i("[Controller] Psiphon ALWAYS $modeDesc")
                            if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                if (effectiveConfig.protocol == AetherProtocol.MASQUE && masqueOrder == "auto") {
                                    LogRepository.w("[Controller] MASQUE direct failed, falling back to Psiphon first")
                                } else {
                                    throw IllegalStateException("Core failed direct ${effectiveConfig.protocol}")
                                }
                            }
                            if (status.value == ConnectionStatus.RUNNING) {
                                notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                            }
                            val httpUpstream = "http://127.0.0.1:${effectiveConfig.httpPort}"
                            runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = httpUpstream) }
                            if (PsiphonController.isRunning()) {
                                if (awaitPsiphonStable()) {
                                    ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                    LogRepository.i("[Controller] Psiphon over ${effectiveConfig.protocol} ready via ${ActiveProxyProvider.psiphonProxyUrl}")
                                    try { val intent = Intent().setClassName(appContext.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService").apply { action = "io.github.immaghzbad.aetherst.SWITCH_HEV"; putExtra("host", "127.0.0.1"); putExtra("port", 3080) }; appContext.startService(intent) } catch (_: Exception) {}
                                    notifyStatusChanged(appContext, ConnectionStatus.RUNNING)
                                } else {
                                    if (effectiveConfig.protocol == AetherProtocol.MASQUE && masqueOrder == "auto") {
                                        LogRepository.w("[Controller] Psiphon not connected over MASQUE auto, trying Psiphon first fallback")
                                        runCatching { runner.stop() }
                                        delay(800.milliseconds)
                                        runNativeBounded<Unit>(30000L, "Psiphon.start2") { PsiphonController.start(appContext, effectiveConfig, upstream = null) }
                                        var w2 = 0
                                        while (w2 < 30 && !PsiphonController.isConnected()) { delay(1000.milliseconds); w2++ }
                                        var s2 = 0
                                        while (s2 < 25 && !PsiphonController.stableFor(10000)) { delay(1000.milliseconds); s2++ }
                                        if (PsiphonController.isConnected() && PsiphonController.stableFor(10000)) {
                                            effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                            ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                            if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                                throw IllegalStateException("Core failed via psiphon chain fallback")
                                            }
                                        } else {
                                            LogRepository.e("[Controller] Psiphon not connected over ${effectiveConfig.protocol} after fallback - aborting chain")
                                            PsiphonController.stop()
                                            ActiveProxyProvider.psiphonProxyUrl = null
                                            psiphonChaining = false
                                            runCatching { runner.stop() }
                                            throw IllegalStateException("Psiphon not connected over ${effectiveConfig.protocol}")
                                        }
                                    } else {
                                        LogRepository.e("[Controller] Psiphon not connected over ${effectiveConfig.protocol} - chain requires Psiphon, aborting")
                                        PsiphonController.stop()
                                        ActiveProxyProvider.psiphonProxyUrl = null
                                        psiphonChaining = false
                                        runCatching { runner.stop() }
                                        throw IllegalStateException("Psiphon not connected over ${effectiveConfig.protocol}")
                                    }
                                }
                            } else {
                                LogRepository.e("[Controller] Psiphon failed to start over ${effectiveConfig.protocol} - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon failed to start over ${effectiveConfig.protocol}")
                            }
                        } else {
                            LogRepository.i("[Controller] Psiphon ALWAYS mode -> psiphon-first chain")
                            runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }) }
                            if (PsiphonController.isRunning()) {
                                var waitPsiphon = 0
                                while (waitPsiphon < 30 && !PsiphonController.isConnected()) { delay(1000.milliseconds); waitPsiphon++ }
                                var stableWait = 0
                                while (stableWait < 25 && !PsiphonController.stableFor(10000)) { delay(1000.milliseconds); stableWait++ }
                                if (PsiphonController.isConnected() && PsiphonController.stableFor(10000)) {
                                    LogRepository.i("[Controller] Psiphon settled (stable), proceeding to chain core")
                                    effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                    ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                    LogRepository.i("[Controller] Psiphon active, chaining via ${effectiveConfig.upstreamProxy} outer=${effectiveConfig.protocol}")
                                } else {
                                    LogRepository.e("[Controller] Psiphon not connected/stable - chain requires Psiphon, aborting")
                                    ActiveProxyProvider.psiphonProxyUrl = null
                                    PsiphonController.stop()
                                    psiphonChaining = false
                                    runCatching { runner.stop() }
                                    throw IllegalStateException("Psiphon not connected/stable")
                                }
                            } else {
                                LogRepository.e("[Controller] Psiphon failed to start - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon failed to start")
                            }
                            psiphonChaining = false
                            if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                throw IllegalStateException("Core failed via psiphon chain")
                            }
                        }
                    }
                    PsiphonChainMode.FALLBACK -> {
                        val isWireGuardFamilyFallback = effectiveConfig.protocol == AetherProtocol.WG || effectiveConfig.protocol == AetherProtocol.GOOL
                        var directSuccess = false
                        try {
                            LogRepository.i("[Controller] Psiphon FALLBACK mode -> trying direct first")
                            directSuccess = startAetherInternal(effectiveConfig, bindAddress, attemptId)
                        } catch (e: Exception) {
                            LogRepository.w("[Controller] Direct aether failed: ${e.localizedMessage}")
                            runCatching { cleanup(attemptId) }
                            delay(500.milliseconds)
                            if (activeAttemptId.get() != attemptId) return
                            notifyStatusChanged(appContext, ConnectionStatus.STARTING)
                        }
                        if (directSuccess) {
                            LogRepository.i("[Controller] Direct aether ready, chaining Psiphon over it via http://127.0.0.1:${effectiveConfig.httpPort}")
                            notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                            var psiphonReady = false
                            try {
                                val httpUpstream = "http://127.0.0.1:${effectiveConfig.httpPort}"
                                val started = runNativeBounded<Unit>(30000L, "Psiphon.bg") { PsiphonController.start(appContext, effectiveConfig, upstream = httpUpstream) } != null && PsiphonController.isRunning()
                                if (started) {
                                    var waitPsiphon = 0
                                    while (waitPsiphon < 30 && !PsiphonController.isConnected()) { delay(1000.milliseconds); waitPsiphon++ }
                                    var stableWait = 0
                                    while (stableWait < 25 && !PsiphonController.stableFor(10000)) { delay(1000.milliseconds); stableWait++ }
                                    psiphonReady = PsiphonController.isConnected() && PsiphonController.stableFor(10000)
                                }
                            } catch (_: Exception) {}
                            if (psiphonReady) {
                                ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                LogRepository.i("[Controller] Psiphon over direct ready via ${ActiveProxyProvider.psiphonProxyUrl}")
                                try {
                                    val intent = Intent().setClassName(appContext.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService").apply {
                                        action = "io.github.immaghzbad.aetherst.SWITCH_HEV"
                                        putExtra("host", "127.0.0.1")
                                        putExtra("port", 3080)
                                    }
                                    appContext.startService(intent)
                                } catch (_: Exception) {}
                                psiphonChaining = false
                                notifyStatusChanged(appContext, ConnectionStatus.RUNNING)
                                return
                            } else {
                                LogRepository.e("[Controller] Psiphon not ready after direct aether - chain requires Psiphon, aborting")
                                PsiphonController.stop()
                                ActiveProxyProvider.psiphonProxyUrl = null
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon not ready after direct aether")
                            }
                        }
                        if (isWireGuardFamilyFallback) {
                            LogRepository.w("[Controller] Direct ${effectiveConfig.protocol} failed, WireGuard cannot be chained over Psiphon SOCKS (code 7 UDP); failing")
                            throw IllegalStateException("WireGuard family cannot fallback via Psiphon SOCKS")
                        }
                        LogRepository.i("[Controller] Fallback to psiphon-first chain")
                        runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }) }
                        if (PsiphonController.isRunning()) {
                            var waitPsiphon = 0
                            while (waitPsiphon < 30 && !PsiphonController.isConnected()) {
                                delay(1000.milliseconds)
                                waitPsiphon++
                            }
                            var stableWait = 0
                            while (stableWait < 25 && !PsiphonController.stableFor(10000)) {
                                delay(1000.milliseconds)
                                stableWait++
                            }
                            if (PsiphonController.isConnected() && PsiphonController.stableFor(10000)) {
                                LogRepository.i("[Controller] Psiphon settled (stable), proceeding to chain core")
                                effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                LogRepository.i("[Controller] Psiphon active, chaining via ${effectiveConfig.upstreamProxy} outer=${effectiveConfig.protocol}")
                            } else {
                                LogRepository.e("[Controller] Psiphon not connected/stable - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                PsiphonController.stop()
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon not connected/stable")
                            }
                        } else {
                            LogRepository.e("[Controller] Psiphon failed to start in fallback - chain requires Psiphon, aborting")
                            ActiveProxyProvider.psiphonProxyUrl = null
                            psiphonChaining = false
                            runCatching { runner.stop() }
                            throw IllegalStateException("Psiphon failed to start in fallback")
                        }
                        psiphonChaining = false
                        if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                            throw IllegalStateException("Core failed via psiphon chain")
                        }
                    }
                    else -> {
                        val isWireGuardFamily = effectiveConfig.protocol == AetherProtocol.WG || effectiveConfig.protocol == AetherProtocol.GOOL
                        if (isWireGuardFamily) {
                            var directSuccess = false
                            try {
                                LogRepository.i("[Controller] Psiphon AUTO WireGuard family -> direct first for Psiphon over ${effectiveConfig.protocol}")
                                directSuccess = startAetherInternal(effectiveConfig, bindAddress, attemptId)
                            } catch (e: Exception) {
                                LogRepository.w("[Controller] Direct aether failed: ${e.localizedMessage}")
                                runCatching { cleanup(attemptId) }
                                delay(500.milliseconds)
                                if (activeAttemptId.get() != attemptId) return
                                notifyStatusChanged(appContext, ConnectionStatus.STARTING)
                            }
                            if (directSuccess) {
                                LogRepository.i("[Controller] Direct ${effectiveConfig.protocol} ready, chaining Psiphon over it via http://127.0.0.1:${effectiveConfig.httpPort}")
                                notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                                var psiphonReady = false
                                try {
                                    val httpUpstream = "http://127.0.0.1:${effectiveConfig.httpPort}"
                                    val started = runNativeBounded<Unit>(30000L, "Psiphon.bg") { PsiphonController.start(appContext, effectiveConfig, upstream = httpUpstream) } != null && PsiphonController.isRunning()
                                    if (started) {
                                        var waitPsiphon = 0
                                        while (waitPsiphon < 30 && !PsiphonController.isConnected()) { delay(1000.milliseconds); waitPsiphon++ }
                                        var stableWait = 0
                                        while (stableWait < 25 && !PsiphonController.stableFor(10000)) { delay(1000.milliseconds); stableWait++ }
                                        psiphonReady = PsiphonController.isConnected() && PsiphonController.stableFor(10000)
                                    }
                                } catch (_: Exception) {}
                                if (psiphonReady) {
                                    ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                    LogRepository.i("[Controller] Psiphon over ${effectiveConfig.protocol} ready via ${ActiveProxyProvider.psiphonProxyUrl}")
                                    try {
                                        val intent = Intent().setClassName(appContext.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService").apply {
                                            action = "io.github.immaghzbad.aetherst.SWITCH_HEV"
                                            putExtra("host", "127.0.0.1")
                                            putExtra("port", 3080)
                                        }
                                        appContext.startService(intent)
                                    } catch (_: Exception) {}
                                    psiphonChaining = false
                                    notifyStatusChanged(appContext, ConnectionStatus.RUNNING)
                                    return
                                } else {
                                    LogRepository.e("[Controller] Psiphon not ready over ${effectiveConfig.protocol} after direct - chain requires Psiphon, aborting")
                                    PsiphonController.stop()
                                    ActiveProxyProvider.psiphonProxyUrl = null
                                    psiphonChaining = false
                                    runCatching { runner.stop() }
                                    throw IllegalStateException("Psiphon not ready over ${effectiveConfig.protocol}")
                                }
                            }
                            LogRepository.e("[Controller] Direct ${effectiveConfig.protocol} failed and Psiphon over HTTP also failed - aborting")
                            throw IllegalStateException("WireGuard family cannot fallback via Psiphon SOCKS")
                        } else {
                        val masqueOrder = effectiveConfig.psiphonMasqueOrder.lowercase().trim()
                        if (masqueOrder != "aether_first") {
                            LogRepository.i("[Controller] Psiphon AUTO MASQUE -> psiphon-first chain (order=$masqueOrder)")
                            runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }) }
                            if (PsiphonController.isRunning()) {
                                var waitPsiphon = 0
                                while (waitPsiphon < 30 && !PsiphonController.isConnected()) { delay(1000.milliseconds); waitPsiphon++ }
                                var stableWait = 0
                                while (stableWait < 25 && !PsiphonController.stableFor(10000)) { delay(1000.milliseconds); stableWait++ }
                                if (PsiphonController.isConnected() && PsiphonController.stableFor(10000)) {
                                    LogRepository.i("[Controller] Psiphon settled (stable), proceeding to chain core")
                                    effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                    ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                    LogRepository.i("[Controller] Psiphon active, chaining via ${effectiveConfig.upstreamProxy} outer=${effectiveConfig.protocol}")
                                    notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                                    psiphonChaining = false
                                    if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                        throw IllegalStateException("Core failed via psiphon chain")
                                    }
                                    return
                                } else {
                                    LogRepository.e("[Controller] Psiphon not connected/stable over ${effectiveConfig.protocol} - chain requires Psiphon, aborting")
                                    PsiphonController.stop()
                                    ActiveProxyProvider.psiphonProxyUrl = null
                                    psiphonChaining = false
                                    runCatching { runner.stop() }
                                    throw IllegalStateException("Psiphon not connected/stable over ${effectiveConfig.protocol}")
                                }
                            } else {
                                LogRepository.e("[Controller] Psiphon failed to start over ${effectiveConfig.protocol} - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon failed to start over ${effectiveConfig.protocol}")
                            }
                        }
                        var directSuccess = false
                        try {
                            LogRepository.i("[Controller] Psiphon AUTO mode -> trying direct first")
                            directSuccess = startAetherInternal(effectiveConfig, bindAddress, attemptId)
                        } catch (e: Exception) {
                            LogRepository.w("[Controller] Direct aether failed: ${e.localizedMessage}")
                            runCatching { cleanup(attemptId) }
                            delay(500.milliseconds)
                            if (activeAttemptId.get() != attemptId) return
                            notifyStatusChanged(appContext, ConnectionStatus.STARTING)
                        }
                        if (directSuccess) {
                            LogRepository.i("[Controller] Direct aether ready, now waiting for Psiphon to chain egress")
                            notifyStatusChanged(appContext, ConnectionStatus.VALIDATING)
                            var psiphonReady = false
                            try {
                                val started = runNativeBounded<Unit>(30000L, "Psiphon.bg") { PsiphonController.start(appContext, effectiveConfig, upstream = null) } != null && PsiphonController.isRunning()
                                if (started) {
                                    var waitPsiphon = 0
                                    while (waitPsiphon < 30 && !PsiphonController.isConnected()) { delay(1000.milliseconds); waitPsiphon++ }
                                    var stableWait = 0
                                    while (stableWait < 25 && !PsiphonController.stableFor(10000)) { delay(1000.milliseconds); stableWait++ }
                                    psiphonReady = PsiphonController.isConnected() && PsiphonController.stableFor(10000)
                                }
                            } catch (_: Exception) {}
                            if (psiphonReady) {
                                LogRepository.i("[Controller] Re-chaining aether via Psiphon for Psiphon egress")
                                runCatching { runner.stop() }
                                delay(800.milliseconds)
                                if (activeAttemptId.get() != attemptId) return
                                notifyStatusChanged(appContext, ConnectionStatus.STARTING)
                                effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                LogRepository.i("[Controller] Restarting aether via Psiphon ${effectiveConfig.upstreamProxy}")
                                psiphonChaining = false
                                if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                                    throw IllegalStateException("Core failed via psiphon chain after direct")
                                }
                                try {
                                    val intent = Intent().setClassName(appContext.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService").apply {
                                        action = "io.github.immaghzbad.aetherst.SWITCH_HEV"
                                        putExtra("host", "127.0.0.1")
                                        putExtra("port", 3080)
                                    }
                                    appContext.startService(intent)
                                } catch (_: Exception) {}
                                return
                            } else {
                                LogRepository.e("[Controller] Psiphon not ready after direct aether - chain requires Psiphon, aborting")
                                PsiphonController.stop()
                                ActiveProxyProvider.psiphonProxyUrl = null
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon not ready after direct aether")
                            }
                        }
                        LogRepository.i("[Controller] Fallback to psiphon-first chain")
                        runNativeBounded<Unit>(30000L, "Psiphon.start") { PsiphonController.start(appContext, effectiveConfig, upstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }) }
                        if (PsiphonController.isRunning()) {
                            var waitPsiphon = 0
                            while (waitPsiphon < 30 && !PsiphonController.isConnected()) {
                                delay(1000.milliseconds)
                                waitPsiphon++
                            }
                            var stableWait = 0
                            while (stableWait < 25 && !PsiphonController.stableFor(10000)) {
                                delay(1000.milliseconds)
                                stableWait++
                            }
                            if (PsiphonController.isConnected() && PsiphonController.stableFor(10000)) {
                                LogRepository.i("[Controller] Psiphon settled (stable), proceeding to chain core")
                                effectiveConfig = effectiveConfig.copy(upstreamProxyEnabled = true, upstreamProxy = PsiphonController.getUpstreamProxy())
                                ActiveProxyProvider.psiphonProxyUrl = PsiphonController.getUpstreamProxy()
                                LogRepository.i("[Controller] Psiphon active, chaining via ${effectiveConfig.upstreamProxy} outer=${effectiveConfig.protocol}")
                            } else {
                                LogRepository.e("[Controller] Psiphon not connected/stable in fallback - chain requires Psiphon, aborting")
                                ActiveProxyProvider.psiphonProxyUrl = null
                                PsiphonController.stop()
                                psiphonChaining = false
                                runCatching { runner.stop() }
                                throw IllegalStateException("Psiphon not connected/stable in fallback")
                            }
                        } else {
                            LogRepository.e("[Controller] Psiphon failed to start in fallback - chain requires Psiphon, aborting")
                            ActiveProxyProvider.psiphonProxyUrl = null
                            psiphonChaining = false
                            runCatching { runner.stop() }
                            throw IllegalStateException("Psiphon failed to start in fallback")
                        }
                        psiphonChaining = false
                        if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                            throw IllegalStateException("Core failed via psiphon chain")
                        }
                        }
                    }
                }
            } else {
                ActiveProxyProvider.psiphonProxyUrl = null
                if (!startAetherInternal(effectiveConfig, bindAddress, attemptId)) {
                    throw IllegalStateException("Core failed direct")
                }
            }
        } catch (e: Exception) {
            val st = _status.value
            if (st == ConnectionStatus.STOPPED || st == ConnectionStatus.STOPPING) {
                runCatching { cleanup(attemptId) }
                return
            }
            if (runner.connectionStatus.value != ConnectionStatus.RUNNING) {
                LogRepository.e("[Controller] Startup failed: ${e.localizedMessage}")
                cleanup(attemptId)
                notifyStatusChanged(appContext, ConnectionStatus.ERROR)
            } else {
                LogRepository.w("[Controller] Startup check failed but core is running: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun startAetherInternal(config: AetherConfig, bindAddress: String, attemptId: Long): Boolean {
        LogRepository.i("[Controller] Starting core at $bindAddress (event-driven, awaiting core verdict)")
        runner.start(config, bindAddress, onCodeRequired = { updateIsWaitingForCode(true) }, inputProvider = { loginCodeChannel.receive() })
        val proxyPort = config.socksPort.toIntOrNull() ?: 1819
        var dpValidated = false
        var lastStatus = runner.connectionStatus.value
        var lastProgressAt = System.currentTimeMillis()
        var stallWarned = false
        while (currentCoroutineContext().isActive) {
            if (activeAttemptId.get() != attemptId) return false
            val coreStatus = runner.connectionStatus.value
            if (coreStatus != lastStatus) {
                lastStatus = coreStatus
                lastProgressAt = System.currentTimeMillis()
                stallWarned = false
                LogRepository.i("[Controller] Core status -> $coreStatus")
            }
            if (coreStatus == ConnectionStatus.DATAPLANE_VALIDATED || coreStatus == ConnectionStatus.SOCKS_READY) {
                dpValidated = true
                break
            }
            if (coreStatus == ConnectionStatus.ERROR) throw IllegalStateException("Core reported error during startup")
            if (coreStatus == ConnectionStatus.STOPPED) throw IllegalStateException("Core stopped unexpectedly during startup")
            if (isWaitingForCode.value) {
                lastProgressAt = System.currentTimeMillis()
                delay(1000.milliseconds)
                continue
            }
            val stalledMs = System.currentTimeMillis() - lastProgressAt
            if (stalledMs > 120_000 && !stallWarned) {
                LogRepository.w("[Controller] Core stalled without progress for ${stalledMs / 1000}s while awaiting data-plane validation (still waiting for core verdict)")
                stallWarned = true
            }
            if (stalledMs > 180_000) {
                throw IllegalStateException("Core stalled: no data-plane validation after 180s without status change (core did not report success or failure)")
            }
            delay(250.milliseconds)
        }
        if (!dpValidated) {
            val coreStatus = runner.connectionStatus.value
            if (coreStatus == ConnectionStatus.ERROR) throw IllegalStateException("Core failed to start (error)")
            if (coreStatus == ConnectionStatus.STOPPED) throw IllegalStateException("Core stopped unexpectedly")
            throw IllegalStateException("Core data-plane validation failed (no verdict from core)")
        }
        notifyStatusChanged(appContext, ConnectionStatus.DATAPLANE_VALIDATED)
        val socksReady = withTimeoutOrNull(60.seconds) {
            while (currentCoroutineContext().isActive) {
                if (activeAttemptId.get() != attemptId) return@withTimeoutOrNull false
                val coreStatus = runner.connectionStatus.value
                if (coreStatus == ConnectionStatus.SOCKS_READY) return@withTimeoutOrNull true
                if (coreStatus == ConnectionStatus.ERROR) throw IllegalStateException("Core reported error during startup")
                if (coreStatus == ConnectionStatus.STOPPED) throw IllegalStateException("Core stopped unexpectedly during startup")
                if (probeSocksReady("127.0.0.1", proxyPort)) return@withTimeoutOrNull true
                delay(250.milliseconds)
            }
            false
        } ?: false
        if (!socksReady) throw IllegalStateException("SOCKS proxy not ready (0x00 probe failed) after 60s")
        notifyStatusChanged(appContext, ConnectionStatus.SOCKS_READY)
        if (!verifyPortListening("127.0.0.1", proxyPort)) throw IllegalStateException("Proxy port $proxyPort is not listening")
        delay(3000.milliseconds)
        notifyStatusChanged(appContext, ConnectionStatus.RUNNING)
        startTimer()
        LogRepository.i("[Controller] Core is active and validated on port $proxyPort")
        return true
    }

    override suspend fun stop() {
        mutex.withLock {
            if (_status.value == ConnectionStatus.STOPPED) {
                stopTimer()
                ActiveProxyProvider.psiphonProxyUrl = null
                return@withLock
            }

        psiphonChaining = false
        val attemptId = activeAttemptId.get()
        notifyStatusChanged(appContext, ConnectionStatus.STOPPING)
        LogRepository.i("[Controller] Stopping core")

        try {
            withTimeoutOrNull(10000.milliseconds) {
                withContext(Dispatchers.IO) {
                    runNativeBounded(3000L, "Cloak.stop") { CloakController.stop() }
                    runNativeBounded(3000L, "Psiphon.stop") { PsiphonController.stop() }
                }
                ActiveProxyProvider.psiphonProxyUrl = null
                runCatching { cleanup(attemptId) }
            } ?: LogRepository.w("[Controller] Stop teardown exceeded 10s safety bound")
        } catch (e: Exception) {
            LogRepository.e("[Controller] Stop teardown error: ${e.localizedMessage}")
        } finally {
            ActiveProxyProvider.psiphonProxyUrl = null
            stopTimer()
            runCatching { withTimeoutOrNull(2000.milliseconds) { cleanup(attemptId) } }
            notifyStatusChanged(appContext, ConnectionStatus.STOPPED)
            LogRepository.i("[Controller] Core stopped")
        }
        }
    }

    private suspend fun awaitPsiphonStable(timeoutSec: Int = 30, stableMs: Long = 10000): Boolean {
        var wait = 0
        while (wait < timeoutSec && !PsiphonController.isConnected()) { delay(1000.milliseconds); wait++ }
        var stable = 0
        while (stable < 25 && !PsiphonController.stableFor(stableMs)) { delay(1000.milliseconds); stable++ }
        return PsiphonController.isConnected() && PsiphonController.stableFor(stableMs)
    }

    private suspend fun <T> runNativeBounded(timeoutMs: Long, label: String, block: () -> T): T? {
        return withContext(Dispatchers.IO) {
            val done = CompletableDeferred<T?>()
            val thread = Thread {
                try {
                    done.complete(block())
                } catch (e: Throwable) {
                    LogRepository.w("[Controller] $label failed: ${e.message}")
                    done.complete(null)
                }
            }
            thread.isDaemon = true
            thread.name = "native-$label"
            thread.start()
            withTimeoutOrNull(timeoutMs.milliseconds) { done.await() }
                ?: run { LogRepository.w("[Controller] $label did not finish within ${timeoutMs}ms; continuing"); null }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    private suspend fun cleanup(attemptId: Long) {
        if (activeAttemptId.get() == attemptId) {
            activeAttemptId.set(0)
        }
        runner.stop()
        updateIsWaitingForCode(false)
        runCatching {
            var r = loginCodeChannel.tryReceive()
            while (r.isSuccess) r = loginCodeChannel.tryReceive()
        }
        delay(500.milliseconds)
    }

    private fun handleCoreStatus(coreStatus: ConnectionStatus) {
        _status.update { current ->
            if (current == ConnectionStatus.STOPPED) return@update current
            if (psiphonChaining) return@update current
            if (current == ConnectionStatus.STOPPING && coreStatus != ConnectionStatus.STOPPED) {
                return@update current
            }

            val next = when (coreStatus) {
                ConnectionStatus.ERROR -> {
                    if (current == ConnectionStatus.RUNNING || current == ConnectionStatus.RECONNECTING) {
                        LogRepository.e("[Controller] Core reported error")
                        stopTimer()
                        ConnectionStatus.ERROR
                    } else {
                        LogRepository.e("[Controller] Core error during $current")
                        coreStatus
                    }
                }
                ConnectionStatus.STOPPED -> {
                    if (current == ConnectionStatus.RUNNING || current == ConnectionStatus.RECONNECTING) {
                        LogRepository.w("[Controller] Core stopped unexpectedly")
                        stopTimer()
                        ConnectionStatus.ERROR
                    } else if (current == ConnectionStatus.STOPPING) {
                        stopTimer()
                        ConnectionStatus.STOPPED
                    } else if (current == ConnectionStatus.STARTING || current == ConnectionStatus.VALIDATING) {
                        LogRepository.w("[Controller] Core stopped during $current")
                        stopTimer()
                        coreStatus
                    } else {
                        current
                    }
                }
                ConnectionStatus.RECONNECTING -> {
                    if (current != ConnectionStatus.RECONNECTING) {
                        pauseTimer()
                        ConnectionStatus.RECONNECTING
                    } else {
                        current
                    }
                }
                ConnectionStatus.SOCKS_READY -> {
                    if (current == ConnectionStatus.RECONNECTING) {
                        resumeTimer()
                        ConnectionStatus.RUNNING
                    } else {
                        current
                    }
                }
                ConnectionStatus.DATAPLANE_VALIDATED -> current
                ConnectionStatus.RUNNING -> {
                    if (current != ConnectionStatus.RUNNING) {
                        if (current == ConnectionStatus.RECONNECTING) resumeTimer() else startTimer()
                        ConnectionStatus.RUNNING
                    } else {
                        current
                    }
                }
                else -> current
            }
            
            if (next != current) {
                notifyStatusChanged(appContext, next)
            }
            next
        }
    }

    private suspend fun isPortListening(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1000)
                    true
                }
            }.getOrDefault(false)
        }
    }

    private suspend fun verifyPortListening(host: String, port: Int): Boolean {
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            if (isPortListening(host, port)) return true
            delay(500.milliseconds)
        }
        return false
    }

    private suspend fun probeSocksReady(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), 3000)
                    socket.soTimeout = 6000
                    val ins = socket.getInputStream()
                    val out = socket.getOutputStream()
                    out.write(byteArrayOf(5, 1, 0))
                    out.flush()
                    val method = ByteArray(2)
                    if (!fillStream(ins, method)) return@runCatching false
                    if (method[0] != 5.toByte() || method[1] != 0.toByte()) return@runCatching false
                    val addr = InetAddress.getByName("1.1.1.1").address
                    val req = ByteArray(5 + 4 + 2)
                    req[0] = 5; req[1] = 1; req[2] = 0; req[3] = 1
                    System.arraycopy(addr, 0, req, 4, 4)
                    req[8] = (80 shr 8).toByte(); req[9] = 80.toByte()
                    out.write(req)
                    out.flush()
                    val hdr = ByteArray(4)
                    if (!fillStream(ins, hdr)) return@runCatching false
                    hdr[1].toInt() and 0xFF == 0
                }
            }.getOrDefault(false)
        }
    }

    private fun fillStream(ins: java.io.InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = ins.read(buffer, offset, buffer.size - offset)
            if (n <= 0) return false
            offset += n
        }
        return true
    }

    private fun startTimer() {
        timerJob?.cancel()
        durationSeconds = 0L
        prevTotalTx = 0L
        prevTotalRx = 0L
        timerJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                durationSeconds++
                Bridge.elapsedOverride.value = durationSeconds
                if (useTrafficStatsFallback && !hasManualTraffic) {
                    updateTrafficFromStats()
                }
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun resumeTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                durationSeconds++
                Bridge.elapsedOverride.value = durationSeconds
                if (useTrafficStatsFallback && !hasManualTraffic) {
                    updateTrafficFromStats()
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        durationSeconds = 0L
        Bridge.elapsedOverride.value = 0L
        Bridge.trafficOverride.value = SessionTraffic()
        hasManualTraffic = false
        accumulatedTx = 0L
        accumulatedRx = 0L
        lastEngineTx = 0L
        lastEngineRx = 0L
        prevTotalTx = 0L
        prevTotalRx = 0L
        baseTx = 0L
        baseRx = 0L
    }

    private fun updateTrafficFromStats() {
        val rawTx = TrafficStats.getUidTxBytes(Process.myUid())
        val rawRx = TrafficStats.getUidRxBytes(Process.myUid())
        if (rawTx == TrafficStats.UNSUPPORTED.toLong() || rawRx == TrafficStats.UNSUPPORTED.toLong()) return
        val currentTx = if (rawTx < 0) 0L else rawTx
        val currentRx = if (rawRx < 0) 0L else rawRx
        val diffTx = (currentTx - baseTx).coerceAtLeast(0)
        val diffRx = (currentRx - baseRx).coerceAtLeast(0)
        val uploadSpeed = (diffTx - prevTotalTx).coerceAtLeast(0).toDouble()
        val downloadSpeed = (diffRx - prevTotalRx).coerceAtLeast(0).toDouble()
        prevTotalTx = diffTx
        prevTotalRx = diffRx
        Bridge.trafficOverride.value = SessionTraffic(diffTx, diffRx, uploadSpeed, downloadSpeed)
    }

    fun setTraffic(tx: Long, rx: Long) {
        val safeTx = tx.coerceAtLeast(0)
        val safeRx = rx.coerceAtLeast(0)
        if (!hasManualTraffic) {
            if (safeTx == 0L && safeRx == 0L) {
                hasManualTraffic = true
                lastEngineTx = 0L
                lastEngineRx = 0L
                accumulatedTx = 0L
                accumulatedRx = 0L
                prevTotalTx = 0L
                prevTotalRx = 0L
                Bridge.trafficOverride.value = SessionTraffic(0, 0, 0.0, 0.0)
                return
            }
            hasManualTraffic = true
        }
        if (safeTx < lastEngineTx || safeRx < lastEngineRx) {
            accumulatedTx += lastEngineTx
            accumulatedRx += lastEngineRx
        }
        lastEngineTx = safeTx
        lastEngineRx = safeRx
        val totalTx = accumulatedTx + safeTx
        val totalRx = accumulatedRx + safeRx
        val uploadSpeed = (totalTx - prevTotalTx).coerceAtLeast(0).toDouble()
        val downloadSpeed = (totalRx - prevTotalRx).coerceAtLeast(0).toDouble()
        prevTotalTx = totalTx
        prevTotalRx = totalRx
        Bridge.trafficOverride.value = SessionTraffic(totalTx, totalRx, uploadSpeed, downloadSpeed)
    }
}
