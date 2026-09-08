package io.github.immaghzbad.aetherst.core

import android.content.Context
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong

class AetherRegistrationRunner(private val context: Context) {

    private val lock = Any()
    private var process: Process? = null
    private var runnerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentAttemptId = AtomicLong(0)

    fun runTest(
        protocol: AetherProtocol,
        config: AetherConfig,
        onStatusUpdate: (ProtocolTestStatus) -> Unit,
        onComplete: (RegistrationResult) -> Unit,
    ) {
        synchronized(lock) {
            stop()
            val attemptId = currentAttemptId.incrementAndGet()
            runnerJob = scope.launch {
                try {
                    onStatusUpdate(ProtocolTestStatus.PREPARING)
                    val result = runProtocolBinary(protocol, config, attemptId, onStatusUpdate)
                    if (currentAttemptId.get() == attemptId) {
                        onComplete(result)
                    }
                } catch (e: CancellationException) {
                    if (currentAttemptId.get() == attemptId) {
                        onComplete(RegistrationResult.Cancelled)
                    }
                    throw e
                } catch (e: Exception) {
                    LogRepository.e("Registration error: ${e.localizedMessage}")
                    if (currentAttemptId.get() == attemptId) {
                        onComplete(RegistrationResult.Failed(e.localizedMessage ?: "Unknown error"))
                    }
                } finally {
                    stopInternal(attemptId)
                }
            }
        }
    }

    private suspend fun runProtocolBinary(
        protocol: AetherProtocol,
        config: AetherConfig,
        attemptId: Long,
        onStatusUpdate: (ProtocolTestStatus) -> Unit
    ): RegistrationResult {
        var proc: Process? = null
        return try {
            val binaryFile = BinaryManager.prepareBinary(context)
            if (currentAttemptId.get() != attemptId) return RegistrationResult.Cancelled

            val port = config.socksPort.toIntOrNull() ?: 1819
            val bindAddr = "${config.socksHost}:$port"

            val commandList = mutableListOf<String>()
            commandList.add(binaryFile.absolutePath)
            commandList.add("--bind")
            commandList.add(bindAddr)

            if (config.h2Mode) commandList.add("--h2")
            if (config.quickReconnect) commandList.add("--quick-reconnect") else commandList.add("--no-quick-reconnect")

            if ((protocol == AetherProtocol.WG) || (protocol == AetherProtocol.GOOL)) {
                commandList.add("--keepalive")
                commandList.add(if (config.keepaliveEnabled) config.keepalive.toString() else "0")
            }

            if (config.upstreamProxyEnabled && config.upstreamProxy.isNotEmpty()) {
                commandList.add("--upstream")
                commandList.add(config.upstreamProxy)
                if (config.upstreamProxy.startsWith("http://", ignoreCase = true)) commandList.add("--h2")
            }

            val pb = ProcessBuilder(commandList)
            pb.directory(context.filesDir)
            val env = pb.environment()
            env["AETHER_PROTOCOL"] = protocol.rawValue
            env["AETHER_SCAN"] = config.scanMode.rawValue
            env["AETHER_IP"] = config.ipMode.rawValue
            env["AETHER_NOIZE"] = config.noise.rawValue
            env["AETHER_SOCKS"] = bindAddr

            if (config.h2Mode) env["AETHER_MASQUE_HTTP2"] = "1"
            if (config.quickReconnect) env["AETHER_QUICK_RECONNECT"] = "1" else env["AETHER_QUICK_RECONNECT"] = "0"
            env["AETHER_WG_KEEPALIVE"] = if (config.keepaliveEnabled) config.keepalive.toString() else "0"
            env["AETHER_MASQUE_VALIDATE_SECS"] = config.validateSecs.toString()
            env["AETHER_WG_RECONNECT_SECS"] = config.reconnectSecs.toString()
            if (config.upstreamProxyEnabled && config.upstreamProxy.isNotEmpty()) env["AETHER_UPSTREAM"] = config.upstreamProxy
            env["AETHER_REPROVISION"] = if (config.reprovision) "1" else "0"

            pb.redirectErrorStream(true)

            LogRepository.i("Onboarding test: protocol=${protocol.name}, scan=${config.scanMode.name}, port=$port")

            proc = withContext(Dispatchers.IO) { pb.start() }
            synchronized(lock) {
                if (currentAttemptId.get() != attemptId) {
                    proc?.destroyForcibly()
                    return RegistrationResult.Cancelled
                }
                process = proc
            }

            var validated = false

            BufferedReader(InputStreamReader(proc!!.inputStream)).use { reader ->
                var line: String?
                while (currentAttemptId.get() == attemptId) {
                    line = try {
                        reader.readLine()
                    } catch (e: java.io.IOException) {
                        if (currentAttemptId.get() != attemptId) null else throw e
                    } ?: break

                    LogRepository.i(line, "AetherRegistration")
                    val lower = line.lowercase()

                    if (lower.contains("provisioned and saved") || lower.contains("identity ready")) {
                        onStatusUpdate(ProtocolTestStatus.IDENTITY_READY)
                        validated = true
                        break
                    } else if (lower.contains("enrolling")) {
                        onStatusUpdate(ProtocolTestStatus.REGISTERING)
                    }

                    if (lower.contains("fatal") || lower.contains("panic")) {
                        return RegistrationResult.Failed("Core fatal error")
                    }
                    if (lower.contains("error: api")) {
                        return RegistrationResult.Failed("API registration failure")
                    }
                    if (lower.contains("address already in use")) {
                        return RegistrationResult.Failed("Port $port is busy")
                    }
                }
            }

            if (validated) {
                onStatusUpdate(ProtocolTestStatus.CONNECTED)
                RegistrationResult.Success
            } else {
                RegistrationResult.Failed("Handshake incomplete")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RegistrationResult.Failed(e.localizedMessage ?: "Process failed")
        } finally {
            synchronized(lock) {
                if (process === proc) process = null
            }
            try { proc?.destroyForcibly() } catch (_: Exception) {}
        }
    }

    private fun stopInternal(attemptId: Long) {
        synchronized(lock) {
            if (currentAttemptId.get() == attemptId) {
                process?.destroyForcibly()
                process = null
                runnerJob = null
            }
        }
    }

    fun stop() {
        currentAttemptId.incrementAndGet()
        val jobToCancel: Job?
        val procToDestroy: Process?
        synchronized(lock) {
            jobToCancel = runnerJob
            procToDestroy = process
            runnerJob = null
            process = null
        }
        jobToCancel?.cancel()
        try { procToDestroy?.destroyForcibly() } catch (_: Exception) {}
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
