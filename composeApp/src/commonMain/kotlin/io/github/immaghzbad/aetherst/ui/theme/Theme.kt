package io.github.immaghzbad.aetherst.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A2A5E),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFF8A8A95),
    background = Color(0xFF0F0F12),
    onBackground = Color(0xFFECECF1),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF232329),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF3A3A42),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0A6CFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF5A5A63),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF1A1A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1F),
    surfaceVariant = Color(0xFFECECF1),
    onSurfaceVariant = Color(0xFF5A5A63),
    outline = Color(0xFFDCDCE2),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    isRtl: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) darkAppColors else lightAppColors

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalAppColors provides appColors,
    ) {
        BoxWithConstraints {
            val scale = (maxWidth.value / 411f).coerceIn(0.7f, 1.1f)
            CompositionLocalProvider(LocalScaleFactor provides scale) {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = appTypography(isRtl = isRtl),
                    content = content,
                )
            }
        }
    }
}
