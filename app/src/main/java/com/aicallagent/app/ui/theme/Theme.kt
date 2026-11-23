package com.aicallagent.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aicallagent.app.R

/** Neo Noir: pure black, smoked glass, one electric-lime accent. */
object Noir {
    val Bg = Color(0xFF000000)
    val Raised = Color(0xFF0B0B0B)
    val Smoke = Color(0x12FFFFFF)
    val SmokeLo = Color(0x06FFFFFF)
    val Hair = Color(0x1FFFFFFF)
    val HairHi = Color(0x33FFFFFF)
    val Text = Color(0xFFF2F2F2)
    val Dim = Color(0xFFA8A8A8)
    val Faint = Color(0xFF8A8A8A)
    val Lime = Color(0xFFC8FF2E)
    val LimeGlow = Color(0x47C8FF2E)
    val Ink = Color(0xFF0A0A0A)
    val Red = Color(0xFFFF4D5E)
    val Amber = Color(0xFFFFC23D)
}

/**
 * Samy, the voice assistant, has its own look: violet on deep ink, orbs and fully round pills.
 * Otto (calls) keeps lime on black with square cards, so the two never look alike.
 */
object SamyColors {
    val Violet = Color(0xFFB49CFF)
    val Deep = Color(0xFF8A6FF0)
    val Light = Color(0xFFE6DEFF)
    val Text2 = Color(0xFFCFC6E8)
    val Soft = Color(0x29B49CFF)
    val Line = Color(0x47B49CFF)
    val Ink = Color(0xFF07060C)
    val Surface = Color(0xFF0E0B18)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Grotesk = FontFamily(
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

val Mono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 600),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = (-0.04).em),
    displayMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-0.04).em),
    headlineMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
    headlineSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp),
    titleSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp),
    bodyLarge = TextStyle(fontFamily = Grotesk, fontSize = 15.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Grotesk, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Grotesk, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.08.em),
)

@Composable
fun NoirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Noir.Lime,
            onPrimary = Noir.Ink,
            secondary = Noir.Lime,
            background = Noir.Bg,
            onBackground = Noir.Text,
            surface = Noir.Raised,
            onSurface = Noir.Text,
            surfaceVariant = Color(0xFF141414),
            onSurfaceVariant = Noir.Dim,
            outline = Noir.HairHi,
            outlineVariant = Noir.Hair,
            error = Noir.Red,
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1B1B1B),
        ),
        typography = AppTypography,
        content = content,
    )
}
