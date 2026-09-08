package io.github.immaghzbad.aetherst.shared.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request

object PingRepository {
    private val _pingState = MutableStateFlow(PingState())
    val pingState: StateFlow<PingState> = _pingState.asStateFlow()

    private val mutex = kotlinx.coroutines.sync.Mutex()

    private const val TCP_TIMEOUT_MS = 3000
    private const val HTTP_TIMEOUT_MS = 5000

    suspend fun runPing(
        socksHost: String,
        socksPort: Int,
        useProxy: Boolean,
        pingUrl: String
    ) {
        if (!mutex.tryLock()) return
        try {
            _pingState.value = _pingState.value.copy(isPinging = true, error = null)
            val result = withContext(Dispatchers.IO) {
                val url = pingUrl.trim().ifEmpty { "https://www.gstatic.com/generate_204" }
                val resolvedHost = socksHost.trim().ifEmpty { "127.0.0.1" }
                val resolvedPort = if (socksPort in 1..65535) socksPort else 1819
                val target = parseTarget(url)
                val tcpMs = measureTcpConnect(target.host, target.port, resolvedHost, resolvedPort, useProxy)
                if (tcpMs < 0) {
                    return@withContext -1L to "Tcping failed"
                }
                val httpMs = measureHttpWarm(url, resolvedHost, resolvedPort, useProxy)
                if (httpMs < 0) {
                    return@withContext -1L to "HTTP 204 failed"
                }
                httpMs to null
            }
            if (result.first >= 0) {
                _pingState.value = PingState(ms = result.first, isPinging = false)
                LogRepository.i("Ping: ${result.first}ms via $pingUrl (tcp ${result.first}ms)", "Ping")
            } else {
                _pingState.value = PingState(ms = -1, isPinging = false, error = "Timeout")
                LogRepository.w("Ping failed: ${result.second}", "Ping")
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun parseTarget(url: String): Target {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: "www.gstatic.com"
            val port = when {
                uri.port != -1 -> uri.port
                uri.scheme == "https" -> 443
                else -> 80
            }
            Target(host, port)
        } catch (_: Exception) {
            Target("www.gstatic.com", 443)
        }
    }

    private fun measureTcpConnect(
        host: String,
        port: Int,
        socksHost: String,
        socksPort: Int,
        useProxy: Boolean
    ): Long {
        return try {
            val proxy = if (useProxy) Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort)) else Proxy.NO_PROXY
            val socket = if (useProxy) Socket(proxy) else Socket()
            socket.use { s ->
                s.soTimeout = TCP_TIMEOUT_MS
                val start = System.nanoTime()
                s.connect(InetSocketAddress.createUnresolved(host, port), TCP_TIMEOUT_MS)
                (System.nanoTime() - start) / 1_000_000
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun measureHttpWarm(
        urlString: String,
        socksHost: String,
        socksPort: Int,
        useProxy: Boolean
    ): Long {
        val proxy = if (useProxy) Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort)) else Proxy.NO_PROXY
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(HTTP_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(HTTP_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(HTTP_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
        return try {
            val warmReq = Request.Builder().url(urlString).get().header("Connection", "keep-alive").build()
            try {
                client.newCall(warmReq).execute().use { resp ->
                    resp.body?.close()
                }
            } catch (_: Exception) {
            }
            val req = Request.Builder().url(urlString).get().header("Connection", "keep-alive").build()
            val start = System.nanoTime()
            val code = client.newCall(req).execute().use { resp ->
                resp.body?.close()
                resp.code
            }
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (code == 204 || code == 200) elapsed else -1
        } catch (_: Exception) {
            -1
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    fun reset() {
        _pingState.value = PingState()
    }

    private data class Target(val host: String, val port: Int)
}
