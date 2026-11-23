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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aicallagent.app.agent.CatalogModel
import com.aicallagent.app.agent.CloudLlm
import com.aicallagent.app.agent.ModelCatalog
import com.aicallagent.app.agent.ModelDownloads
import com.aicallagent.app.agent.ReplyLanguage
import com.aicallagent.app.local.LocalModelRuntime
import com.aicallagent.app.local.ReplyRequest
import com.aicallagent.app.model.CallConversation
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.suspendCancellableCoroutine
import com.aicallagent.app.agent.Models
import com.aicallagent.app.agent.PromptBuilder
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.ui.components.ChipFlow
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.LimeButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.NoirChip
import com.aicallagent.app.ui.components.NoirField
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.theme.Noir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrainScreen(state: AppState, onTry: () -> Unit, inSheet: Boolean = false) {
    val c = state.config
    LazyColumn(
        Modifier.fillMaxSize().then(if (inSheet) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)).imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonoLabel("${c.assistant} / brain", Modifier.padding(top = 4.dp)) }
        item {
            Text(
                buildAnnotatedString { append("Pick my\n"); withStyle(SpanStyle(color = Noir.Lime)) { append("brain.") } },
                style = MaterialTheme.typography.displayMedium, color = Noir.Text, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Provider.entries.forEach { p ->
                    ProviderRow(p, selected = c.provider == p, ready = if (p == Provider.LOCAL) state.status.localModel else state.apiKey(p).isNotEmpty()) {
                        state.update(reapply = true) { it.copy(provider = p) }
                    }
                }
            }
        }
        if (c.provider == Provider.LOCAL) {
            item { LocalBlock(state) }
        } else {
            item { KeyBlock(state, c.provider) }
            item { ModelBlock(state, c.provider) }
            item { TestBlock(state, onTry) }
        }
        item {
            Text(
                if (c.provider == Provider.LOCAL) "On-device: nothing leaves your phone. Samsung still does the speech-to-text."
                else "During AI-answered calls, the caller's transcribed words go to ${c.provider.label} to write replies. Your key is encrypted with Android Keystore and never leaves this phone except to call ${c.provider.label}.",
                style = MaterialTheme.typography.bodySmall, color = Noir.Faint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ProviderRow(p: Provider, selected: Boolean, ready: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0x14C8FF2E) else Color(0x0AFFFFFF))
            .border(1.dp, if (selected) Noir.Lime else Noir.Hair, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).border(1.5.dp, if (selected) Noir.Lime else Noir.HairHi, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(Noir.Lime)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                if (p == Provider.GEMINI) { Spacer(Modifier.width(8.dp)); MonoLabel("recommended", color = Noir.Lime) }
            }
            Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
        }
        MonoLabel(if (ready) "ready" else if (p == Provider.LOCAL) "no model" else "no key", color = if (ready) Noir.Lime else Noir.Faint)
    }
}

@Composable
private fun KeyBlock(state: AppState, provider: Provider) {
    var key by remember(provider) { mutableStateOf(state.apiKey(provider)) }
    var show by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (confirmRemove) NoirConfirm(
        title = "Remove ${provider.label} key?",
        body = "Otto won't be able to answer calls with ${provider.label} until you add a key again.",
        confirm = "Remove", destructive = true,
        onDismiss = { confirmRemove = false },
    ) { confirmRemove = false; key = ""; state.setApiKey(provider, ""); state.toast("${provider.label} key removed", ToastKind.INFO) }
    val saved = key.trim() == state.apiKey(provider)
    Smoke {
        MonoLabel("01 — ${provider.label} api key")
        Spacer(Modifier.height(10.dp))
        NoirField(
            key, { key = it.trim() }, "API key", mono = true,
            visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboard = KeyboardType.Password,
            placeholder = if (provider == Provider.GEMINI) "AIza…" else "sk-…",
            trailing = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide key" else "Show key", tint = Noir.Dim)
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LimeButton(if (saved && key.isNotEmpty()) "Saved ✓" else "Save key", height = 42.dp, enabled = !saved && key.isNotBlank()) {
                if (provider == Provider.GEMINI && !key.startsWith("AIza")) { state.toast("That doesn't look like a Gemini key (they start with AIza)", ToastKind.ERROR); return@LimeButton }
                state.setApiKey(provider, key)
                state.toast("${provider.label} key saved · verifying…", ToastKind.INFO)
                scope.launch { verifyKey(state, provider) }
            }
            HairButton("Get key ↗", onClick = {
                state.openUrl(if (provider == Provider.GEMINI) "https://aistudio.google.com/apikey" else "https://platform.openai.com/api-keys")
            })
            if (key.isNotEmpty() && saved) HairButton("Remove", tint = Noir.Red, onClick = { confirmRemove = true })
        }
    }
}

@Composable
private fun ModelBlock(state: AppState, provider: Provider) {
    val c = state.config
    val options = if (provider == Provider.GEMINI) Models.gemini else Models.openai
    val current = c.model(provider)
    var custom by remember(provider) { mutableStateOf(if (options.any { it.id == current }) "" else current) }
    fun pick(id: String) = state.update(reapply = true) {
        if (provider == Provider.GEMINI) it.copy(geminiModel = id) else it.copy(openAiModel = id)
    }
    Smoke {
        MonoLabel("02 — model")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val on = o.id == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) Noir.Lime else Color.Transparent)
                        .border(1.dp, if (on) Noir.Lime else Noir.Hair, RoundedCornerShape(12.dp))
                        .clickable { custom = ""; pick(o.id) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.label, style = MaterialTheme.typography.titleSmall, color = if (on) Noir.Ink else Noir.Text)
                        Text(o.note, style = MaterialTheme.typography.bodySmall, color = if (on) Color(0xFF2C3A00) else Noir.Faint)
                    }
                    if (on) Text("✓", style = MaterialTheme.typography.titleMedium, color = Noir.Ink)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        NoirField(custom, { v -> custom = v.trim(); if (custom.isNotEmpty()) pick(custom) }, "Custom model id", mono = true, placeholder = "any model id your key supports")
    }
}

@Composable
private fun TestBlock(state: AppState, onTry: () -> Unit) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    Smoke {
        MonoLabel("03 — test")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimeButton(if (running) "Testing…" else "Ping brain", Modifier.weight(1f), icon = Icons.Rounded.Bolt, enabled = !running && state.status.brainReady, height = 46.dp) {
                running = true; result = null
                val c = state.config
                val key = state.apiKey(c.provider)
                scope.launch {
                    result = withContext(Dispatchers.IO) {
                        val start = System.nanoTime()
                        try {
                            val reply = CloudLlm.speakable(
                                CloudLlm.complete(c.provider, key, c.model(), PromptBuilder.systemPrompt(c),
                                    listOf(CloudLlm.Turn("user", "Hello? Is ${c.ownerName.ifBlank { "he" }} there?")))
                            )
                            true to "${(System.nanoTime() - start) / 1_000_000} ms · “$reply”"
                        } catch (e: CloudLlm.LlmException) {
                            false to CloudLlm.friendlyError(e.code) + (e.message?.takeIf { it != e.code }?.let { "\n$it" } ?: "")
                        } catch (e: Exception) {
                            false to "Failed: ${e.javaClass.simpleName}"
                        }
                    }
                    running = false
                }
            }
            LimeButton("Chat test", outlined = true, enabled = state.status.brainReady, height = 46.dp, onClick = onTry)
        }
        result?.let { (ok, text) ->
            Spacer(Modifier.height(12.dp))
            MonoLabel(if (ok) "ok" else "error", color = if (ok) Noir.Lime else Noir.Red)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = Noir.Text)
        }
    }
}

private suspend fun verifyKey(state: AppState, provider: Provider) {
    val key = state.apiKey(provider)
    val model = state.config.model(provider)
    val result = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        runCatching {
            CloudLlm.complete(provider, key, model, "Reply with the single word: ready", listOf(CloudLlm.Turn("user", "ping")), maxTokens = 20)
            (System.nanoTime() - start) / 1_000_000
        }
    }
    result.onSuccess { ms -> state.toast("${provider.label} key verified · replied in ${"%.1f".format(ms / 1000.0)} s") }
        .onFailure { e ->
            val why = (e as? CloudLlm.LlmException)?.let { CloudLlm.friendlyError(it.code) } ?: e.javaClass.simpleName
            state.toast("Key saved, but the test failed: $why", ToastKind.ERROR)
        }
}

@Composable
private fun LocalBlock(state: AppState) {
    val context = state.appContext()
    val dl = state.download
    val installedId = remember(state.status.localModel, dl.phase) { ModelDownloads.installedId(context) }
    val catalog = remember { ModelCatalog.forDevice(context) }
    val ram = remember { ModelCatalog.ramGb(context) }
    var confirmMetered by remember { mutableStateOf<CatalogModel?>(null) }
    var lastPhase by remember { mutableStateOf(dl.phase) }
    LaunchedEffect(dl.phase) {
        if (dl.phase != lastPhase) {
            val title = ModelCatalog.byId(dl.modelId)?.title ?: "Model"
            when (dl.phase) {
                ModelDownloads.Phase.DONE -> state.toast("$title installed · offline brain ready")
                ModelDownloads.Phase.FAILED -> state.toast(ModelDownloads.friendlyError(dl.error), ToastKind.ERROR)
                ModelDownloads.Phase.CANCELLED -> state.toast("Download cancelled · it will resume next time", ToastKind.INFO)
                else -> {}
            }
            lastPhase = dl.phase
        }
    }
    fun start(m: CatalogModel) {
        if (ModelDownloads.isMetered(context)) confirmMetered = m
        else { ModelDownloads.start(context, m); state.toast("Downloading ${m.title} · you can leave this screen", ToastKind.INFO) }
    }
    confirmMetered?.let { m ->
        NoirConfirm(
            title = "Use mobile data?",
            body = "${m.title} is ${"%.1f".format(m.approxBytes / 1e9)} GB. You're not on Wi-Fi — this may use your data pack.",
            confirm = "Download anyway",
            onDismiss = { confirmMetered = null },
        ) { confirmMetered = null; ModelDownloads.start(context, m); state.toast("Downloading ${m.title}", ToastKind.INFO) }
    }

    Smoke {
        MonoLabel("01 — offline brain")
        Spacer(Modifier.height(10.dp))
        Text(
            "Runs entirely on your phone: no key, no internet during calls. Replies are slower than Gemini.",
            style = MaterialTheme.typography.bodyMedium, color = Noir.Text,
        )
        Spacer(Modifier.height(4.dp))
        MonoLabel("this phone · ${"%.0f".format(Math.ceil(ram))} gb ram · models sized to fit")
        Spacer(Modifier.height(12.dp))
        if (dl.active) {
            val m = ModelCatalog.byId(dl.modelId)
            Text(
                when (dl.phase) {
                    ModelDownloads.Phase.CHECKING -> "Preparing ${m?.title}…"
                    ModelDownloads.Phase.VERIFYING -> "Verifying ${m?.title} checksum…"
                    else -> "Downloading ${m?.title}"
                },
                style = MaterialTheme.typography.titleMedium, color = Noir.Text,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1FFFFFFF))) {
                Box(Modifier.fillMaxWidth(if (dl.phase == ModelDownloads.Phase.VERIFYING) 1f else dl.fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Noir.Lime))
            }
            Spacer(Modifier.height(8.dp))
            val left = if (dl.bytesPerSec > 0) (dl.total - dl.done) / dl.bytesPerSec else -1
            MonoLabel(
                if (dl.phase == ModelDownloads.Phase.DOWNLOADING && dl.total > 0)
                    "${"%.2f".format(dl.done / 1e9)} / ${"%.2f".format(dl.total / 1e9)} gb · ${"%.1f".format(dl.bytesPerSec / 1e6)} mb/s" +
                        (if (left >= 0) " · ~${if (left > 90) "${left / 60} min" else "$left s"} left" else "")
                else "keep the app installed · runs in background",
            )
            Spacer(Modifier.height(10.dp))
            if (dl.phase != ModelDownloads.Phase.VERIFYING) HairButton("Cancel", tint = Noir.Red, onClick = { ModelDownloads.cancel() })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                catalog.forEachIndexed { index, m ->
                    val installed = state.status.localModel && installedId == m.id
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(shape)
                            .border(1.dp, if (installed) Noir.Lime else Noir.Hair, shape)
                            .background(if (installed) Color(0x10C8FF2E) else Color.Transparent)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium, color = Noir.Text, modifier = Modifier.weight(1f))
                            if (index == 0) MonoLabel("best fit", color = Noir.Lime)
                        }
                        Text(m.blurb, style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
                        Spacer(Modifier.height(10.dp))
                        if (installed) {
                            MonoLabel("installed · ${if (LocalModelRuntime.get(context).prefersGpu()) "gpu" else "cpu"}", color = Noir.Lime)
                        } else {
                            LimeButton("Download · ${"%.1f".format(m.approxBytes / 1e9)} GB", height = 42.dp, outlined = index != 0) { start(m) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Import a model file manually",
            style = MaterialTheme.typography.bodySmall, color = Noir.Faint,
            modifier = Modifier.clickable { state.openLocalModel() }.padding(vertical = 4.dp),
        )
    }
    if (state.status.localModel && !dl.active) BenchmarkBlock(state)
}

@Composable
private fun BenchmarkBlock(state: AppState) {
    val context = state.appContext()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    Smoke {
        MonoLabel("02 — offline test")
        Spacer(Modifier.height(10.dp))
        Text("Three call replies (English, हिंदी, मराठी) on this phone, timed.", style = MaterialTheme.typography.bodySmall, color = Noir.Faint)
        Spacer(Modifier.height(10.dp))
        LimeButton(if (running) "Running… (first run loads the model)" else "Run offline test", Modifier.fillMaxWidth(), icon = Icons.Rounded.Bolt, enabled = !running, height = 46.dp) {
            running = true; lines = emptyList()
            val c = state.config
            val owner = c.ownerName.ifBlank { "Rahul" }
            val cases = listOf(
                ReplyLanguage.ENGLISH to "Hello, is $owner there? This is Amit from his office, it's about tomorrow's meeting.",
                ReplyLanguage.HINDI to "हेलो, $owner है क्या? मैं उनकी मम्मी बोल रही हूँ।",
                ReplyLanguage.MARATHI to "हॅलो, $owner आहे का? मी त्याचा मित्र बोलतोय.",
            )
            val requests = cases.map { (lang, text) ->
                ReplyRequest.forAssistant(PromptBuilder.systemPrompt(c.copy(language = lang)),
                    listOf(CallConversation.Message("user", text)), ReplyRequest.LOCAL_ASSISTANT_BYTES)
            }
            scope.launch {
                val out = suspendCancellableCoroutine { cont ->
                    val started = LocalModelRuntime.get(context).testAssistant(requests) { answers, loadMs, replyMs, gpu, error ->
                        val result = buildList {
                            add("${if (gpu) "GPU" else "CPU"} · load ${"%.1f".format(loadMs / 1000.0)} s")
                            answers.forEachIndexed { i, a -> add("${cases[i].first.native} · ${"%.1f".format(replyMs[i] / 1000.0)} s · “$a”") }
                            if (error.isNotEmpty()) add("error: $error")
                        }
                        if (cont.isActive) cont.resume(result) {}
                    }
                    if (!started && cont.isActive) cont.resume(listOf("error: on-device brain is busy — try again in a moment")) {}
                }
                lines = out
                running = false
                state.toast(if (out.any { it.startsWith("error") }) "Offline test hit an error" else "Offline test finished",
                    if (out.any { it.startsWith("error") }) ToastKind.ERROR else ToastKind.OK)
            }
        }
        lines.forEach { line ->
            Spacer(Modifier.height(8.dp))
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (line.startsWith("error")) Noir.Red else Noir.Text)
        }
    }
}
