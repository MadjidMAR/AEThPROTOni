package io.github.immaghzbad.aetherst.shared.ui.components
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun IosToast(
    message: String?,
    isError: Boolean,
    scaleFactor: Float = 1f
) {
    var internalVisible by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        if (message != null) {
            lastMessage = message
            internalVisible = true
            delay(900.milliseconds)
            showText = true
        } else {
            showText = false
            delay(850.milliseconds)
            internalVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = (85 * scaleFactor).dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = internalVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing)
            ) + fadeIn(tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
            ) + fadeOut(tween(500))
        ) {
            Surface(
                color = AppPalette.surfaceRaised.copy(alpha = 0.96f),
                shape = RoundedCornerShape(100.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) AppPalette.statusError else AppPalette.statusConnected,
                        modifier = Modifier.size((22 * scaleFactor).dp)
                    )
                    
                    AnimatedVisibility(
                        visible = showText,
                        enter = expandHorizontally(
                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Start
                        ) + fadeIn(tween(350, delayMillis = 150)),
                        exit = shrinkHorizontally(
                            animationSpec = tween(550, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Start
                        ) + fadeOut(tween(250))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
                            Text(
                                text = lastMessage,
                                color = Color.White,
                                fontSize = (14 * scaleFactor).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
