package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.*
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.platform.getTrafficProvider
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.shared.platform.Bridge
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

actual object ConnectionController {
    private val _isWaitingForCode = MutableStateFlow(false)
    actual val isWaitingForCode: StateFlow<Boolean> = _isWaitingForCode.asStateFlow()

    private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
    actual val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    actual val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sessionTraffic = MutableStateFlow(SessionTraffic())
    actual val sessionTraffic: StateFlow<SessionTraffic> = _sessionTraffic.asStateFlow()

    actual fun markStatus(status: ConnectionStatus) {
        _status.value = status
    }

    @Volatile
    private var INSTANCE: ControllerImpl? = null

    actual fun getInstance(context: PlatformContext) {
        if (INSTANCE == null) {
            synchronized(this) {
                if (INSTANCE == null) INSTANCE = ControllerImpl(context)
            }
        }
    }

    fun submitLoginCode(code: String) {
        _isWaitingForCode.value = false
        INSTANCE?.submitLoginCode(code)
    }

    fun getImpl(context: PlatformContext): ControllerImpl {
        getInstance(context)
        return INSTANCE!!
    }

    class ControllerImpl(private val context: PlatformContext) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val runner = AetherProcessRunner(context)
        private val trafficProvider = getTrafficProvider(context)
        private val loginCodeChannel = Channel<String>(Channel.UNLIMITED)

        private var timerJob: Job? = null
        private var baseTx = 0L
        private var baseRx = 0L
        private var prevTx = 0L
        private var prevRx = 0L
        @Volatile private var socksProxy: LocalSocksProxyServer? = null
        @Volatile private var httpProxy: LocalHttpProxyServer? = null
        @Volatile private var dnsServer: LocalDnsServer? = null
        @Volatile private var tunnelModeStarted = false
        private var routingEngine: RoutingEngine? = null
        private var statusJob: Job? = null
        private var modeJob: Job? = null

        // ── NEW: Feature integrations ───────────────────────────────────
        @Volatile private var lanProxyServer: LanProxyServer? = null
        @Volatile private var networkMonitorStarted = false

        init {
            scope.launch {
                Bridge.statusOverride.collect { s ->
                    if (s != null) {
                        _status.value = s
                        if (s == ConnectionStatus.STOPPED || s == ConnectionStatus.ERROR) {
                            stopTimer()
                        }
                    }
                }
            }
            scope.launch {
                Bridge.trafficOverride.collect {
                    if (it != null) _sessionTraffic.value = it
                }
            }
            scope.launch {
                Bridge.elapsedOverride.collect {
                    if (it != null) _elapsedSeconds.value = it
                }
            }
        }

        fun start() {
            LogRepository.i("Initializing connection process...", "AetherSystem")
            if (statusJob == null) {
                statusJob = scope.launch {
                    runner.connectionStatus.collect { _status.value = it }
                }
            }
            val config = AetherConfigRepository.getInstance(getSettings(context)).config.value
            var effectiveConfig = config
            baseTx = trafficProvider.getTxBytes()
            baseRx = trafficProvider.getRxBytes()

            getSystemUtils(context).clearSystemProxy()

            runner.start(
                config = effectiveConfig,
                bindAddress = "127.0.0.1:${effectiveConfig.socksPort}",
                onCodeRequired = { _isWaitingForCode.value = true },
                inputProvider = { loginCodeChannel.receive() }
            )

            val coreSocksPort = effectiveConfig.socksPort.toIntOrNull() ?: 1819
            routingEngine = RoutingEngine(effectiveConfig.routingRules)
            socksProxy = LocalSocksProxyServer(
                listenHost = "127.0.0.1",
                listenPort = 10808,
                targetHost = "127.0.0.1",
                targetPort = coreSocksPort,
                routingEngine = routingEngine!!
            ).apply { start() }
            httpProxy = LocalHttpProxyServer(
                listenHost = "127.0.0.1",
                listenPort = 10809,
                targetHost = "127.0.0.1",
                targetPort = coreSocksPort,
                routingEngine = routingEngine!!
            ).apply { start() }
            LogRepository.i("[Controller] Counting proxies started (socks=10808, http=10809) -> core $coreSocksPort")

            // ── NEW: Start LAN proxy if hotspot sharing is enabled ──────
            if (effectiveConfig.shareHotspot) {
                startLanProxy(effectiveConfig)
            }

            // ── NEW: Start network monitor for auto-reconnect ───────────
            startNetworkMonitor(effectiveConfig)

            if (effectiveConfig.connectionMode == ConnectionMode.TUNNEL || effectiveConfig.connectionMode == ConnectionMode.SYSTEM_PROXY) {
                val dnsUpstream = (if (effectiveConfig.dnsEnabled) effectiveConfig.dnsList else "").ifEmpty { "1.1.1.1,1.0.0.1" }
                val tmpDns = LocalDnsServer(listenHost = "127.0.0.1", listenPort = 53, socksHost = "127.0.0.1", socksPort = coreSocksPort, upstreamList = dnsUpstream)
                tmpDns.start()
                if (tmpDns.isRunning()) {
                    dnsServer = tmpDns
                    try { getSystemUtils(context).setSystemDns("127.0.0.1") } catch (_: Throwable) {}
                } else {
                    LogRepository.w("[Controller] DNS relay failed to bind 53 (needs admin), using system DNS $dnsUpstream without relay")
                    try { getSystemUtils(context).setSystemDns(dnsUpstream) } catch (_: Throwable) {}
                }
            }

            modeJob?.cancel()
            if (effectiveConfig.connectionMode == ConnectionMode.TUNNEL) {
                modeJob = scope.launch {
                    status.collect { s ->
                        if (s == ConnectionStatus.RUNNING) {
                            delay(500.milliseconds)
                            startTunnelMode(effectiveConfig)
                        }
                    }
                }
            } else if (effectiveConfig.connectionMode == ConnectionMode.SYSTEM_PROXY) {
                modeJob = scope.launch {
                    status.collect { s ->
                        if (s == ConnectionStatus.RUNNING) {
                            delay(500.milliseconds)
                            getSystemUtils(context).setSystemProxy("127.0.0.1", 10809)
                        }
                    }
                }
            }

            startTimer()
        }

        private fun startTunnelMode(config: AetherConfig) {
            if (tunnelModeStarted) return
            tunnelModeStarted = true

            if (socksProxy == null) {
                val coreSocksPort = config.socksPort.toIntOrNull() ?: 1819
                if (routingEngine == null) routingEngine = RoutingEngine(config.routingRules)
                socksProxy = LocalSocksProxyServer(
                    listenHost = "127.0.0.1",
                    listenPort = 10808,
                    targetHost = "127.0.0.1",
                    targetPort = coreSocksPort,
                    routingEngine = routingEngine!!
                ).apply { start() }
            }
            LogRepository.i("[Controller] Local SOCKS bridge listening on 127.0.0.1:10808")

            TunHelper.start(config.socksPort.toIntOrNull() ?: 1819)
            LogRepository.i("[Controller] TUN helper started mtu=${config.mtu}")
        }

        // ── NEW: LAN Proxy (Hotspot Sharing) ────────────────────────────

        fun startLanProxy(config: AetherConfig) {
            if (lanProxyServer?.isActive() == true) return
            val socksPort = (config.socksPort.toIntOrNull() ?: 1819) + 10 // LAN SOCKS on core+10
            val httpPort = (config.httpPort.toIntOrNull() ?: 1820) + 10   // LAN HTTP on core+10
            lanProxyServer = LanProxyServer(
                socksListenPort = socksPort,
                httpListenPort = httpPort,
                targetSocksHost = "127.0.0.1",
                targetSocksPort = 10808,
                targetHttpHost = "127.0.0.1",
                targetHttpPort = 10809
            )
            scope.launch(Dispatchers.IO) {
                lanProxyServer?.start()
            }
        }

        fun stopLanProxy() {
            scope.launch(Dispatchers.IO) {
                lanProxyServer?.stop()
                lanProxyServer = null
            }
        }

        fun getLanProxyServer(): LanProxyServer? = lanProxyServer

        // ── NEW: Network Monitor + Auto-Reconnect ───────────────────────

        private fun startNetworkMonitor(config: AetherConfig) {
            if (networkMonitorStarted) return
            if (!config.smartReconnect) return
            networkMonitorStarted = true

            NetworkMonitor.start(
                scope = scope,
                onNetworkLost = {
                    LogRepository.w("[Controller] Network lost – pausing connection")
                    val currentStatus = _status.value
                    if (currentStatus == ConnectionStatus.RUNNING) {
                        _status.value = ConnectionStatus.RECONNECTING
                    }
                },
                onNetworkRestored = {
                    LogRepository.i("[Controller] Network restored – auto-reconnecting")
                    val currentStatus = _status.value
                    if (currentStatus == ConnectionStatus.RECONNECTING || currentStatus == ConnectionStatus.ERROR) {
                        scope.launch {
                            delay(1000) // Brief delay for network stabilization
                            stopInternal()
                            delay(500)
                            start()
                        }
                    }
                }
            )
        }

        private fun stopNetworkMonitor() {
            NetworkMonitor.stop()
            networkMonitorStarted = false
        }

        private val stopLock = Any()

        fun stop() {
            synchronized(stopLock) {
                stopInternal()
            }
        }

        private fun stopInternal() {
            runner.stop()
            stopTimer()
            modeJob?.cancel()
            modeJob = null
            getSystemUtils(context).clearSystemProxy()
            try { getSystemUtils(context).clearSystemDns() } catch (_: Throwable) {}
            dnsServer?.stop()
            dnsServer = null
            socksProxy?.stop()
            socksProxy = null
            httpProxy?.stop()
            httpProxy = null
            tunnelModeStarted = false
            TunHelper.stop()
            routingEngine = null
            statusJob?.cancel()
            statusJob = null

            // ── NEW: Clean up new features ──────────────────────────────
            lanProxyServer?.stop()
            lanProxyServer = null
            stopNetworkMonitor()
            PsiphonController.stop()
        }

        private fun startTimer() {
            timerJob?.cancel()
            _elapsedSeconds.value = 0
            prevTx = 0L
            prevRx = 0L
            timerJob = scope.launch {
                var seconds = 0L
                while (isActive) {
                    delay(1000.milliseconds)
                    seconds++
                    _elapsedSeconds.value = seconds
                    updateTraffic()
                }
            }
        }

        private fun stopTimer() {
            timerJob?.cancel()
            _elapsedSeconds.value = 0L
            _sessionTraffic.value = SessionTraffic()
        }

        private fun updateTraffic() {
            val socksStats = socksProxy?.getStats()
            val httpStats = httpProxy?.getStats()
            val lanStats = lanProxyServer?.getStats()
            val hasCounting = socksStats != null || httpStats != null
            if (hasCounting) {
                val totalTx = (socksStats?.txBytes ?: 0L) + (httpStats?.txBytes ?: 0L) + (lanStats?.txBytes ?: 0L)
                val totalRx = (socksStats?.rxBytes ?: 0L) + (httpStats?.rxBytes ?: 0L) + (lanStats?.rxBytes ?: 0L)
                val uploadSpeed = (totalTx - prevTx).coerceAtLeast(0L).toDouble()
                val downloadSpeed = (totalRx - prevRx).coerceAtLeast(0L).toDouble()
                prevTx = totalTx
                prevRx = totalRx
                _sessionTraffic.value = SessionTraffic(
                    uploadedBytes = totalTx,
                    downloadedBytes = totalRx,
                    uploadSpeedBps = uploadSpeed,
                    downloadSpeedBps = downloadSpeed
                )
                return
            }
            val currentTx = trafficProvider.getTxBytes()
            val currentRx = trafficProvider.getRxBytes()
            val totalTx = (currentTx - baseTx).coerceAtLeast(0L)
            val totalRx = (currentRx - baseRx).coerceAtLeast(0L)
            val uploadSpeed = (totalTx - prevTx).coerceAtLeast(0L).toDouble()
            val downloadSpeed = (totalRx - prevRx).coerceAtLeast(0L).toDouble()
            prevTx = totalTx
            prevRx = totalRx
            _sessionTraffic.value = SessionTraffic(
                uploadedBytes = totalTx,
                downloadedBytes = totalRx,
                uploadSpeedBps = uploadSpeed,
                downloadSpeedBps = downloadSpeed
            )
        }

        fun submitLoginCode(code: String) {
            _isWaitingForCode.value = false
            loginCodeChannel.trySend(code)
        }
    }
}
