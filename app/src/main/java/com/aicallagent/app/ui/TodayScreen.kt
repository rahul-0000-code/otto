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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aicallagent.app.agent.AnswerMode
import com.aicallagent.app.agent.CallRecord
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.ui.components.ChipFlow
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.LimeButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.Monogram
import com.aicallagent.app.ui.components.NoirChip
import com.aicallagent.app.ui.components.NoirDivider
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.components.StatusDot
import com.aicallagent.app.ui.theme.Grotesk
import com.aicallagent.app.ui.theme.Noir
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Calls from today, plus the urgent ones the owner hasn't dealt with yet. */
private fun isToday(time: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

internal fun needsYou(state: AppState): List<CallRecord> =
    state.calls.filter { it.urgent && it.id !in state.handledCalls && System.currentTimeMillis() - it.startedAt < 48 * 3600_000L }

private fun duration(totalMs: Long): String {
    val s = (totalMs / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${"%02d".format(s % 60)}s"
}

/**
 * Today: one screen, no tab bar. The avatar opens "You" (profile, persona, brain, setup), the brain
 * pill at the bottom opens the brain, and the headline counts what Otto handled today.
 */
@Composable
fun TodayScreen(
    state: AppState,
    onAvatar: () -> Unit,
    onBrain: () -> Unit,
    onSetup: () -> Unit,
    onAllCalls: () -> Unit,
    onOpenCall: (CallRecord) -> Unit,
    onStatus: () -> Unit,
    onBack: () -> Unit,
) {
    val s = state.status
    val today = state.calls.filter { isToday(it.startedAt) }
    val needs = needsYou(state)
    val recent = state.calls.filter { it !in needs }.take(4)
    val setupLeft = listOf(s.accessibility, s.phonePerm, s.brainReady, s.callLogPerm, s.notifPerm, s.battery, state.samsungConfirmed).count { !it }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar(state, setupLeft, onAvatar, onBack) }
            item { StatusLine(state) }
            item {
                if (today.isEmpty()) Headline(state)
                else TodayHeadline(today.size, needs.size)
            }
            if (today.isNotEmpty()) item { StatsRow(today) }
            item { StatusCard(state, onStatus) }
            item { ModeBlock(state) }
            if (setupLeft > 0) item { SetupNudge(setupLeft, onSetup) }
            if (needs.isNotEmpty()) {
                item { MonoLabel("[ needs you ]", Modifier.padding(top = 4.dp)) }
                needs.forEach { record -> item(key = "need-${record.id}") { NeedCard(state, record, onOpenCall) } }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("[ recent · ${state.calls.size} ]", Modifier.weight(1f))
                    if (state.calls.isNotEmpty()) {
                        Text(
                            "ALL CALLS →", style = MaterialTheme.typography.labelSmall, color = Noir.Lime,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAllCalls).padding(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            item {
                if (recent.isEmpty() && needs.isEmpty()) {
                    Smoke(padding = 18.dp) {
                        Text("No calls yet.", style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                        Spacer(Modifier.height(4.dp))
                        Text("When ${state.config.assistant} answers, the transcript and a one-line summary land here.", style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
                    }
                } else if (recent.isNotEmpty()) {
                    Smoke(padding = 0.dp) {
                        recent.forEachIndexed { i, record ->
                            if (i > 0) NoirDivider()
                            CallRow(record) { onOpenCall(record) }
                        }
                    }
                }
            }
        }
        BrainPill(state, Modifier.align(Alignment.BottomCenter), onBrain)
    }
}

/** "Right now": what the owner is doing. One tap on a chip sets it; Otto tells callers the message. */
@Composable
private fun StatusCard(state: AppState, onStatus: () -> Unit) {
    val c = state.config
    val status = state.activeStatus
    Column {
        MonoLabel("[ right now ]", Modifier.padding(top = 4.dp, bottom = 8.dp))
        if (status == null) {
            ChipFlow {
                c.statuses.take(4).forEach { s -> NoirChip("${s.emoji} ${s.label}", selected = false) { state.setStatus(s) } }
                NoirChip("More…", selected = false, onClick = onStatus)
            }
            Text(
                "Set what you're doing and ${c.assistant} answers every call, telling callers why you can't talk.",
                style = MaterialTheme.typography.bodySmall, color = Noir.Faint, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        } else {
            Smoke(padding = 16.dp, radius = 18.dp, accent = true, onClick = onStatus) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.label, style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                        MonoLabel(untilText(c.statusUntil), color = Noir.Lime)
                    }
                    HairButton("Clear") { state.setStatus(null) }
                }
                Spacer(Modifier.height(10.dp))
                Text("“${c.statusMessage(status)}”", style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${c.assistant} answers every call after ${c.autoDelaySec}s while this is on.",
                    style = MaterialTheme.typography.bodySmall, color = Noir.Faint,
                )
            }
        }
    }
}

@Composable
private fun TopBar(state: AppState, setupLeft: Int, onAvatar: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BackToDoors(Noir.Text, Noir.HairHi, onBack)
        Text(
            buildAnnotatedString { append("otto"); withStyle(SpanStyle(color = Noir.Lime)) { append(".") } },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.03).em),
            color = Noir.Text, modifier = Modifier.weight(1f),
        )
        val owner = state.config.ownerName.trim()
        val letter = owner.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Noir.Text)
                .clickable(onClick = onAvatar)
                .semantics { contentDescription = if (setupLeft > 0) "You and ${state.config.assistant}, $setupLeft setup steps left" else "You and ${state.config.assistant}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = MaterialTheme.typography.titleMedium, color = Noir.Ink)
        }
        if (setupLeft > 0) {
            Box(Modifier.offset(x = (-12).dp, y = (-16).dp).size(12.dp).clip(CircleShape).background(Noir.Bg).padding(2.dp).clip(CircleShape).background(Noir.Amber))
        }
    }
}

@Composable
private fun StatusLine(state: AppState) {
    val s = state.status
    val c = state.config
    val status = state.activeStatus
    val (word, color) = when {
        s.onCall -> "on a call" to Noir.Lime
        !s.coreReady -> "setup" to Noir.Amber
        status != null -> "${status.label} · auto" to Noir.Lime
        c.mode == AnswerMode.OFF -> "off" to Noir.Faint
        s.preparing -> "waking up" to Noir.Amber
        !s.armed -> "idle" to Noir.Amber
        c.mode == AnswerMode.ASK -> "ask me" to Noir.Lime
        else -> "auto · ${c.autoDelaySec} s" to Noir.Lime
    }
    val date = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date()).lowercase()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonoLabel("today · $date", Modifier.weight(1f))
        StatusDot(color)
        Spacer(Modifier.width(6.dp))
        MonoLabel(word, color = color)
    }
}

@Composable
private fun TodayHeadline(handled: Int, needs: Int) {
    Text(
        buildAnnotatedString {
            append("$handled call${if (handled == 1) "" else "s"}\nhandled.\n")
            withStyle(SpanStyle(color = Noir.Lime)) { append(if (needs > 0) "$needs need${if (needs == 1) "s" else ""} you." else "All clear.") }
        },
        style = MaterialTheme.typography.displayLarge, color = Noir.Text, modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StatsRow(today: List<CallRecord>) {
    val spam = today.count { it.spam }
    val total = today.sumOf { (it.endedAt - it.startedAt).coerceAtLeast(0) }
    Smoke(padding = 0.dp, radius = 18.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Stat("${today.size}", "answered", Noir.Text, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(Noir.Hair))
            Stat("$spam", "spam", if (spam > 0) Noir.Amber else Noir.Text, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(Noir.Hair))
            Stat(duration(total), "talk time", Noir.Lime, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        MonoLabel(label, maxLines = 1)
    }
}

@Composable
private fun SetupNudge(left: Int, onSetup: () -> Unit) {
    Smoke(onClick = onSetup, radius = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Noir.Amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Finish setup", style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                Text("$left of 7 steps left before every call is covered", style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
            }
            MonoLabel("open →", color = Noir.Amber)
        }
    }
}

@Composable
private fun NeedCard(state: AppState, record: CallRecord, onOpenCall: (CallRecord) -> Unit) {
    Smoke(radius = 18.dp) {
        Row(Modifier.clickable { onOpenCall(record) }, verticalAlignment = Alignment.CenterVertically) {
            Monogram(record.displayName, highlight = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.displayName, style = MaterialTheme.typography.titleSmall, color = Noir.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp)); MonoLabel("urgent", color = Noir.Red)
                }
                Text(record.summary.ifBlank { record.title }, style = MaterialTheme.typography.bodySmall, color = Noir.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(ago(record.startedAt), style = MaterialTheme.typography.labelMedium, color = Noir.Faint)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimeButton("Call back", Modifier.weight(1f), icon = Icons.Rounded.Call, height = 44.dp, enabled = record.number.isNotBlank()) {
                state.markHandled(record.id)
                state.dial(record.number)
                state.toast("Marked as handled · ${record.displayName} moved out of “needs you”")
            }
            HairButton("Done", Modifier.weight(1f)) {
                state.markHandled(record.id)
                state.toast("${record.displayName} moved out of “needs you”", ToastKind.INFO)
            }
        }
    }
}

@Composable
private fun BrainPill(state: AppState, modifier: Modifier, onBrain: () -> Unit) {
    val c = state.config
    val s = state.status
    val shape = RoundedCornerShape(23.dp)
    val (icon, text, color) = when {
        !s.brainReady -> Triple(Icons.Rounded.ErrorOutline, "no brain · set one up", Noir.Red)
        c.provider == Provider.LOCAL -> Triple(Icons.Rounded.Memory, "gemma · on-device", Noir.Lime)
        else -> Triple(Icons.Rounded.AutoAwesome, "${c.provider.label.lowercase()} · ${c.model().removePrefix("gemini-").removePrefix("gpt-")}", Noir.Lime)
    }
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(shape)
                .background(Color(0xF0181818))
                .border(1.dp, Noir.Hair, shape)
                .clickable(onClick = onBrain)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (color == Noir.Red) Noir.Red else Noir.Text, maxLines = 1)
        }
    }
}
