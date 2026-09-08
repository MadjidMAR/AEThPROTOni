package io.github.immaghzbad.aetherst.shared.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ─── Server Locations ───────────────────────────────────────────────────────────
// Each entry: (name, x% across width, y% down height)
private val serverLocations = listOf(
    // North America
    "New York" to 0.28f to 0.35f,
    "Miami" to 0.25f to 0.42f,
    "Chicago" to 0.22f to 0.33f,
    "Dallas" to 0.20f to 0.38f,
    "Los Angeles" to 0.14f to 0.37f,
    "Toronto" to 0.27f to 0.30f,
    "Vancouver" to 0.13f to 0.27f,
    // South America
    "São Paulo" to 0.35f to 0.65f,
    "Buenos Aires" to 0.33f to 0.72f,
    "Bogotá" to 0.27f to 0.55f,
    "Lima" to 0.26f to 0.60f,
    // Europe
    "London" to 0.45f to 0.27f,
    "Paris" to 0.47f to 0.29f,
    "Frankfurt" to 0.50f to 0.28f,
    "Amsterdam" to 0.48f to 0.26f,
    "Warsaw" to 0.53f to 0.27f,
    "Stockholm" to 0.52f to 0.20f,
    "Madrid" to 0.44f to 0.33f,
    // Africa
    "Cape Town" to 0.53f to 0.72f,
    "Nairobi" to 0.58f to 0.57f,
    "Lagos" to 0.47f to 0.52f,
    "Cairo" to 0.55f to 0.38f,
    // Asia
    "Tokyo" to 0.82f to 0.33f,
    "Singapore" to 0.74f to 0.52f,
    "Hong Kong" to 0.77f to 0.42f,
    "Mumbai" to 0.65f to 0.43f,
    "Dubai" to 0.61f to 0.40f,
    "Seoul" to 0.80f to 0.32f,
    "Taipei" to 0.79f to 0.40f,
    "Jakarta" to 0.76f to 0.58f,
    // Oceania
    "Sydney" to 0.86f to 0.68f,
    "Auckland" to 0.95f to 0.72f,
)

// Fake user location for connection line (defaults to middle of Atlantic)
private val defaultUserLocation = 0.37f to 0.40f

// ─── Continent Path Builders ────────────────────────────────────────────────────
// All coordinates in 0..1 range, scaled to canvas at draw time.

private fun buildNorthAmericaPath(): Path {
    val p = Path()
    // Alaska area
    p.moveTo(0.08f, 0.18f)
    p.lineTo(0.12f, 0.14f)
    p.lineTo(0.16f, 0.16f)
    p.lineTo(0.18f, 0.20f)
    // West coast up
    p.lineTo(0.14f, 0.22f)
    p.lineTo(0.12f, 0.26f)
    p.lineTo(0.13f, 0.30f)
    p.lineTo(0.14f, 0.36f)
    p.lineTo(0.12f, 0.38f)
    // Mexico / Central America
    p.lineTo(0.14f, 0.42f)
    p.lineTo(0.17f, 0.44f)
    p.lineTo(0.20f, 0.46f)
    p.lineTo(0.22f, 0.44f)
    p.lineTo(0.24f, 0.42f)
    // East coast going north
    p.lineTo(0.26f, 0.40f)
    p.lineTo(0.28f, 0.38f)
    p.lineTo(0.30f, 0.35f)
    p.lineTo(0.31f, 0.32f)
    p.lineTo(0.30f, 0.28f)
    p.lineTo(0.29f, 0.25f)
    // Labrador / NE Canada
    p.lineTo(0.31f, 0.22f)
    p.lineTo(0.33f, 0.20f)
    p.lineTo(0.32f, 0.17f)
    // Hudson Bay area
    p.lineTo(0.28f, 0.16f)
    p.lineTo(0.25f, 0.18f)
    p.lineTo(0.22f, 0.17f)
    // NW Territories
    p.lineTo(0.18f, 0.15f)
    p.lineTo(0.14f, 0.14f)
    p.lineTo(0.10f, 0.16f)
    p.close()
    return p
}

private fun buildSouthAmericaPath(): Path {
    val p = Path()
    p.moveTo(0.27f, 0.48f)
    p.lineTo(0.30f, 0.47f)
    p.lineTo(0.33f, 0.48f)
    p.lineTo(0.36f, 0.50f)
    p.lineTo(0.38f, 0.53f)
    p.lineTo(0.39f, 0.57f)
    p.lineTo(0.40f, 0.61f)
    p.lineTo(0.39f, 0.65f)
    p.lineTo(0.37f, 0.68f)
    p.lineTo(0.35f, 0.70f)
    p.lineTo(0.33f, 0.73f)
    p.lineTo(0.31f, 0.75f)
    // Tip
    p.lineTo(0.30f, 0.78f)
    p.lineTo(0.31f, 0.77f)
    p.lineTo(0.33f, 0.76f)
    // West coast up
    p.lineTo(0.30f, 0.74f)
    p.lineTo(0.27f, 0.70f)
    p.lineTo(0.25f, 0.66f)
    p.lineTo(0.24f, 0.62f)
    p.lineTo(0.23f, 0.58f)
    p.lineTo(0.24f, 0.54f)
    p.lineTo(0.25f, 0.50f)
    p.close()
    return p
}

private fun buildEuropePath(): Path {
    val p = Path()
    p.moveTo(0.42f, 0.22f)
    p.lineTo(0.45f, 0.20f)
    p.lineTo(0.48f, 0.18f)
    p.lineTo(0.52f, 0.17f)
    p.lineTo(0.55f, 0.18f)
    p.lineTo(0.56f, 0.20f)
    // Scandinavia
    p.lineTo(0.54f, 0.22f)
    p.lineTo(0.52f, 0.23f)
    p.lineTo(0.50f, 0.22f)
    p.lineTo(0.48f, 0.23f)
    p.lineTo(0.50f, 0.25f)
    p.lineTo(0.53f, 0.26f)
    // Eastern Europe / Russia border
    p.lineTo(0.56f, 0.25f)
    p.lineTo(0.58f, 0.27f)
    p.lineTo(0.57f, 0.30f)
    p.lineTo(0.55f, 0.32f)
    // Mediterranean
    p.lineTo(0.52f, 0.33f)
    p.lineTo(0.50f, 0.34f)
    p.lineTo(0.48f, 0.35f)
    p.lineTo(0.46f, 0.34f)
    // Iberia
    p.lineTo(0.43f, 0.35f)
    p.lineTo(0.42f, 0.33f)
    p.lineTo(0.41f, 0.30f)
    p.lineTo(0.42f, 0.27f)
    p.close()
    return p
}

private fun buildAfricaPath(): Path {
    val p = Path()
    p.moveTo(0.46f, 0.37f)
    p.lineTo(0.50f, 0.36f)
    p.lineTo(0.54f, 0.37f)
    p.lineTo(0.57f, 0.39f)
    p.lineTo(0.58f, 0.42f)
    p.lineTo(0.59f, 0.46f)
    p.lineTo(0.58f, 0.50f)
    p.lineTo(0.57f, 0.54f)
    p.lineTo(0.56f, 0.58f)
    p.lineTo(0.55f, 0.62f)
    p.lineTo(0.54f, 0.66f)
    p.lineTo(0.52f, 0.70f)
    // Cape
    p.lineTo(0.50f, 0.73f)
    p.lineTo(0.49f, 0.72f)
    // West coast
    p.lineTo(0.46f, 0.68f)
    p.lineTo(0.44f, 0.62f)
    p.lineTo(0.43f, 0.56f)
    p.lineTo(0.42f, 0.50f)
    p.lineTo(0.43f, 0.44f)
    p.lineTo(0.44f, 0.40f)
    p.close()
    return p
}

private fun buildAsiaPath(): Path {
    val p = Path()
    // Start from Turkey area
    p.moveTo(0.57f, 0.30f)
    p.lineTo(0.60f, 0.28f)
    p.lineTo(0.64f, 0.27f)
    p.lineTo(0.68f, 0.25f)
    p.lineTo(0.72f, 0.23f)
    p.lineTo(0.76f, 0.22f)
    // Siberia top
    p.lineTo(0.80f, 0.20f)
    p.lineTo(0.84f, 0.19f)
    p.lineTo(0.88f, 0.20f)
    p.lineTo(0.90f, 0.22f)
    // Kamchatka / East Russia
    p.lineTo(0.90f, 0.26f)
    p.lineTo(0.88f, 0.28f)
    p.lineTo(0.85f, 0.30f)
    // Korea / Japan area
    p.lineTo(0.83f, 0.32f)
    p.lineTo(0.82f, 0.34f)
    p.lineTo(0.80f, 0.36f)
    // China coast
    p.lineTo(0.79f, 0.38f)
    p.lineTo(0.78f, 0.40f)
    p.lineTo(0.77f, 0.42f)
    p.lineTo(0.76f, 0.44f)
    // Southeast Asia
    p.lineTo(0.74f, 0.46f)
    p.lineTo(0.73f, 0.48f)
    p.lineTo(0.72f, 0.50f)
    p.lineTo(0.73f, 0.52f)
    p.lineTo(0.74f, 0.54f)
    p.lineTo(0.73f, 0.56f)
    // Indonesia curve
    p.lineTo(0.75f, 0.58f)
    p.lineTo(0.77f, 0.57f)
    p.lineTo(0.79f, 0.56f)
    // Back up west coast through Indian subcontinent
    p.lineTo(0.70f, 0.54f)
    p.lineTo(0.67f, 0.52f)
    p.lineTo(0.65f, 0.48f)
    p.lineTo(0.64f, 0.45f)
    p.lineTo(0.65f, 0.42f)
    p.lineTo(0.64f, 0.39f)
    // Arabian Peninsula
    p.lineTo(0.62f, 0.38f)
    p.lineTo(0.60f, 0.36f)
    p.lineTo(0.58f, 0.34f)
    p.lineTo(0.57f, 0.32f)
    p.close()
    return p
}

private fun buildOceaniaPath(): Path {
    val p = Path()
    // Australia
    p.moveTo(0.80f, 0.60f)
    p.lineTo(0.84f, 0.58f)
    p.lineTo(0.88f, 0.59f)
    p.lineTo(0.90f, 0.62f)
    p.lineTo(0.91f, 0.65f)
    p.lineTo(0.90f, 0.68f)
    p.lineTo(0.88f, 0.71f)
    p.lineTo(0.85f, 0.73f)
    p.lineTo(0.82f, 0.72f)
    p.lineTo(0.80f, 0.70f)
    p.lineTo(0.79f, 0.67f)
    p.lineTo(0.78f, 0.64f)
    p.lineTo(0.79f, 0.62f)
    p.close()
    return p
}

// ─── Drawing Helpers ────────────────────────────────────────────────────────────

private fun DrawScope.drawServerDot(
    cx: Float,
    cy: Float,
    color: Color,
    radius: Float,
    glowRadius: Float,
    glowAlpha: Float,
) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = glowAlpha * 0.6f), Color.Transparent),
            center = Offset(cx, cy),
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = Offset(cx, cy),
    )
    // Core dot
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(cx, cy),
    )
    // Bright center
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = radius * 0.4f,
        center = Offset(cx, cy),
    )
}

private fun DrawScope.drawConnectionLine(
    fromX: Float, fromY: Float,
    toX: Float, toY: Float,
    color: Color,
    alpha: Float,
    dashPhase: Float,
) {
    val midX = (fromX + toX) / 2f
    val midY = (fromY + toY) / 2f - (kotlin.math.abs(toX - fromX) + kotlin.math.abs(toY - fromY)) * 0.1f

    val curvePath = Path().apply {
        moveTo(fromX, fromY)
        quadraticBezierTo(midX, midY, toX, toY)
    }

    // Glow line
    drawPath(
        path = curvePath,
        color = color.copy(alpha = alpha * 0.3f),
        style = Stroke(
            width = 6f,
            cap = StrokeCap.Round,
        ),
    )
    // Main line
    drawPath(
        path = curvePath,
        color = color.copy(alpha = alpha),
        style = Stroke(
            width = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(12f, 8f),
                phase = dashPhase,
            ),
        ),
    )
}

private fun DrawScope.drawGridLines(color: Color, alpha: Float) {
    val w = size.width
    val h = size.height

    // Latitude lines
    for (i in 1..5) {
        val y = h * i / 6f
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 0.5f,
        )
    }
    // Longitude lines
    for (i in 1..7) {
        val x = w * i / 8f
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.5f,
        )
    }
}

// ─── Public Composable ──────────────────────────────────────────────────────────

/**
 * A stylised dark world map with animated server pins and connection line.
 *
 * @param selectedServer  Name of the currently highlighted server region, or null.
 * @param connectionStatus Current [ConnectionStatus] controlling animations.
 * @param onServerSelected Lambda invoked when the user taps a server dot.
 * @param modifier        Modifier applied to the root Canvas.
 */
@Composable
fun WorldMapCanvas(
    selectedServer: String?,
    connectionStatus: ConnectionStatus,
    onServerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionStatus == ConnectionStatus.RUNNING ||
            connectionStatus == ConnectionStatus.TUN_ACTIVE

    // Pulse animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "worldmap")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulsePhase",
    )
    val dashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dashOffset",
    )

    // Pre-build continent paths
    val continents = remember {
        listOf(
            buildNorthAmericaPath(),
            buildSouthAmericaPath(),
            buildEuropePath(),
            buildAfricaPath(),
            buildAsiaPath(),
            buildOceaniaPath(),
        )
    }

    // Convert server locations to a rememberable structure for tap detection
    val serverPoints = remember {
        serverLocations.map { (name, x, y) -> Triple(name, x, y) }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()
                    val hitRadius = 28f

                    // Find closest server within hit radius
                    var closest: String? = null
                    var closestDist = Float.MAX_VALUE

                    for ((name, sx, sy) in serverPoints) {
                        val dx = offset.x - sx * canvasW
                        val dy = offset.y - sy * canvasH
                        val dist = hypot(dx, dy)
                        if (dist < hitRadius && dist < closestDist) {
                            closest = name
                            closestDist = dist
                        }
                    }
                    closest?.let { onServerSelected(it) }
                }
            },
    ) {
        val w = size.width
        val h = size.height

        // ── Background ──────────────────────────────────────────────────────
        drawRect(color = Color(0xFF1B1B1F))

        // Subtle radial vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF222228).copy(alpha = 0.3f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = w * 0.7f,
            ),
        )

        // ── Grid lines ──────────────────────────────────────────────────────
        drawGridLines(Color(0xFF2A2A30), alpha = 0.25f)

        // ── Continents ──────────────────────────────────────────────────────
        // Scale the 0..1 normalised paths to canvas dimensions
        for (continent in continents) {
            withTransform({
                scale(scaleX = w, scaleY = h, pivot = Offset.Zero)
            }) {
                drawPath(
                    path = continent,
                    color = Color(0xFF2A2A30),
                )
                drawPath(
                    path = continent,
                    color = Color(0xFF3A3A40).copy(alpha = 0.6f),
                    style = Stroke(width = 0.002f * w, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        // ── Connection line ─────────────────────────────────────────────────
        if (isConnected && selectedServer != null) {
            val target = serverLocations.find { it.first == selectedServer }
            if (target != null) {
                val (tx, ty) = target
                val (ux, uy) = defaultUserLocation
                drawConnectionLine(
                    fromX = ux * w,
                    fromY = uy * h,
                    toX = tx * w,
                    toY = ty * h,
                    color = AppPalette.statusConnected,
                    alpha = 0.8f,
                    dashPhase = dashOffset,
                )
            }
        }

        // ── Server dots ─────────────────────────────────────────────────────
        for ((name, sx, sy) in serverPoints) {
            val cx = sx * w
            val cy = sy * h

            val isSelected = name == selectedServer
            val isActiveServer = isSelected && isConnected

            val dotColor = when {
                isActiveServer -> AppPalette.statusConnected
                isSelected -> AppPalette.accent
                else -> Color(0xFF64D2FF).copy(alpha = 0.7f)
            }

            val baseRadius = if (isSelected) 5f else 3.5f

            if (isActiveServer) {
                // Pulsing glow for connected server
                val glowIntensity = (sin(pulsePhase * 2f * PI.toFloat()) + 1f) / 2f
                val pulseRadius = baseRadius + 4f * glowIntensity

                // Animated expanding ring
                val ringRadius = baseRadius + 12f * pulsePhase
                drawCircle(
                    color = AppPalette.statusConnected.copy(alpha = 0.4f * (1f - pulsePhase)),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f),
                )

                drawServerDot(
                    cx = cx,
                    cy = cy,
                    color = dotColor,
                    radius = pulseRadius,
                    glowRadius = pulseRadius * 4f,
                    glowAlpha = 0.5f + 0.3f * glowIntensity,
                )
            } else {
                // Static or selected-but-not-connected
                val glowAlpha = if (isSelected) 0.5f else 0.25f
                drawServerDot(
                    cx = cx,
                    cy = cy,
                    color = dotColor,
                    radius = baseRadius,
                    glowRadius = baseRadius * 3.5f,
                    glowAlpha = glowAlpha,
                )
            }
        }

        // ── User location indicator ─────────────────────────────────────────
        if (isConnected) {
            val ux = defaultUserLocation.first * w
            val uy = defaultUserLocation.second * h

            // Small diamond / crosshair
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 10f,
                center = Offset(ux, uy),
                style = Stroke(width = 1f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = 3f,
                center = Offset(ux, uy),
            )
        }
    }
}
