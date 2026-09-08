package io.github.immaghzbad.aetherst.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalScaleFactor = compositionLocalOf { 1f }

@Composable
fun scaleFactor(): Float = LocalScaleFactor.current

@Suppress("unused")
val Int.sdp: Dp
    @Composable get() = (this * LocalScaleFactor.current).dp

@Suppress("unused")
val Float.sdp: Dp
    @Composable get() = (this * LocalScaleFactor.current).dp

@Suppress("unused")
val Double.sdp: Dp
    @Composable get() = (this * LocalScaleFactor.current).dp

@Suppress("unused")
val Int.ssp: TextUnit
    @Composable get() = (this * LocalScaleFactor.current).sp

@Suppress("unused")
val Float.ssp: TextUnit
    @Composable get() = (this * LocalScaleFactor.current).sp
