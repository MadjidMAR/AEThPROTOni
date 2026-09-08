package io.github.immaghzbad.aetherst.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val ElegantPrimary = Color(0xFFD0BCFF)
val ElegantOnPrimary = Color(0xFF381E72)
val ElegantPrimaryContainer = Color(0xFF381E72)
val ElegantOnPrimaryContainer = Color(0xFFEADDFF)
val ElegantSecondary = Color(0xFFCCC2DC)
val ElegantBackground = Color(0xFF000000)
val ElegantSurface = Color(0xFF000000)
val ElegantSurfaceCard = Color(0xFF1C1B1F)
val ElegantSurfaceActive = Color(0xFF4A4458)
val ElegantOutline = Color(0xFF49454F)
val ElegantTextPrimary = Color(0xFFE6E1E5)
val ElegantTextSecondary = Color(0xFFCAC4D0)
val ConnectedGreen = Color(0xFF81C784)
val ScanningAmber = Color(0xFFFFB74D)
val ErrorRed = Color(0xFFF2B8B5)

object AppPalette {
    val accent = Color(0xFF007AFF)
    val accentVariant = Color(0xFF5856D6)
    val accentVariantAlt = Color(0xFFAF52DE)
    val onAccent = Color(0xFFFFFFFF)

    val statusConnected = Color(0xFF34C759)
    val statusScanning = Color(0xFFFF9500)
    val statusError = Color(0xFFFF3B30)
    val debugCyan = Color(0xFF64D2FF)

    val surfaceRaised = Color(0xFF1C1C1E)
    val surfaceSunken = Color(0xFF0A0A0D)
    val groupBg = Color(0xFF2C2C2E)
    val divider = Color(0xFF2C2C2E)
    val inactiveTrack = Color(0xFF3A3A3C)

    val textPrimary = Color(0xFFECECF1)
    val textSecondary = Color(0xFF8E8E93)

    val navBackground = Color(0xFF1C1C1E)
    val navActive = Color(0xFF007AFF)
    val navInactive = Color(0xFF8E8E93)
}

@Immutable
data class AppColors(
    val accent: Color,
    val onAccent: Color,
    val accentVariant: Color,
    val debugCyan: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val groupBg: Color,
    val divider: Color,
    val inactiveTrack: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val statusConnected: Color,
    val statusScanning: Color,
    val statusError: Color,
    val navBackground: Color,
    val navActive: Color,
    val navInactive: Color,
)

internal val darkAppColors = AppColors(
    accent = AppPalette.accent,
    onAccent = AppPalette.onAccent,
    accentVariant = AppPalette.accentVariant,
    debugCyan = AppPalette.debugCyan,
    surfaceRaised = AppPalette.surfaceRaised,
    surfaceSunken = AppPalette.surfaceSunken,
    groupBg = AppPalette.groupBg,
    divider = AppPalette.divider,
    inactiveTrack = AppPalette.inactiveTrack,
    textPrimary = AppPalette.textPrimary,
    textSecondary = AppPalette.textSecondary,
    statusConnected = AppPalette.statusConnected,
    statusScanning = AppPalette.statusScanning,
    statusError = AppPalette.statusError,
    navBackground = AppPalette.navBackground,
    navActive = AppPalette.navActive,
    navInactive = AppPalette.navInactive,
)

internal val lightAppColors = AppColors(
    accent = Color(0xFF0A6CFF),
    onAccent = Color(0xFFFFFFFF),
    accentVariant = Color(0xFF5E5CE6),
    debugCyan = Color(0xFF0A84FF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFF2F3F7),
    groupBg = Color(0xFFECECF1),
    divider = Color(0xFFDCDCE2),
    inactiveTrack = Color(0xFFD1D1D6),
    textPrimary = Color(0xFF1A1A1F),
    textSecondary = Color(0xFF5A5A63),
    statusConnected = Color(0xFF1E9E6B),
    statusScanning = Color(0xFFC77D1E),
    statusError = Color(0xFFC0392B),
    navBackground = Color(0xFFF2F3F7),
    navActive = Color(0xFF0A6CFF),
    navInactive = Color(0xFF8A8A95),
)

val LocalAppColors = staticCompositionLocalOf { darkAppColors }

@Composable
fun appColors(): AppColors = LocalAppColors.current
