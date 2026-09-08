package io.github.immaghzbad.aetherst.shared.model

import io.github.immaghzbad.aetherst.platform.isWindows
import io.github.immaghzbad.aetherst.shared.i18n.AppStrings
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class AetherProtocol(val rawValue: String, val displayName: String, val description: String) {
    MASQUE("masque", "MASQUE", "HTTP/2/3 Tunneling (MASQUE)"),
    WG("wg", "WireGuard", "Lean speed WireGuard tunnel"),
    GOOL("gool", "Gool (WG-in-WG)", "Double encryption WireGuard-in-WireGuard"),
    ZERO_TRUST("zt", "Zero Trust", "Cloudflare for Organizations")
}

@Serializable
enum class AetherNoise(val rawValue: String, val displayName: String) {
    FIREWALL("firewall", "Firewall (Strict Censorship)"),
    GFW("gfw", "GFW (Heavy DPI Obfuscation)"),
    OFF("off", "Off (No Noise)"),
    BALANCED("balanced", "Balanced Stealth & Speed"),
    AGGRESSIVE("aggressive", "Aggressive Decoy Packets"),
    LIGHT("light", "Light Overhead")
}

@Serializable
enum class AetherScanMode(val rawValue: String, val description: String) {
    TURBO("turbo", "Fastest endpoint match"),
    BALANCED("balanced", "Optimal speed & reliability"),
    THOROUGH("thorough", "Deep ping & latency optimization"),
    STEALTH("stealth", "Quiet slow scanning"),
    IRONCLAD("ironclad", "Full end-to-end HTTP data probe verification")
}

@Serializable
enum class AetherIpMode(val rawValue: String, val displayName: String) {
    AUTO("Auto", "Auto (Smart)"),
    IPV4("IPv4", "IPv4 Only"),
    IPV6("IPv6", "IPv6 Only"),
    DUAL("Dual", "Dual Stack (4+6)")
}

@Serializable
enum class AetherPerfProfile(val rawValue: String, val displayName: String) {
    AUTO("auto", "Auto (Recommended)"),
    LOW("low", "Power Saver (Low CPU)"),
    MEDIUM("medium", "Balanced Performance"),
    HIGH("high", "Maximum Speed (High CPU)")
}

@Serializable
enum class AetherLogLevel(val displayName: String, val rawValue: String) {
    OFF("Off (Disabled - Default, Zero RAM Overhead)", "off"),
    ERROR("Error Only", "error"),
    WARN("Warning & Error", "warn"),
    INFO("Info, Warn & Error", "info"),
    DEBUG("Debug (All Verbose Output)", "debug")
}

@Serializable
enum class TunnelEngine(val displayName: String) {
    HEV_TUN2SOCKS("HEV Tun2Socks"),
    SOCKS_TUN_BRIDGE("SocksTunBridge")
}

@Serializable
enum class PsiphonChainMode(val rawValue: String, val displayName: String) {
    AUTO("auto", "Auto"),
    ALWAYS("always", "Always"),
    FALLBACK("fallback", "Fallback")
}

@Serializable
enum class ConnectionMode {
    TUNNEL,
    PROXY_ONLY,
    SYSTEM_PROXY
}

@Serializable
enum class ConnectionStatus {
    STOPPED,
    STARTING,
    VALIDATING,
    DATAPLANE_VALIDATED,
    SOCKS_READY,
    TUN_ACTIVE,
    RUNNING,
    RECONNECTING,
    STOPPING,
    ERROR,
    FAILED
}

@Serializable
enum class SocksReadiness {
    NOT_READY,
    LISTENING,
    PROBED_OK
}

@Serializable
enum class RoutingMode {
    TUNNEL,
    DIRECT,
    BLOCK
}

@Serializable
data class RoutingRule(
    val pattern: String,
    val mode: RoutingMode
)

@Serializable
data class AetherConfig(
    val presetId: String = "turbo",
    val protocol: AetherProtocol = AetherProtocol.MASQUE,
    val noise: AetherNoise = AetherNoise.GFW,
    val scanMode: AetherScanMode = AetherScanMode.TURBO,
    val ipMode: AetherIpMode = AetherIpMode.AUTO,
    val echEnabled: Boolean = false,
    val httpProxyEnabled: Boolean = false,
    val perfProfile: AetherPerfProfile = AetherPerfProfile.AUTO,
    val h2Mode: Boolean = true,
    val h2Fragment: Boolean = false,
    val fragmentSize: String = "16-32",
    val fragmentDelay: String = "2-10",
    val noDataCheck: Boolean = false,
    val quickReconnect: Boolean = true,
    val socksHost: String = "127.0.0.1",
    val socksPort: String = "1819",
    val httpPort: String = "1820",
    val appLogLevel: AetherLogLevel = AetherLogLevel.INFO,
    val coreLogLevel: AetherLogLevel = AetherLogLevel.INFO,
    val peer: String = "",
    val wgPeer: String = "",
    val wiwOuter: String = "",
    val wiwInner: String = "",
    val wiwScan: Boolean = true,
    val masqueMtu: Int = 0,
    val netstackTcpRx: Int = 0,
    val netstackTcpTx: Int = 0,
    val keepaliveEnabled: Boolean = true,
    val keepalive: Int = 5,
    val validateSecs: Int = 10,
    val reconnectSecs: Int = 2,
    val wgEndpointCooldownSecs: Int = 300,
    val noProfileRetry: Boolean = false,
    val tlsGroups: String = "",
    val mtu: Int = 1320,
    val connectionMode: ConnectionMode = if (isWindows) ConnectionMode.SYSTEM_PROXY else ConnectionMode.TUNNEL,
    val tunnelEngine: TunnelEngine = TunnelEngine.HEV_TUN2SOCKS,
    val excludedPackages: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet(),
    val tunneledPackages: Set<String> = emptySet(),
    val routingRules: List<RoutingRule> = emptyList(),
    val teamName: String = "",
    val accessEmail: String = "",
    val accessId: String = "",
    val accessSecret: String = "",
    val accessToken: String = "",
    val ztStaySignedIn: Boolean = true,
    val ztTokenExpiry: Long = 0,
    val useGateway: Boolean = false,
    val killSwitch: Boolean = false,
    val ipv6Leak: Boolean = true,
    val smartReconnect: Boolean = true,
    val reconnectRetryLimit: Int = 10,
    val strictKillSwitch: Boolean = false,
    val dnsEnabled: Boolean = false,
    val dnsList: String = "1.1.1.1,2606:4700:4700::1111",
    val shareHotspot: Boolean = false,
    val tunnelAllApps: Boolean = true,
    val upstreamProxy: String = "",
    val upstreamProxyEnabled: Boolean = false,
    val routeSniffing: Boolean = true,
    val sniffingTimeoutMs: Int = 100,
    val reprovision: Boolean = true,
    val hevLogLevel: String = "warn",
    val hevConnectTimeoutMs: Int = 5000,
    val hevReadWriteTimeoutMs: Int = 60000,
    val hevMaxSessionCount: Int = 0,
    val hevMapdnsCacheSize: Int = 10000,
    val hevUdpMode: String = "udp",
    val cloakEnabled: Boolean = false,
    val cloakSniList: String = "www.hcaptcha.com,www.speedtest.net,www.bing.com",
    val cloakTtlList: String = "4,5,6,8",
    val cloakJitterMin: Int = 20,
    val cloakJitterMax: Int = 80,
    val cloakFragment: Boolean = false,
    val cloakAdaptive: Boolean = true,
    val cloakFallbackPorts: String = "443,2053,2083,2087,2096,8443",
    val cloakLogLevel: String = "info",
    val cloakRandomizeSniCase: Boolean = false,
    val psiphonEnabled: Boolean = false,
    val psiphonChainOuter: String = "masque",
    val psiphonSocksPort: String = "3080",
    val psiphonEgressRegion: String = "",
    val psiphonChainMode: PsiphonChainMode = PsiphonChainMode.AUTO,
    val psiphonMasqueOrder: String = "auto",
    val psiphonViaAether: Boolean = true,
    val pingUrl: String = "https://www.gstatic.com/generate_204",
    val connectButtonStyle: String = "swipe",
    val appLanguage: String = "auto"
) {
    fun zeroTrustError(): String? {
        if (protocol != AetherProtocol.ZERO_TRUST) return null
        if (teamName.isBlank()) return "Organization Team Name is required for Zero Trust"

        val hasEmail = accessEmail.isNotBlank()
        val hasServiceToken = accessId.isNotBlank() || accessSecret.isNotBlank()
        val hasToken = accessToken.isNotBlank()

        if (!hasEmail && !hasServiceToken && !hasToken) {
            return "Provide one authentication method: Access Email, Service Token, or Access Token"
        }
        if (hasServiceToken && (accessId.isBlank() || accessSecret.isBlank())) {
            return "Service Token requires both Access Client ID and Access Client Secret"
        }
        val providedCount = listOf(hasEmail, hasServiceToken, hasToken).count { it }
        if (providedCount > 1) return "Use only one authentication method at a time"

        return null
    }

    fun zeroTrustErrorLocalized(strings: AppStrings): String? {
        if (protocol != AetherProtocol.ZERO_TRUST) return null
        if (teamName.isBlank()) return strings.TOAST_ZT_TEAM_REQUIRED
        val hasEmail = accessEmail.isNotBlank()
        val hasServiceToken = accessId.isNotBlank() || accessSecret.isNotBlank()
        val hasToken = accessToken.isNotBlank()
        if (!hasEmail && !hasServiceToken && !hasToken) {
            return strings.TOAST_ZT_PROVIDE_ONE_AUTH
        }
        if (hasServiceToken && (accessId.isBlank() || accessSecret.isBlank())) {
            return strings.TOAST_ZT_SERVICE_TOKEN_REQUIRES
        }
        val providedCount = listOf(hasEmail, hasServiceToken, hasToken).count { it }
        if (providedCount > 1) return strings.TOAST_ZT_ONLY_ONE_AUTH
        return null
    }

    fun effectiveZeroTrustConfig(): AetherConfig {
        if (protocol != AetherProtocol.ZERO_TRUST) return this
        if (!ztStaySignedIn) {
            return this.copy(accessToken = "", accessId = "", accessSecret = "")
        }
        if (accessToken.isNotBlank() && ztTokenExpiry != 0L && ztTokenExpiry < System.currentTimeMillis()) {
            return this.copy(accessToken = "")
        }
        return this
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseJwtExpiry(token: String): Long {
        val parts = token.split(".")
        if (parts.size < 2) return 0
        return try {
            var p = parts[1]
            val rem = p.length % 4
            if (rem != 0) p = p.padEnd(p.length + (4 - rem), '=')
            val json = Base64.UrlSafe.decode(p).decodeToString()
            val m = Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)
            m?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun resolvedLanguage(): String {
        if (appLanguage != "auto") return appLanguage
        return try {
            val sys = Locale.getDefault().language.lowercase()
            if (sys.startsWith("fa")) "fa" else "en"
        } catch (_: Exception) { "en" }
    }

    fun effectiveIpMode(): AetherIpMode = if (ipMode == AetherIpMode.AUTO) AetherIpMode.DUAL else ipMode
}
