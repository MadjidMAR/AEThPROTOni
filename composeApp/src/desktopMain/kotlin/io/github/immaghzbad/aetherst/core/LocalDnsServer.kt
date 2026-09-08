package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalDnsServer(
    private val listenHost: String = "127.0.0.1",
    private val listenPort: Int = 53,
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int = 1819,
    private val upstreamList: String = "1.1.1.1,1.0.0.1"
) {
    private val runningFlag = AtomicBoolean(false)
    private var udpSocket: DatagramSocket? = null
    private var tcpServer: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var udpThread: Thread? = null
    private var tcpThread: Thread? = null
    @Volatile private var port: Int = 0
    fun isRunning(): Boolean = runningFlag.get() && (udpSocket != null || tcpServer != null)
    fun getPort(): Int = port

    private fun upstreams(): List<InetSocketAddress> {
        return upstreamList.split(",").mapNotNull { s ->
            val t = s.trim()
            if (t.isEmpty()) return@mapNotNull null
            val host = t.substringBefore(":")
            val port = t.substringAfter(":", "53").toIntOrNull() ?: 53
            try { InetSocketAddress(host, port) } catch (_: Exception) { null }
        }.ifEmpty { listOf(InetSocketAddress("1.1.1.1", 53)) }
    }

    fun start() {
        if (runningFlag.getAndSet(true)) return
        try {
            udpSocket = DatagramSocket(null)
            udpSocket!!.reuseAddress = true
            udpSocket!!.bind(InetSocketAddress(listenHost, listenPort))
        } catch (e: Exception) {
            LogRepository.w("[DnsRelay] UDP bind failed on $listenPort: ${e.message}")
            runningFlag.set(false)
            return
        }
        try {
            tcpServer = ServerSocket(listenPort, 50, InetAddress.getByName(listenHost))
        } catch (e: Exception) {
            LogRepository.w("[DnsRelay] TCP bind failed on $listenPort: ${e.message}")
        }
        udpThread = Thread {
            val buf = ByteArray(4096)
            while (runningFlag.get()) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    udpSocket!!.receive(pkt)
                    val data = pkt.data.copyOfRange(0, pkt.length)
                    val clientAddr = pkt.address
                    val clientPort = pkt.port
                    executor.execute {
                        var resp = forwardViaSocksTcp(data)
                        if (resp == null) resp = forwardDirect(data)
                        if (resp != null) {
                            try {
                                val outPkt = DatagramPacket(resp, resp.size, clientAddr, clientPort)
                                udpSocket!!.send(outPkt)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    if (!runningFlag.get()) break
                }
            }
        }.apply { isDaemon = true; start() }
        tcpThread = Thread {
            while (runningFlag.get()) {
                try {
                    val client = tcpServer!!.accept()
                    executor.execute { handleTcpClient(client) }
                } catch (_: Exception) {
                    if (!runningFlag.get()) break
                }
            }
        }.apply { isDaemon = true; start() }
        LogRepository.i("[DnsRelay] started on $listenHost:$listenPort upstream=$upstreamList")
    }

    private fun handleTcpClient(client: Socket) {
        try {
            client.soTimeout = 5000
            val ins = client.getInputStream()
            val outs = client.getOutputStream()
            val lenBuf = ByteArray(2)
            if (ins.read(lenBuf) != 2) { client.close(); return }
            val len = ((lenBuf[0].toInt() and 0xFF) shl 8) or (lenBuf[1].toInt() and 0xFF)
            if (len <= 0 || len > 4096) { client.close(); return }
            val query = ByteArray(len)
            var off = 0
            while (off < len) {
                val r = ins.read(query, off, len - off)
                if (r <= 0) break
                off += r
            }
            if (off != len) { client.close(); return }
            var resp = forwardViaSocksTcp(query)
            if (resp == null) resp = forwardDirect(query)
            if (resp == null) { client.close(); return }
            outs.write((resp.size shr 8) and 0xFF)
            outs.write(resp.size and 0xFF)
            outs.write(resp)
            outs.flush()
            client.close()
        } catch (_: Exception) {
            try { client.close() } catch (_: Throwable) {}
        }
    }

    private fun forwardViaSocksTcp(query: ByteArray): ByteArray? {
        val ups = upstreams()
        for (upstream in ups) {
            var sock: Socket? = null
            try {
                sock = Socket()
                sock.tcpNoDelay = true
                sock.soTimeout = 5000
                sock.connect(InetSocketAddress(socksHost, socksPort), 3000)
                val sIn = sock.getInputStream()
                val sOut = sock.getOutputStream()
                sOut.write(byteArrayOf(0x05, 0x01, 0x00))
                sOut.flush()
                val auth = ByteArray(2)
                if (readExact(sIn, auth) != 2) continue
                if (auth[0] != 0x05.toByte() || auth[1] != 0x00.toByte()) continue
                val addr = upstream.address.address
                val req: ByteArray = if (addr.size == 4) {
                    val b = ByteArray(10)
                    b[0] = 0x05; b[1] = 0x01; b[2] = 0x00; b[3] = 0x01
                    System.arraycopy(addr, 0, b, 4, 4)
                    b[8] = (upstream.port shr 8).toByte(); b[9] = (upstream.port and 0xFF).toByte()
                    b
                } else {
                    val b = ByteArray(22)
                    b[0] = 0x05; b[1] = 0x01; b[2] = 0x00; b[3] = 0x04
                    System.arraycopy(addr, 0, b, 4, 16)
                    b[20] = (upstream.port shr 8).toByte(); b[21] = (upstream.port and 0xFF).toByte()
                    b
                }
                sOut.write(req)
                sOut.flush()
                val respHdr = ByteArray(4)
                if (readExact(sIn, respHdr) != 4) continue
                if (respHdr[1] != 0x00.toByte()) continue
                when (respHdr[3]) {
                    0x01.toByte() -> readExact(sIn, ByteArray(6))
                    0x04.toByte() -> readExact(sIn, ByteArray(18))
                    0x03.toByte() -> {
                        val l = sIn.read()
                        if (l >= 0) readExact(sIn, ByteArray(l + 2))
                    }
                    else -> {}
                }
                val qLen = query.size
                sOut.write((qLen shr 8) and 0xFF)
                sOut.write(qLen and 0xFF)
                sOut.write(query)
                sOut.flush()
                val rLenBuf = ByteArray(2)
                if (readExact(sIn, rLenBuf) != 2) continue
                val rLen = ((rLenBuf[0].toInt() and 0xFF) shl 8) or (rLenBuf[1].toInt() and 0xFF)
                if (rLen <= 0 || rLen > 4096) continue
                val resp = ByteArray(rLen)
                if (readExact(sIn, resp) != rLen) continue
                return resp
            } catch (_: Exception) {
            } finally {
                try { sock?.close() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun forwardDirect(query: ByteArray): ByteArray? {
        for (upstream in upstreams()) {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                sock.soTimeout = 3000
                val pkt = DatagramPacket(query, query.size, upstream.address, upstream.port)
                sock.send(pkt)
                val buf = ByteArray(4096)
                val respPkt = DatagramPacket(buf, buf.size)
                sock.receive(respPkt)
                return respPkt.data.copyOfRange(0, respPkt.length)
            } catch (_: Exception) {
            } finally {
                try { sock?.close() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun readExact(ins: java.io.InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = ins.read(buf, off, buf.size - off)
            if (r <= 0) return off
            off += r
        }
        return off
    }

    fun stop() {
        if (!runningFlag.getAndSet(false)) return
        try { udpSocket?.close() } catch (_: Throwable) {}
        try { tcpServer?.close() } catch (_: Throwable) {}
        udpSocket = null
        tcpServer = null
        udpThread?.interrupt()
        tcpThread?.interrupt()
        udpThread = null
        tcpThread = null
        LogRepository.i("[DnsRelay] stopped")
    }
}

