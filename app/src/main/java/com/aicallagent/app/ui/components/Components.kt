package com.aicallagent.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aicallagent.app.ui.theme.Mono
import com.aicallagent.app.ui.theme.Noir

/** Pure black with a soft lime glow in one corner. */
fun Modifier.noirBackground(glowTopRight: Boolean = true): Modifier = this
    .background(Noir.Bg)
    .drawBehind {
        val center = if (glowTopRight) Offset(size.width * 1.0f, size.height * 0.02f) else Offset(0f, size.height * 0.95f)
        val r = size.width * 0.75f
        drawCircle(Brush.radialGradient(listOf(Noir.Lime.copy(alpha = 0.20f), Color.Transparent), center = center, radius = r), radius = r, center = center)
    }

/** Smoked glass panel with a hairline border. */
@Composable
fun Smoke(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    radius: Dp = 20.dp,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Noir.Smoke, Noir.SmokeLo)))
            .border(1.dp, if (accent) Noir.Lime.copy(alpha = 0.7f) else Noir.Hair, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Uppercase monospace label, e.g. "[ when a call rings ]". */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Noir.Faint, maxLines: Int = Int.MAX_VALUE) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color, maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

@Composable
fun LimeButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    outlined: Boolean = false,
    height: Dp = 54.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val content = if (outlined) Noir.Lime else Noir.Ink
    Row(
        modifier
            .height(height)
            .then(if (!outlined && enabled) Modifier.shadow(18.dp, shape, ambientColor = Noir.Lime, spotColor = Noir.Lime) else Modifier)
            .clip(shape)
            .then(
                when {
                    !enabled -> Modifier.background(Color(0xFF1A1A1A))
                    outlined -> Modifier.border(1.dp, Noir.Lime, shape)
                    else -> Modifier.background(Noir.Lime)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) content else Noir.Faint
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun HairButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Noir.Text, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, if (tint == Noir.Text) Noir.HairHi else tint.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
    }
}

/** Segmented control inside a smoke track; the selected segment turns lime. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Noir.Smoke, Noir.SmokeLo)))
            .border(1.dp, Noir.Hair, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Noir.Lime else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) Noir.Ink else Noir.Dim)
            }
        }
    }
}

@Composable
fun NoirChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        modifier = modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Noir.Lime) else Modifier.border(1.dp, Noir.HairHi, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        color = if (selected) Noir.Ink else Color(0xFFC8C8C8),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
}

@Composable
fun NoirField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    visual: VisualTransformation = VisualTransformation.None,
    keyboard: KeyboardType = KeyboardType.Text,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label.uppercase(), fontFamily = Mono) },
        placeholder = placeholder?.let { { Text(it, color = Noir.Faint) } },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = trailing,
        textStyle = if (mono) TextStyle(fontFamily = Mono, color = Noir.Text, fontSize = MaterialTheme.typography.bodySmall.fontSize, lineHeight = MaterialTheme.typography.bodySmall.lineHeight)
        else MaterialTheme.typography.bodyLarge.copy(color = Noir.Text),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Noir.Lime,
            unfocusedBorderColor = Noir.HairHi,
            focusedLabelColor = Noir.Lime,
            unfocusedLabelColor = Noir.Faint,
            cursorColor = Noir.Lime,
            focusedContainerColor = Color(0x0AFFFFFF),
            unfocusedContainerColor = Color(0x0AFFFFFF),
        ),
    )
}

/** Lime equaliser bars; they dance while the assistant is live. */
@Composable
fun Waveform(live: Boolean, modifier: Modifier = Modifier, color: Color = Noir.Lime) {
    val base = listOf(14, 30, 42, 22, 34, 16, 26)
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (live) 650 else 2600), RepeatMode.Reverse), label = "phase")
    Row(modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        base.forEachIndexed { i, h ->
            val wobble = if (i % 2 == 0) phase else 1f - phase
            val scale = if (live) 0.45f + 0.55f * wobble else 0.85f + 0.15f * wobble
            Box(Modifier.width(4.dp).height((h * scale).dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
fun StatusDot(color: Color = Noir.Lime, size: Dp = 7.dp) {
    Box(
        Modifier.size(size)
            .shadow(8.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun NoirDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Color(0x1AFFFFFF)))
}

/** Top toast for confirmations and errors; dismisses itself. */
@Composable
fun NoirToast(msg: com.aicallagent.app.ui.ToastMsg?, modifier: Modifier = Modifier, onDone: (Long) -> Unit) {
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(msg) }
    if (msg != null) shown = msg
    androidx.compose.runtime.LaunchedEffect(msg?.id) {
        if (msg != null) { kotlinx.coroutines.delay(if (msg.kind == com.aicallagent.app.ui.ToastKind.ERROR) 4200 else 2600); onDone(msg.id) }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = msg != null,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
    ) {
        val m = shown ?: return@AnimatedVisibility
        val accent = when (m.kind) {
            com.aicallagent.app.ui.ToastKind.OK -> Noir.Lime
            com.aicallagent.app.ui.ToastKind.ERROR -> Noir.Red
            com.aicallagent.app.ui.ToastKind.INFO -> Noir.Text
        }
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .windowInsetsPaddingStatus()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .fillMaxWidth()
                .shadow(20.dp, shape)
                .clip(shape)
                .background(Color(0xF5141414))
                .border(1.dp, accent.copy(alpha = 0.6f), shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text(if (m.kind == com.aicallagent.app.ui.ToastKind.ERROR) "!" else if (m.kind == com.aicallagent.app.ui.ToastKind.OK) "✓" else "i",
                    color = Noir.Ink, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(12.dp))
            Text(m.text, style = MaterialTheme.typography.bodyMedium, color = Noir.Text)
        }
    }
}

@Composable
private fun Modifier.windowInsetsPaddingStatus(): Modifier =
    this.then(Modifier.padding(top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))

/** Rounded-square monogram used for callers. */
@Composable
fun Monogram(name: String, size: Dp = 40.dp, highlight: Boolean = false) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size).clip(shape)
            .background(if (highlight) Noir.Lime else Color(0x0DFFFFFF))
            .border(1.dp, if (highlight) Noir.Lime else Color(0x2EFFFFFF), shape),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = MaterialTheme.typography.titleMedium, color = if (highlight) Noir.Ink else Noir.Text) }
}
