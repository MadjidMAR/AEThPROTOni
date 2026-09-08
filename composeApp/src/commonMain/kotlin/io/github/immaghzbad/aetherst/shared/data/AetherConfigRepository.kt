package io.github.immaghzbad.aetherst.shared.data

import io.github.immaghzbad.aetherst.shared.model.*
import io.github.immaghzbad.aetherst.platform.Settings
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.platform.isWindows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AetherConfigRepository private constructor(private val settings: Settings) {

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AetherConfig> = _config.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(value = settings.getBoolean("onboarding_complete", false))
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: AetherConfigRepository? = null

        fun getInstance(settings: Settings): AetherConfigRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AetherConfigRepository(settings).also { INSTANCE = it }
            }
        }
    }

    init {
        LogRepository.currentAppLogLevel = _config.value.appLogLevel
        LogRepository.currentCoreLogLevel = _config.value.coreLogLevel
        PsiphonEgressRegistry.setAvailableRegions(loadCachedEgressRegions())
    }

    private fun loadCachedEgressRegions(): List<String> {
        val defaultRegions = listOf("AT", "AU", "BE", "BR", "CA", "CH", "CZ", "DE", "DK", "ES", "FI", "FR", "GB", "ID", "IE", "IN", "IT", "JP", "NL", "NO", "PL", "RO", "RS", "SE", "SG", "US")
        val cached = settings.getString("psiphon_egress_regions_cache", "")
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.matches(Regex("^[A-Z]{2}$")) }
            .distinct()
        return if (cached.isEmpty()) defaultRegions else (defaultRegions + cached).distinct().sorted()
    }

    fun cacheEgressRegions(regions: List<String>) {
        val defaultRegions = listOf("AT", "AU", "BE", "BR", "CA", "CH", "CZ", "DE", "DK", "ES", "FI", "FR", "GB", "ID", "IE", "IN", "IT", "JP", "NL", "NO", "PL", "RO", "RS", "SE", "SG", "US")
        val existing = settings.getString("psiphon_egress_regions_cache", "").split(",").map { it.trim().uppercase() }.filter { it.matches(Regex("^[A-Z]{2}$")) }
        val normalized = (defaultRegions + existing + regions.map { it.trim().uppercase() }.filter { it.matches(Regex("^[A-Z]{2}$")) }).distinct().sorted()
        settings.putString("psiphon_egress_regions_cache", normalized.joinToString(","))
        PsiphonEgressRegistry.setAvailableRegions(normalized)
    }

    private fun loadConfig(): AetherConfig {
        migrateCoreLoggingDefault()
        return readFromSettings("")
    }

    private fun migrateCoreLoggingDefault() {
        if (settings.getBoolean("core_logging_default_v2", false)) return
        val current = settings.getString("core_log_level", "")
        val manual = settings.getString("manual_core_log_level", "")
        
        if (current.isEmpty() || (current == AetherLogLevel.OFF.name)) {
            settings.putString("core_log_level", AetherLogLevel.INFO.name)
        }
        if (manual.isEmpty() || (manual == AetherLogLevel.OFF.name)) {
            settings.putString("manual_core_log_level", AetherLogLevel.INFO.name)
        }
        settings.putBoolean("core_logging_default_v2", true)
    }

    private fun loadManualConfig(): AetherConfig {
        return readFromSettings("manual_")
    }

    private fun readFromSettings(prefix: String): AetherConfig {
        val protocolStr = settings.getString("${prefix}protocol", AetherProtocol.MASQUE.name)
        val noiseStr = settings.getString("${prefix}noise", AetherNoise.FIREWALL.name)
        val scanModeStr = settings.getString("${prefix}scan_mode", AetherScanMode.BALANCED.name)
        val ipModeStr = settings.getString("${prefix}ip_mode", AetherIpMode.AUTO.name)
        val appLogLevelStr = settings.getString("${prefix}app_log_level", AetherLogLevel.INFO.name)
        val coreLogLevelStr = settings.getString("${prefix}core_log_level", AetherLogLevel.INFO.name)
        val perfProfileStr = settings.getString("${prefix}perf_profile", AetherPerfProfile.AUTO.name)
        val connectionModeStr = settings.getString("${prefix}connection_mode", "")
        val legacyProxyOnly = settings.getBoolean("${prefix}proxy_only", false)
        
        val connectionMode = if (connectionModeStr.isNotEmpty()) {
            runCatching { ConnectionMode.valueOf(connectionModeStr) }.getOrDefault(ConnectionMode.TUNNEL)
        } else {
            if (legacyProxyOnly) ConnectionMode.PROXY_ONLY else if (isWindows) ConnectionMode.SYSTEM_PROXY else ConnectionMode.TUNNEL
        }

        val protocol = runCatching { AetherProtocol.valueOf(protocolStr) }.getOrDefault(AetherProtocol.MASQUE)
        val finalConnectionMode = connectionMode

        val presetId = settings.getString("${prefix}preset_id", "custom")
        val socksHost = settings.getString("${prefix}socks_host", "127.0.0.1")
        val cleanHost = if (socksHost == "198.18.0.1") "127.0.0.1" else socksHost
        
        return AetherConfig(
            presetId = presetId,
            protocol = protocol,
            noise = runCatching { AetherNoise.valueOf(noiseStr) }.getOrDefault(AetherNoise.FIREWALL),
            scanMode = runCatching { AetherScanMode.valueOf(scanModeStr) }.getOrDefault(AetherScanMode.BALANCED),
            ipMode = runCatching { AetherIpMode.valueOf(ipModeStr) }.getOrDefault(AetherIpMode.AUTO),
            echEnabled = settings.getBoolean("${prefix}ech_enabled", false),
            httpProxyEnabled = settings.getBoolean("${prefix}http_proxy_enabled", false),
            perfProfile = runCatching { AetherPerfProfile.valueOf(perfProfileStr) }.getOrDefault(AetherPerfProfile.AUTO),
            h2Mode = settings.getBoolean("${prefix}h2_mode", true),
            h2Fragment = settings.getBoolean("${prefix}h2_fragment", false),
            fragmentSize = settings.getString("${prefix}fragment_size", "16-32"),
            fragmentDelay = settings.getString("${prefix}fragment_delay", "2-10"),
            noDataCheck = settings.getBoolean("${prefix}no_data_check", false),
            quickReconnect = settings.getBoolean("${prefix}quick_reconnect", true),
            socksHost = cleanHost,
            socksPort = settings.getString("${prefix}socks_port", "1819"),
            httpPort = settings.getString("${prefix}http_port", "1820"),
            appLogLevel = runCatching { AetherLogLevel.valueOf(appLogLevelStr) }.getOrDefault(AetherLogLevel.INFO),
            coreLogLevel = runCatching { AetherLogLevel.valueOf(coreLogLevelStr) }.getOrDefault(AetherLogLevel.INFO),
            peer = settings.getString("${prefix}peer", ""),
            wgPeer = settings.getString("${prefix}wg_peer", ""),
            wiwOuter = settings.getString("${prefix}wiw_outer", ""),
            wiwInner = settings.getString("${prefix}wiw_inner", ""),
            wiwScan = settings.getBoolean("${prefix}wiw_scan", true),
            masqueMtu = settings.getInt("${prefix}masque_mtu", 0),
            netstackTcpRx = settings.getInt("${prefix}netstack_tcp_rx", 0),
            netstackTcpTx = settings.getInt("${prefix}netstack_tcp_tx", 0),
            keepaliveEnabled = settings.getBoolean("${prefix}keepalive_enabled", true),
            keepalive = settings.getInt("${prefix}keepalive", 5),
            validateSecs = settings.getInt("${prefix}validate_secs", 10),
            reconnectSecs = settings.getInt("${prefix}reconnect_secs", 2),
            wgEndpointCooldownSecs = settings.getInt("${prefix}wg_endpoint_cooldown_secs", 300),
            noProfileRetry = settings.getBoolean("${prefix}no_profile_retry", false),
            tlsGroups = settings.getString("${prefix}tls_groups", ""),
            mtu = settings.getInt("${prefix}mtu", 1100),
            connectionMode = finalConnectionMode,
            routingRules = settings.getString("${prefix}routing_rules", "").let {
                if (it.isEmpty()) emptyList() else runCatching { Json.decodeFromString<List<RoutingRule>>(it) }.getOrDefault(emptyList())
            },
            teamName = settings.getString("${prefix}team_name", ""),
            accessEmail = settings.getString("${prefix}access_email", ""),
            accessId = settings.getString("${prefix}access_id", ""),
            accessSecret = settings.getString("${prefix}access_secret", ""),
            accessToken = settings.getString("${prefix}access_token", ""),
            useGateway = settings.getBoolean("${prefix}use_gateway", false),
            smartReconnect = settings.getBoolean("${prefix}smart_reconnect", true),
            reconnectRetryLimit = settings.getInt("${prefix}reconnect_retry_limit", 10),
            dnsEnabled = settings.getBoolean("${prefix}dns_enabled", false),
            dnsList = settings.getString("${prefix}dns_list", "1.1.1.1,2606:4700:4700::1111"),
            shareHotspot = settings.getBoolean("${prefix}share_hotspot", false),
            upstreamProxy = settings.getString("${prefix}upstream_proxy", ""),
            upstreamProxyEnabled = settings.getBoolean("${prefix}upstream_proxy_enabled", false),
            routeSniffing = settings.getBoolean("${prefix}route_sniffing", true),
            sniffingTimeoutMs = settings.getInt("${prefix}sniffing_timeout_ms", 100),
            reprovision = settings.getBoolean("${prefix}reprovision", true),
            hevLogLevel = sanitizeHevLogLevel(settings.getString("${prefix}hev_log_level", "warn")),
            hevConnectTimeoutMs = settings.getInt("${prefix}hev_connect_timeout_ms", 5000),
            hevReadWriteTimeoutMs = settings.getInt("${prefix}hev_read_write_timeout_ms", 60000),
            hevMaxSessionCount = settings.getInt("${prefix}hev_max_session_count", 0),
            hevMapdnsCacheSize = settings.getInt("${prefix}hev_mapdns_cache_size", 10000),
            hevUdpMode = sanitizeHevUdpMode(settings.getString("${prefix}hev_udp_mode", "udp")),
            cloakEnabled = settings.getBoolean("${prefix}cloak_enabled", false),
            cloakSniList = settings.getString("${prefix}cloak_sni_list", "www.hcaptcha.com,www.speedtest.net,www.bing.com"),
            cloakTtlList = settings.getString("${prefix}cloak_ttl_list", "4,5,6,8"),
            cloakJitterMin = settings.getInt("${prefix}cloak_jitter_min", 20),
            cloakJitterMax = settings.getInt("${prefix}cloak_jitter_max", 80),
            cloakFragment = settings.getBoolean("${prefix}cloak_fragment", false),
            cloakAdaptive = settings.getBoolean("${prefix}cloak_adaptive", true),
            cloakFallbackPorts = settings.getString("${prefix}cloak_fallback_ports", "443,2053,2083,2087,2096,8443"),
            cloakLogLevel = sanitizeHevLogLevel(settings.getString("${prefix}cloak_log_level", "info")),
            cloakRandomizeSniCase = settings.getBoolean("${prefix}cloak_randomize_sni_case", false),
            psiphonEnabled = settings.getBoolean("${prefix}psiphon_enabled", false),
            psiphonChainOuter = settings.getString("${prefix}psiphon_chain_outer", "masque"),
            psiphonSocksPort = settings.getString("${prefix}psiphon_socks_port", "3080"),
            psiphonEgressRegion = sanitizePsiphonEgressRegion(settings.getString("${prefix}psiphon_egress_region", "")),
            psiphonChainMode = sanitizePsiphonChainMode(settings.getString("${prefix}psiphon_chain_mode", "AUTO")),
            psiphonMasqueOrder = settings.getString("${prefix}psiphon_masque_order", "auto"),
            psiphonViaAether = settings.getBoolean("${prefix}psiphon_via_aether", true),
            pingUrl = sanitizePingUrl(settings.getString("${prefix}ping_url", "https://www.gstatic.com/generate_204")),
            ztStaySignedIn = settings.getBoolean("${prefix}zt_stay_signed_in", true),
            ztTokenExpiry = settings.getString("${prefix}zt_token_expiry", "0").toLongOrNull() ?: 0,
            connectButtonStyle = sanitizeConnectButtonStyle(settings.getString("${prefix}connect_button_style", "swipe")),
            appLanguage = sanitizeAppLanguage(settings.getString("${prefix}app_language", "auto")),
            tunnelAllApps = settings.getBoolean("${prefix}tunnel_all_apps", true),
            excludedPackages = settings.getStringSet("${prefix}excluded_packages", emptySet()),
            blockedPackages = settings.getStringSet("${prefix}blocked_packages", emptySet()),
            tunneledPackages = settings.getStringSet("${prefix}tunneled_packages", emptySet()),
        ).let { enforceWireGuardConstraints(it) }
    }

    private fun sanitizeHevLogLevel(value: String): String {
        return if (value in setOf("error", "warn", "info", "debug")) value else "warn"
    }

    private fun sanitizeHevUdpMode(value: String): String {
        val v = value.lowercase().trim()
        return if (v in setOf("udp", "icmp", "off", "false")) v else "udp"
    }

    private fun enforceWireGuardConstraints(cfg: AetherConfig): AetherConfig {
        var out = cfg
        if (out.psiphonEnabled && !out.httpProxyEnabled) {
            out = out.copy(httpProxyEnabled = true)
        }
        if ((out.psiphonChainOuter == "wg" || out.psiphonChainOuter == "gool") && out.psiphonChainMode == PsiphonChainMode.FALLBACK) {
            out = out.copy(psiphonChainMode = PsiphonChainMode.AUTO)
        }
        return out
    }

    private fun sanitizePingUrl(value: String): String {
        val v = value.trim()
        if (v.isEmpty()) return "https://www.gstatic.com/generate_204"
        val lower = v.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return "https://www.gstatic.com/generate_204"
        val withoutScheme = v.substringAfter("://")
        val hostPart = withoutScheme.substringBefore("/").substringBefore(":")
        if (hostPart.isBlank() || !hostPart.contains(".")) return "https://www.gstatic.com/generate_204"
        return v
    }

    private fun sanitizePsiphonEgressRegion(value: String): String {
        val v = value.trim().uppercase()
        return if (v.isEmpty() || v.matches(Regex("^[A-Z]{2}$"))) v else ""
    }

    private fun sanitizeConnectButtonStyle(value: String): String {
        val v = value.trim().lowercase()
        return if (v == "capsule" || v == "swipe") v else "swipe"
    }

    private fun sanitizeAppLanguage(value: String): String {
        val v = value.trim().lowercase()
        return if (v == "auto" || v == "fa" || v == "en") v else "auto"
    }

    private fun sanitizePsiphonChainMode(value: String): PsiphonChainMode {
        return try { PsiphonChainMode.valueOf(value.uppercase()) } catch (_: Exception) { PsiphonChainMode.AUTO }
    }

    fun updateConfig(newConfig: AetherConfig) {
        val oldConfig = _config.value
        val sanitized = newConfig
        var manualConfig = enforceWireGuardConstraints(sanitized.copy(presetId = "custom"))
        
        val finalConfig = if (oldConfig.protocol != manualConfig.protocol) {
            saveProtocolSettings(oldConfig)
            loadProtocolSettings(manualConfig.protocol, manualConfig)
        } else {
            saveProtocolSettings(manualConfig)
            manualConfig
        }

        saveToSettings("", finalConfig)
        saveToSettings("manual_", finalConfig)
        LogRepository.currentAppLogLevel = finalConfig.appLogLevel
        LogRepository.currentCoreLogLevel = finalConfig.coreLogLevel
        _config.value = finalConfig
    }

    fun applyDetectedConfig(newConfig: AetherConfig) {
        val oldConfig = _config.value
        var manualConfig = enforceWireGuardConstraints(newConfig.copy(presetId = "custom"))

        if (oldConfig.protocol != manualConfig.protocol) {
            saveProtocolSettings(oldConfig)
        }
        saveProtocolSettings(manualConfig)

        saveToSettings("", manualConfig)
        saveToSettings("manual_", manualConfig)
        LogRepository.currentAppLogLevel = manualConfig.appLogLevel
        LogRepository.currentCoreLogLevel = manualConfig.coreLogLevel
        _config.value = manualConfig
    }

    fun setOnboardingComplete(complete: Boolean) {
        settings.putBoolean("onboarding_complete", complete)
        _isOnboardingComplete.value = complete
    }

    fun getOnboardingStep(): OnboardingStep {
        val name = settings.getString("onboarding_step_name", OnboardingStep.WELCOME.name)
        return try { OnboardingStep.valueOf(name) } catch (_: Exception) { OnboardingStep.WELCOME }
    }

    fun setOnboardingStep(step: OnboardingStep) {
        settings.putString("onboarding_step_name", step.name)
    }

    private fun saveToSettings(prefix: String, cfg: AetherConfig) {
        settings.putString("${prefix}preset_id", cfg.presetId)
        settings.putString("${prefix}protocol", cfg.protocol.name)
        settings.putString("${prefix}noise", cfg.noise.name)
        settings.putString("${prefix}scan_mode", cfg.scanMode.name)
        settings.putString("${prefix}ip_mode", cfg.ipMode.name)
        settings.putBoolean("${prefix}ech_enabled", cfg.echEnabled)
        settings.putBoolean("${prefix}http_proxy_enabled", cfg.httpProxyEnabled)
        settings.putString("${prefix}perf_profile", cfg.perfProfile.name)
        settings.putBoolean("${prefix}h2_mode", cfg.h2Mode)
        settings.putBoolean("${prefix}h2_fragment", cfg.h2Fragment)
        settings.putString("${prefix}fragment_size", cfg.fragmentSize)
        settings.putString("${prefix}fragment_delay", cfg.fragmentDelay)
        settings.putBoolean("${prefix}no_data_check", cfg.noDataCheck)
        settings.putBoolean("${prefix}quick_reconnect", cfg.quickReconnect)
        settings.putString("${prefix}socks_host", cfg.socksHost)
        settings.putString("${prefix}socks_port", cfg.socksPort)
        settings.putString("${prefix}http_port", cfg.httpPort)
        settings.putString("${prefix}app_log_level", cfg.appLogLevel.name)
        settings.putString("${prefix}core_log_level", cfg.coreLogLevel.name)
        settings.putString("${prefix}peer", cfg.peer)
        settings.putString("${prefix}wg_peer", cfg.wgPeer)
        settings.putString("${prefix}wiw_outer", cfg.wiwOuter)
        settings.putString("${prefix}wiw_inner", cfg.wiwInner)
        settings.putBoolean("${prefix}wiw_scan", cfg.wiwScan)
        settings.putInt("${prefix}masque_mtu", cfg.masqueMtu.coerceIn(0, 9000))
        settings.putInt("${prefix}netstack_tcp_rx", cfg.netstackTcpRx.coerceIn(0, 67108864))
        settings.putInt("${prefix}netstack_tcp_tx", cfg.netstackTcpTx.coerceIn(0, 67108864))
        settings.putBoolean("${prefix}keepalive_enabled", cfg.keepaliveEnabled)
        settings.putInt("${prefix}keepalive", cfg.keepalive)
        settings.putInt("${prefix}validate_secs", cfg.validateSecs)
        settings.putInt("${prefix}reconnect_secs", cfg.reconnectSecs)
        settings.putInt("${prefix}wg_endpoint_cooldown_secs", cfg.wgEndpointCooldownSecs)
        settings.putBoolean("${prefix}no_profile_retry", cfg.noProfileRetry)
        settings.putString("${prefix}tls_groups", cfg.tlsGroups)
        settings.putInt("${prefix}mtu", cfg.mtu)
        settings.putString("${prefix}connection_mode", cfg.connectionMode.name)
        settings.putString("${prefix}routing_rules", Json.encodeToString(cfg.routingRules))
        settings.putString("${prefix}team_name", cfg.teamName)
        settings.putString("${prefix}access_email", cfg.accessEmail)
        settings.putString("${prefix}access_id", cfg.accessId)
        settings.putString("${prefix}access_secret", cfg.accessSecret)
        settings.putString("${prefix}access_token", cfg.accessToken)
        settings.putBoolean("${prefix}use_gateway", cfg.useGateway)
        settings.putBoolean("${prefix}smart_reconnect", cfg.smartReconnect)
        settings.putInt("${prefix}reconnect_retry_limit", cfg.reconnectRetryLimit)
        settings.putBoolean("${prefix}dns_enabled", cfg.dnsEnabled)
        settings.putString("${prefix}dns_list", cfg.dnsList)
        settings.putBoolean("${prefix}share_hotspot", cfg.shareHotspot)
        settings.putString("${prefix}upstream_proxy", cfg.upstreamProxy)
        settings.putBoolean("${prefix}upstream_proxy_enabled", cfg.upstreamProxyEnabled)
        settings.putBoolean("${prefix}route_sniffing", cfg.routeSniffing)
        settings.putInt("${prefix}sniffing_timeout_ms", cfg.sniffingTimeoutMs)
        settings.putBoolean("${prefix}reprovision", cfg.reprovision)
        settings.putString("${prefix}hev_log_level", sanitizeHevLogLevel(cfg.hevLogLevel))
        settings.putInt("${prefix}hev_connect_timeout_ms", cfg.hevConnectTimeoutMs.coerceIn(500, 120000))
        settings.putInt("${prefix}hev_read_write_timeout_ms", cfg.hevReadWriteTimeoutMs.coerceIn(1000, 600000))
        settings.putInt("${prefix}hev_max_session_count", cfg.hevMaxSessionCount.coerceIn(0, 200000))
        settings.putInt("${prefix}hev_mapdns_cache_size", cfg.hevMapdnsCacheSize.coerceIn(100, 1000000))
        settings.putString("${prefix}hev_udp_mode", sanitizeHevUdpMode(cfg.hevUdpMode))
        settings.putBoolean("${prefix}cloak_enabled", cfg.cloakEnabled)
        settings.putString("${prefix}cloak_sni_list", cfg.cloakSniList)
        settings.putString("${prefix}cloak_ttl_list", cfg.cloakTtlList)
        settings.putInt("${prefix}cloak_jitter_min", cfg.cloakJitterMin.coerceIn(5, 500))
        settings.putInt("${prefix}cloak_jitter_max", cfg.cloakJitterMax.coerceIn(10, 1000))
        settings.putBoolean("${prefix}cloak_fragment", cfg.cloakFragment)
        settings.putBoolean("${prefix}cloak_adaptive", cfg.cloakAdaptive)
        settings.putString("${prefix}cloak_fallback_ports", cfg.cloakFallbackPorts)
        settings.putString("${prefix}cloak_log_level", sanitizeHevLogLevel(cfg.cloakLogLevel))
        settings.putBoolean("${prefix}cloak_randomize_sni_case", cfg.cloakRandomizeSniCase)
        settings.putBoolean("${prefix}psiphon_enabled", cfg.psiphonEnabled)
        settings.putString("${prefix}psiphon_chain_outer", cfg.psiphonChainOuter)
        settings.putString("${prefix}psiphon_socks_port", cfg.psiphonSocksPort)
        settings.putString("${prefix}psiphon_egress_region", sanitizePsiphonEgressRegion(cfg.psiphonEgressRegion))
        settings.putString("${prefix}psiphon_chain_mode", cfg.psiphonChainMode.name)
        settings.putString("${prefix}psiphon_masque_order", cfg.psiphonMasqueOrder)
        settings.putBoolean("${prefix}psiphon_via_aether", cfg.psiphonViaAether)
        settings.putString("${prefix}ping_url", sanitizePingUrl(cfg.pingUrl))
        settings.putBoolean("${prefix}zt_stay_signed_in", cfg.ztStaySignedIn)
        settings.putString("${prefix}zt_token_expiry", cfg.ztTokenExpiry.toString())
        settings.putString("${prefix}connect_button_style", sanitizeConnectButtonStyle(cfg.connectButtonStyle))
        settings.putString("${prefix}app_language", sanitizeAppLanguage(cfg.appLanguage))
        settings.putBoolean("${prefix}tunnel_all_apps", cfg.tunnelAllApps)
        settings.putStringSet("${prefix}excluded_packages", cfg.excludedPackages)
        settings.putStringSet("${prefix}blocked_packages", cfg.blockedPackages)
        settings.putStringSet("${prefix}tunneled_packages", cfg.tunneledPackages)
    }

    fun resetToDefaults() {
        val defaultConfig = AetherConfig()
        updateConfig(defaultConfig)
        LogRepository.i("System reset: All settings restored to factory defaults")
    }

    fun getFullConfigJson(): String {
        val sanitized = _config.value.copy(
            excludedPackages = emptySet(),
            blockedPackages = emptySet(),
            tunneledPackages = emptySet(),
            routingRules = emptyList()
        )
        return Json.encodeToString(sanitized)
    }

    fun restoreFullConfig(json: String): Boolean {
        return try {
            val restored = Json.decodeFromString<AetherConfig>(json).copy(
                excludedPackages = emptySet(),
                blockedPackages = emptySet(),
                tunneledPackages = emptySet(),
                routingRules = emptyList()
            )
            updateConfig(restored)
            LogRepository.i("Full configuration restored from backup")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyPreset(presetId: String) {
        val current = _config.value
        if (presetId == "custom") {
            val manualRaw = loadManualConfig()
            if (current.protocol != manualRaw.protocol) {
                saveProtocolSettings(current)
            }
            val manual = enforceWireGuardConstraints(manualRaw)
            saveToSettings("", manual)
            saveProtocolSettings(manual)
            LogRepository.i("Configuration profile applied: custom")
            LogRepository.currentAppLogLevel = manual.appLogLevel
            LogRepository.currentCoreLogLevel = manual.coreLogLevel
            _config.value = manual
            return
        }
        val targetProtocol = when (presetId) {
            "turbo", "thorough" -> AetherProtocol.MASQUE
            "stealth" -> AetherProtocol.GOOL
            "ironclad" -> AetherProtocol.WG
            else -> return
        }
        if (current.protocol != targetProtocol) {
            saveProtocolSettings(current)
        }
        val base = loadProtocolSettings(targetProtocol, current)
        var updated = when (presetId) {
            "turbo" -> base.copy(
                presetId = "turbo",
                protocol = AetherProtocol.MASQUE,
                noise = AetherNoise.GFW,
                scanMode = AetherScanMode.TURBO,
                echEnabled = false,
                httpProxyEnabled = false,
                h2Mode = true,
                h2Fragment = false,
                noDataCheck = false,
                tlsGroups = "",
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                mtu = 1320,
                connectionMode = ConnectionMode.TUNNEL
            )
            "thorough" -> base.copy(
                presetId = "thorough",
                protocol = AetherProtocol.MASQUE,
                noise = AetherNoise.GFW,
                scanMode = AetherScanMode.TURBO,
                echEnabled = false,
                httpProxyEnabled = false,
                h2Mode = true,
                h2Fragment = false,
                noDataCheck = false,
                tlsGroups = "",
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                mtu = 1320,
                connectionMode = ConnectionMode.TUNNEL
            )
            "stealth" -> base.copy(
                presetId = "stealth",
                protocol = AetherProtocol.GOOL,
                noise = AetherNoise.AGGRESSIVE,
                scanMode = AetherScanMode.STEALTH,
                echEnabled = false,
                httpProxyEnabled = true,
                h2Mode = true,
                h2Fragment = false,
                noDataCheck = false,
                tlsGroups = "",
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                mtu = 1330,
                connectionMode = ConnectionMode.TUNNEL
            )
            "ironclad" -> base.copy(
                presetId = "ironclad",
                protocol = AetherProtocol.WG,
                noise = AetherNoise.AGGRESSIVE,
                scanMode = AetherScanMode.STEALTH,
                echEnabled = false,
                httpProxyEnabled = true,
                h2Mode = true,
                h2Fragment = false,
                noDataCheck = false,
                tlsGroups = "",
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                mtu = 1330,
                connectionMode = ConnectionMode.TUNNEL
            )
            else -> current
        }
        updated = enforceWireGuardConstraints(updated)
        LogRepository.i("Configuration profile applied: $presetId")
        saveToSettings("", updated)
        saveProtocolSettings(updated)
        saveToSettings("manual_", updated)
        LogRepository.currentAppLogLevel = updated.appLogLevel
        LogRepository.currentCoreLogLevel = updated.coreLogLevel
        _config.value = updated
    }

    private fun saveProtocolSettings(cfg: AetherConfig) {
        val p = "protocol_${cfg.protocol.name}_"
        settings.putString("${p}noise", cfg.noise.name)
        settings.putString("${p}scan_mode", cfg.scanMode.name)
        settings.putString("${p}ip_mode", cfg.ipMode.name)
        settings.putBoolean("${p}ech_enabled", cfg.echEnabled)
        settings.putBoolean("${p}http_proxy_enabled", cfg.httpProxyEnabled)
        settings.putBoolean("${p}h2_mode", cfg.h2Mode)
        settings.putBoolean("${p}h2_fragment", cfg.h2Fragment)
        settings.putString("${p}fragment_size", cfg.fragmentSize)
        settings.putString("${p}fragment_delay", cfg.fragmentDelay)
        settings.putBoolean("${p}no_data_check", cfg.noDataCheck)
        settings.putBoolean("${p}quick_reconnect", cfg.quickReconnect)
        settings.putString("${p}peer", cfg.peer)
        settings.putString("${p}wg_peer", cfg.wgPeer)
        settings.putString("${p}wiw_outer", cfg.wiwOuter)
        settings.putString("${p}wiw_inner", cfg.wiwInner)
        settings.putBoolean("${p}wiw_scan", cfg.wiwScan)
        settings.putInt("${p}masque_mtu", cfg.masqueMtu.coerceIn(0, 9000))
        settings.putInt("${p}netstack_tcp_rx", cfg.netstackTcpRx.coerceIn(0, 67108864))
        settings.putInt("${p}netstack_tcp_tx", cfg.netstackTcpTx.coerceIn(0, 67108864))
        settings.putBoolean("${p}keepalive_enabled", cfg.keepaliveEnabled)
        settings.putInt("${p}keepalive", cfg.keepalive)
        settings.putInt("${p}validate_secs", cfg.validateSecs)
        settings.putInt("${p}reconnect_secs", cfg.reconnectSecs)
        settings.putInt("${p}wg_endpoint_cooldown_secs", cfg.wgEndpointCooldownSecs)
        settings.putBoolean("${p}no_profile_retry", cfg.noProfileRetry)
        settings.putString("${p}tls_groups", cfg.tlsGroups)
        settings.putInt("${p}mtu", cfg.mtu)
        settings.putString("${p}team_name", cfg.teamName)
        settings.putString("${p}access_email", cfg.accessEmail)
        settings.putString("${p}access_id", cfg.accessId)
        settings.putString("${p}access_secret", cfg.accessSecret)
        settings.putString("${p}access_token", cfg.accessToken)
        settings.putBoolean("${p}zt_stay_signed_in", cfg.ztStaySignedIn)
        settings.putString("${p}zt_token_expiry", cfg.ztTokenExpiry.toString())
        settings.putBoolean("${p}use_gateway", cfg.useGateway)
        settings.putString("${p}upstream_proxy", cfg.upstreamProxy)
        settings.putBoolean("${p}upstream_proxy_enabled", cfg.upstreamProxyEnabled)
        settings.putBoolean("${p}route_sniffing", cfg.routeSniffing)
        settings.putInt("${p}sniffing_timeout_ms", cfg.sniffingTimeoutMs)
        settings.putBoolean("${p}reprovision", cfg.reprovision)
        settings.putBoolean("${p}initialized", true)
    }

    private fun protocolDefaults(base: AetherConfig, protocol: AetherProtocol): AetherConfig {
        return when (protocol) {
            AetherProtocol.MASQUE -> base.copy(
                protocol = protocol,
                noise = AetherNoise.GFW,
                scanMode = AetherScanMode.TURBO,
                ipMode = AetherIpMode.AUTO,
                echEnabled = false,
                httpProxyEnabled = false,
                h2Mode = true,
                h2Fragment = false,
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                noDataCheck = false,
                quickReconnect = true,
                peer = "",
                wgPeer = "",
                wiwOuter = "",
                wiwInner = "",
                wiwScan = true,
                masqueMtu = 0,
                netstackTcpRx = 0,
                netstackTcpTx = 0,
                keepaliveEnabled = true,
                keepalive = 5,
                validateSecs = 10,
                reconnectSecs = 2,
                wgEndpointCooldownSecs = 300,
                noProfileRetry = false,
                tlsGroups = "",
                mtu = 1320
            )
            AetherProtocol.WG -> base.copy(
                protocol = protocol,
                noise = AetherNoise.AGGRESSIVE,
                scanMode = AetherScanMode.STEALTH,
                ipMode = AetherIpMode.AUTO,
                echEnabled = false,
                httpProxyEnabled = true,
                h2Mode = true,
                h2Fragment = false,
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                noDataCheck = false,
                quickReconnect = true,
                peer = "",
                wgPeer = "",
                wiwOuter = "",
                wiwInner = "",
                wiwScan = true,
                masqueMtu = 0,
                netstackTcpRx = 0,
                netstackTcpTx = 0,
                keepaliveEnabled = true,
                keepalive = 5,
                validateSecs = 10,
                reconnectSecs = 2,
                wgEndpointCooldownSecs = 300,
                noProfileRetry = false,
                tlsGroups = "",
                mtu = 1330
            )
            AetherProtocol.GOOL -> base.copy(
                protocol = protocol,
                noise = AetherNoise.AGGRESSIVE,
                scanMode = AetherScanMode.STEALTH,
                ipMode = AetherIpMode.AUTO,
                echEnabled = false,
                httpProxyEnabled = true,
                h2Mode = true,
                h2Fragment = false,
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                noDataCheck = false,
                quickReconnect = true,
                peer = "",
                wgPeer = "",
                wiwOuter = "",
                wiwInner = "",
                wiwScan = true,
                masqueMtu = 0,
                netstackTcpRx = 0,
                netstackTcpTx = 0,
                keepaliveEnabled = true,
                keepalive = 5,
                validateSecs = 10,
                reconnectSecs = 2,
                wgEndpointCooldownSecs = 300,
                noProfileRetry = false,
                tlsGroups = "",
                mtu = 1330
            )
            AetherProtocol.ZERO_TRUST -> base.copy(
                protocol = protocol,
                noise = AetherNoise.OFF,
                scanMode = AetherScanMode.BALANCED,
                ipMode = AetherIpMode.AUTO,
                echEnabled = false,
                httpProxyEnabled = false,
                h2Mode = true,
                h2Fragment = false,
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                noDataCheck = false,
                quickReconnect = true,
                peer = "",
                wgPeer = "",
                wiwOuter = "",
                wiwInner = "",
                wiwScan = true,
                masqueMtu = 0,
                netstackTcpRx = 0,
                netstackTcpTx = 0,
                keepaliveEnabled = true,
                keepalive = 5,
                validateSecs = 10,
                reconnectSecs = 2,
                wgEndpointCooldownSecs = 300,
                noProfileRetry = false,
                tlsGroups = "",
                mtu = 1420
            )
        }
    }

    private fun loadProtocolSettings(protocol: AetherProtocol, base: AetherConfig): AetherConfig {
        val p = "protocol_${protocol.name}_"
        if (!settings.getBoolean("${p}initialized", false)) {
            return protocolDefaults(base, protocol)
        }
        return base.copy(
            protocol = protocol,
            noise = runCatching { AetherNoise.valueOf(settings.getString("${p}noise", "")) }.getOrDefault(base.noise),
            scanMode = runCatching { AetherScanMode.valueOf(settings.getString("${p}scan_mode", "")) }.getOrDefault(base.scanMode),
            ipMode = runCatching { AetherIpMode.valueOf(settings.getString("${p}ip_mode", "")) }.getOrDefault(base.ipMode),
            echEnabled = settings.getBoolean("${p}ech_enabled", false),
            httpProxyEnabled = settings.getBoolean("${p}http_proxy_enabled", base.httpProxyEnabled),
            h2Mode = settings.getBoolean("${p}h2_mode", true),
            h2Fragment = settings.getBoolean("${p}h2_fragment", false),
            fragmentSize = settings.getString("${p}fragment_size", "16-32"),
            fragmentDelay = settings.getString("${p}fragment_delay", "2-10"),
            noDataCheck = settings.getBoolean("${p}no_data_check", false),
            quickReconnect = settings.getBoolean("${p}quick_reconnect", true),
            peer = settings.getString("${p}peer", ""),
            wgPeer = settings.getString("${p}wg_peer", ""),
            wiwOuter = settings.getString("${p}wiw_outer", ""),
            wiwInner = settings.getString("${p}wiw_inner", ""),
            wiwScan = settings.getBoolean("${p}wiw_scan", true),
            masqueMtu = settings.getInt("${p}masque_mtu", 0),
            netstackTcpRx = settings.getInt("${p}netstack_tcp_rx", 0),
            netstackTcpTx = settings.getInt("${p}netstack_tcp_tx", 0),
            keepaliveEnabled = settings.getBoolean("${p}keepalive_enabled", true),
            keepalive = settings.getInt("${p}keepalive", 5),
            validateSecs = settings.getInt("${p}validate_secs", 10),
            reconnectSecs = settings.getInt("${p}reconnect_secs", 2),
            wgEndpointCooldownSecs = settings.getInt("${p}wg_endpoint_cooldown_secs", 300),
            noProfileRetry = settings.getBoolean("${p}no_profile_retry", false),
            tlsGroups = settings.getString("${p}tls_groups", ""),
            mtu = settings.getInt("${p}mtu", 1100),
            teamName = settings.getString("${p}team_name", ""),
            accessEmail = settings.getString("${p}access_email", ""),
            accessId = settings.getString("${p}access_id", ""),
            accessSecret = settings.getString("${p}access_secret", ""),
            accessToken = settings.getString("${p}access_token", ""),
            ztStaySignedIn = settings.getBoolean("${p}zt_stay_signed_in", true),
            ztTokenExpiry = settings.getString("${p}zt_token_expiry", "0").toLongOrNull() ?: 0,
            useGateway = settings.getBoolean("${p}use_gateway", false),
            upstreamProxy = settings.getString("${p}upstream_proxy", ""),
            upstreamProxyEnabled = settings.getBoolean("${p}upstream_proxy_enabled", false),
            routeSniffing = settings.getBoolean("${p}route_sniffing", true),
            sniffingTimeoutMs = settings.getInt("${p}sniffing_timeout_ms", 100),
            reprovision = settings.getBoolean("${p}reprovision", true)
        )
    }
}
