package com.aicallagent.app.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aicallagent.app.agent.AnswerMode
import com.aicallagent.app.agent.CallRecord
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.agent.ReplyLanguage
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.Monogram
import com.aicallagent.app.ui.components.NoirDivider
import com.aicallagent.app.ui.components.Segmented
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.components.StatusDot
import com.aicallagent.app.ui.components.Waveform
import com.aicallagent.app.ui.theme.Noir
import java.util.Calendar

@Composable
fun HomeScreen(state: AppState, onOpenCall: (CallRecord) -> Unit, onGoBrain: () -> Unit) {
    val s = state.status
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopRow(state) }
        item { Headline(state) }
        item { BrainStrip(state, onGoBrain) }
        item { ModeBlock(state) }
        if (!s.allReady) item { SetupBlock(state, onGoBrain) }
        item { MonoLabel("[ recent · ${state.calls.size} ]", Modifier.padding(top = 6.dp)) }
        if (state.calls.isEmpty()) {
            item {
                Smoke(padding = 18.dp) {
                    Text("No calls yet.", style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                    Spacer(Modifier.height(4.dp))
                    Text("When ${state.config.assistant} answers, the transcript and a one-line summary land here.", style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
                }
            }
        } else {
            item {
                Smoke(padding = 0.dp) {
                    state.calls.take(20).forEachIndexed { i, record ->
                        if (i > 0) NoirDivider()
                        CallRow(record) { onOpenCall(record) }
                    }
                }
            }
        }
        item { SamsungBlock(state) }
    }
}

@Composable
private fun TopRow(state: AppState) {
    val s = state.status
    val (word, color) = when {
        s.onCall -> "live" to Noir.Lime
        !s.coreReady -> "setup" to Noir.Amber
        state.config.mode == AnswerMode.OFF -> "off" to Noir.Faint
        s.armed -> "armed" to Noir.Lime
        else -> "idle" to Noir.Amber
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MonoLabel("Otto / v0.2", Modifier.weight(1f))
        StatusDot(color)
        Spacer(Modifier.width(6.dp))
        MonoLabel(word)
    }
}

@Composable
internal fun Headline(state: AppState) {
    val s = state.status
    val c = state.config
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; else -> "Evening" }
    val (plain, accent) = when {
        s.onCall -> "On a call\nfor" to "you."
        !s.accessibility || !s.phonePerm -> "Almost\nready." to "Finish setup."
        !s.brainReady -> "I need a\nbrain." to "Add a key."
        c.mode == AnswerMode.OFF -> "Resting.\nCalls are" to "all yours."
        s.preparing -> "Waking\nthe on-device" to "brain…"
        c.mode == AnswerMode.ASK -> "I'll ask\nbefore I" to "pick up."
        else -> "I pick up\nafter" to "${c.autoDelaySec} seconds."
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(if (c.ownerName.isBlank()) "$greet." else "$greet, ${c.ownerName.trim()}.", style = MaterialTheme.typography.bodyLarge, color = Noir.Dim)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                append(plain); append("\n")
                withStyle(SpanStyle(color = Noir.Lime)) { append(accent) }
            },
            style = MaterialTheme.typography.displayLarge,
            color = Noir.Text,
        )
    }
}

@Composable
private fun BrainStrip(state: AppState, onGoBrain: () -> Unit) {
    val c = state.config
    val s = state.status
    Smoke(onClick = onGoBrain) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Waveform(live = s.onCall, color = if (s.brainReady) Noir.Lime else Noir.Faint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (c.provider == Provider.LOCAL) "gemma · on-device" else c.model(),
                    style = MaterialTheme.typography.labelMedium, color = Noir.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                MonoLabel("${if (c.language == ReplyLanguage.MATCH) "auto" else c.language.native.takeIf { c.language == ReplyLanguage.HINDI || c.language == ReplyLanguage.MARATHI } ?: c.language.label} · ${c.style.label}", maxLines = 1)
            }
            Text(
                if (s.brainReady) (if (c.provider == Provider.LOCAL) "local" else "cloud") else "no key",
                style = MaterialTheme.typography.labelMedium, color = if (s.brainReady) Noir.Lime else Noir.Amber,
            )
        }
    }
}

@Composable
internal fun ModeBlock(state: AppState) {
    val c = state.config
    Column {
        MonoLabel("[ when a call rings ]", Modifier.padding(top = 4.dp, bottom = 8.dp))
        Segmented(
            listOf("Off", "Ask me", "Auto ${c.autoDelaySec}s"),
            selected = c.mode.ordinal,
        ) { i ->
            val mode = AnswerMode.entries[i]
            if (mode == c.mode) return@Segmented
            state.update(reapply = true) { it.copy(mode = mode) }
            state.toast(
                when (mode) {
                    AnswerMode.OFF -> "${c.assistant} is off · calls are all yours"
                    AnswerMode.ASK -> "Ask me is on · tap the button when a call rings"
                    AnswerMode.AUTO -> "Auto is on · ${c.assistant} picks up after ${c.autoDelaySec}s"
                },
                if (mode == AnswerMode.OFF) ToastKind.INFO else ToastKind.OK,
            )
        }
        AnimatedVisibility(c.mode == AnswerMode.AUTO) {
            Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                MonoLabel("delay")
                Slider(
                    value = c.autoDelaySec.toFloat(),
                    onValueChange = { v -> state.update { it.copy(autoDelaySec = v.toInt()) } },
                    valueRange = 3f..30f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(thumbColor = Noir.Lime, activeTrackColor = Noir.Lime, inactiveTrackColor = Color(0x22FFFFFF)),
                )
                Text("${c.autoDelaySec}s", style = MaterialTheme.typography.labelMedium, color = Noir.Lime)
            }
        }
        val status = state.activeStatus
        Text(
            if (status != null) "Your status “${status.label}” is on, so ${c.assistant} answers every call after ${c.autoDelaySec}s. This setting is back when it ends."
            else when (c.mode) {
                AnswerMode.OFF -> "${c.assistant} won't touch your calls."
                AnswerMode.ASK -> "A “Let ${c.assistant} answer” button appears on incoming calls. Answer yourself and ${c.assistant} stays out, unless you switch to Text call."
                AnswerMode.AUTO -> "If you don't answer within ${c.autoDelaySec}s, ${c.assistant} picks up. Tap “Not this time” to skip a call."
            },
            style = MaterialTheme.typography.bodySmall, color = Noir.Faint, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}

@Composable
private fun SetupBlock(state: AppState, onGoBrain: () -> Unit) {
    val s = state.status
    val name = state.config.assistant
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state.refresh() }
    Column {
        MonoLabel("[ setup ]", Modifier.padding(top = 4.dp, bottom = 8.dp))
        Smoke(padding = 6.dp, accent = !s.coreReady) {
            SetupRow("01", "Accessibility", "Read the call screen, tap Text call", s.accessibility, "Open") { state.openAccessibilitySettings() }
            SetupRow("02", "Phone", "Know when a call rings", s.phonePerm, "Allow") {
                perms.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.POST_NOTIFICATIONS))
            }
            SetupRow("03", "Brain", "Gemini or ChatGPT key", s.brainReady, "Add", onClick = onGoBrain)
            SetupRow("04", "Caller names", "Show who called · optional", s.callLogPerm, "Allow") { perms.launch(arrayOf(Manifest.permission.READ_CALL_LOG)) }
            SetupRow("05", "Summaries", "Notify after each call · optional", s.notifPerm, "Allow") { perms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
            SetupRow("06", "Background", "Keep $name awake · optional", s.battery, "Allow") { state.requestBatteryExemption() }
        }
    }
}

@Composable
private fun SetupRow(n: String, title: String, sub: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(n, style = MaterialTheme.typography.labelMedium, color = if (done) Noir.Lime else Noir.Faint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (done) Noir.Dim else Noir.Text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Noir.Faint)
        }
        if (done) Icon(Icons.Rounded.Check, "Done", tint = Noir.Lime, modifier = Modifier.size(22.dp))
        else HairButton(action, onClick = onClick)
    }
}

@Composable
internal fun CallRow(record: CallRecord, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(record.displayName, highlight = record.urgent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.displayName, style = MaterialTheme.typography.titleSmall, color = Noir.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (record.urgent) { Spacer(Modifier.width(8.dp)); MonoLabel("urgent", color = Noir.Red) }
                if (record.spam) { Spacer(Modifier.width(8.dp)); MonoLabel("spam", color = Noir.Amber) }
            }
            val line = record.summary.ifBlank { record.transcript.firstOrNull { it.role == "user" }?.text ?: "" }
            Text(line, style = MaterialTheme.typography.bodySmall, color = Noir.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(ago(record.startedAt), style = MaterialTheme.typography.labelMedium, color = Noir.Faint)
    }
}

fun ago(time: Long): String = DateUtils.getRelativeTimeSpanString(
    time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
).toString().replace(" ago", "").replace(" mins", "m").replace(" min", "m").replace(" hours", "h").replace(" hour", "h").replace(" hr", "h").replace(" hrs", "h")

@Composable
private fun SamsungBlock(state: AppState) {
    Column {
        MonoLabel("[ samsung setup ]", Modifier.padding(top = 6.dp, bottom = 8.dp))
        Smoke {
            Tip("a", "Phone → Settings → Text call: on. Pick a voice — a Hindi voice for हिंदी / मराठी replies.")
            Tip("b", "Phone → Settings → Call display while using apps → Full screen, so the button can show.")
            Tip("c", "Paste your greeting (Persona → 05) into Text call's greeting so callers know who's speaking.")
            Spacer(Modifier.height(8.dp))
            HairButton("Open call settings", onClick = { state.openCallSettings() })
        }
    }
}

@Composable
private fun Tip(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(n, style = MaterialTheme.typography.labelMedium, color = Noir.Lime)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
    }
}
