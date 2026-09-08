package io.github.immaghzbad.aetherst.core

import android.net.VpnService
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.RoutingMode
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LocalSocksProxyServer(
    private val vpnService: VpnService,
    private val listenHost: String = "127.0.0.1",
    private val listenPort: Int = 10808,
    private val targetHost: String = "127.0.0.1",
    private val targetPort: Int = 1819,
    private val routingEngine: RoutingEngine
) {
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = ThreadPoolExecutor(
        16, 512,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(1024),
        ThreadFactory { r ->
            Thread(r, "Aether-SockProxy-${r.hashCode().toString(16)}").apply { isDaemon = true }
        },
        ThreadPoolExecutor.CallerRunsPolicy()
    )
    private var mainThread: Thread? = null

    fun start() {
        if (isRunning.getAndSet(true)) return
        mainThread = Thread {
            try {
                serverSocket = ServerSocket(listenPort, 50, InetAddress.getByName(listenHost))
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

    private fun handleRelay(clientSocket: Socket) {
        var targetSocket: Socket? = null
        try {
            clientSocket.tcpNoDelay = true
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val header = ByteArray(2)
            if (readExact(clientIn, header) < 2) return
            if (header[0] != 0x05.toByte()) return
            val nMethods = header[1].toInt() and 0xFF
            val methods = ByteArray(nMethods)
            readExact(clientIn, methods)
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            val request = ByteArray(4)
            if (readExact(clientIn, request) < 4) return
            if (request[0] != 0x05.toByte()) return
            val cmd = request[1]
            val atyp = request[3]

            var finalTargetDomain: String?
            var finalTargetIp: String? = null
            var port = 0

            when (atyp) {
                0x01.toByte() -> {
                    val addr = ByteArray(4)
                    readExact(clientIn, addr)
                    val pBytes = ByteArray(2)
                    readExact(clientIn, pBytes)
                    port = ((pBytes[0].toInt() and 0xFF) shl 8) or (pBytes[1].toInt() and 0xFF)
                    finalTargetIp = InetAddress.getByAddress(addr).hostAddress
                    finalTargetDomain = DnsMap.get(finalTargetIp ?: "")
                }
                0x04.toByte() -> {
                    val addr = ByteArray(16)
                    readExact(clientIn, addr)
                    val pBytes = ByteArray(2)
                    readExact(clientIn, pBytes)
                    port = ((pBytes[0].toInt() and 0xFF) shl 8) or (pBytes[1].toInt() and 0xFF)
                    finalTargetIp = InetAddress.getByAddress(addr).hostAddress
                    finalTargetDomain = DnsMap.get(finalTargetIp ?: "")
                }
                0x03.toByte() -> {
                    val len = clientIn.read()
                    val domain = ByteArray(len)
                    readExact(clientIn, domain)
                    val pBytes = ByteArray(2)
                    readExact(clientIn, pBytes)
                    port = ((pBytes[0].toInt() and 0xFF) shl 8) or (pBytes[1].toInt() and 0xFF)
                    finalTargetDomain = String(domain)
                }
                else -> finalTargetDomain = null
            }

            val cachedDomain = DnsMap.get(finalTargetIp ?: "")
            val domainToMatch = finalTargetDomain ?: cachedDomain
            var decision = routingEngine.resolve(finalTargetIp ?: "", port, domainToMatch, null, null)

            var peekedData: ByteArray? = null
            var fakeSuccessSent = false

            if (cmd == 0x01.toByte() && decision.mode == RoutingMode.TUNNEL && domainToMatch == null && (port == 80 || port == 443)) {
                clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                fakeSuccessSent = true
                
                clientSocket.soTimeout = 1500
                val buffer = ByteArray(2048)
                val n = runCatching { clientIn.read(buffer) }.getOrDefault(-1)
                clientSocket.soTimeout = 0
                
                if (n > 0) {
                    val sniffed = TrafficSniffer.sniffDomain(buffer.copyOfRange(0, n), port)
                    if (sniffed != null) {
                        DnsMap.put(finalTargetIp ?: "", sniffed)
                        decision = routingEngine.resolve(
                            finalTargetIp ?: "",
                            port,
                            null,
                            if (port == 443) sniffed else null,
                            if (port == 80) sniffed else null
                        )
                        finalTargetDomain = sniffed
                    }
                    peekedData = buffer.copyOfRange(0, n)
                }
            }

            if (decision.mode == RoutingMode.BLOCK) {
                if (!fakeSuccessSent) {
                    clientOut.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                }
                clientSocket.close()
                return
            }

            if (decision.mode == RoutingMode.DIRECT) {
                try {
                    val directSocket = Socket()
                    runCatching { vpnService.protect(directSocket) }
                    directSocket.tcpNoDelay = true
                    directSocket.connect(InetSocketAddress(finalTargetIp ?: finalTargetDomain, port), 5000)

                    if (!fakeSuccessSent) {
                        clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                        clientOut.flush()
                    }

                    val dIn = directSocket.getInputStream()
                    val dOut = directSocket.getOutputStream()

                    if (peekedData != null) {
                        dOut.write(peekedData)
                        dOut.flush()
                    }

                    val t1 = Thread { pipe(dIn, clientOut) }
                    val t2 = Thread { pipe(clientIn, dOut) }
                    t1.start()
                    t2.start()
                    t1.join(300000)
                    t2.join(300000)
                    runCatching { directSocket.close() }
                } catch (e: Exception) {
                    LogRepository.w("[SockProxy] DIRECT relay failed target=${finalTargetDomain ?: finalTargetIp}:$port: ${e.localizedMessage}")
                }
                return
            }

            targetSocket = Socket()
            runCatching { vpnService.protect(targetSocket) }
            targetSocket.tcpNoDelay = true
            try {
                targetSocket.connect(InetSocketAddress(targetHost, targetPort), 5000)
            } catch (e: Exception) {
                LogRepository.e("[SockProxy] Failed to connect to upstream SOCKS5 $targetHost:$targetPort: ${e.message}")
                throw e
            }
            LogRepository.i("[SockProxy] Connected to upstream SOCKS5 $targetHost:$targetPort for target=${finalTargetDomain ?: finalTargetIp}:$port")

            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            targetOut.write(byteArrayOf(0x05, 0x01, 0x00))
            targetOut.flush()
            val authResponse = ByteArray(2)
            readExact(targetIn, authResponse)

            val modifiedRequest: ByteArray
            if (finalTargetDomain != null) {
                val db = finalTargetDomain.toByteArray()
                modifiedRequest = ByteArray(7 + db.size)
                modifiedRequest[0] = 0x05
                modifiedRequest[1] = cmd
                modifiedRequest[2] = 0x00
                modifiedRequest[3] = 0x03
                modifiedRequest[4] = db.size.toByte()
                System.arraycopy(db, 0, modifiedRequest, 5, db.size)
                modifiedRequest[5 + db.size] = (port shr 8).toByte()
                modifiedRequest[6 + db.size] = (port and 0xFF).toByte()
            } else {
                val ipStr = finalTargetIp ?: ""
                val ipBytes = InetAddress.getByName(ipStr).address
                modifiedRequest = ByteArray(6 + ipBytes.size)
                modifiedRequest[0] = 0x05
                modifiedRequest[1] = cmd
                modifiedRequest[2] = 0x00
                modifiedRequest[3] = if (ipBytes.size == 4) 0x01.toByte() else 0x04.toByte()
                System.arraycopy(ipBytes, 0, modifiedRequest, 4, ipBytes.size)
                modifiedRequest[4 + ipBytes.size] = (port shr 8).toByte()
                modifiedRequest[5 + ipBytes.size] = (port and 0xFF).toByte()
            }
            targetOut.write(modifiedRequest)
            targetOut.flush()

            val targetReplyHeader = ByteArray(4)
            if (readExact(targetIn, targetReplyHeader) < 4) return
            
            val bndAddr: ByteArray = when (targetReplyHeader[3]) {
                0x01.toByte() -> {
                    val b = ByteArray(6)
                    readExact(targetIn, b)
                    b
                }
                0x04.toByte() -> {
                    val b = ByteArray(18)
                    readExact(targetIn, b)
                    b
                }
                0x03.toByte() -> {
                    val len = targetIn.read()
                    val b = ByteArray(len + 2)
                    readExact(targetIn, b)
                    val full = ByteArray(1 + b.size)
                    full[0] = len.toByte()
                    System.arraycopy(b, 0, full, 1, b.size)
                    full
                }
                else -> ByteArray(0)
            }

            if (targetReplyHeader[1] == 0x00.toByte()) {
                LogRepository.i("[SockProxy] Upstream CONNECT granted target=${finalTargetDomain ?: finalTargetIp}:$port")
                if (!fakeSuccessSent) {
                    clientOut.write(targetReplyHeader)
                    clientOut.write(bndAddr)
                    clientOut.flush()
                }

                if (peekedData != null) {
                    targetOut.write(peekedData)
                    targetOut.flush()
                }

                val t1 = Thread { pipe(targetIn, clientOut) }
                val t2 = Thread { pipe(clientIn, targetOut) }
                t1.start()
                t2.start()
                t1.join(300000)
                t2.join(300000)
            } else {
                LogRepository.w("[SockProxy] Upstream CONNECT rejected code=0x%02X target=${finalTargetDomain ?: finalTargetIp}:$port".format(targetReplyHeader[1].toInt() and 0xFF))
                if (!fakeSuccessSent) {
                    clientOut.write(targetReplyHeader)
                    clientOut.write(bndAddr)
                    clientOut.flush()
                }
            }
        } catch (e: Exception) {
            LogRepository.w("[SockProxy] Relay failed: ${e.localizedMessage}")
        } finally {
            runCatching { targetSocket?.close() }
            runCatching { clientSocket.close() }
        }
    }

    private fun readExact(ins: InputStream, b: ByteArray): Int {
        var o = 0
        while (o < b.size) {
            val c = ins.read(b, o, b.size - o)
            if (c < 0) return o
            o += c
        }
        return o
    }

    private fun pipe(ins: InputStream, out: OutputStream) {
        try {
            val buffer = ByteArray(32768)
            while (isRunning.get()) {
                val n = ins.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
                out.flush()
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
