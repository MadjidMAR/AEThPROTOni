package io.github.immaghzbad.aetherst.shared.data

import io.github.immaghzbad.aetherst.platform.Settings
import io.github.immaghzbad.aetherst.shared.model.ProbeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

data class DnsServerEntry(
    val id: String,
    val name: String,
    val ip: String,
    val isIpv6: Boolean,
    val group: String
)

data class DnsProbeResult(
    val id: String,
    val name: String,
    val ip: String,
    val isIpv6: Boolean,
    val group: String,
    val status: ProbeStatus = ProbeStatus.IDLE,
    val medianMs: Long = -1,
    val successCount: Int = 0,
    val totalCount: Int = 0,
    val samples: List<Long> = emptyList()
)

enum class DnsBenchmarkPhase {
    IDLE,
    RUNNING,
    COMPLETE,
    ERROR
}

data class DnsBenchmarkState(
    val phase: DnsBenchmarkPhase = DnsBenchmarkPhase.IDLE,
    val currentStep: String = "",
    val progressPercent: Int = 0,
    val results: List<DnsProbeResult> = emptyList(),
    val bestDnsList: String = "",
    val includeIpv6: Boolean = true,
    val customDns: String = "",
    val enabledIds: Set<String> = emptySet(),
    val error: String? = null
)

object DnsBenchmarkRepository {
    val globalServers = listOf(
        DnsServerEntry("cf-1", "Cloudflare", "1.1.1.1", false, "global"),
        DnsServerEntry("cf-2", "Cloudflare", "1.0.0.1", false, "global"),
        DnsServerEntry("cf-6-1", "Cloudflare", "2606:4700:4700::1111", true, "global"),
        DnsServerEntry("cf-6-2", "Cloudflare", "2606:4700:4700::1001", true, "global"),
        DnsServerEntry("google-1", "Google", "8.8.8.8", false, "global"),
        DnsServerEntry("google-2", "Google", "8.8.4.4", false, "global"),
        DnsServerEntry("google-6-1", "Google", "2001:4860:4860::8888", true, "global"),
        DnsServerEntry("google-6-2", "Google", "2001:4860:4860::8844", true, "global"),
        DnsServerEntry("quad9-1", "Quad9", "9.9.9.9", false, "global"),
        DnsServerEntry("quad9-2", "Quad9", "149.112.112.112", false, "global"),
        DnsServerEntry("quad9-6-1", "Quad9", "2620:fe::fe", true, "global"),
        DnsServerEntry("quad9-6-2", "Quad9", "2620:fe::9", true, "global"),
        DnsServerEntry("opendns-1", "OpenDNS", "208.67.222.222", false, "global"),
        DnsServerEntry("opendns-2", "OpenDNS", "208.67.220.220", false, "global"),
        DnsServerEntry("opendns-6-1", "OpenDNS", "2620:119:35::35", true, "global"),
        DnsServerEntry("opendns-6-2", "OpenDNS", "2620:119:53::53", true, "global"),
        DnsServerEntry("adguard-1", "AdGuard", "94.140.14.14", false, "global"),
        DnsServerEntry("adguard-2", "AdGuard", "94.140.15.15", false, "global"),
        DnsServerEntry("adguard-6-1", "AdGuard", "2a10:50c0::ad1:ff", true, "global"),
        DnsServerEntry("adguard-6-2", "AdGuard", "2a10:50c0::ad2:ff", true, "global")
    )

    val iranServers = listOf(
        DnsServerEntry("shecan-1", "Shecan", "178.22.122.100", false, "iran"),
        DnsServerEntry("shecan-2", "Shecan", "185.51.200.2", false, "iran"),
        DnsServerEntry("begzar-1", "Begzar", "185.55.226.26", false, "iran"),
        DnsServerEntry("begzar-2", "Begzar", "185.55.225.25", false, "iran"),
        DnsServerEntry("electro-1", "Electro", "78.157.42.100", false, "iran"),
        DnsServerEntry("electro-2", "Electro", "78.157.42.101", false, "iran"),
        DnsServerEntry("radar-1", "Radar", "10.202.10.10", false, "iran"),
        DnsServerEntry("radar-2", "Radar", "10.202.10.11", false, "iran"),
        DnsServerEntry("403-1", "403.online", "10.202.10.202", false, "iran"),
        DnsServerEntry("403-2", "403.online", "10.202.10.102", false, "iran")
    )

    val allCatalog: List<DnsServerEntry> = globalServers + iranServers

    private const val QUERY_TIMEOUT_MS = 2800

    private val _state = MutableStateFlow(DnsBenchmarkState())
    val state: StateFlow<DnsBenchmarkState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private val mutex = Mutex()
    private var settings: Settings? = null

    fun initialize(s: Settings) {
        if (settings != null) return
        settings = s
        val includeIpv6 = s.getBoolean("dns_bench_include_ipv6", true)
        val customDns = s.getString("dns_bench_custom", "")
        val enabledRaw = s.getString("dns_bench_enabled", "")
        val enabled = if (enabledRaw.isBlank()) allCatalog.map { it.id }.toSet() else enabledRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        _state.value = _state.value.copy(includeIpv6 = includeIpv6, customDns = customDns, enabledIds = enabled)
    }

    fun setIncludeIpv6(value: Boolean) {
        _state.value = _state.value.copy(includeIpv6 = value)
        settings?.putBoolean("dns_bench_include_ipv6", value)
    }

    fun setCustomDns(value: String) {
        val normalized = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        _state.value = _state.value.copy(customDns = value)
        settings?.putString("dns_bench_custom", normalized)
    }

    fun toggleServer(id: String) {
        val current = _state.value.enabledIds.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _state.value = _state.value.copy(enabledIds = current)
        settings?.putString("dns_bench_enabled", current.joinToString(","))
    }

    fun activeEntries(): List<DnsServerEntry> {
        val s = _state.value
        val catalog = allCatalog.filter { s.enabledIds.contains(it.id) }.filter { s.includeIpv6 || !it.isIpv6 }
        val custom = parseCustomEntries(s.customDns, s.includeIpv6)
        return catalog + custom
    }

    fun parseCustomEntries(raw: String, includeIpv6: Boolean): List<DnsServerEntry> {
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().mapNotNull { ip ->
            val v6 = ip.contains(":")
            if (v6 && !includeIpv6) return@mapNotNull null
            if (!isValidIp(ip)) return@mapNotNull null
            DnsServerEntry("custom-$ip", "Custom", ip, v6, "custom")
        }
    }

    fun isValidIp(ip: String): Boolean {
        val v4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (v4.matches(ip)) {
            return ip.split(".").all { (it.toIntOrNull() ?: 256) in 0..255 }
        }
        if (ip.contains(":")) {
            return ip.length in 3..45 && ip.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }
        }
        return false
    }

    fun cancel() {
        scanJob?.cancel()
        if (_state.value.phase == DnsBenchmarkPhase.RUNNING) {
            _state.value = _state.value.copy(phase = DnsBenchmarkPhase.IDLE, currentStep = "")
        }
    }

    fun reset() {
        scanJob?.cancel()
        val s = _state.value
        _state.value = s.copy(phase = DnsBenchmarkPhase.IDLE, currentStep = "", progressPercent = 0, results = emptyList(), bestDnsList = "", error = null)
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        if (!mutex.tryLock()) return
        val entries = activeEntries()
        if (entries.isEmpty()) {
            mutex.unlock()
            _state.value = _state.value.copy(phase = DnsBenchmarkPhase.ERROR, error = "empty")
            return
        }
        scanJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val total = entries.size
                val collected = Collections.synchronizedList(mutableListOf<DnsProbeResult>())
                val doneCount = AtomicInteger(0)
                val permits = Semaphore(6)
                _state.update { it.copy(phase = DnsBenchmarkPhase.RUNNING, currentStep = entries.first().ip, progressPercent = 2, results = emptyList(), bestDnsList = "", error = null) }
                val deferred = entries.map { entry ->
                    async {
                        permits.withPermit {
                            ensureActive()
                            val result = withContext(Dispatchers.IO) { probeDns(entry) }
                            collected.add(result)
                            val done = doneCount.incrementAndGet()
                            _state.update { it.copy(currentStep = entry.ip, progressPercent = (2 + done * 96 / total).coerceIn(0, 99), results = collected.toList()) }
                            result
                        }
                    }
                }
                val all = deferred.awaitAll()
                val ranked = all.filter { it.status == ProbeStatus.SUCCESS }.sortedBy { it.medianMs }
                val best = ranked.take(3).map { it.ip }
                val bestList = if (best.isNotEmpty()) best.joinToString(",") else ""
                _state.update { it.copy(phase = DnsBenchmarkPhase.COMPLETE, currentStep = "", progressPercent = 100, results = all.sortedBy { if (it.status == ProbeStatus.SUCCESS) it.medianMs else Long.MAX_VALUE }, bestDnsList = bestList) }
                LogRepository.i("DNS benchmark complete: tested=$total success=${ranked.size} best=$bestList")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = DnsBenchmarkPhase.ERROR, error = e.message ?: "failed")
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun probeDns(entry: DnsServerEntry): DnsProbeResult {
        val samples = mutableListOf<Long>()
        var success = 0
        val rounds = 5
        val domains = listOf("google.com", "cloudflare.com", "aparat.com")
        val qtype = if (entry.isIpv6) 28 else 1
        repeat(rounds) { index ->
            val rtt = queryDns(entry.ip, domains[index % domains.size], qtype)
            if (rtt in 1..5000) {
                samples.add(rtt)
                success++
            }
            delay(90.milliseconds)
        }
        return if (success >= 3) {
            val sorted = samples.sorted()
            DnsProbeResult(entry.id, entry.name, entry.ip, entry.isIpv6, entry.group, ProbeStatus.SUCCESS, sorted[sorted.size / 2], success, rounds, sorted)
        } else {
            DnsProbeResult(entry.id, entry.name, entry.ip, entry.isIpv6, entry.group, ProbeStatus.FAILED, -1, success, rounds, emptyList())
        }
    }

    private fun queryDns(host: String, domain: String, qtype: Int): Long {
        return try {
            val txId = Random.nextBytes(2)
            val query = buildDnsQuery(txId, domain, qtype)
            DatagramSocket().use { socket ->
                socket.soTimeout = QUERY_TIMEOUT_MS
                val address = InetSocketAddress(host, 53)
                val buffer = ByteArray(512)
                val resp = DatagramPacket(buffer, buffer.size)
                val start = System.nanoTime()
                socket.send(DatagramPacket(query, query.size, address))
                socket.receive(resp)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                val len = resp.length
                if (isTruncated(buffer, len, txId)) return queryDnsOverTcp(host, query, txId)
                if (isValidResponse(buffer, len, txId)) elapsed else -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun queryDnsOverTcp(host: String, query: ByteArray, txId: ByteArray): Long {
        val socket = Socket()
        return try {
            socket.soTimeout = QUERY_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, 53), QUERY_TIMEOUT_MS)
            val start = System.nanoTime()
            val out = socket.getOutputStream()
            out.write((query.size shr 8) and 0xFF)
            out.write(query.size and 0xFF)
            out.write(query)
            out.flush()
            val inp = socket.getInputStream()
            val head = ByteArray(2)
            var off = 0
            while (off < 2) {
                val n = inp.read(head, off, 2 - off)
                if (n < 0) return -1
                off += n
            }
            val msgLen = ((head[0].toInt() and 0xFF) shl 8) or (head[1].toInt() and 0xFF)
            if (msgLen <= 12 || msgLen > 4096) return -1
            val msg = ByteArray(msgLen)
            var got = 0
            while (got < msgLen) {
                val n = inp.read(msg, got, msgLen - got)
                if (n < 0) return -1
                got += n
            }
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            if (isValidResponse(msg, msgLen, txId)) elapsed else -1
        } catch (_: Exception) {
            -1
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun isValidResponse(msg: ByteArray, len: Int, txId: ByteArray): Boolean {
        if (len <= 12) return false
        if (msg[0] != txId[0] || msg[1] != txId[1]) return false
        val flags1 = msg[2].toInt() and 0xFF
        val flags2 = msg[3].toInt() and 0xFF
        if ((flags1 and 0x80) == 0) return false
        if ((flags2 and 0x0F) != 0) return false
        val anCount = ((msg[4].toInt() and 0xFF) shl 8) or (msg[5].toInt() and 0xFF)
        return anCount > 0
    }

    private fun isTruncated(msg: ByteArray, len: Int, txId: ByteArray): Boolean {
        if (len <= 12) return false
        if (msg[0] != txId[0] || msg[1] != txId[1]) return false
        val flags1 = msg[2].toInt() and 0xFF
        return (flags1 and 0x80) != 0 && (flags1 and 0x02) != 0
    }

    private fun buildDnsQuery(txId: ByteArray, domain: String, qtype: Int): ByteArray {
        val out = mutableListOf<Byte>()
        out.add(txId[0])
        out.add(txId[1])
        out.add(0x01)
        out.add(0x00)
        out.add(0x00)
        out.add(0x01)
        out.add(0x00)
        out.add(0x00)
        out.add(0x00)
        out.add(0x00)
        out.add(0x00)
        out.add(0x00)
        val parts = domain.split(".")
        for (p in parts) {
            out.add(p.length.toByte())
            for (c in p) out.add(c.code.toByte())
        }
        out.add(0x00)
        out.add(((qtype shr 8) and 0xFF).toByte())
        out.add((qtype and 0xFF).toByte())
        out.add(0x00)
        out.add(0x01)
        return out.toByteArray()
    }
}
