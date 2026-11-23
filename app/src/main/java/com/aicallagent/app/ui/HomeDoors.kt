package com.aicallagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aicallagent.app.agent.AnswerMode
import com.aicallagent.app.agent.PromptBuilder
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.theme.Grotesk
import com.aicallagent.app.ui.theme.Noir
import com.aicallagent.app.ui.theme.SamyColors
import java.util.Calendar

/** Where the app opens: Otto's lime card for calls, Samy's violet orb card for voice. */
enum class Space { HOME, OTTO, SAMY }

/**
 * "Who do you need?": two floating doors. Otto's is square and lime with today's calls; Samy's is
 * round and violet with the orb. Each opens its own space; Back comes here.
 */
@Composable
fun HomeDoors(state: AppState, onOtto: () -> Unit, onSamy: () -> Unit, onAvatar: () -> Unit) {
    val c = state.config
    val s = state.status
    val startOfDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val answered = state.calls.count { it.startedAt >= startOfDay }
    val needs = needsYou(state).size
    val samyToday = state.samyHistory.count { it.at >= startOfDay }
    val status = state.activeStatus
    val ottoState = when {
        !s.coreReady -> "setup needed" to Noir.Amber
        status != null -> "${status.label} · auto" to Noir.Lime
        c.mode == AnswerMode.OFF -> "off" to Noir.Faint
        c.mode == AnswerMode.ASK -> "ask me" to Noir.Lime
        else -> "auto · ${c.autoDelaySec} s" to Noir.Lime
    }
    val name = c.ownerName.trim().let { if (it.isEmpty()) "" else ", " + PromptBuilder.spokenName(it) }
    val isAssistant = state.isPhoneAssistant()

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString { append("otto"); withStyle(SpanStyle(color = Noir.Lime)) { append(".") } },
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.03).em),
                color = Noir.Text, modifier = Modifier.weight(1f),
            )
            val letter = c.ownerName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Noir.Text).clickable(onClick = onAvatar).semantics { contentDescription = "You" },
                contentAlignment = Alignment.Center,
            ) { Text(letter, style = MaterialTheme.typography.titleMedium, color = Noir.Ink) }
        }
        Text("Who do you\nneed$name?", style = MaterialTheme.typography.displayMedium, color = Noir.Text, modifier = Modifier.padding(top = 6.dp))

        // Otto: square, lime, facts in mono.
        val ottoShape = RoundedCornerShape(18.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 240.dp)
                .shadow(24.dp, ottoShape, ambientColor = Noir.Lime, spotColor = Noir.Lime)
                .clip(ottoShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF141A05), Color(0xFF0A0A0A))))
                .border(1.5.dp, Noir.Lime.copy(alpha = 0.6f), ottoShape)
                .clickable(role = Role.Button, onClick = onOtto)
                .semantics { contentDescription = "Otto, your call assistant. $answered answered today, $needs need you." }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(Noir.Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, null, tint = Noir.Ink, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                MonoLabel("● ${ottoState.first}", color = ottoState.second)
            }
            Column {
                Text("Otto", style = MaterialTheme.typography.headlineLarge, color = Noir.Text)
                Text("Answers your calls when you can't", style = MaterialTheme.typography.bodyLarge, color = Noir.Dim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("$answered answered", Noir.Text, Color(0x2EFFFFFF))
                if (needs > 0) Tag("$needs need you", Noir.Amber, Noir.Amber.copy(alpha = 0.55f))
            }
        }

        // Samy: round, violet, the orb.
        val samyShape = RoundedCornerShape(36.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 240.dp)
                .shadow(28.dp, samyShape, ambientColor = SamyColors.Violet, spotColor = SamyColors.Violet)
                .clip(samyShape)
                .background(SamyColors.Surface)
                .background(Brush.radialGradient(listOf(SamyColors.Violet.copy(alpha = 0.42f), SamyColors.Violet.copy(alpha = 0.06f)), radius = 700f))
                .border(1.dp, SamyColors.Violet.copy(alpha = 0.4f), samyShape)
                .clickable(role = Role.Button, onClick = onSamy)
                .semantics { contentDescription = "${c.voice}, your voice assistant" }
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SamyOrb(Modifier.size(72.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    if (isAssistant) "Swipe from a corner, anywhere" else "Tap to talk",
                    style = MaterialTheme.typography.bodyMedium, color = SamyColors.Light,
                )
            }
            Column {
                Text(c.voice, style = MaterialTheme.typography.headlineLarge, color = Noir.Text)
                Text("Calls, texts, WhatsApp and Maps, by voice", style = MaterialTheme.typography.bodyLarge, color = SamyColors.Text2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("“Call Pooja”")
                Pill(if (samyToday == 0) "Nothing yet today" else "$samyToday done today")
            }
        }

        MonoLabel("shared · profile · gemini key · contacts", Modifier.fillMaxWidth().padding(top = 2.dp), color = Noir.Faint)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Tag(text: String, color: Color, line: Color) {
    MonoLabel(text, Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, line, RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 6.dp), color = color)
}

@Composable
private fun Pill(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = SamyColors.Light,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(SamyColors.Soft).padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

/** The ‹ in each space's top bar: back to the two doors. */
@Composable
internal fun BackToDoors(tint: Color, line: Color, onBack: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(1.dp, line, CircleShape).clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Back to home" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = tint, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(12.dp))
}
