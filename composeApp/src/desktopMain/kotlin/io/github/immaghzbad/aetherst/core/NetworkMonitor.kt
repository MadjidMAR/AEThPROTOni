package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Monitors Windows network connectivity changes and triggers auto-reconnect.
 *
 * On Android, AetherVpnService uses ConnectivityManager.NetworkCallback.
 * On desktop we poll the active default gateway and DNS resolution periodically.
 */
object NetworkMonitor {

    private const val TAG = "NetMon"

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _activeInterface = MutableStateFlow("")
    val activeInterface: StateFlow<String> = _activeInterface.asStateFlow()

    private var monitorJob: Job? = null
    private var onNetworkLost: (() -> Unit)? = null
    private var onNetworkRestored: (() -> Unit)? = null
    private var lastGateway: String? = null
    private var wasConnected: Boolean = true

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Start monitoring network changes.
     * @param onNetworkLost     Called when connectivity drops.
     * @param onNetworkRestored Called when connectivity returns after a drop.
     */
    fun start(
        scope: CoroutineScope,
        onNetworkLost: () -> Unit = {},
        onNetworkRestored: () -> Unit = {}
    ) {
        if (monitorJob?.isActive == true) return
        this.onNetworkLost = onNetworkLost
        this.onNetworkRestored = onNetworkRestored

        monitorJob = scope.launch(Dispatchers.IO) {
            LogRepository.i("[$TAG] Network monitor started")
            while (isActive) {
                try {
                    val gw = getDefaultGateway()
                    val dnsOk = testDnsResolution()
                    val networkUp = gw != null && dnsOk

                    _isNetworkAvailable.value = networkUp
                    if (gw != null) _activeInterface.value = gw

                    if (wasConnected && !networkUp) {
                        LogRepository.w("[$TAG] Network lost (gateway=${gw ?: "null"}, dns=$dnsOk)")
                        withContext(Dispatchers.Default) { onNetworkLost() }
                    } else if (!wasConnected && networkUp) {
                        LogRepository.i("[$TAG] Network restored (gateway=$gw)")
                        withContext(Dispatchers.Default) { onNetworkRestored() }
                    }
                    wasConnected = networkUp
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogRepository.e("[$TAG] Monitor error: ${e.message}")
                }
                delay(3000L)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        lastGateway = null
        wasConnected = true
        LogRepository.i("[$TAG] Network monitor stopped")
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun getDefaultGateway(): String? {
        return try {
            val proc = ProcessBuilder("route", "print", "0.0.0.0")
                .redirectErrorStream(true)
                .start()
            val lines = proc.inputStream.bufferedReader().readLines()
            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)

            // Look for the default route (destination 0.0.0.0, mask 0.0.0.0)
            // The format is: Network Destination  Netmask  Gateway  Interface  Metric
            for (line in lines) {
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 3 && cols[0] == "0.0.0.0" && cols[1] == "0.0.0.0") {
                    val gw = cols[2]
                    // Skip virtual gateway addresses used by TUN
                    if (gw.startsWith("198.18.") || gw.startsWith("10.98.") || gw == "0.0.0.0") continue
                    return gw
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun testDnsResolution(): Boolean {
        return try {
            InetAddress.getByName("www.google.com").isReachable(3000)
        } catch (_: Exception) {
            try {
                InetAddress.getByName("1.1.1.1").isReachable(3000)
            } catch (_: Exception) {
                false
            }
        }
    }
}
