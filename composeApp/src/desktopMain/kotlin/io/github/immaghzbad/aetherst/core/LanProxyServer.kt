package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.RoutingMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * LAN Proxy Server — exposes SOCKS5 and HTTP proxy to other devices on the local network.
 *
 * This enables "hotspot sharing" on desktop: other devices connected to the same
 * WiFi/hotspot can route their traffic through this machine's proxy.
 *
 * The server binds to 0.0.0.0 on configurable ports and forwards connections to
 * the local SOCKS5/HTTP proxy that routes through the tunnel.
 */
class LanProxyServer(
    private val socksListenPort: Int = 10810,
    private val httpListenPort: Int = 10811,
    private val targetSocksHost: String = "127.0.0.1",
    private val targetSocksPort: Int = 10808,
    private val targetHttpHost: String = "127.0.0.1",
    private val targetHttpPort: Int = 10809
) {
    data class Stats(
        val socksConnections: Long = 0,
        val httpConnections: Long = 0,
        val txBytes: Long = 0,
        val rxBytes: Long = 0
    )

    private val isRunning = AtomicBoolean(false)
    private var socksServer: ServerSocket? = null
    private var httpServer: ServerSocket? = null
    private var socksThread: Thread? = null
    private var httpThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()
    private val socksConns = AtomicLong(0)
    private val httpConns = AtomicLong(0)
    private val txBytes = AtomicLong(0)
    private val rxBytes = AtomicLong(0)

    private val _lanIp = MutableStateFlow("")
    val lanIp: StateFlow<String> = _lanIp.asStateFlow()

    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status.asStateFlow()

    // ── Public API ──────────────────────────────────────────────────────

    fun getStats(): Stats = Stats(
        socksConnections = socksConns.get(),
        httpConnections = httpConns.get(),
        txBytes = txBytes.get(),
        rxBytes = rxBytes.get()
    )

    fun start(): Boolean {
        if (isRunning.getAndSet(true)) return true
        _status.value = "Starting…"

        return try {
            // Detect LAN IP
            val lanIp = detectLanIp()
            _lanIp.value = lanIp

            // Start SOCKS proxy
            socksServer = ServerSocket()
            socksServer!!.reuseAddress = true
            socksServer!!.bind(InetSocketAddress("0.0.0.0", socksListenPort))
            socksThread = Thread({
                LogRepository.i("[LanProxy] SOCKS listening on 0.0.0.0:$socksListenPort")
                while (isRunning.get()) {
                    try {
                        val client = socksServer?.accept() ?: break
                        socksConns.incrementAndGet()
                        executor.execute { handleSocksRelay(client) }
                    } catch (_: Exception) { break }
                }
            }, "LanSOCKS").apply { isDaemon = true; start() }

            // Start HTTP proxy
            httpServer = ServerSocket()
            httpServer!!.reuseAddress = true
            httpServer!!.bind(InetSocketAddress("0.0.0.0", httpListenPort))
            httpThread = Thread({
                LogRepository.i("[LanProxy] HTTP listening on 0.0.0.0:$httpListenPort")
                while (isRunning.get()) {
                    try {
                        val client = httpServer?.accept() ?: break
                        httpConns.incrementAndGet()
                        executor.execute { handleHttpRelay(client) }
                    } catch (_: Exception) { break }
                }
            }, "LanHTTP").apply { isDaemon = true; start() }

            _status.value = "Running"
            LogRepository.i("[LanProxy] Started – LAN IP: $lanIp, SOCKS: $socksListenPort, HTTP: $httpListenPort")
            true
        } catch (e: Exception) {
            LogRepository.e("[LanProxy] Start failed: ${e.message}")
            _status.value = "Error: ${e.message}"
            isRunning.set(false)
            stop()
            false
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        _status.value = "Stopping…"
        runCatching { socksServer?.close() }
        runCatching { httpServer?.close() }
        socksServer = null
        httpServer = null
        socksThread?.interrupt()
        httpThread?.interrupt()
        socksThread = null
        httpThread = null
        _status.value = "Stopped"
        LogRepository.i("[LanProxy] Stopped")
    }

    fun isActive(): Boolean = isRunning.get()

    // ── SOCKS5 relay ────────────────────────────────────────────────────

    private fun handleSocksRelay(client: Socket) {
        var target: Socket? = null
        try {
            client.tcpNoDelay = true
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // SOCKS5 handshake
            val header = ByteArray(2)
            if (readExact(clientIn, header) < 2) return
            if (header[0] != 0x05.toByte()) return
            val nMethods = header[1].toInt() and 0xFF
            val methods = ByteArray(nMethods)
            readExact(clientIn, methods)
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            // SOCKS5 request
            val request = ByteArray(4)
            if (readExact(clientIn, request) < 4) return
            if (request[0] != 0x05.toByte()) return
            val atyp = request[3]

            var addrBytes: ByteArray
            var portBytes: ByteArray

            when (atyp) {
                0x01.toByte() -> { // IPv4
                    addrBytes = ByteArray(4)
                    readExact(clientIn, addrBytes)
                    portBytes = ByteArray(2)
                    readExact(clientIn, portBytes)
                }
                0x03.toByte() -> { // Domain
                    val len = clientIn.read()
                    addrBytes = ByteArray(len)
                    readExact(clientIn, addrBytes)
                    portBytes = ByteArray(2)
                    readExact(clientIn, portBytes)
                }
                0x04.toByte() -> { // IPv6
                    addrBytes = ByteArray(16)
                    readExact(clientIn, addrBytes)
                    portBytes = ByteArray(2)
                    readExact(clientIn, portBytes)
                }
                else -> return
            }

            val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // Forward to local SOCKS5 proxy
            target = Socket()
            target.tcpNoDelay = true
            target.connect(InetSocketAddress(targetSocksHost, targetSocksPort), 5000)

            val targetIn = target.getInputStream()
            val targetOut = target.getOutputStream()

            // Send auth to local proxy
            targetOut.write(byteArrayOf(0x05, 0x01, 0x00))
            targetOut.flush()
            val authResp = ByteArray(2)
            readExact(targetIn, authResp)

            // Forward the original request to local proxy
            val fwdRequest = ByteArray(4 + addrBytes.size + 2)
            fwdRequest[0] = 0x05
            fwdRequest[1] = request[1] // CMD
            fwdRequest[2] = 0x00
            fwdRequest[3] = atyp
            System.arraycopy(addrBytes, 0, fwdRequest, 4, addrBytes.size)
            fwdRequest[4 + addrBytes.size] = portBytes[0]
            fwdRequest[5 + addrBytes.size] = portBytes[1]
            targetOut.write(fwdRequest)
            targetOut.flush()

            // Read response
            val respHeader = ByteArray(4)
            if (readExact(targetIn, respHeader) < 4) return
            val respAtyp = respHeader[3]
            val respBnd = when (respAtyp) {
                0x01.toByte() -> { val b = ByteArray(6); readExact(targetIn, b); b }
                0x04.toByte() -> { val b = ByteArray(18); readExact(targetIn, b); b }
                0x03.toByte() -> { val len = targetIn.read(); val b = ByteArray(len + 2); readExact(targetIn, b); val full = ByteArray(1 + b.size); full[0] = len.toByte(); System.arraycopy(b, 0, full, 1, b.size); full }
                else -> ByteArray(0)
            }

            // Send response to LAN client
            clientOut.write(respHeader)
            clientOut.write(respBnd)
            clientOut.flush()

            if (respHeader[1] != 0x00.toByte()) return

            // Bidirectional relay
            val t1 = Thread { pipe(targetIn, clientOut, rxBytes) }
            val t2 = Thread { pipe(clientIn, targetOut, txBytes) }
            t1.start(); t2.start()
            t1.join(300000); t2.join(300000)
        } catch (_: Exception) {
        } finally {
            runCatching { target?.close() }
            runCatching { client.close() }
        }
    }

    // ── HTTP relay ──────────────────────────────────────────────────────

    private fun handleHttpRelay(client: Socket) {
        var target: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = 30000
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // Read HTTP request header
            val headerBytes = readHttpHeader(clientIn)
            if (headerBytes.isEmpty()) return
            val headerText = String(headerBytes, Charsets.ISO_8859_1)
            val requestLine = headerText.substringBefore("\r\n")
            val parts = requestLine.split(" ")
            if (parts.size < 3) return
            val method = parts[0].uppercase()

            val isConnect = method == "CONNECT"
            val targetHost: String
            val targetPort: Int

            if (isConnect) {
                val auth = parts[1].substringBefore("/")
                val hp = auth.split(":")
                targetHost = hp[0]
                targetPort = hp.getOrNull(1)?.toIntOrNull() ?: 443
            } else {
                val url = try { java.net.URI(parts[1]) } catch (_: Exception) { null }
                targetHost = url?.host ?: headerText.lines().firstOrNull {
                    it.startsWith("Host:", true)
                }?.substringAfter(":")?.trim()?.substringBefore(":") ?: return
                targetPort = url?.let { if (it.port != -1) it.port else if (it.scheme == "https") 443 else 80 } ?: 80
            }

            // Forward to local HTTP proxy
            target = Socket()
            target.tcpNoDelay = true
            target.connect(InetSocketAddress(targetHttpHost, targetHttpPort), 5000)

            if (isConnect) {
                // Send CONNECT to local proxy
                val req = "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\n\r\n"
                target.getOutputStream().write(req.toByteArray())
                target.getOutputStream().flush()

                val resp = readHttpHeader(target.getInputStream())
                val respText = String(resp, Charsets.ISO_8859_1)
                if (respText.contains("200")) {
                    clientOut.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                    clientOut.flush()
                    client.soTimeout = 0
                    target.soTimeout = 0
                    val t1 = Thread { pipe(target!!.getInputStream(), clientOut, rxBytes) }
                    val t2 = Thread { pipe(clientIn, target.getOutputStream(), txBytes) }
                    t1.start(); t2.start()
                    t1.join(300000); t2.join(300000)
                } else {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                }
            } else {
                // Forward plain HTTP
                target.getOutputStream().write(headerBytes)
                target.getOutputStream().flush()
                client.soTimeout = 0
                pipe(target.getInputStream(), clientOut, rxBytes)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { target?.close() }
            runCatching { client.close() }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun readExact(ins: java.io.InputStream, b: ByteArray): Int {
        var o = 0
        while (o < b.size) {
            val c = ins.read(b, o, b.size - o)
            if (c < 0) return o
            o += c
        }
        return o
    }

    private fun readHttpHeader(ins: java.io.InputStream): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        var prev = -1; var crlfCount = 0; var total = 0
        while (total < 16384) {
            val b = try { ins.read() } catch (_: Exception) { break }
            if (b < 0) break
            buf.write(b); total++
            if (prev == 13 && b == 10) { crlfCount++; if (crlfCount == 2) break }
            else if (b != 10 && b != 13) crlfCount = 0
            prev = b
        }
        return buf.toByteArray()
    }

    private fun pipe(ins: java.io.InputStream, out: java.io.OutputStream, counter: AtomicLong) {
        try {
            val buffer = ByteArray(32768)
            while (isRunning.get()) {
                val n = ins.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
                out.flush()
                counter.addAndGet(n.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun detectLanIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                .map { it.hostAddress }
                .firstOrNull() ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
