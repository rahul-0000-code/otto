package com.aicallagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aicallagent.app.agent.PromptBuilder
import com.aicallagent.app.assistant.VoiceCommand
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.theme.Grotesk
import com.aicallagent.app.ui.theme.Noir
import com.aicallagent.app.ui.theme.SamyColors
import kotlinx.coroutines.delay

/** One swipeable card: run [say] straight away, or (for an example) open Samy listening. */
private class Suggestion(val icon: ImageVector, val title: String, val say: String, val again: Boolean)

/**
 * Samy's own space, voice-mode style: the question on top, the living orb in the middle (tap it and
 * talk), suggestion cards you can swipe (they also drift along by themselves), then what Samy did.
 * Violet and round throughout, so it never reads as Otto's lime call screens.
 */
@Composable
fun SamyHome(state: AppState, onSettings: () -> Unit, onBack: () -> Unit) {
    val c = state.config
    val history = state.samyHistory
    val isAssistant = state.isPhoneAssistant()
    val name = c.ownerName.trim().let { if (it.isEmpty()) "" else PromptBuilder.spokenName(it) }

    val suggestions = buildList {
        history.distinctBy { it.said.lowercase() }.take(3).forEach { e ->
            add(Suggestion(iconFor(e.action), e.title, e.said, again = true))
        }
        add(Suggestion(Icons.AutoMirrored.Rounded.Chat, "Send a WhatsApp", "WhatsApp Soham where have you reached", false))
        add(Suggestion(Icons.Rounded.Navigation, "Ride somewhere by bike", "Take me to Phoenix Mall by bike", false))
        add(Suggestion(Icons.Rounded.Call, "Call someone", "Call Pooja", false))
        add(Suggestion(Icons.Rounded.Sms, "Send a text", "Text Aai I'm on my way", false))
    }

    Box(
        Modifier.fillMaxSize()
            .background(SamyColors.Ink)
            .background(Brush.radialGradient(listOf(SamyColors.Violet.copy(alpha = 0.20f), Color.Transparent), radius = 1100f)),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BackToDoors(SamyColors.Light, SamyColors.Line, onBack)
                    Text(
                        c.voice.lowercase(), color = Noir.Text, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.03).em),
                    )
                    // Samy's own settings, not Otto's profile.
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(SamyColors.Soft).border(1.dp, SamyColors.Line, CircleShape)
                            .clickable(role = Role.Button, onClick = onSettings).semantics { contentDescription = "${c.voice} settings" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Tune, null, tint = SamyColors.Light, modifier = Modifier.size(20.dp)) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (name.isNotEmpty()) Text("Hi $name,", style = MaterialTheme.typography.titleMedium, color = SamyColors.Text2)
                    Text("What do you want\nto do today?", style = MaterialTheme.typography.displayMedium, color = Noir.Text)
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SamyOrb(
                        Modifier.size(250.dp).clip(CircleShape)
                            .clickable(role = Role.Button) { state.openSamy() }
                            .semantics { contentDescription = "Talk to ${c.voice}" },
                    )
                    Text(
                        if (isAssistant) "Tap the orb and talk, or swipe up from a corner in any app" else "Tap the orb and talk",
                        style = MaterialTheme.typography.bodyMedium, color = SamyColors.Text2, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    if (!isAssistant) {
                        Text(
                            "Open me from any app: set up the gesture",
                            style = MaterialTheme.typography.labelLarge, color = SamyColors.Violet,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable { state.openAssistantSettings() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            item { Suggestions(state, suggestions) }
            if (history.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MonoLabel("[ recent ]", color = SamyColors.Text2)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(SamyColors.Violet.copy(alpha = 0.08f))
                                .border(1.dp, SamyColors.Violet.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            history.take(6).forEachIndexed { i, e ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(SamyColors.Violet.copy(alpha = 0.16f)))
                                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(9.dp).clip(CircleShape).background(SamyColors.Violet))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title, style = MaterialTheme.typography.bodyLarge, color = Noir.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (e.detail.isNotBlank()) Text(e.detail, style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(sinceShort(e.at), style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Swipe through them; they also move on by themselves every few seconds while you're not touching. */
@Composable
private fun Suggestions(state: AppState, items: List<Suggestion>) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (true) {
            delay(4200)
            if (!pager.isScrollInProgress && items.isNotEmpty()) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(start = 20.dp, end = 64.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val s = items[page]
            Column(
                Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(SamyColors.Violet.copy(alpha = 0.26f), SamyColors.Violet.copy(alpha = 0.07f))))
                    .border(1.dp, SamyColors.Line, RoundedCornerShape(30.dp))
                    .clickable(role = Role.Button) { if (s.again) state.openSamy(s.say) else state.openSamy() }
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(SamyColors.Violet), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = Noir.Ink, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (s.again) {
                        Row(Modifier.clip(RoundedCornerShape(50)).background(SamyColors.Soft).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, null, tint = SamyColors.Light, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("again", style = MaterialTheme.typography.labelMedium, color = SamyColors.Light)
                        }
                    }
                }
                Column {
                    Text(s.title, style = MaterialTheme.typography.titleLarge, color = Noir.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("“${s.say}”", style = MaterialTheme.typography.bodyMedium, color = SamyColors.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (on) SamyColors.Violet else SamyColors.Line),
                )
            }
        }
    }
}

private fun iconFor(action: VoiceCommand.Action): ImageVector = when (action) {
    VoiceCommand.Action.CALL -> Icons.Rounded.Call
    VoiceCommand.Action.SMS -> Icons.Rounded.Sms
    VoiceCommand.Action.WHATSAPP -> Icons.AutoMirrored.Rounded.Chat
    VoiceCommand.Action.NAVIGATE -> Icons.Rounded.Navigation
    VoiceCommand.Action.UNKNOWN -> Icons.Rounded.History
}

private fun sinceShort(at: Long): String {
    val min = (System.currentTimeMillis() - at) / 60_000
    return when {
        min < 1 -> "now"
        min < 60 -> "${min}m"
        min < 24 * 60 -> "${min / 60}h"
        else -> "${min / (24 * 60)}d"
    }
}
