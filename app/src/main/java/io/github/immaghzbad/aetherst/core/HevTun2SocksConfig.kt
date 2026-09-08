package io.github.immaghzbad.aetherst.core

data class HevEngineSettings(
    val logLevel: String = "warn",
    val connectTimeoutMs: Int = 5000,
    val readWriteTimeoutMs: Int = 60000,
    val maxSessionCount: Int = 0,
    val mapdnsCacheSize: Int = 10000
)

object HevTun2SocksConfig {
    const val MAP_DNS_ADDRESS = "198.18.0.2"

    fun generate(
        socksAddress: String,
        socksPort: Int,
        mtu: Int,
        settings: HevEngineSettings = HevEngineSettings(),
        ipv4Address: String = "198.18.0.1",
        ipv6Address: String = "fd00::1",
        udpMode: String = "udp"
    ): String {
        val sb = StringBuilder()
        sb.append("tunnel:\n")
        sb.append("  mtu: $mtu\n")
        sb.append("  ipv4: $ipv4Address\n")
        sb.append("  ipv6: '$ipv6Address'\n")

        sb.append("\nsocks5:\n")
        sb.append("  address: $socksAddress\n")
        sb.append("  port: $socksPort\n")
        when (udpMode.lowercase().trim()) {
            "udp" -> sb.append("  udp: 'udp'\n")
            "tcp" -> sb.append("  udp: 'tcp'\n")
            "true", "icmp" -> sb.append("  udp: 'tcp'\n")
            "off", "disable", "disabled", "none" -> { }
            else -> sb.append("  udp: 'tcp'\n")
        }

        sb.append("\nmapdns:\n")
        sb.append("  address: $MAP_DNS_ADDRESS\n")
        sb.append("  port: 53\n")
        sb.append("  network: 100.64.0.0\n")
        sb.append("  netmask: 255.192.0.0\n")
        sb.append("  cache-size: ${settings.mapdnsCacheSize.coerceIn(100, 1000000)}\n")
        sb.append("  nat64-prefix: 64:ff9b::/96\n")

        val logLevel = if (settings.logLevel in setOf("error", "warn", "info", "debug")) settings.logLevel else "warn"
        val connectTimeout = settings.connectTimeoutMs.coerceIn(500, 120000)
        val readWriteTimeout = settings.readWriteTimeoutMs.coerceIn(1000, 600000)
        val maxSessions = settings.maxSessionCount.coerceAtLeast(0)

        val isUdpEnabled = udpMode.lowercase().trim() !in setOf("off", "disable", "disabled", "none", "")
        sb.append("\nmisc:\n")
        sb.append("  log-level: $logLevel\n")
        sb.append("  connect-timeout: $connectTimeout\n")
        sb.append("  read-write-timeout: $readWriteTimeout\n")
        if (maxSessions > 0) {
            sb.append("  max-session-count: $maxSessions\n")
        }
        if (isUdpEnabled) {
            val udpTimeout = maxOf(readWriteTimeout, 180000)
            sb.append("  udp-read-write-timeout: $udpTimeout\n")
        }

        return sb.toString()
    }
}
