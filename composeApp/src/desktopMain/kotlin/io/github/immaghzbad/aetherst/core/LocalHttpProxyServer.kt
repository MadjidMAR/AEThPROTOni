package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.RoutingMode
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalHttpProxyServer(
    private val listenHost: String = "127.0.0.1",
    private val listenPort: Int = 10809,
    private val targetHost: String = "127.0.0.1",
    private val targetPort: Int = 1819,
    private val routingEngine: RoutingEngine
) {
    data class Stats(val txBytes: Long = 0, val rxBytes: Long = 0)

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var mainThread: Thread? = null
    private val txBytes = AtomicLong(0)
    private val rxBytes = AtomicLong(0)

    fun getStats(): Stats = Stats(txBytes.get(), rxBytes.get())

    fun start() {
        if (isRunning.getAndSet(true)) return
        mainThread = Thread {
            try {
                serverSocket = ServerSocket(listenPort, 50, java.net.InetAddress.getByName(listenHost))
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute { handleRelay(client) }
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
        mainThread?.start()
    }

    @Suppress("UNUSED_VARIABLE", "REDUNDANT_ELVIS_OPERATOR", "UNUSED_VALUE", "unused")
    private fun handleRelay(clientSocket: Socket) {
        var targetSocket: Socket? = null
        try {
            clientSocket.tcpNoDelay = true
            clientSocket.soTimeout = 30000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val requestBytes = readHttpRequestHeader(clientIn)
            if (requestBytes.isEmpty()) return

            val requestText = String(requestBytes, Charsets.ISO_8859_1)
            val requestLine = requestText.substringBefore("\r\n")
            val parts = requestLine.split(" ")
            if (parts.size < 3) return
            val method = parts[0].uppercase(Locale.ROOT)
            val target = parts[1]

            var isConnect = false
            var targetDomain: String? = null
            var remotePort: Int

            if (method == "CONNECT") {
                isConnect = true
                val authority = target.substringBefore("/")
                val hostPort = authority.split(":")
                targetDomain = hostPort[0]
                remotePort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
            } else {
                val url = try { java.net.URI(target) } catch (_: Exception) { null }
                targetDomain = url?.host
                remotePort = if (url != null && url.port != -1) url.port else if (url?.scheme == "https") 443 else 80
                if (targetDomain == null) {
                    val hostHeader = requestText.lines().firstOrNull { it.startsWith("Host:", true) }?.substringAfter(":")?.trim()?.substringBefore(":")?.trim()
                    if (!hostHeader.isNullOrEmpty()) targetDomain = hostHeader
                    if (targetDomain == null) return
                }
            }

            @Suppress("USELESS_ELVIS")
            val domain = targetDomain ?: return
            val cachedDomain = DnsMap.get(domain)
            val decision = routingEngine.resolve(
                domain,
                remotePort,
                cachedDomain ?: domain,
                if (isConnect) domain else null,
                if (!isConnect) domain else null
            )

            if (decision.mode == RoutingMode.BLOCK) {
                clientOut.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
                clientSocket.close()
                return
            }

            if (decision.mode == RoutingMode.DIRECT) {
                try {
                    val directSocket = Socket()
                    directSocket.tcpNoDelay = true
                    directSocket.connect(InetSocketAddress(targetDomain, remotePort), 5000)

                    if (isConnect) {
                        clientOut.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                        clientOut.flush()
                        runCatching { clientSocket.soTimeout = 0 }
                        val dIn = directSocket.getInputStream()
                        val dOut = directSocket.getOutputStream()
                        val t1 = Thread { pipe(dIn, clientOut, rxBytes) }
                        val t2 = Thread { pipe(clientIn, dOut, txBytes) }
                        t1.start()
                        t2.start()
                        t1.join(300000)
                        t2.join(300000)
                    } else {
                        val originBytes = toOriginForm(requestBytes)
                        directSocket.getOutputStream().write(originBytes)
                        directSocket.getOutputStream().flush()
                        txBytes.addAndGet(originBytes.size.toLong())
                        runCatching { clientSocket.soTimeout = 0 }
                        pipe(directSocket.getInputStream(), clientOut, rxBytes)
                    }
                    runCatching { directSocket.close() }
                } catch (_: Exception) {}
                return
            }

            targetSocket = Socket()
            targetSocket.tcpNoDelay = true
            targetSocket.soTimeout = 30000
            targetSocket.connect(InetSocketAddress(targetHost, this.targetPort), 5000)

            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            targetOut.write(byteArrayOf(0x05, 0x01, 0x00))
            targetOut.flush()
            val authResponse = ByteArray(2)
            readExact(targetIn, authResponse)

            val db = domain.toByteArray()
            val connectRequest = ByteArray(7 + db.size)
            connectRequest[0] = 0x05
            connectRequest[1] = 0x01
            connectRequest[2] = 0x00
            connectRequest[3] = 0x03
            connectRequest[4] = db.size.toByte()
            System.arraycopy(db, 0, connectRequest, 5, db.size)
            connectRequest[5 + db.size] = (remotePort shr 8).toByte()
            connectRequest[6 + db.size] = (remotePort and 0xFF).toByte()
            targetOut.write(connectRequest)
            targetOut.flush()

            val targetReplyHeader = ByteArray(4)
            if (readExact(targetIn, targetReplyHeader) < 4) return
            when (targetReplyHeader[3]) {
                0x01.toByte() -> {
                    val b = ByteArray(6)
                    readExact(targetIn, b)
                }
                0x04.toByte() -> {
                    val b = ByteArray(18)
                    readExact(targetIn, b)
                }
                0x03.toByte() -> {
                    val len = targetIn.read()
                    if (len >= 0) {
                        val b = ByteArray(len + 2)
                        readExact(targetIn, b)
                    }
                }
                else -> {}
            }

            if (targetReplyHeader[1] != 0x00.toByte()) {
                LogRepository.w("[HttpProxy] SOCKS connect to $targetDomain:$remotePort failed: ${targetReplyHeader[1].toInt()}")
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
                return
            }

            if (isConnect) {
                clientOut.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                clientOut.flush()
                runCatching { clientSocket.soTimeout = 0 }
                runCatching { targetSocket.soTimeout = 0 }
                val t1 = Thread { pipe(targetIn, clientOut, rxBytes) }
                val t2 = Thread { pipe(clientIn, targetOut, txBytes) }
                t1.start()
                t2.start()
                t1.join(300000)
                t2.join(300000)
            } else {
                runCatching { clientSocket.soTimeout = 0 }
                runCatching { targetSocket.soTimeout = 0 }
                val originBytes = toOriginForm(requestBytes)
                targetOut.write(originBytes)
                targetOut.flush()
                txBytes.addAndGet(originBytes.size.toLong())
                pipe(targetIn, clientOut, rxBytes)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (!msg.contains("timed out", true) && !msg.contains("Socket closed", true) && e !is java.net.SocketTimeoutException) {
                LogRepository.e("[HttpProxy] Relay error: $msg")
            }
        } finally {
            runCatching { targetSocket?.close() }
            runCatching { clientSocket.close() }
        }
    }

    private fun toOriginForm(requestBytes: ByteArray): ByteArray {
        try {
            val text = String(requestBytes, Charsets.ISO_8859_1)
            val headerEnd = text.indexOf("\r\n\r\n")
            if (headerEnd < 0) return requestBytes
            val header = text.substring(0, headerEnd)
            val lines = header.split("\r\n")
            if (lines.isEmpty()) return requestBytes
            val requestLine = lines[0]
            val lp = requestLine.split(" ")
            if (lp.size < 3) return requestBytes
            val method = lp[0]
            val target = lp[1]
            val version = lp[2]
            if (method.equals("CONNECT", true)) return requestBytes
            var newTarget = target
            try {
                val uri = java.net.URI(target)
                if (uri.scheme != null) {
                    var path = uri.rawPath
                    if (path.isNullOrEmpty()) path = "/"
                    val q = uri.rawQuery
                    newTarget = if (q != null) "$path?$q" else path
                }
            } catch (_: Exception) {}
            val sb = StringBuilder()
            sb.append(method).append(' ').append(newTarget).append(' ').append(version).append("\r\n")
            var hasConnection = false
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.startsWith("Proxy-Connection", true) || line.startsWith("Proxy-Authorization", true)) continue
                if (line.startsWith("Connection:", true)) {
                    sb.append("Connection: close\r\n")
                    hasConnection = true
                } else {
                    sb.append(line).append("\r\n")
                }
            }
            if (!hasConnection) sb.append("Connection: close\r\n")
            sb.append("\r\n")
            val headerBytes = sb.toString().toByteArray(Charsets.ISO_8859_1)
            val bodyStart = headerEnd + 4
            return if (requestBytes.size > bodyStart) {
                val body = requestBytes.copyOfRange(bodyStart, requestBytes.size)
                val out = ByteArray(headerBytes.size + body.size)
                System.arraycopy(headerBytes, 0, out, 0, headerBytes.size)
                System.arraycopy(body, 0, out, headerBytes.size, body.size)
                out
            } else headerBytes
        } catch (_: Exception) {
            return requestBytes
        }
    }

    private fun readHttpRequestHeader(ins: InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        var prev = -1
        var crlfCount = 0
        val maxBytes = 16384
        var total = 0
        while (total < maxBytes) {
            val b = try { ins.read() } catch (_: java.net.SocketTimeoutException) { break } catch (_: Exception) { break }
            if (b < 0) break
            buffer.write(b)
            total++
            if (prev == 13 && b == 10) {
                crlfCount++
                if (crlfCount == 2) break
            } else if (b != 10 && b != 13) {
                crlfCount = 0
            }
            prev = b
        }
        return buffer.toByteArray()
    }

    private fun readExact(ins: InputStream, b: ByteArray): Int {
        var o = 0
        while (o < b.size) {
            val c = try { ins.read(b, o, b.size - o) } catch (_: java.net.SocketTimeoutException) { return o } catch (_: Exception) { return o }
            if (c < 0) return o
            o += c
        }
        return o
    }

    private fun pipe(ins: InputStream, out: OutputStream, counter: AtomicLong) {
        try {
            val buffer = ByteArray(32768)
            while (isRunning.get()) {
                val n = ins.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
                out.flush()
                counter.addAndGet(n.toLong())
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        mainThread?.interrupt()
        mainThread = null
    }
}
