package io.github.immaghzbad.aetherst.shared.ui.theme

import aetherst_tunnel.composeapp.generated.resources.Inter_Bold
import aetherst_tunnel.composeapp.generated.resources.Inter_Regular
import aetherst_tunnel.composeapp.generated.resources.Res
import aetherst_tunnel.composeapp.generated.resources.Vazirmatn_Bold
import aetherst_tunnel.composeapp.generated.resources.Vazirmatn_Regular
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.InternalResourceApi

@OptIn(InternalResourceApi::class)
@Composable
fun appTypography(isRtl: Boolean = false): Typography {
    val vazirmatnRegular = Font(Res.font.Vazirmatn_Regular, FontWeight.Normal)
    val vazirmatnBold = Font(Res.font.Vazirmatn_Bold, FontWeight.Bold)
    val interRegular = Font(Res.font.Inter_Regular, FontWeight.Normal)
    val interBold = Font(Res.font.Inter_Bold, FontWeight.Bold)
    val ff = FontFamily(vazirmatnRegular, vazirmatnBold, interRegular, interBold)
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = ff),
        displayMedium = base.displayMedium.copy(fontFamily = ff),
        displaySmall = base.displaySmall.copy(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = ff),
        headlineMedium = base.headlineMedium.copy(fontFamily = ff),
        headlineSmall = base.headlineSmall.copy(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontFamily = ff),
        titleMedium = base.titleMedium.copy(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        titleSmall = base.titleSmall.copy(fontFamily = ff),
        bodyLarge = base.bodyLarge.copy(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        bodySmall = base.bodySmall.copy(fontFamily = ff),
        labelLarge = base.labelLarge.copy(fontFamily = ff),
        labelMedium = base.labelMedium.copy(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelSmall = base.labelSmall.copy(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
    )
}
