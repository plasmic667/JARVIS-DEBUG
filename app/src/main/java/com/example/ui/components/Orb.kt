package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

enum class OrbState {
    IDLE, LISTENING, THINKING, SPEAKING
}

private data class OrbPoint(val phi: Float, val theta: Float, val seed: Float)

private val PaletteStops = listOf(
    0.0f to Color(0xFFFF4AA2),
    0.35f to Color(0xFFEC5CE7),
    0.62f to Color(0xFFB06CFF),
    0.85f to Color(0xFF6E69FF),
    1.0f to Color(0xFF4884FF)
)

private fun interpolateColor(t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    for (i in 0 until PaletteStops.size - 1) {
        val (s1, c1) = PaletteStops[i]
        val (s2, c2) = PaletteStops[i + 1]
        if (clamped in s1..s2) {
            val ratio = (clamped - s1) / (s2 - s1)
            val r = c1.red + (c2.red - c1.red) * ratio
            val g = c1.green + (c2.green - c1.green) * ratio
            val b = c1.blue + (c2.blue - c1.blue) * ratio
            return Color(r, g, b)
        }
    }
    return PaletteStops.last().second
}

@Composable
fun Orb(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.IDLE,
    density: Int = 900
) {
    val points = remember(density) {
        val golden = (Math.PI * (3.0 - sqrt(5.0))).toFloat()
        List(density) { i ->
            val phi = acos(1f - (2f * (i + 0.5f)) / density)
            val theta = golden * i
            val seed = ((sin(i * 12.9898f) * 43758.5453f) % 1.0f).absoluteValue
            OrbPoint(phi, theta, seed)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "OrbLoop")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbTime"
    )

    val speed = when (state) {
        OrbState.THINKING -> 2.4f
        OrbState.LISTENING -> 1.8f
        OrbState.SPEAKING -> 1.6f
        OrbState.IDLE -> 0.85f
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.35f

        val t = animTime * speed

        // Soft atmospheric glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x38D85CFF),
                    Color(0x1A7C3AED),
                    Color(0x0006030D)
                ),
                center = Offset(cx, cy),
                radius = baseRadius * 1.35f
            ),
            radius = baseRadius * 1.35f,
            center = Offset(cx, cy)
        )

        val rotY = t * 0.16f
        val tiltX = -0.32f + sin(t * 0.25f) * 0.06f
        val breathe = 1f + sin(t * if (state == OrbState.IDLE) 0.9f else 1.6f) * 0.02f

        // Draw points with 3D projection
        points.forEach { p ->
            val ripple = 1f +
                    0.055f * sin(p.phi * 4f + t * 1.35f) * cos(p.theta * 3f + t * 0.85f) +
                    0.03f * sin(p.theta * 6f - t * 1.1f)
            val r = baseRadius * ripple * breathe

            var x = sin(p.phi) * cos(p.theta + rotY) * r
            var y = cos(p.phi) * r
            var z = sin(p.phi) * sin(p.theta + rotY) * r

            // Tilt X
            val yTilt = y * cos(tiltX) - z * sin(tiltX)
            val zTilt = y * sin(tiltX) + z * cos(tiltX)
            y = yTilt
            z = zTilt

            val perspective = 1f / (1f + z / (baseRadius * 5.2f))
            val px = cx + x * perspective
            val py = cy + y * perspective

            val depth = ((z / baseRadius) + 1f) / 2f // 0 back -> 1 front
            val colorT = (0.5f - y / (baseRadius * 2.1f) + (x / (baseRadius * 3.4f))).coerceIn(0f, 1f)
            val pointColor = interpolateColor(colorT)
            val alpha = (0.12f + depth * 0.85f).coerceIn(0.05f, 1f)
            val dotRadius = ((0.8f + depth * 1.5f) * (0.85f + (p.seed % 1f) * 0.3f)).coerceAtLeast(0.6f)

            drawCircle(
                color = pointColor.copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(px, py)
            )
        }

        // Concentric pulsing rings for active states
        if (state != OrbState.IDLE) {
            val ringCount = if (state == OrbState.SPEAKING) 3 else 2
            for (i in 0 until ringCount) {
                val phase = (t * 0.7f + i * 0.33f) % 1.0f
                val ringRadius = baseRadius * (1f + phase * 0.5f)
                val ringAlpha = (0.35f * (1f - phase)).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFFFF5CBE).copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }
        }
    }
}
