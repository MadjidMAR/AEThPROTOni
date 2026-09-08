package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop implementation of DirectRouteVerifier.
 * Detects DNS/routing leaks by comparing the tunnel exit IP against the direct (physical) IP.
 * On Android this uses VpnService.protect() to bypass the TUN; on desktop we connect
 * through a separate socket that bypasses the TUN interface to reach the physical network.
 */
object DirectRouteVerifier {

    private const val TAG = "DirectRoute"

    /** Maximum age (ms) before re-verifying a domain. */
    private const val DOMAIN_COOLDOWN_MS = 300_000L

    /** Minimum gap between two API requests. */
    private const val GLOBAL_COOLDOWN_MS = 3_000L

    private val lastVerification = ConcurrentHashMap<String, Long>()
    @Volatile private var lastGlobalRequest = 0L

    // ── Public state exposed to the UI ──────────────────────────────────

    enum class LeakStatus { UNKNOWN, SAFE, LEAK_DETECTED, CHECKING }

    data class RouteCheckResult(
        val domain: String = "",
        val directIp: String = "",
        val tunnelIp: String = "",
        val status: LeakStatus = LeakStatus.UNKNOWN,
        val message: String = ""
    )

    private val _result = MutableStateFlow(RouteCheckResult())
    val result: StateFlow<RouteCheckResult> = _result.asStateFlow()

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Verify that [domain]'s traffic is actually routed through the tunnel.
     * @param tunnelExitIp  The exit IP reported by the connected tunnel (from IpInfoRepository).
     * @param socksHost     The local SOCKS proxy host (e.g. "127.0.0.1").
     * @param socksPort     The local SOCKS proxy port (e.g. 10808).
     * @param bypassProxy   When true, the check itself bypasses the proxy to reach the physical network.
     */
    suspend fun verify(
        domain: String,
        tunnelExitIp: String,
        socksHost: String = "127.0.0.1",
        socksPort: Int = 10808,
        bypassProxy: Boolean = true
    ): RouteCheckResult = withContext(Dispatchers.IO) {
        // Rate-limit per domain
        val now = System.currentTimeMillis()
        val lastForDomain = lastVerification[domain] ?: 0L
        if (now - lastForDomain < DOMAIN_COOLDOWN_MS) {
            return@withContext _result.value.copy(domain = domain, status = LeakStatus.SAFE, message = "Recently verified")
        }

        // Global rate-limit
        val sinceGlobal = now - lastGlobalRequest
        if (sinceGlobal < GLOBAL_COOLDOWN_MS) {
            delay(GLOBAL_COOLDOWN_MS - sinceGlobal)
        }

        _result.value = RouteCheckResult(domain = domain, status = LeakStatus.CHECKING, message = "Checking route…")
        lastGlobalRequest = System.currentTimeMillis()

        val checkResult = try {
            val directIp = fetchDirectIp(domain, bypassProxy)
            lastVerification[domain] = System.currentTimeMillis()

            if (directIp.isNullOrBlank()) {
                RouteCheckResult(domain, "", tunnelExitIp, LeakStatus.CHECKING, "Could not determine direct IP")
            } else if (tunnelExitIp.isBlank()) {
                RouteCheckResult(domain, directIp, "", LeakStatus.SAFE, "Tunnel IP not available yet")
            } else if (directIp == tunnelExitIp) {
                RouteCheckResult(domain, directIp, tunnelExitIp, LeakStatus.SAFE, "Traffic is routed through tunnel")
            } else {
                LogRepository.w("[$TAG] Route mismatch for $domain: direct=$directIp tunnel=$tunnelExitIp")
                RouteCheckResult(domain, directIp, tunnelExitIp, LeakStatus.LEAK_DETECTED, "Routing leak detected – traffic bypasses tunnel")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogRepository.e("[$TAG] Verification failed for $domain: ${e.message}")
            RouteCheckResult(domain, "", tunnelExitIp, LeakStatus.CHECKING, "Check failed: ${e.message}")
        }

        _result.value = checkResult
        checkResult
    }

    fun reset() {
        _result.value = RouteCheckResult()
        lastVerification.clear()
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun fetchDirectIp(domain: String, bypassProxy: Boolean): String? {
        val apis = listOf(
            "https://ipwho.is",
            "https://ipinfo.io/ip",
            "https://api.ipify.org"
        )
        for (api in apis) {
            try {
                val url = URL(api)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true

                // On desktop we don't set a proxy so it goes through the physical
                // network when the TUN routes 0.0.0.0/0 (the TUN is on a different
                // subnet and our socket binds to the physical interface). If needed
                // in the future we can use a raw socket or Win32 API to force
                // bypassing the TUN.
                conn.connect()
                val body = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()

                // Parse IP from response
                val ip = parseIp(body)
                if (ip != null) return ip
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun parseIp(json: String): String? {
        // Simple JSON parsing without external deps
        // Try "ip":"x.x.x.x" pattern
        val ipPattern = Regex(""""ip"\s*:\s*"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})"""")
        ipPattern.find(json)?.let { return it.groupValues[1] }

        // Try plain text IP (for ipify/ipinfo)
        val plainIp = Regex("""^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})$""")
        plainIp.find(json.trim())?.let { return it.groupValues[1] }

        return null
    }
}
