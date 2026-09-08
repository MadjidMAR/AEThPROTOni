package io.github.immaghzbad.aetherst.shared.model

import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.AetherScanMode
import kotlinx.serialization.Serializable

@Serializable
enum class OnboardingStep {
    LANGUAGE_SELECT,
    WELCOME,
    PROTOCOL_TEST,
    VPN_PERMISSION,
    NOTIFICATION_PERMISSION,
    BATTERY_OPTIMIZATION,
    SUCCESS,
    COMPLETED
}

@Serializable
enum class ProtocolTestStatus {
    WAITING,
    PREPARING,
    REGISTERING,
    IDENTITY_READY,
    CONNECTED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}

@Serializable
sealed interface RegistrationResult {
    @Serializable
    @Suppress("unused")
    data object Success : RegistrationResult
    @Serializable
    @Suppress("unused")
    data object TimedOut : RegistrationResult
    @Serializable
    data class Failed(val reason: String) : RegistrationResult
    @Serializable
    @Suppress("unused")
    data object Cancelled : RegistrationResult
}

@Serializable
data class ProtocolAttemptResult(
    val protocol: AetherProtocol,
    val status: ProtocolTestStatus = ProtocolTestStatus.WAITING,
    val error: String? = null
)

@Serializable
data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val protocolResults: List<ProtocolAttemptResult> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val selectedScanMode: AetherScanMode = AetherScanMode.TURBO,
    val activeProtocol: AetherProtocol? = null,
    val vpnPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false,
    val isVerifyingPermission: Boolean = false,
    val permissionJustGranted: Boolean = false
)
