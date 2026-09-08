package io.github.immaghzbad.aetherst.shared.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getLocalIpAddress(): String? = getHotspotIpAddress()

    fun getHotspotIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?.let { Collections.list(it) }
                .orEmpty()

            val candidates = interfaces
                .asSequence()
                .filter { iface ->
                    runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
                }
                .filterNot(::isRejectedInterface)
                .flatMap { iface ->
                    Collections.list(iface.inetAddresses)
                        .asSequence()
                        .filterIsInstance<Inet4Address>()
                        .filter { address ->
                            !address.isLoopbackAddress &&
                                !address.isLinkLocalAddress &&
                                address.isSiteLocalAddress &&
                                address.hostAddress != "198.18.0.1"
                        }
                        .map { address ->
                            HotspotCandidate(
                                address = address.hostAddress ?: "",
                                score = hotspotScore(iface, address)
                            )
                        }
                }
                .filter { it.score < NOT_A_HOTSPOT }
                .toList()

            candidates
                .minWithOrNull(
                    compareBy<HotspotCandidate> { it.score }
                        .thenByDescending { it.address.endsWith(".1") }
                        .thenBy { it.address }
                )
                ?.address
        } catch (_: Exception) {
            null
        }
    }

    private fun isRejectedInterface(iface: NetworkInterface): Boolean {
        val id = interfaceId(iface)
        return REJECTED_MARKERS.any(id::contains)
    }

    private fun hotspotScore(iface: NetworkInterface, address: Inet4Address): Int {
        val id = interfaceId(iface)
        val name = iface.name.orEmpty().lowercase()

        return when {
            HOTSPOT_MARKERS.any(id::contains) -> 0
            name.matches(Regex("wlan\\d+")) -> 1
            name.matches(Regex("wl\\d+")) -> 2
            name.matches(Regex("ap\\d*")) -> 1
            name.matches(Regex("rndis\\d*")) -> 1
            name.matches(Regex("usb\\d*")) -> 1
            name.matches(Regex("bt-pan\\d*")) -> 1
            name.matches(Regex("eth\\d+")) -> 3
            address.address.last().toInt().and(0xff) == 1 -> 10
            else -> NOT_A_HOTSPOT
        }
    }

    private fun interfaceId(iface: NetworkInterface): String =
        "${iface.name.orEmpty()} ${iface.displayName.orEmpty()}".lowercase()

    private data class HotspotCandidate(
        val address: String,
        val score: Int
    )

    private const val NOT_A_HOTSPOT = 1_000

    private val HOTSPOT_MARKERS = listOf(
        "softap",
        "wifi_ap",
        "wifi-ap",
        "wifiap",
        "swlan",
        "sap",
        "hotspot",
        "tether",
        "bridge",
        "br0",
        "rndis",
        "usb",
        "bt-pan"
    )

    private val REJECTED_MARKERS = listOf(
        "rmnet",
        "radio",
        "cell",
        "ccmni",
        "pdp",
        "ncm",
        "pbp",
        "tun",
        "tap",
        "vpn",
        "clat",
        "dummy",
        "loopback",
        "p2p"
    )
}
