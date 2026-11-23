package com.aicallagent.app.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aicallagent.app.agent.CallRecord
import com.aicallagent.app.agent.CloudLlm
import com.aicallagent.app.agent.PromptBuilder
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.LimeButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.NoirField
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.components.Waveform
import com.aicallagent.app.ui.components.noirBackground
import com.aicallagent.app.ui.theme.Noir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoirConfirm(title: String, body: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.clip(shape).background(Color(0xFF111111)).border(1.dp, Noir.HairHi, shape).padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Noir.Text)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Noir.Dim)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HairButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
                if (destructive) HairButton(confirm, Modifier.weight(1f), tint = Noir.Red, onClick = onConfirm)
                else LimeButton(confirm, Modifier.weight(1f), height = 40.dp, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().noirBackground()) { content() }
    }
}

@Composable
private fun TopBar(label: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Noir.Text) }
        MonoLabel(label, Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun CallDetailDialog(state: AppState, record: CallRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val name = state.config.assistant
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) NoirConfirm(
        title = "Delete this call?",
        body = "The transcript and summary for ${record.displayName} will be removed from this phone.",
        confirm = "Delete", destructive = true, onDismiss = { confirmDelete = false },
    ) { confirmDelete = false; state.deleteCall(record.id); state.toast("Call deleted", ToastKind.INFO); onDismiss() }
    FullScreen(onDismiss) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TopBar("call / detail", onDismiss) }
            item {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(record.displayName, style = MaterialTheme.typography.displayMedium, color = Noir.Text)
                    Spacer(Modifier.height(6.dp))
                    val time = DateFormat.getMediumDateFormat(context).format(record.startedAt) + " · " + DateFormat.getTimeFormat(context).format(record.startedAt)
                    MonoLabel(listOfNotNull(record.number.ifBlank { null }, time, "${record.durationSec / 60}m ${record.durationSec % 60}s").joinToString("  ·  "))
                }
            }
            item {
                Smoke(accent = record.urgent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel("summary", Modifier.weight(1f))
                        if (record.urgent) MonoLabel("urgent", color = Noir.Red)
                        if (record.spam) MonoLabel("  spam", color = Noir.Amber)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (record.title.isNotBlank()) Text(record.title, style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                    Text(
                        record.summary.ifBlank { "No summary — summaries need a Gemini or ChatGPT key." },
                        style = MaterialTheme.typography.bodyMedium, color = if (record.summary.isBlank()) Noir.Faint else Noir.Dim,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.number.isNotBlank()) LimeButton("Call back", Modifier.weight(1f), icon = Icons.Rounded.Call, height = 48.dp) { state.dial(record.number) }
                    HairButton("Delete", icon = Icons.Rounded.DeleteOutline, tint = Noir.Red, modifier = Modifier.height(48.dp)) { confirmDelete = true }
                }
            }
            item { MonoLabel("[ transcript ]", Modifier.padding(top = 8.dp)) }
            items(record.transcript) { turn -> Bubble(fromCaller = turn.role == "user", who = if (turn.role == "user") "caller" else name, text = turn.text) }
        }
    }
}

@Composable
private fun Bubble(fromCaller: Boolean, who: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromCaller) Arrangement.Start else Arrangement.End) {
        Column(horizontalAlignment = if (fromCaller) Alignment.Start else Alignment.End) {
            MonoLabel(who, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), color = if (fromCaller) Noir.Faint else Noir.Lime)
            val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (fromCaller) 4.dp else 16.dp, bottomEnd = if (fromCaller) 16.dp else 4.dp)
            Text(
                text,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(if (fromCaller) Color(0x14FFFFFF) else Color(0x14C8FF2E))
                    .border(1.dp, if (fromCaller) Noir.Hair else Noir.Lime.copy(alpha = 0.45f), shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium, color = Noir.Text,
            )
        }
    }
}

/** Chat as if you were the caller, using the live persona and model. */
@Composable
fun PlaygroundDialog(state: AppState, onDismiss: () -> Unit) {
    val c = state.config
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf(CloudLlm.Turn("assistant", PromptBuilder.suggestedGreeting(c))) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size, thinking) { list.animateScrollToItem((turns.size + 1).coerceAtLeast(0)) }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || thinking) return
        input = ""; error = ""
        turns.add(CloudLlm.Turn("user", text))
        thinking = true
        val snapshot = turns.toList()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CloudLlm.speakable(CloudLlm.complete(c.provider, state.apiKey(c.provider), c.model(), PromptBuilder.systemPrompt(c), snapshot))
                }
            }
            result.onSuccess { turns.add(CloudLlm.Turn("assistant", it)) }
                .onFailure { e -> error = (e as? CloudLlm.LlmException)?.let { CloudLlm.friendlyError(it.code) } ?: "Failed: ${e.javaClass.simpleName}" }
            thinking = false
        }
    }

    FullScreen(onDismiss) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
            TopBar("${c.assistant} / test call", onDismiss) {
                IconButton(onClick = { turns.clear(); turns.add(CloudLlm.Turn("assistant", PromptBuilder.suggestedGreeting(c))); error = "" }) {
                    Icon(Icons.Rounded.Refresh, "Restart", tint = Noir.Dim)
                }
            }
            if (c.provider == Provider.LOCAL || !state.status.brainReady) {
                Smoke(Modifier.padding(20.dp)) {
                    Text("Test calls use Gemini or ChatGPT. Add a key in Brain first.", style = MaterialTheme.typography.bodyMedium, color = Noir.Text)
                }
                return@Column
            }
            Text(
                "You're the caller. Type what you'd say — ${c.assistant} replies exactly as on a real call.",
                style = MaterialTheme.typography.bodySmall, color = Noir.Faint, modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(turns) { t -> Bubble(fromCaller = t.role == "user", who = if (t.role == "user") "you (caller)" else c.assistant, text = t.text) }
                if (thinking) item { Row(Modifier.padding(top = 6.dp)) { Waveform(live = true) } }
                if (error.isNotEmpty()) item { MonoLabel(error, color = Noir.Red) }
            }
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoirField(input, { input = it }, "Say something", Modifier.weight(1f), placeholder = "Hello, is Rahul there?")
                Box(
                    Modifier.padding(start = 8.dp).size(54.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (input.isNotBlank() && !thinking) Noir.Lime else Color(0xFF1A1A1A))
                        .clickable(enabled = input.isNotBlank() && !thinking) { send() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (input.isNotBlank() && !thinking) Noir.Ink else Noir.Faint) }
            }
        }
    }
}
