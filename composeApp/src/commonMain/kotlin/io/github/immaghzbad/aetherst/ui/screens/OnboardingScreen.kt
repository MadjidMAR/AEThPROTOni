package io.github.immaghzbad.aetherst.shared.ui.screens
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.model.*
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onGetStarted: () -> Unit,
    onRetryRegistration: () -> Unit,
    onCancelRegistration: () -> Unit,
    onUpdateScanMode: (AetherScanMode) -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onFinish: () -> Unit,
    appLanguage: String = "auto",
    onSelectLanguage: (String) -> Unit = {},
    onConfirmLanguage: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)
        val horizontalPadding = (24 * scaleFactor).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = (24 * scaleFactor).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            OnboardingHeader(scaleFactor)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    label = "step_transition"
                ) { step ->
                    when (step) {
                        OnboardingStep.LANGUAGE_SELECT -> LanguageSelectionStep(appLanguage, onSelectLanguage, onConfirmLanguage, scaleFactor)
                        OnboardingStep.WELCOME -> WelcomeStep(onGetStarted, scaleFactor)
                        OnboardingStep.PROTOCOL_TEST -> ProtocolTestStep(
                            state,
                            onRetryRegistration,
                            onCancelRegistration,
                            onUpdateScanMode,
                            onFinish,
                            scaleFactor
                        )
                        OnboardingStep.VPN_PERMISSION -> VpnPermissionStep(state, onRequestVpnPermission, scaleFactor)
                        OnboardingStep.NOTIFICATION_PERMISSION -> NotificationPermissionStep(state, onRequestNotificationPermission, scaleFactor)
                        OnboardingStep.BATTERY_OPTIMIZATION -> BatteryOptimizationStep(state, onRequestBatteryOptimization, onFinish, scaleFactor)
                        OnboardingStep.SUCCESS -> SuccessStep(onFinish, scaleFactor)
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }

            OnboardingFooter(state.currentStep, scaleFactor)
        }
    }
}

@Composable
private fun OnboardingHeader(scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val slogans = listOf(
        strings.ONBOARDING_SLOGAN_PRIVACY,
        strings.ONBOARDING_SLOGAN_BEYOND,
        strings.ONBOARDING_SLOGAN_INVISIBLE,
        strings.ONBOARDING_SLOGAN_FUTURE,
        strings.ONBOARDING_SLOGAN_SHIELD,
        strings.ONBOARDING_SLOGAN_ENCRYPTION,
        strings.ONBOARDING_SLOGAN_CENSORSHIP,
        strings.ONBOARDING_SLOGAN_SECURE_FREE,
        strings.ONBOARDING_SLOGAN_INSTANT,
        strings.ONBOARDING_SLOGAN_FREEDOM,
        strings.ONBOARDING_SLOGAN_PROXY,
        strings.ONBOARDING_SLOGAN_TRACKING,
        strings.ONBOARDING_SLOGAN_GLOBAL,
        strings.ONBOARDING_SLOGAN_RELIABLE,
        strings.ONBOARDING_SLOGAN_OPEN_INTERNET,
        strings.ONBOARDING_SLOGAN_LOW_LATENCY,
        strings.ONBOARDING_SLOGAN_COMPANION,
        strings.ONBOARDING_SLOGAN_FAST_SECURE
    )
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000.milliseconds)
            index = (index + 1) % slogans.size
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height((48 * scaleFactor).dp))
        Text(
            text = "AetherST",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = (32 * scaleFactor).sp
        )
        Box(modifier = Modifier.height((24 * scaleFactor).dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = slogans[index],
                transitionSpec = {
                    (slideInVertically { it } + fadeIn(tween(600))) togetherWith
                            (slideOutVertically { -it } + fadeOut(tween(600)))
                },
                label = "slogan_animation"
            ) { slogan ->
                Text(
                    text = slogan,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppPalette.textSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = (14 * scaleFactor).sp
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectionStep(appLanguage: String, onSelectLanguage: (String) -> Unit, onConfirm: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val options = listOf(
        "auto" to strings.LANGUAGE_AUTO,
        "en" to strings.LANGUAGE_ENGLISH,
        "fa" to strings.LANGUAGE_PERSIAN
    )
    val descs = listOf(
        strings.ONBOARDING_LANGUAGE_AUTO_DESC,
        "English",
        "فارسی"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = strings.ONBOARDING_LANGUAGE_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = (26 * scaleFactor).sp
        )
        Spacer(modifier = Modifier.height((12 * scaleFactor).dp))
        Text(
            text = strings.ONBOARDING_LANGUAGE_SUBTITLE,
            style = MaterialTheme.typography.bodyMedium,
            color = AppPalette.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = (14 * scaleFactor).sp
        )
        Spacer(modifier = Modifier.height((32 * scaleFactor).dp))
        Column(verticalArrangement = Arrangement.spacedBy((10 * scaleFactor).dp)) {
            options.forEachIndexed { idx, (code, label) ->
                val selected = appLanguage == code
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) AppPalette.accent.copy(alpha = 0.15f) else AppPalette.surfaceRaised,
                    border = if (selected) BorderStroke(1.5.dp, AppPalette.accent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectLanguage(code) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = (16 * scaleFactor).dp, vertical = (14 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size((20 * scaleFactor).dp).clip(CircleShape).background(if (selected) AppPalette.accent else Color.Transparent).border(1.5.dp, if (selected) AppPalette.accent else AppPalette.textSecondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Box(modifier = Modifier.size((10 * scaleFactor).dp).clip(CircleShape).background(Color.White))
                        }
                        Spacer(modifier = Modifier.width((14 * scaleFactor).dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = if (selected) Color.White else AppPalette.textSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = (15 * scaleFactor).sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(descs[idx], color = AppPalette.textSecondary, fontSize = (11 * scaleFactor).sp)
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, null, tint = AppPalette.accent, modifier = Modifier.size((20 * scaleFactor).dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height((28 * scaleFactor).dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height((54 * scaleFactor).dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent, contentColor = Color.White)
        ) {
            Text(strings.ONBOARDING_LANGUAGE_CONFIRM, fontWeight = FontWeight.Bold, fontSize = (16 * scaleFactor).sp)
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.ONBOARDING_WELCOME,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = (28 * scaleFactor).sp
        )
        Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
        Text(
            text = strings.ONBOARDING_WELCOME_DESC,
            style = MaterialTheme.typography.bodyLarge,
            color = AppPalette.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = (16 * scaleFactor).sp
        )
        Spacer(modifier = Modifier.height((48 * scaleFactor).dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height((56 * scaleFactor).dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent, contentColor = Color.White)
        ) {
            Text(strings.ONBOARDING_GET_STARTED, fontSize = (18 * scaleFactor).sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun ProtocolTestStep(
    state: OnboardingState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onUpdateScanMode: (AetherScanMode) -> Unit,
    onContinue: () -> Unit,
    scaleFactor: Float
) {
    val allowedModes = listOf(AetherScanMode.TURBO, AetherScanMode.BALANCED, AetherScanMode.STEALTH, AetherScanMode.IRONCLAD)
    val allDone = !state.isProcessing && state.protocolResults.all {
        it.status == ProtocolTestStatus.CONNECTED ||
        it.status == ProtocolTestStatus.FAILED ||
        it.status == ProtocolTestStatus.TIMED_OUT ||
        it.status == ProtocolTestStatus.CANCELLED
    }
    val anySuccess = state.protocolResults.any { it.status == ProtocolTestStatus.CONNECTED }

    val strings = LocalAppStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = strings.ONBOARDING_PREPARE,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = (22 * scaleFactor).sp
        )
        Spacer(modifier = Modifier.height((24 * scaleFactor).dp))

        SelectorLabel(scaleFactor)
        AetherScanModeSelector(
            selected = state.selectedScanMode,
            allowedModes = allowedModes,
            enabled = !state.isProcessing,
            onSelect = onUpdateScanMode,
            scaleFactor = scaleFactor
        )

        Spacer(modifier = Modifier.height((32 * scaleFactor).dp))

        state.protocolResults.forEach { result ->
            ProtocolRow(result.protocol.displayName, result.status, state.activeProtocol == result.protocol, scaleFactor)
            Spacer(modifier = Modifier.height((12 * scaleFactor).dp))
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
            Text(text = state.error, color = AppPalette.statusError, fontSize = (12 * scaleFactor).sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height((32 * scaleFactor).dp))

        if (state.isProcessing) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette.divider, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height((56 * scaleFactor).dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(strings.ONBOARDING_CANCEL_TEST, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (16 * scaleFactor).sp)
            }
        } else if (allDone && anySuccess) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height((56 * scaleFactor).dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette.statusConnected, contentColor = Color.White)
            ) {
                Text(strings.ONBOARDING_CONTINUE, fontWeight = FontWeight.Bold, color = Color.White, fontSize = (16 * scaleFactor).sp)
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height((56 * scaleFactor).dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent, contentColor = Color.White)
            ) {
                Text(
                    text = if (state.error != null) strings.ONBOARDING_TRY_AGAIN else strings.ONBOARDING_START_CONNECTION_TEST,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (16 * scaleFactor).sp
                )
            }
        }
    }
}

@Composable
private fun SelectorLabel(scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Text(
        text = strings.ONBOARDING_SCAN_MODE,
        style = MaterialTheme.typography.labelSmall,
        color = AppPalette.textSecondary,
        fontSize = (11 * scaleFactor).sp,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = (8 * scaleFactor).dp)
    )
}

@Composable
private fun AetherScanModeSelector(
    selected: AetherScanMode,
    allowedModes: List<AetherScanMode>,
    enabled: Boolean,
    onSelect: (AetherScanMode) -> Unit,
    scaleFactor: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppPalette.surfaceRaised).padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        allowedModes.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) AppPalette.accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(mode) }
                    .padding(vertical = (8 * scaleFactor).dp),
                contentAlignment = Alignment.Center
            ) {
                val label = when(mode) {
                    AetherScanMode.TURBO -> "Turbo"
                    AetherScanMode.BALANCED -> "Balanced"
                    AetherScanMode.STEALTH -> "Stealth"
                    AetherScanMode.IRONCLAD -> "Ironclad"
                    else -> mode.name
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else AppPalette.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = (12 * scaleFactor).sp
                )
            }
        }
    }
}

@Composable
private fun ProtocolRow(name: String, status: ProtocolTestStatus, isActive: Boolean, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Surface(
        color = AppPalette.surfaceRaised,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding((16 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (16 * scaleFactor).sp)
                if (isActive) {
                    Text(
                        text = when (status) {
                            ProtocolTestStatus.PREPARING -> strings.ONBOARDING_PREPARING_ENGINE
                            ProtocolTestStatus.REGISTERING -> strings.ONBOARDING_REGISTERING
                            ProtocolTestStatus.IDENTITY_READY -> strings.ONBOARDING_IDENTITY_VERIFIED
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppPalette.accent,
                        fontSize = (11 * scaleFactor).sp
                    )
                }
            }

            when (status) {
                ProtocolTestStatus.WAITING -> Text(strings.ONBOARDING_WAITING, color = AppPalette.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = (11 * scaleFactor).sp)
                ProtocolTestStatus.CONNECTED -> Icon(Icons.Default.CheckCircle, null, tint = AppPalette.statusConnected, modifier = Modifier.size((20 * scaleFactor).dp))
                ProtocolTestStatus.FAILED, ProtocolTestStatus.TIMED_OUT -> Icon(Icons.Default.Error, null, tint = AppPalette.statusError, modifier = Modifier.size((20 * scaleFactor).dp))
                ProtocolTestStatus.CANCELLED -> Text(strings.ONBOARDING_CANCELLED, color = AppPalette.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = (11 * scaleFactor).sp)
                else -> CircularProgressIndicator(modifier = Modifier.size((20 * scaleFactor).dp), strokeWidth = 2.dp, color = AppPalette.accent)
            }
        }
    }
}

@Composable
private fun PermissionStepWrapper(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    buttonLabel: String,
    state: OnboardingState,
    onRequest: () -> Unit,
    onSkip: (() -> Unit)?,
    scaleFactor: Float
) {
    val isVerifying = state.isVerifyingPermission
    val justGranted = state.permissionJustGranted
    val strings = LocalAppStrings.current

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size((72 * scaleFactor).dp).clip(CircleShape).background(AppPalette.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.height((24 * scaleFactor).dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = (24 * scaleFactor).sp)
        Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = AppPalette.textSecondary, textAlign = TextAlign.Center, fontSize = (14 * scaleFactor).sp)
        Spacer(modifier = Modifier.height((40 * scaleFactor).dp))
        when {
            justGranted -> GrantedIndicator(scaleFactor)
            isVerifying -> VerifyingIndicator(scaleFactor)
            else -> {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height((56 * scaleFactor).dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent, contentColor = Color.White)) {
                    Text(if (state.error != null) strings.ONBOARDING_TRY_AGAIN else buttonLabel, fontWeight = FontWeight.Bold, color = Color.White, fontSize = (16 * scaleFactor).sp)
                }
            }
        }
        if (state.error != null && !isVerifying && !justGranted) {
            Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
            Text(state.error, color = AppPalette.statusScanning, fontSize = (13 * scaleFactor).sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        }
        if (onSkip != null && !isVerifying && !justGranted) {
            Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
            TextButton(onClick = onSkip) { Text(strings.ONBOARDING_NOT_NOW, color = Color.White, fontSize = (14 * scaleFactor).sp) }
        }
    }
}

@Composable
private fun GrantedIndicator(scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppPalette.statusConnected, modifier = Modifier.size((56 * scaleFactor).dp))
        Spacer(modifier = Modifier.height((20 * scaleFactor).dp))
        Text(strings.ONBOARDING_ACCESS_GRANTED, style = MaterialTheme.typography.bodyLarge, color = AppPalette.statusConnected, fontWeight = FontWeight.SemiBold, fontSize = (16 * scaleFactor).sp)
        Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
        Text(strings.ONBOARDING_CONTINUING, style = MaterialTheme.typography.bodySmall, color = AppPalette.textSecondary, textAlign = TextAlign.Center, fontSize = (13 * scaleFactor).sp)
    }
}

@Composable
private fun VerifyingIndicator(scaleFactor: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "verifying")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "pulse_alpha")
    val scale by infiniteTransition.animateFloat(initialValue = 0.85f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse_scale")

    val strings = LocalAppStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size((56 * scaleFactor).dp).graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale).clip(CircleShape).background(AppPalette.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size((28 * scaleFactor).dp), strokeWidth = 3.dp, color = AppPalette.accent)
        }
        Spacer(modifier = Modifier.height((20 * scaleFactor).dp))
        Text(strings.ONBOARDING_VERIFYING, style = MaterialTheme.typography.bodyLarge, color = AppPalette.accent, fontWeight = FontWeight.SemiBold, fontSize = (16 * scaleFactor).sp)
        Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
        Text(strings.ONBOARDING_VERIFY_DESC, style = MaterialTheme.typography.bodySmall, color = AppPalette.textSecondary, textAlign = TextAlign.Center, fontSize = (13 * scaleFactor).sp)
    }
}

@Composable
private fun VpnPermissionStep(state: OnboardingState, onRequest: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    PermissionStepWrapper(icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AppPalette.accent, modifier = Modifier.size((32 * scaleFactor).dp)) }, title = strings.ONBOARDING_ALLOW_VPN_TITLE, description = strings.ONBOARDING_ALLOW_VPN_DESC, buttonLabel = strings.ONBOARDING_ALLOW_ACCESS, state = state, onRequest = onRequest, onSkip = null, scaleFactor = scaleFactor)
}

@Composable
private fun NotificationPermissionStep(state: OnboardingState, onRequest: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    PermissionStepWrapper(icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = AppPalette.accent, modifier = Modifier.size((32 * scaleFactor).dp)) }, title = strings.ONBOARDING_STAY_INFORMED_TITLE, description = strings.ONBOARDING_STAY_INFORMED_DESC, buttonLabel = strings.ONBOARDING_ENABLE_NOTIFICATIONS, state = state, onRequest = onRequest, onSkip = null, scaleFactor = scaleFactor)
}

@Composable
private fun BatteryOptimizationStep(state: OnboardingState, onRequest: () -> Unit, onSkip: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    PermissionStepWrapper(icon = { Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = AppPalette.accent, modifier = Modifier.size((32 * scaleFactor).dp)) }, title = strings.ONBOARDING_UNRESTRICTED_TITLE, description = strings.ONBOARDING_UNRESTRICTED_DESC, buttonLabel = strings.ONBOARDING_DISABLE_RESTRICTIONS, state = state, onRequest = onRequest, onSkip = onSkip, scaleFactor = scaleFactor)
}

@Composable
private fun SuccessStep(onFinish: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, tint = AppPalette.statusConnected, modifier = Modifier.size((80 * scaleFactor).dp))
        Spacer(modifier = Modifier.height((24 * scaleFactor).dp))
        Text(strings.ONBOARDING_SETUP_COMPLETE, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = (24 * scaleFactor).sp)
        Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
        Text(
            text = strings.ONBOARDING_READY,
            color = AppPalette.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = (14 * scaleFactor).sp
        )
        Spacer(modifier = Modifier.height((48 * scaleFactor).dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height((56 * scaleFactor).dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette.statusConnected, contentColor = Color.White)
        ) {
            Text(strings.ONBOARDING_START_SECURE_JOURNEY, fontWeight = FontWeight.Bold, color = Color.White, fontSize = (16 * scaleFactor).sp)
        }
    }
}

@Composable
private fun OnboardingFooter(currentStep: OnboardingStep, scaleFactor: Float) {
    Row(
        modifier = Modifier.padding(bottom = (32 * scaleFactor).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val visibleSteps = if (isDesktop) {
            OnboardingStep.entries.filter {
                it != OnboardingStep.VPN_PERMISSION &&
                it != OnboardingStep.NOTIFICATION_PERMISSION &&
                it != OnboardingStep.BATTERY_OPTIMIZATION &&
                it != OnboardingStep.COMPLETED
            }
        } else {
            OnboardingStep.entries.filter { it != OnboardingStep.COMPLETED }
        }

        visibleSteps.forEach { step ->
            val isSelected = step == currentStep
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) AppPalette.accent else AppPalette.divider,
                animationSpec = tween(400),
                label = "indicator_color"
            )

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
