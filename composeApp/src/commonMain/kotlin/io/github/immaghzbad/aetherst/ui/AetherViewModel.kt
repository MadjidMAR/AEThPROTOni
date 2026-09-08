package io.github.immaghzbad.aetherst.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getAppInfoProvider
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.platform.getVpnController
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.shared.core.ConnectionController
import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.shared.data.IpInfo
import io.github.immaghzbad.aetherst.shared.data.IpInfoRepository
import io.github.immaghzbad.aetherst.shared.data.ActiveProxyProvider
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.data.PingRepository
import io.github.immaghzbad.aetherst.shared.data.PingState
import io.github.immaghzbad.aetherst.shared.i18n.getEffectiveStrings
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.AppInfo
import io.github.immaghzbad.aetherst.shared.model.ConnectionMode
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.LogEntry
import io.github.immaghzbad.aetherst.shared.model.RoutingMode
import io.github.immaghzbad.aetherst.shared.model.RoutingRule
import io.github.immaghzbad.aetherst.shared.model.TunnelEngine
import io.github.immaghzbad.aetherst.shared.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AetherViewModel(platformContext: PlatformContext) : ViewModel() {
    private val repository = AetherConfigRepository.getInstance(getSettings(platformContext))
    private val vpnController = getVpnController(platformContext)
    private val systemUtils = getSystemUtils(platformContext)
    private val appInfoProvider = getAppInfoProvider(platformContext)

    val config: StateFlow<AetherConfig> = repository.config
    val isOnboardingComplete: StateFlow<Boolean> = repository.isOnboardingComplete
    val connectionStatus: StateFlow<ConnectionStatus> = ConnectionController.status
    val elapsedSeconds: StateFlow<Long> = ConnectionController.elapsedSeconds
    val sessionTraffic = ConnectionController.sessionTraffic
    val isWaitingForLoginCode = ConnectionController.isWaitingForCode
    val logs: StateFlow<List<LogEntry>> = LogRepository.logs
    val ipInfo: StateFlow<IpInfo> = IpInfoRepository.ipInfo
    val pingState: StateFlow<PingState> = PingRepository.pingState

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isBatteryOptimized = MutableStateFlow(value = false)
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    val appVersion: String = systemUtils.getAppVersion()

    private val _importConflictRules = MutableStateFlow<List<RoutingRule>?>(null)
    val importConflictRules: StateFlow<List<RoutingRule>?> = _importConflictRules.asStateFlow()

    private val _importErrorMessage = MutableStateFlow<String?>(null)
    val importErrorMessage: StateFlow<String?> = _importErrorMessage.asStateFlow()

    private val _scrollToZeroTrust = MutableStateFlow(false)
    val scrollToZeroTrust: StateFlow<Boolean> = _scrollToZeroTrust.asStateFlow()

    private val _isOptimizingMtu = MutableStateFlow(false)
    val isOptimizingMtu: StateFlow<Boolean> = _isOptimizingMtu.asStateFlow()

    private val _crashLog = MutableStateFlow<String?>(null)
    val crashLog: StateFlow<String?> = _crashLog.asStateFlow()

    data class ToastState(val message: String, val isError: Boolean = false)
    private val _toastState = MutableStateFlow<ToastState?>(null)
    val toastState: StateFlow<ToastState?> = _toastState.asStateFlow()

    init {
        LogRepository.initialize(getSettings(platformContext))
        io.github.immaghzbad.aetherst.shared.data.SpeedTestRepository.initialize(getSettings(platformContext))
        io.github.immaghzbad.aetherst.shared.data.DnsBenchmarkRepository.initialize(getSettings(platformContext))
        LogRepository.i("Initializing AetherST Multiplatform UI...", "AetherSystem")
        ConnectionController.getInstance(platformContext)
        observeConnectionStatus()
        checkBatteryOptimizationStatus()
        checkLastCrash()
        loadInstalledApps()
        checkForUpdates()
    }

    private fun checkLastCrash() {
        viewModelScope.launch {
            val log = withContext(Dispatchers.Default) {
                systemUtils.readLastCrashLog()
            }
            _crashLog.value = log
        }
    }

    private fun localizedToast(selector: io.github.immaghzbad.aetherst.shared.i18n.AppStrings.() -> String): String {
        return getEffectiveStrings(config.value.appLanguage).selector()
    }

    fun toggleVpn(onPermissionRequired: () -> Unit) {
        val currentState = connectionStatus.value
        if (currentState == ConnectionStatus.STOPPING) return

        val cfg = config.value
        if (cfg.protocol == AetherProtocol.ZERO_TRUST) {
            val strings = getEffectiveStrings(cfg.appLanguage)
            val ztError = cfg.zeroTrustErrorLocalized(strings)
            if (ztError != null) {
                showToast(ztError, true)
                _scrollToZeroTrust.value = true
                return
            }
        }

        try {
            if ((currentState == ConnectionStatus.STOPPED) || (currentState == ConnectionStatus.ERROR)) {
                if (cfg.connectionMode == ConnectionMode.TUNNEL) {
                    if (vpnController.prepareVpn(onPermissionRequired)) {
                        ConnectionController.markStatus(ConnectionStatus.STARTING)
                        vpnController.startVpn()
                    }
                } else {
                    ConnectionController.markStatus(ConnectionStatus.STARTING)
                    vpnController.startProxy()
                }
            } else {
                ConnectionController.markStatus(ConnectionStatus.STOPPING)
                if (cfg.connectionMode == ConnectionMode.TUNNEL) {
                    vpnController.stopVpn()
                } else {
                    vpnController.stopProxy()
                }
            }
        } catch (exception: Exception) {
            LogRepository.e("[UI] Connection toggle failed: ${exception.message}")
            ConnectionController.markStatus(ConnectionStatus.ERROR)
        }
    }

    fun forceStop() {
        LogRepository.w("[UI] Force stop requested by user (recovering from stuck state)", "AetherSystem")
        ConnectionController.markStatus(ConnectionStatus.STOPPED)
        viewModelScope.launch {
            try {
                val mode = config.value.connectionMode
                if (mode == ConnectionMode.TUNNEL) vpnController.stopVpn() else vpnController.stopProxy()
            } catch (e: Exception) {
                LogRepository.e("[UI] Force stop backend teardown failed: ${e.message}")
            }
        }
    }

    fun prepareVpn(onPermissionRequired: () -> Unit): Boolean {
        return vpnController.prepareVpn(onPermissionRequired)
    }

    fun updateConfig(newConfig: AetherConfig) {
        val oldConfig = config.value
        if (oldConfig.psiphonEnabled && !newConfig.httpProxyEnabled && oldConfig.httpProxyEnabled) {
            showToast(localizedToast { TOAST_DISABLE_PSIPHON_FIRST }, true)
            return
        }
        var effectiveNewConfig = newConfig
        if (!effectiveNewConfig.psiphonEnabled && oldConfig.psiphonEnabled) {
            effectiveNewConfig = effectiveNewConfig.copy(psiphonChainOuter = "", psiphonMasqueOrder = "auto")
        }
        val isUiOnly = oldConfig.copy(connectButtonStyle = effectiveNewConfig.connectButtonStyle, appLanguage = effectiveNewConfig.appLanguage) == effectiveNewConfig
        if (!isUiOnly && !requireDisconnected()) return
        if (oldConfig.protocol == AetherProtocol.ZERO_TRUST && effectiveNewConfig.protocol != AetherProtocol.ZERO_TRUST) {
            _scrollToZeroTrust.value = false
        }
        repository.updateConfig(effectiveNewConfig)
        val needsRestart = oldConfig.connectionMode != effectiveNewConfig.connectionMode ||
                oldConfig.tunnelAllApps != effectiveNewConfig.tunnelAllApps ||
                oldConfig.protocol != effectiveNewConfig.protocol ||
                oldConfig.ipMode != effectiveNewConfig.ipMode ||
                oldConfig.mtu != effectiveNewConfig.mtu ||
                oldConfig.tunnelEngine != effectiveNewConfig.tunnelEngine ||
                oldConfig.ipv6Leak != effectiveNewConfig.ipv6Leak ||
                oldConfig.socksPort != effectiveNewConfig.socksPort ||
                oldConfig.socksHost != effectiveNewConfig.socksHost ||
                oldConfig.psiphonEnabled != effectiveNewConfig.psiphonEnabled ||
                oldConfig.psiphonChainMode != effectiveNewConfig.psiphonChainMode ||
                oldConfig.psiphonViaAether != effectiveNewConfig.psiphonViaAether ||
                oldConfig.psiphonEgressRegion != effectiveNewConfig.psiphonEgressRegion ||
                oldConfig.psiphonSocksPort != effectiveNewConfig.psiphonSocksPort ||
                oldConfig.psiphonChainOuter != effectiveNewConfig.psiphonChainOuter ||
                oldConfig.psiphonMasqueOrder != effectiveNewConfig.psiphonMasqueOrder
        if (needsRestart) {
            restartConnection()
        }
    }

    private fun restartConnection() {
        val state = connectionStatus.value
        if (state == ConnectionStatus.STOPPED || state == ConnectionStatus.ERROR || state == ConnectionStatus.STOPPING) return

        viewModelScope.launch {
            val oldCfg = config.value
            if (oldCfg.connectionMode == ConnectionMode.TUNNEL) vpnController.stopVpn() else vpnController.stopProxy()

            withTimeoutOrNull(5.seconds) {
                connectionStatus.first { it == ConnectionStatus.STOPPED || it == ConnectionStatus.ERROR }
                true
            }

            delay(500.milliseconds)

            val newCfg = config.value
            if (newCfg.connectionMode == ConnectionMode.TUNNEL) {
                if (vpnController.prepareVpn {}) {
                    vpnController.startVpn()
                }
            } else {
                vpnController.startProxy()
            }
        }
    }

    fun updateTunnelEngine(engine: TunnelEngine) {
        val current = config.value
        if (current.tunnelEngine == engine) return
        updateConfig(current.copy(tunnelEngine = engine))
        restartConnection()
    }

    fun updateAppSplitTunnelingMode(packageName: String, modeOrdinal: Int) {
        val current = config.value
        val tunneled = current.tunneledPackages.toMutableSet()
        val blocked = current.blockedPackages.toMutableSet()
        val excluded = current.excludedPackages.toMutableSet()
        tunneled.remove(packageName)
        blocked.remove(packageName)
        excluded.remove(packageName)
        when (modeOrdinal) {
            1 -> tunneled.add(packageName)
            2 -> blocked.add(packageName)
        }
        updateConfig(current.copy(tunneledPackages = tunneled.toSet(), blockedPackages = blocked.toSet(), excludedPackages = excluded.toSet()))
        restartConnection()
    }

    fun bulkUpdateAppSplitTunnelingMode(packageNames: List<String>, modeOrdinal: Int) {
        if (packageNames.isEmpty()) return
        val current = config.value
        val tunneled = current.tunneledPackages.toMutableSet()
        val blocked = current.blockedPackages.toMutableSet()
        val excluded = current.excludedPackages.toMutableSet()
        for (pkg in packageNames) {
            tunneled.remove(pkg)
            blocked.remove(pkg)
            excluded.remove(pkg)
            when (modeOrdinal) {
                1 -> tunneled.add(pkg)
                2 -> blocked.add(pkg)
            }
        }
        updateConfig(current.copy(tunneledPackages = tunneled.toSet(), blockedPackages = blocked.toSet(), excludedPackages = excluded.toSet()))
        restartConnection()
    }

    fun addRoutingRule(pattern: String, mode: RoutingMode) {
        val current = config.value
        if (current.routingRules.any { it.pattern == pattern }) return

        val newList = current.routingRules + RoutingRule(pattern, mode)
        LogRepository.i("Routing rule added: $pattern ($mode)")
        updateConfig(current.copy(routingRules = newList))
        restartConnection()
    }

    fun removeRoutingRule(pattern: String) {
        val current = config.value
        val newList = current.routingRules.filter { it.pattern != pattern }
        if (newList.size == current.routingRules.size) return

        LogRepository.i("Routing rule removed: $pattern")
        updateConfig(current.copy(routingRules = newList))
        restartConnection()
    }

    fun updateRoutingRuleMode(pattern: String, mode: RoutingMode) {
        val current = config.value
        val newList = current.routingRules.map {
            if (it.pattern == pattern) it.copy(mode = mode) else it
        }
        if (newList == current.routingRules) return

        LogRepository.i("Routing rule updated: $pattern -> $mode")
        updateConfig(current.copy(routingRules = newList))
        restartConnection()
    }

    fun clearAllRoutingRules() {
        val current = config.value
        if (current.routingRules.isEmpty()) return
        LogRepository.i("All routing rules cleared")
        updateConfig(current.copy(routingRules = emptyList()))
        restartConnection()
    }

    fun resetAllSettings() {
        if (!requireDisconnected()) return
        repository.resetToDefaults()
        restartConnection()
    }

    fun optimizeMtu() {
        if (!requireDisconnected()) return
        if (_isOptimizingMtu.value) return
        _isOptimizingMtu.value = true
        
        viewModelScope.launch {
            showToast(localizedToast { TOAST_MTU_DISCOVERY_START })
            
            var probeResult: Int? = null
            var dfIgnored = false

            withContext(Dispatchers.Default) {
                try {
                    val currentProtocol = config.value.protocol
                    val overhead = when (currentProtocol) {
                        AetherProtocol.WG, AetherProtocol.GOOL -> 80
                        AetherProtocol.MASQUE -> 60
                        else -> 40
                    }

                    val localMtu = systemUtils.getInterfaceMtu()
                    LogRepository.i("Step 1: Local interface reports MTU: $localMtu", "MTUProbe")

                    fun testMtu(totalSize: Int): Boolean {
                        val payloadSize = totalSize - 28
                        if (payloadSize < 0) return true
                        val targets = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
                        for (target in targets) {
                            if (systemUtils.execPing(target, payloadSize, 700, dontFragment = true)) return true
                        }
                        return false
                    }

                    LogRepository.i("Step 2: Validating network DF constraint...", "MTUProbe")
                    if (testMtu(2000)) {
                        LogRepository.w("WARNING: Network is ignoring DF bit. Results may be inaccurate.", "MTUProbe")
                        dfIgnored = true
                        probeResult = 1280 + overhead
                        return@withContext
                    }

                    LogRepository.i("Step 3: Probing path MTU via binary search...", "MTUProbe")
                    var low = 1200
                    var high = localMtu.coerceAtMost(1500)
                    var bestPathMtu = 1200

                    while (low <= high) {
                        val mid = (low + high) / 2
                        if (testMtu(mid)) {
                            bestPathMtu = mid
                            low = mid + 1
                            LogRepository.d("Probe success at $mid bytes", "MTUProbe")
                        } else {
                            high = mid - 1
                            LogRepository.d("Probe failed at $mid bytes", "MTUProbe")
                        }
                        delay(50.milliseconds)
                    }

                    LogRepository.i("Final Discovered Path MTU: $bestPathMtu", "MTUProbe")
                    probeResult = (bestPathMtu - overhead).coerceIn(1100, 1460)
                } catch (e: Exception) {
                    LogRepository.e("MTU Optimization failed: ${e.message}", "MTUProbe")
                }
            }

            _isOptimizingMtu.value = false

            probeResult?.let { finalResult ->
                val current = config.value
                val message = when {
                    dfIgnored -> localizedToast { TOAST_MTU_DF_IGNORED.format(finalResult) }
                    finalResult >= 1420 -> localizedToast { TOAST_MTU_HIGH_SPEED.format(finalResult) }
                    else -> localizedToast { TOAST_MTU_OPTIMAL.format(finalResult) }
                }
                showToast(message)
                updateConfig(current.copy(mtu = finalResult))
            } ?: run {
                showToast(localizedToast { TOAST_MTU_FAILED }, true)
                updateConfig(config.value.copy(mtu = 1280))
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val updateUrl = if (isDesktop) {
                    "https://raw.githubusercontent.com/immaghzbad/AetherST/refs/heads/main/updatewin.json"
                } else {
                    "https://raw.githubusercontent.com/immaghzbad/AetherST/refs/heads/main/update.json"
                }
                val info = withContext(Dispatchers.Default) {
                    val request = Request.Builder().url(updateUrl).build()
                    io.github.immaghzbad.aetherst.shared.core.NetworkClient.instance.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val jsonStr = response.body?.string() ?: return@withContext null
                            val element = Json.parseToJsonElement(jsonStr).jsonObject
                            UpdateInfo(
                                version = element["version"]?.jsonPrimitive?.content ?: "",
                                versionCode = element["version_code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                                isBeta = element["is_beta"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                                changelog = element["changelog"]?.jsonPrimitive?.content ?: "",
                                releaseUrl = element["release_url"]?.jsonPrimitive?.content ?: ""
                            )
                        } else null
                    }
                }
                if (info != null && isNewerVersion(info.version, appVersion, info.versionCode, systemUtils.getAppVersionCode())) {
                    _updateInfo.value = info
                }
            } catch (e: Throwable) {
                val msg = if (e is VerifyError) "VerifyError (ProGuard/OkHttp mismatch) - update check skipped" else e.message
                LogRepository.w("Update check failed: $msg")
            }
        }
    }

    private fun isNewerVersion(remoteVersion: String, localVersion: String, remoteCode: Int, localCode: Int): Boolean {
        if (remoteVersion.isBlank() || localVersion.isBlank()) return remoteCode > localCode
        fun parse(v: String): List<Int> = v.trim().removePrefix("v").removePrefix("V").split(".", "-", "_").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val r = parse(remoteVersion)
        val l = parse(localVersion)
        val maxLen = maxOf(r.size, l.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return remoteCode > localCode
    }

    fun clearCrashLog() {
        _crashLog.value = null
        viewModelScope.launch(Dispatchers.Default) {
            systemUtils.clearCrashLog()
        }
    }
    fun clearImportError() { _importErrorMessage.value = null }
    fun onZeroTrustScrolled() { _scrollToZeroTrust.value = false }
    fun dismissUpdate() { _updateInfo.value = null }
    fun cancelImport() { _importConflictRules.value = null }
    fun applyPreset(presetId: String) {
        if (!requireDisconnected()) return
        repository.applyPreset(presetId)
    }

    fun applyAutoDetectResult(result: io.github.immaghzbad.aetherst.shared.model.AutoDetectResult) {
        if (!requireDisconnected()) return
        val oldConfig = config.value
        val newConfig = oldConfig.copy(
            presetId = "custom",
            protocol = result.recommendedProtocol,
            noise = result.recommendedNoise,
            scanMode = result.recommendedScanMode,
            mtu = if (result.recommendedMtu > 0) result.recommendedMtu else oldConfig.mtu,
            ipMode = result.recommendedIpMode,
            h2Mode = result.recommendedH2Mode,
            echEnabled = result.recommendedEch,
            h2Fragment = result.recommendedFragment,
            fragmentSize = "16-32",
            fragmentDelay = "2-10",
            noDataCheck = result.recommendedNoDataCheck
        )
        repository.applyDetectedConfig(newConfig)
        val needsRestart = oldConfig.connectionMode != newConfig.connectionMode ||
                oldConfig.tunnelAllApps != newConfig.tunnelAllApps ||
                oldConfig.protocol != newConfig.protocol ||
                oldConfig.ipMode != newConfig.ipMode ||
                oldConfig.mtu != newConfig.mtu ||
                oldConfig.tunnelEngine != newConfig.tunnelEngine ||
                oldConfig.ipv6Leak != newConfig.ipv6Leak ||
                oldConfig.socksPort != newConfig.socksPort ||
                oldConfig.socksHost != newConfig.socksHost ||
                oldConfig.psiphonEnabled != newConfig.psiphonEnabled ||
                oldConfig.psiphonChainMode != newConfig.psiphonChainMode ||
                oldConfig.psiphonViaAether != newConfig.psiphonViaAether ||
                oldConfig.psiphonEgressRegion != newConfig.psiphonEgressRegion
        if (needsRestart) {
            restartConnection()
        }
        showToast(localizedToast { TOAST_AUTODETECT_APPLIED })
    }

    fun applyDnsList(dnsList: String) {
        if (!requireDisconnected()) return
        val normalized = dnsList.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        if (normalized.isBlank()) return
        updateConfig(config.value.copy(presetId = "custom", dnsList = normalized, dnsEnabled = true))
        showToast(localizedToast { TOAST_DNS_APPLIED.format(normalized) })
    }

    private fun requireDisconnected(): Boolean {
        val s = connectionStatus.value
        if (s != ConnectionStatus.STOPPED && s != ConnectionStatus.ERROR) {
            showToast(localizedToast { TOAST_DISCONNECT_FIRST }, true)
            return false
        }
        return true
    }

    private var toastJob: Job? = null

    fun showToast(message: String, isError: Boolean = false) {
        toastJob?.cancel()
        _toastState.value = ToastState(message, isError)
        toastJob = viewModelScope.launch {
            delay(5000.milliseconds)
            _toastState.value = null
        }
    }

    fun submitLoginCode(code: String) {
        vpnController.submitLoginCode(code)
    }

    fun refreshIpInfo() {
        viewModelScope.launch {
            val state = connectionStatus.value
            if (state == ConnectionStatus.RUNNING) {
                fetchPublicIp()
            } else {
                IpInfoRepository.fetchIpInfo(useProxy = false)
            }
        }
    }

    private suspend fun fetchPublicIp() {
        val psiphon = ActiveProxyProvider.psiphonProxyUrl
        if (!psiphon.isNullOrEmpty()) {
            val body = psiphon.removePrefix("socks5://").removePrefix("socks://")
            val parts = body.split(":", limit = 2)
            val host = parts.first()
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 3080
            IpInfoRepository.fetchIpInfo(host, port, useProxy = true)
        } else {
            val cfg = config.value
            IpInfoRepository.fetchIpInfo(cfg.socksHost, cfg.socksPort.toIntOrNull() ?: 1819, useProxy = true)
        }
    }

    fun refreshPing() {
        viewModelScope.launch {
            val state = connectionStatus.value
            if (state == ConnectionStatus.RUNNING) {
                val cfg = config.value
                PingRepository.runPing(cfg.socksHost, cfg.socksPort.toIntOrNull() ?: 1819, useProxy = true, pingUrl = cfg.pingUrl)
            } else {
                PingRepository.reset()
            }
        }
    }

    fun checkBatteryOptimizationStatus() {
        viewModelScope.launch(Dispatchers.Default) {
            val optimized = runCatching { systemUtils.isBatteryOptimized() }.getOrDefault(true)
            _isBatteryOptimized.value = optimized
        }
    }

    fun requestBatteryOptimization() {
        systemUtils.requestBatteryOptimization()
    }

    fun openVpnSettings() {
        systemUtils.openVpnSettings()
    }

    fun requestNotificationPermission() {
        systemUtils.requestNotificationPermission()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.Default) {
            delay(1500.milliseconds)
            val apps = appInfoProvider.getInstalledApps()
            _installedApps.value = apps
        }
    }

    private var ipRetryJob: Job? = null
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            connectionStatus.collect { state ->
                when (state) {
                    ConnectionStatus.RUNNING, ConnectionStatus.TUN_ACTIVE, ConnectionStatus.SOCKS_READY -> {
                        val cfg = config.value
                        val host = cfg.socksHost
                        val port = cfg.socksPort.toIntOrNull() ?: 1819
                        val pingUrl = cfg.pingUrl
                        viewModelScope.launch {
                            PingRepository.runPing(host, port, useProxy = true, pingUrl = pingUrl)
                            delay(600.milliseconds)
                            fetchPublicIp()
                        }
                        ipRetryJob?.cancel()
                        ipRetryJob = viewModelScope.launch {
                            var attempts = 0
                            while (attempts < 6) {
                                delay(7000.milliseconds)
                                val current = IpInfoRepository.ipInfo.value
                                if (current.ip.isEmpty() || current.isLoading) {
                                    fetchPublicIp()
                                    attempts++
                                } else {
                                    break
                                }
                            }
                        }
                    }
                    ConnectionStatus.RECONNECTING, ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.VALIDATING, ConnectionStatus.STARTING -> {
                        ipRetryJob?.cancel()
                    }
                    ConnectionStatus.STOPPED, ConnectionStatus.FAILED, ConnectionStatus.ERROR -> {
                        ipRetryJob?.cancel()
                        viewModelScope.launch { IpInfoRepository.fetchIpInfo(useProxy = false) }
                        PingRepository.reset()
                    }
                    else -> {
                        ipRetryJob?.cancel()
                    }
                }
            }
        }
    }

    fun clearLogs() { LogRepository.clear() }
    
    fun copyToClipboard(text: String) {
        systemUtils.copyToClipboard(text)
        showToast(localizedToast { TOAST_COPIED })
    }

    fun copyLogs() {
        val allLogs = logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level.name}] [${it.tag}] ${it.message}" }
        copyToClipboard(allLogs)
    }

    fun shareLogs() {
        val allLogs = logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level.name}] [${it.tag}] ${it.message}" }
        systemUtils.shareFile("AetherST_Logs.txt", allLogs)
    }

    fun cleanRoutingPattern(input: String): String {
        var pattern = input.trim()
        if (pattern.startsWith("http://", ignoreCase = true)) pattern = pattern.substring(7)
        if (pattern.startsWith("https://", ignoreCase = true)) pattern = pattern.substring(8)
        while (pattern.endsWith("/")) pattern = pattern.dropLast(1)
        return pattern
    }

    fun isValidRoutingPattern(pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        if (pattern.startsWith("regexp:")) return true
        val regex = Regex("^[a-zA-Z0-9.*:\\-/]+$")
        return regex.matches(pattern)
    }

    fun resolveConflict(rules: List<RoutingRule>, replace: Boolean) {
        _importConflictRules.value = null
        applyImport(rules, merge = !replace)
    }

    private fun applyImport(newRules: List<RoutingRule>, merge: Boolean) {
        val current = config.value
        val finalRules = if (merge) {
            val existingPatterns = current.routingRules.mapTo(mutableSetOf()) { it.pattern.lowercase() }
            current.routingRules + newRules.filter { it.pattern.lowercase() !in existingPatterns }
        } else {
            val newPatterns = newRules.mapTo(mutableSetOf()) { it.pattern.lowercase() }
            current.routingRules.filter { it.pattern.lowercase() !in newPatterns } + newRules
        }
        updateConfig(current.copy(routingRules = finalRules))
        restartConnection()
    }

    
    fun exportFullBackup() {
        val json = repository.getFullConfigJson()
        systemUtils.exportFile("AetherST_Backup.astf", json) { success ->
            if (success) {
                showToast(localizedToast { TOAST_BACKUP_EXPORTED }, false)
            } else {
                showToast(localizedToast { TOAST_BACKUP_EXPORT_FAILED }, true)
            }
        }
    }

    fun importFullBackup() {
        systemUtils.importFile { content ->
            if (content != null) {
                if (repository.restoreFullConfig(content)) {
                    showToast(localizedToast { TOAST_CONFIG_RESTORED }, false)
                    restartConnection()
                } else {
                    showToast(localizedToast { TOAST_INVALID_BACKUP }, true)
                }
            }
        }
    }

    fun exportRoutingRules() {
        try {
            val json = Json.encodeToString(config.value.routingRules)
            systemUtils.exportFile("AetherST_Rules.astf", json) { success ->
                if (success) {
                    showToast(localizedToast { TOAST_RULES_EXPORTED }, false)
                } else {
                    showToast(localizedToast { TOAST_RULES_EXPORT_FAILED }, true)
                }
            }
        } catch (e: Exception) {
            showToast(localizedToast { TOAST_EXPORT_ERROR.format(e.message ?: "") }, true)
        }
    }

    fun importRoutingRules() {
        systemUtils.importFile { content ->
            if (content != null) {
                try {
                    val rules = Json.decodeFromString<List<RoutingRule>>(content)
                    if (rules.isEmpty()) {
                        showToast(localizedToast { TOAST_NO_RULES_IN_FILE }, true)
                        return@importFile
                    }
                    _importConflictRules.value = rules
                } catch (_: Exception) {
                    showToast(localizedToast { TOAST_INVALID_RULES_FILE }, true)
                }
            }
        }
    }

    fun importInternalRoutingRules(assetName: String) {
        val content = systemUtils.readInternalAsset(assetName)
        if (content == null) {
            showToast(localizedToast { TOAST_FAILED_LOAD_INTERNAL }, true)
            return
        }

        try {
            val rules = if (assetName.endsWith(".astb")) {
                val lines = content.lines().filter { it.isNotBlank() }
                val parsed = mutableListOf<RoutingRule>()
                var i = 0
                while (i + 1 < lines.size) {
                    val modeStr = lines[i+1].trim().removePrefix("-").uppercase()
                    val mode = when (modeStr) {
                        "TUNNEL" -> RoutingMode.TUNNEL
                        "DIRECT" -> RoutingMode.DIRECT
                        "BLOCK" -> RoutingMode.BLOCK
                        else -> RoutingMode.TUNNEL
                    }
                    parsed.add(RoutingRule(lines[i].trim(), mode))
                    i += 2
                }
                parsed
            } else {
                Json.decodeFromString<List<RoutingRule>>(content)
            }

            if (rules.isEmpty()) {
                showToast(localizedToast { TOAST_NO_RULES_IN_ASSET }, true)
                return
            }
            _importConflictRules.value = rules
        } catch (e: Exception) {
            showToast(localizedToast { TOAST_FAILED_PARSE_INTERNAL }, true)
            LogRepository.e("Internal import error: ${e.message}")
        }
    }
}
