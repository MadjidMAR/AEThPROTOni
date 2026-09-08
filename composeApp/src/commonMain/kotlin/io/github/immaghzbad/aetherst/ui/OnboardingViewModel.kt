package io.github.immaghzbad.aetherst.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.shared.model.*
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.platform.getVpnController
import io.github.immaghzbad.aetherst.platform.isDesktop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.flow.update

class OnboardingViewModel(platformContext: PlatformContext) : ViewModel() {

    private val repository = AetherConfigRepository.getInstance(getSettings(platformContext))
    private var testJob: Job? = null
    private var verifyJob: Job? = null
    private val vpnController = getVpnController(platformContext)
    private val systemUtils = getSystemUtils(platformContext)

    private val _state = MutableStateFlow(
        OnboardingState(
            currentStep = normalizeStep(repository.getOnboardingStep().let { if (it == OnboardingStep.WELCOME) OnboardingStep.LANGUAGE_SELECT else it }),
            protocolResults = listOf(
                ProtocolAttemptResult(AetherProtocol.MASQUE),
                ProtocolAttemptResult(AetherProtocol.WG),
                ProtocolAttemptResult(AetherProtocol.GOOL)
            ),
            selectedScanMode = AetherScanMode.TURBO
        )
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private fun normalizeStep(step: OnboardingStep): OnboardingStep {
        if (!isDesktop) return step
        return when (step) {
            OnboardingStep.VPN_PERMISSION,
            OnboardingStep.NOTIFICATION_PERMISSION,
            OnboardingStep.BATTERY_OPTIMIZATION -> OnboardingStep.SUCCESS
            else -> step
        }
    }

    fun updateLanguage(langCode: String) {
        val current = repository.config.value
        repository.updateConfig(current.copy(appLanguage = langCode))
    }

    fun updateScanMode(mode: AetherScanMode) {
        if (_state.value.isProcessing) return
        _state.update { it.copy(selectedScanMode = mode) }
    }

    fun moveToNextStep() {
        val current = _state.value.currentStep
        val nextStep = when (current) {
            OnboardingStep.LANGUAGE_SELECT -> OnboardingStep.WELCOME
            OnboardingStep.WELCOME -> OnboardingStep.PROTOCOL_TEST
            OnboardingStep.PROTOCOL_TEST -> if (isDesktop) OnboardingStep.SUCCESS else OnboardingStep.VPN_PERMISSION
            OnboardingStep.VPN_PERMISSION -> OnboardingStep.NOTIFICATION_PERMISSION
            OnboardingStep.NOTIFICATION_PERMISSION -> OnboardingStep.BATTERY_OPTIMIZATION
            OnboardingStep.BATTERY_OPTIMIZATION -> OnboardingStep.SUCCESS
            OnboardingStep.SUCCESS -> OnboardingStep.COMPLETED
            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED
        }
        if (nextStep != current) {
            updateStep(nextStep)
        }
    }

    fun showNotificationError() {
        _state.update { it.copy(error = "Permission is required to proceed.") }
    }

    private fun updateStep(step: OnboardingStep) {
        _state.update { it.copy(currentStep = step, error = null) }

        viewModelScope.launch {
            try {
                repository.setOnboardingStep(step)
                if (step == OnboardingStep.COMPLETED) {
                    repository.setOnboardingComplete(complete = true)
                }
            } catch (_: Exception) {
                _state.update { it.copy(error = "Warning: Progress not saved locally.") }
            }
        }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(isVerifyingPermission = true, permissionJustGranted = false, error = null) }
        startPermissionVerification()
    }

    private fun startPermissionVerification() {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < 60) {
                delay(400)
                attempts++
                checkPermissionStatus()
                val s = _state.value
                when (s.currentStep) {
                    OnboardingStep.VPN_PERMISSION -> {
                        if (s.vpnPermissionGranted) {
                            _state.update { it.copy(isVerifyingPermission = false, permissionJustGranted = true) }
                            delay(1500)
                            _state.update { it.copy(permissionJustGranted = false) }
                            moveToNextStep()
                            return@launch
                        }
                    }
                    OnboardingStep.NOTIFICATION_PERMISSION -> {
                        if (s.notificationPermissionGranted) {
                            _state.update { it.copy(isVerifyingPermission = false, permissionJustGranted = true) }
                            delay(1500)
                            _state.update { it.copy(permissionJustGranted = false) }
                            moveToNextStep()
                            return@launch
                        }
                    }
                    OnboardingStep.BATTERY_OPTIMIZATION -> {
                        if (s.batteryOptimizationDisabled) {
                            _state.update { it.copy(isVerifyingPermission = false, permissionJustGranted = true) }
                            delay(1500)
                            _state.update { it.copy(permissionJustGranted = false) }
                            moveToNextStep()
                            return@launch
                        }
                    }
                    else -> return@launch
                }
            }
            _state.update { it.copy(isVerifyingPermission = false, error = "Action not completed. Please try again.") }
        }
    }

    fun checkPermissionStatus() {
        _state.update {
            it.copy(
                vpnPermissionGranted = vpnController.isVpnPrepared(),
                notificationPermissionGranted = systemUtils.isNotificationPermissionGranted(),
                batteryOptimizationDisabled = systemUtils.isBatteryOptimized()
            )
        }
    }

    fun cancelVerification() {
        verifyJob?.cancel()
        _state.update { it.copy(isVerifyingPermission = false) }
    }

    fun startProtocolTests() {
        if (_state.value.isProcessing) return
        _state.update { it.copy(isProcessing = true, error = null) }

        testJob = viewModelScope.launch {
            _state.value.protocolResults.forEach { result ->
                updateProtocolStatus(result.protocol, ProtocolTestStatus.PREPARING)
                delay(1.seconds)
                updateProtocolStatus(result.protocol, ProtocolTestStatus.CONNECTED)
                delay(500)
            }
            _state.update { it.copy(isProcessing = false) }
        }
    }

    fun cancelTests() {
        testJob?.cancel()
        _state.update { it.copy(isProcessing = false) }
        _state.value.protocolResults.forEach {
            if (it.status != ProtocolTestStatus.CONNECTED) {
                updateProtocolStatus(it.protocol, ProtocolTestStatus.CANCELLED)
            }
        }
    }

    private fun updateProtocolStatus(protocol: AetherProtocol, status: ProtocolTestStatus, error: String? = null) {
        _state.update { currentState ->
            val currentResults = currentState.protocolResults.map {
                if (it.protocol == protocol) it.copy(status = status, error = error) else it
            }
            currentState.copy(protocolResults = currentResults)
        }
    }
}