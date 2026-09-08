package io.github.immaghzbad.aetherst.core

import android.os.ParcelFileDescriptor
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HevTun2SocksEngine {
    enum class State {
        IDLE,
        STARTING,
        RUNNING,
        PAUSED,
        STOPPING,
        STOPPED,
        FAILED
    }

    data class Stats(
        val txPackets: Long = 0,
        val txBytes: Long = 0,
        val rxPackets: Long = 0,
        val rxBytes: Long = 0
    )

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val dupLock = Any()
    private val active = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val currentAttemptId = AtomicLong(0)
    private var engineJob: Job? = null
    private var statsJob: Job? = null
    private var duplicatedFd: ParcelFileDescriptor? = null

    suspend fun start(
        tunPfd: ParcelFileDescriptor,
        socksAddress: String,
        socksPort: Int,
        mtu: Int,
        attemptId: Long,
        settings: HevEngineSettings = HevEngineSettings(),
        udpMode: String = "udp"
    ): Boolean {
        if (!active.compareAndSet(false, true)) {
            LogRepository.w("[Hev] [attempt=$attemptId] Start rejected; engine already active")
            return false
        }
        stopping.set(false)
        currentAttemptId.set(attemptId)
        _state.value = State.STARTING
        val started = CompletableDeferred<Boolean>()
        var localEngineJob: Job? = null
        try {
            lifecycleMutex.withLock {
                val duplicate = ParcelFileDescriptor.dup(tunPfd.fileDescriptor)
                duplicatedFd = duplicate
                val fd = duplicate.fd
                val config = HevTun2SocksConfig.generate(socksAddress, socksPort, mtu, settings, udpMode = udpMode)
                localEngineJob = scope.launch {
                    try {
                        LogRepository.i("[Hev] [attempt=$attemptId] Engine event loop starting")
                        val result = HevTun2SocksNative.nativeStart(config, fd)
                        val expected = stopping.get() || (_state.value == State.STOPPING)
                        if (currentAttemptId.get() == attemptId) {
                            if (expected) {
                                LogRepository.i("[Hev] [attempt=$attemptId] Engine event loop exited (Code: $result)")
                                _state.value = State.STOPPED
                            } else {
                                LogRepository.e("[Hev] [attempt=$attemptId] Native loop exited code=$result expected=false")
                                _state.value = State.FAILED
                            }
                        }
                    } catch (throwable: Throwable) {
                        if (currentAttemptId.get() == attemptId) {
                            LogRepository.e("[Hev] [attempt=$attemptId] Native loop failed: ${throwable.message}")
                            _state.value = if (stopping.get()) State.STOPPED else State.FAILED
                        }
                    } finally {
                        if (!started.isCompleted) {
                            started.complete(false)
                        }
                        closeDuplicatedFd(attemptId)
                        active.set(false)
                        stopStatsPolling()
                    }
                }
                engineJob = localEngineJob
                scope.launch {
                    delay(800.milliseconds)
                    if (!started.isCompleted) {
                        val running = localEngineJob?.isActive == true && currentAttemptId.get() == attemptId
                        if (running) {
                            _state.value = State.RUNNING
                            startStatsPolling(attemptId)
                            LogRepository.i("[Hev] [attempt=$attemptId] Engine status: ACTIVE")
                        }
                        started.complete(running)
                    }
                }
            }
            return withTimeoutOrNull(3.seconds) { started.await() } ?: false
        } catch (cancellation: CancellationException) {
            stopping.set(true)
            _state.value = State.STOPPING
            runCatching { HevTun2SocksNative.nativeStop() }
            withTimeoutOrNull(3.seconds) { localEngineJob?.join() }
            throw cancellation
        } catch (throwable: Throwable) {
            LogRepository.e("[Hev] [attempt=$attemptId] Startup failed: ${throwable.localizedMessage}")
            _state.value = State.FAILED
            if (localEngineJob?.isActive == true) {
                stopping.set(true)
                runCatching { HevTun2SocksNative.nativeStop() }
                withTimeoutOrNull(3.seconds) { localEngineJob?.join() }
            } else {
                closeDuplicatedFd(attemptId)
                active.set(false)
            }
            return false
        }
    }

    fun requestStop() {
        if (!active.get()) return
        stopping.set(true)
        if (_state.value == State.STARTING || _state.value == State.RUNNING || _state.value == State.PAUSED) {
            _state.value = State.STOPPING
        }
        stopStatsPolling()
        runCatching { HevTun2SocksNative.nativeStop() }
            .onFailure { LogRepository.w("[Hev] Stop request failed: ${it.localizedMessage}") }
    }

    fun pause() {
        if (!active.get()) return
        if (_state.value != State.RUNNING && _state.value != State.STARTING) return
        _state.value = State.PAUSED
        stopStatsPolling()
        runCatching { HevTun2SocksNative.nativePause() }
            .onFailure { LogRepository.w("[Hev] Pause request failed (nativePause unavailable): ${it.localizedMessage}") }
        LogRepository.i("[Hev] Engine paused (TUN interface kept open for fast resume)")
    }

    fun resume() {
        if (!active.get()) return
        if (_state.value != State.PAUSED) return
        _state.value = State.RUNNING
        startStatsPolling(currentAttemptId.get())
        runCatching { HevTun2SocksNative.nativeResume() }
            .onFailure { LogRepository.w("[Hev] Resume request failed (nativeResume unavailable): ${it.localizedMessage}") }
        LogRepository.i("[Hev] Engine resumed")
    }

    fun updateUpstream(host: String, port: Int) {
        if (!active.get()) return
        runCatching { HevTun2SocksNative.nativeUpdateUpstream(host, port) }
            .onFailure { LogRepository.w("[Hev] UpdateUpstream failed (nativeUpdateUpstream unavailable): ${it.localizedMessage}") }
    }

    private fun startStatsPolling(attemptId: Long) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && _state.value == State.RUNNING && currentAttemptId.get() == attemptId) {
                runCatching { HevTun2SocksNative.nativeGetStats() }
                    .getOrNull()
                    ?.takeIf { it.size >= 4 }
                    ?.let { values ->
                        _stats.value = Stats(values[0], values[1], values[2], values[3])
                    }
                delay(1.seconds)
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun closeDuplicatedFd(attemptId: Long) {
        val descriptor = synchronized(dupLock) {
            val value = duplicatedFd
            duplicatedFd = null
            value
        }
        if (descriptor != null) {
            runCatching { descriptor.close() }
                .onSuccess { LogRepository.i("[Hev] [attempt=$attemptId] Duplicated TUN descriptor closed") }
                .onFailure { LogRepository.w("[Hev] [attempt=$attemptId] Descriptor close failed: ${it.localizedMessage}") }
        }
    }

    val version: Int
        get() = if (HevTun2SocksNative.isAvailable) {
            runCatching { HevTun2SocksNative.nativeGetVersion() }.getOrDefault(-1)
        } else {
            -1
        }

    fun release() {
        requestStop()
        val job = engineJob
        if (job?.isActive == true) {
            scope.launch {
                job.join()
                scope.cancel()
            }
        } else {
            scope.cancel()
        }
    }
}
