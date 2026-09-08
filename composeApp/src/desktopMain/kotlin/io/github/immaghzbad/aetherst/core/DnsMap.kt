package io.github.immaghzbad.aetherst.shared.core

import java.util.Collections
import java.util.Locale

object DnsMap {
    private data class Entry(
        val domain: String,
        val expiresAt: Long
    )

    private const val MAX_ENTRIES = 4096
    private const val DEFAULT_TTL_MILLIS = 300_000L
    private val ipToDomains = Collections.synchronizedMap(
        object : LinkedHashMap<String, MutableList<Entry>>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableList<Entry>>?): Boolean {
                return size > MAX_ENTRIES
            }
        }
    )

    private fun now(): Long = System.nanoTime() / 1_000_000L

    fun put(ip: String, domain: String, ttlMillis: Long = DEFAULT_TTL_MILLIS) {
        val normalizedIp = ip.trim()
        val normalizedDomain = domain.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedIp.isEmpty() || normalizedDomain.isEmpty()) return
        val current = now()
        val expiry = current + ttlMillis.coerceIn(1_000L, 86_400_000L)
        synchronized(ipToDomains) {
            val entries = ipToDomains.getOrPut(normalizedIp) { mutableListOf() }
            entries.removeAll { it.expiresAt <= current || it.domain == normalizedDomain }
            entries.add(Entry(normalizedDomain, expiry))
            while (entries.size > 8) entries.removeAt(0)
        }
    }

    fun get(ip: String): String? {
        val current = now()
        synchronized(ipToDomains) {
            val entries = ipToDomains[ip] ?: return null
            entries.removeAll { it.expiresAt <= current }
            if (entries.isEmpty()) {
                ipToDomains.remove(ip)
                return null
            }
            return entries.last().domain
        }
    }

    fun clear() {
        ipToDomains.clear()
    }
}
