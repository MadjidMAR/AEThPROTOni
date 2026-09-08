package io.github.immaghzbad.aetherst.core

import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object CloakEmbeddedServer {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var port: Int = 0
    private var config: Config? = null
    private data class Config(
        val servers: List<ServerEntry>,
        val sniList: List<String>,
        val ttlList: List<Int>,
        val jitterMin: Int,
        val jitterMax: Int,
        val fragment: Boolean,
        val randomizeCase: Boolean
    )
    private data class ServerEntry(val host: String, val port: Int)
    fun start(confPath: String): Int {
        if (running.get()) return port
        val cfg = parseConfig(confPath) ?: return -1
        config = cfg
        return try {
            val ss = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
            port = ss.localPort
            serverSocket = ss
            running.set(true)
            acceptThread = thread(isDaemon = true, name = "cloak-embedded") {
                while (running.get()) {
                    try {
                        val c = ss.accept()
                        executor.execute { handleClient(c, cfg) }
                    } catch (_: Throwable) { break }
                }
            }
            port
        } catch (_: Throwable) { -1 }
    }
    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
    }
    fun isRunning(): Boolean = running.get() && serverSocket?.isClosed == false
    fun getPort(): Int = port
    private fun parseConfig(path: String): Config? {
        return try {
            val map = mutableMapOf<String, String>()
            File(path).forEachLine { line ->
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) return@forEachLine
                val idx = t.indexOf("=")
                if (idx < 0) return@forEachLine
                map[t.substring(0, idx).trim().lowercase()] = t.substring(idx + 1).trim()
            }
            val listen = map["listen_port"]?.toIntOrNull() ?: 0
            if (listen != 0) port = listen
            val sni = map["sni_list"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("www.hcaptcha.com")
            val ttl = map["ttl_list"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: listOf(4, 5, 6, 8)
            val jitterMin = map["jitter_min_ms"]?.toIntOrNull() ?: 20
            val jitterMax = map["jitter_max_ms"]?.toIntOrNull() ?: 80
            val fragment = map["fragment"]?.let { it.equals("true", true) || it == "1" } ?: false
            val randomize = map["randomize_sni_case"]?.let { it.equals("true", true) || it == "1" } ?: false
            val connectList = map["connect_list"] ?: "162.159.198.79:443"
            val fallback = map["fallback_ports"] ?: "443,2053,2083,2096,8443"
            val fallbacks = fallback.split(",").mapNotNull { it.trim().toIntOrNull() }
            val servers = mutableListOf<ServerEntry>()
            for (entry in connectList.split(",")) {
                val e = entry.trim()
                if (e.isEmpty()) continue
                var host = ""
                var p = 0
                if (e.startsWith("[")) {
                    val c = e.indexOf("]")
                    if (c < 0) continue
                    host = e.substring(1, c)
                    val rest = e.substring(c + 1)
                    if (rest.startsWith(":")) p = rest.substring(1).toIntOrNull() ?: 0
                } else {
                    val col = e.lastIndexOf(":")
                    if (col < 0) { host = e } else { host = e.substring(0, col); p = e.substring(col + 1).toIntOrNull() ?: 0 }
                }
                if (host.isEmpty()) continue
                if (p != 0) servers.add(ServerEntry(host, p)) else for (fp in fallbacks) servers.add(ServerEntry(host, fp))
            }
            if (servers.isEmpty()) servers.add(ServerEntry("162.159.198.79", 443))
            Config(servers, sni, ttl, jitterMin, jitterMax, fragment, randomize)
        } catch (_: Throwable) { null }
    }
    private fun handleClient(client: Socket, cfg: Config) {
        var server: Socket? = null
        try {
            client.soTimeout = 5000
            val hello = readTlsRecord(client) ?: run { client.close(); return }
            client.soTimeout = 0
            val winner = raceServers(cfg.servers) ?: run { client.close(); return }
            val jitter = cfg.jitterMin + if (cfg.jitterMax > cfg.jitterMin) (Math.random() * (cfg.jitterMax - cfg.jitterMin)).toInt() else 0
            if (jitter > 0) Thread.sleep(jitter.toLong())
            server = Socket()
            server.tcpNoDelay = true
            server.connect(InetSocketAddress(winner.host, winner.port), 5000)
            if (cfg.fragment) sendFragmented(server, hello) else server.getOutputStream().write(hello)
            server.getOutputStream().flush()
            val t1 = thread(isDaemon = true) { relay(client.getInputStream(), server.getOutputStream()) }
            val t2 = thread(isDaemon = true) { relay(server.getInputStream(), client.getOutputStream()) }
            t1.join(300000)
            t2.join(300000)
        } catch (_: Throwable) {
        } finally {
            try { client.close() } catch (_: Throwable) {}
            try { server?.close() } catch (_: Throwable) {}
        }
    }
    private fun raceServers(servers: List<ServerEntry>): ServerEntry? {
        for (s in servers) {
            try {
                Socket().use { sock ->
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(s.host, s.port), 800)
                    return s
                }
            } catch (_: Throwable) {}
        }
        return servers.firstOrNull()
    }
    private fun sendDecoy(server: ServerEntry, cfg: Config, ttl: Int) {
        try {
            val sni = cfg.sniList.randomOrNull() ?: "www.bing.com"
            val cs = if (cfg.randomizeCase) sni.map { if (Math.random() < 0.5) it.uppercaseChar() else it.lowercaseChar() }.joinToString("") else sni
            val hello = buildFakeHello(cs)
            Socket().use { sock ->
                try {
                    val field = sock.javaClass.getDeclaredField("impl")
                    field.isAccessible = true
                } catch (_: Throwable) {}
                sock.connect(InetSocketAddress(server.host, server.port), 300)
                sock.getOutputStream().write(hello)
                sock.getOutputStream().flush()
                Thread.sleep(20)
            }
        } catch (_: Throwable) {}
    }
    private fun buildFakeHello(sni: String): ByteArray {
        return try {
            val sniBytes = sni.toByteArray()
            val body = mutableListOf<Byte>()
            body.add(0x03); body.add(0x03)
            repeat(32) { body.add((Math.random() * 256).toInt().toByte()) }
            body.add(0x00)
            val ciphers: List<Int> = listOf(0x1301, 0x1302, 0x1303, 0xC02B, 0xC02F, 0xC02C, 0xC030, 0xCCA9, 0xCCA8, 0xC013, 0xC014, 0x009C, 0x009D, 0x002F, 0x0035)
            body.add(((ciphers.size * 2) shr 8).toByte()); body.add((ciphers.size * 2).toByte())
            for (c in ciphers) { body.add((c shr 8).toByte()); body.add(c.toByte()) }
            body.add(0x01); body.add(0x00)
            val extStart = body.size
            body.add(0x00); body.add(0x00)
            val sniExtLen = sniBytes.size + 5
            body.add((sniExtLen shr 8).toByte()); body.add(sniExtLen.toByte())
            body.add(((sniBytes.size + 3) shr 8).toByte()); body.add((sniBytes.size + 3).toByte())
            body.add(0x00)
            body.add((sniBytes.size shr 8).toByte()); body.add(sniBytes.size.toByte())
            sniBytes.forEach { body.add(it) }
            val extLen = body.size - extStart - 2
            body[extStart] = (extLen shr 8).toByte(); body[extStart + 1] = extLen.toByte()
            val bl = body.size
            val hs = mutableListOf<Byte>()
            hs.add(0x01); hs.add((bl shr 16).toByte()); hs.add((bl shr 8).toByte()); hs.add(bl.toByte())
            hs.addAll(body)
            val out = mutableListOf<Byte>()
            out.add(0x16); out.add(0x03); out.add(0x01)
            out.add((hs.size shr 8).toByte()); out.add(hs.size.toByte())
            out.addAll(hs)
            out.toByteArray()
        } catch (_: Throwable) { ByteArray(0) }
    }
    private fun sendFragmented(sock: Socket, data: ByteArray) {
        try {
            sock.tcpNoDelay = true
            var off = 0
            while (off < data.size) {
                val chunk = if (off == 0) 4 else 32
                val n = minOf(chunk, data.size - off)
                sock.getOutputStream().write(data, off, n)
                sock.getOutputStream().flush()
                off += n
                Thread.sleep(2)
            }
        } catch (_: Throwable) {}
    }
    private fun readTlsRecord(sock: Socket): ByteArray? {
        return try {
            val ins = sock.getInputStream()
            val hdr = ByteArray(5)
            var got = 0
            while (got < 5) {
                val r = ins.read(hdr, got, 5 - got)
                if (r <= 0) return null
                got += r
            }
            if (hdr[0] != 0x16.toByte()) return null
            val len = ((hdr[3].toInt() and 0xFF) shl 8) or (hdr[4].toInt() and 0xFF)
            val total = 5 + len
            val out = ByteArray(total)
            System.arraycopy(hdr, 0, out, 0, 5)
            var off = 5
            while (off < total) {
                val r = ins.read(out, off, total - off)
                if (r <= 0) break
                off += r
            }
            out.copyOf(off)
        } catch (_: Throwable) { null }
    }
    private fun relay(ins: java.io.InputStream, outs: java.io.OutputStream) {
        try {
            val buf = ByteArray(16384)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                outs.write(buf, 0, n)
                outs.flush()
            }
        } catch (_: Throwable) {}
    }
}
