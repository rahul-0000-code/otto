package com.aicallagent.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import com.aicallagent.app.ui.theme.SamyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Samy's living orb: a violet sphere whose light drifts slowly inside it and breathes at rest.
 * [level] (0–1, the microphone loudness) swells it while you talk; [busy] stirs it faster while
 * Samy is working something out.
 */
@Composable
fun SamyOrb(modifier: Modifier, level: Float = 0f, busy: Boolean = false) {
    val t = rememberInfiniteTransition(label = "orb")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(if (busy) 3200 else 9000, easing = LinearEasing)), label = "drift",
    )
    val breathe by t.animateFloat(
        0.965f, 1.035f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val loud by animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "level")

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f * 0.74f * (breathe + loud * 0.17f)
        // Halo: brighter while you talk.
        drawCircle(
            Brush.radialGradient(listOf(SamyColors.Violet.copy(alpha = 0.40f + 0.35f * loud), Color.Transparent), c, r * 1.55f),
            r * 1.55f, c,
        )
        val sphere = Path().apply { addOval(Rect(c, r)) }
        clipPath(sphere) {
            drawCircle(
                Brush.radialGradient(listOf(SamyColors.Light, SamyColors.Violet, SamyColors.Deep), c + Offset(-r * 0.32f, -r * 0.38f), r * 1.55f),
                r, c,
            )
            // Three soft lights drifting inside the sphere, each on its own path.
            val lights = listOf(
                Triple(Color.White.copy(alpha = 0.55f), 0.60f, 1.0f),
                Triple(Color(0xFF9FD6FF).copy(alpha = 0.42f), 0.66f, -1.35f),
                Triple(Color(0xFFF3B4FF).copy(alpha = 0.36f), 0.54f, 0.75f),
            )
            lights.forEachIndexed { i, (tint, spread, speed) ->
                val a = phase * speed + i * 2.1f
                val at = c + Offset(cos(a) * r * (0.42f + 0.12f * loud), sin(a * 1.3f) * r * (0.38f + 0.12f * loud))
                drawCircle(Brush.radialGradient(listOf(tint, Color.Transparent), at, r * spread), r * spread, at)
            }
            // Rim shade for depth.
            drawCircle(Brush.radialGradient(listOf(Color.Transparent, SamyColors.Deep.copy(alpha = 0.55f)), c, r), r, c)
        }
    }
}
