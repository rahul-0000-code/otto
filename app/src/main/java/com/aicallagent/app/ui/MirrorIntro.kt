package com.aicallagent.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aicallagent.app.ui.theme.Grotesk
import com.aicallagent.app.ui.theme.Mono
import com.aicallagent.app.ui.theme.Noir

/**
 * Opening animation "Mirror": OTTO reads the same both ways, so the name splits out of a beam of
 * light, lime streaks fan out behind it, and it settles over its own reflection. About 2.7 s; a tap
 * skips it. Every element reads one clock inside graphicsLayer, so frames redraw without recomposing.
 */
@Composable
fun MirrorIntro(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        onFinished()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Noir.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }
            .semantics { contentDescription = "Otto. Your calls, handled." }
    ) {
        val w = maxWidth
        val h = maxHeight
        val markSize = with(LocalDensity.current) { (w * 0.23f).toSp() }
        val mark = TextStyle(
            fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = markSize,
            lineHeight = markSize, letterSpacing = (-0.05).em,
        )

        // Soft lime light behind the name.
        Box(
            Modifier
                .size(w * 1.4f)
                .offset(x = -w * 0.2f, y = h * 0.18f)
                .graphicsLayer { alpha = segment(clock.value, 1000f, 1600f) }
                .background(Brush.radialGradient(listOf(Noir.Lime.copy(alpha = 0.16f), Color.Transparent)))
        )

        // Streaks fanning out from the centre, like light through the double T.
        RIBBONS.forEach { ribbon ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(w * 0.015f)
                    .height(h * 1.15f)
                    .graphicsLayer {
                        val t = clock.value
                        val p = segment(t, RIBBON_START, RIBBON_MS, Streak)
                        translationX = w.toPx() * ribbon.x * p
                        scaleY = 0.08f + 0.92f * p
                        alpha = ribbonEnvelope(t)
                    }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ribbon.color, ribbon.color, Color.Transparent)))
            )
        }

        // The beam the name splits out of.
        Beam(w, clock::value, Modifier.align(Alignment.Center))

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // A blurred lime copy flashes once as the halves meet.
                Wordmark(
                    mark,
                    Modifier
                        .blur(18.dp)
                        .graphicsLayer { alpha = flash(clock.value) * 0.85f },
                    allLime = true,
                )
                Row {
                    Text("OT", style = mark.copy(color = Noir.Text), modifier = Modifier.graphicsLayer { half(clock.value, fromRight = true) })
                    Row(Modifier.graphicsLayer { half(clock.value, fromRight = false) }) {
                        Text("T", style = mark.copy(color = Noir.Text))
                        Text("O", style = mark.copy(color = Noir.Lime))
                    }
                }
            }
            // Its reflection, fading away from the name.
            Wordmark(
                mark,
                Modifier
                    .offset(y = -(w * 0.03f))
                    .graphicsLayer {
                        scaleY = -1f
                        alpha = 0.16f * segment(clock.value, 1300f, 900f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), blendMode = BlendMode.DstIn)
                    }
            )
        }

        Text(
            "[ YOUR CALLS, HANDLED ]",
            style = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 0.14.em, color = Noir.Faint),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = h * 0.17f)
                .graphicsLayer {
                    val p = segment(clock.value, 2100f, 600f)
                    alpha = p
                    translationY = (1f - p) * 12.dp.toPx()
                }
        )
    }
}

@Composable
private fun Wordmark(style: TextStyle, modifier: Modifier, allLime: Boolean = false) {
    Row(modifier) {
        Text("OTT", style = style.copy(color = if (allLime) Noir.Lime else Noir.Text))
        Text("O", style = style.copy(color = Noir.Lime))
    }
}

@Composable
private fun Beam(width: Dp, time: () -> Float, modifier: Modifier) {
    Box(
        modifier
            .width(2.dp)
            .height(width * 0.46f)
            .graphicsLayer {
                val r = (time() / BEAM_MS).coerceIn(0f, 1f)
                scaleY = if (r < 0.35f) r / 0.35f else 1f + 0.1f * ((r - 0.35f) / 0.65f)
                scaleX = if (r < 0.7f) 1f else 1f + 4f * ((r - 0.7f) / 0.3f)
                alpha = when {
                    time() >= BEAM_MS -> 0f
                    r < 0.7f -> 1f
                    else -> 1f - (r - 0.7f) / 0.3f
                }
            }
            .background(Noir.Lime)
    )
}

/** One half of the name sliding out of the beam, sharpening as it arrives. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.half(time: Float, fromRight: Boolean) {
    val p = segment(time, 450f, 750f, OutExpo)
    translationX = (if (fromRight) 1f else -1f) * size.width * 0.6f * (1f - p)
    alpha = p
    val blur = 14f * (1f - p)
    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
}

private fun segment(time: Float, startMs: Float, durationMs: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((time - startMs) / durationMs).coerceIn(0f, 1f))

private fun ribbonEnvelope(time: Float): Float {
    val r = (time - RIBBON_START) / RIBBON_MS
    return when {
        r <= 0f || r >= 1f -> 0f
        r < 0.12f -> r / 0.12f
        r < 0.65f -> 1f
        else -> 1f - (r - 0.65f) / 0.35f
    }
}

private fun flash(time: Float): Float {
    val r = (time - 1150f) / 900f
    return when {
        r <= 0f || r >= 1f -> 0f
        r < 0.3f -> r / 0.3f
        else -> 1f - (r - 0.3f) / 0.7f
    }
}

private class Ribbon(val x: Float, val color: Color)

private val RIBBONS = listOf(0.06f, 0.13f, 0.21f, 0.30f, 0.40f, 0.51f).zip(
    listOf(Color(0xFFC8FF2E), Color(0xFFECFFB8), Color(0xFF8FBF16), Color(0xFFC8FF2E), Color(0xFF5E8A0A), Color(0xFFECFFB8))
).flatMap { (x, color) -> listOf(Ribbon(-x, color), Ribbon(x, color)) }

private const val TOTAL_MS = 2700f
private const val BEAM_MS = 1150f
private const val RIBBON_START = 850f
private const val RIBBON_MS = 1450f
private val OutExpo = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
private val Streak = CubicBezierEasing(0.25f, 0f, 0.15f, 1f)
