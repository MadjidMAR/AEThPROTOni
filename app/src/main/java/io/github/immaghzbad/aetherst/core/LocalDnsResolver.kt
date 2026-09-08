package io.github.immaghzbad.aetherst.core

import android.net.VpnService
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class LocalDnsResolver(
    private val vpnService: VpnService,
    @Volatile private var socksHost: String,
    @Volatile private var socksPort: Int
) {
    fun updateUpstream(host: String, port: Int) {
        socksHost = host
        socksPort = port
    }
    private val closed = AtomicBoolean(false)

    fun resolve(query: ByteArray, serverIp: String): ByteArray? {
        if (closed.get()) return null
        if (!isUsableQuery(query)) return null
        return resolveViaSocks(query, serverIp) ?: resolveDirect(query, serverIp)
    }

    fun shutdown() {
        closed.set(true)
    }

    private fun isUsableQuery(data: ByteArray): Boolean {
        if (data.size < 17) return false
        if (data[2].toInt() and 0x80 != 0) return false
        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        return qdCount in 1..8
    }

    private fun resolveViaSocks(query: ByteArray, serverIp: String): ByteArray? {
        if (closed.get()) return null
        val address = runCatching { InetAddress.getByName(serverIp) }.getOrNull() ?: return null
        val socket = Socket()
        runCatching { vpnService.protect(socket) }
        return try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(socksHost, socksPort), 3000)
            val ins = socket.getInputStream()
            val out = socket.getOutputStream()
            out.write(byteArrayOf(5, 1, 0))
            out.flush()
            val greeting = ByteArray(2)
            if (!readExact(ins, greeting) || greeting[0] != 5.toByte() || greeting[1] != 0.toByte()) return null
            out.write(connectRequest(address))
            out.flush()
            if (!readSocksReply(ins)) return null
            exchange(socket, ins, out, query)
        } catch (e: Exception) {
            LogRepository.w("[DnsResolver] SOCKS resolve failed server=$serverIp: ${e.localizedMessage}")
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun resolveDirect(query: ByteArray, serverIp: String): ByteArray? {
        if (closed.get()) return null
        val address = runCatching { InetAddress.getByName(serverIp) }.getOrNull() ?: return null
        val socket = Socket()
        runCatching { vpnService.protect(socket) }
        return try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, 53), 3000)
            exchange(socket, socket.getInputStream(), socket.getOutputStream(), query)
        } catch (e: Exception) {
            LogRepository.w("[DnsResolver] Direct resolve failed server=$serverIp: ${e.localizedMessage}")
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun connectRequest(address: InetAddress): ByteArray {
        val ip = address.address
        val req = ByteArray(6 + ip.size)
        req[0] = 5
        req[1] = 1
        req[3] = if (ip.size == 4) 1 else 4
        System.arraycopy(ip, 0, req, 4, ip.size)
        req[4 + ip.size] = 0
        req[5 + ip.size] = 53
        return req
    }

    private fun readSocksReply(ins: InputStream): Boolean {
        val header = ByteArray(4)
        if (!readExact(ins, header)) return false
        if (header[0] != 5.toByte() || header[1] != 0.toByte()) return false
        return when (header[3].toInt() and 0xFF) {
            1 -> readExact(ins, ByteArray(6))
            4 -> readExact(ins, ByteArray(18))
            3 -> {
                val len = ByteArray(1)
                if (!readExact(ins, len)) false else readExact(ins, ByteArray((len[0].toInt() and 0xFF) + 2))
            }
            else -> false
        }
    }

    private fun exchange(socket: Socket, ins: InputStream, out: OutputStream, query: ByteArray): ByteArray? {
        return try {
            socket.soTimeout = 4000
            out.write(byteArrayOf((query.size shr 8).toByte(), (query.size and 0xFF).toByte()))
            out.write(query)
            out.flush()
            val lenHeader = ByteArray(2)
            if (!readExact(ins, lenHeader)) return null
            val len = ((lenHeader[0].toInt() and 0xFF) shl 8) or (lenHeader[1].toInt() and 0xFF)
            if (len < 12) return null
            val response = ByteArray(len)
            if (!readExact(ins, response)) return null
            response
        } catch (e: Exception) {
            LogRepository.w("[DnsResolver] DNS exchange failed: ${e.localizedMessage}")
            null
        }
    }

    private fun readExact(ins: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = ins.read(buffer, offset, buffer.size - offset)
            if (n <= 0) return false
            offset += n
        }
        return true
    }
}
