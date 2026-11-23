package com.aicallagent.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aicallagent.app.agent.PromptBuilder
import com.aicallagent.app.agent.ReplyLanguage
import com.aicallagent.app.agent.Style
import com.aicallagent.app.agent.VoiceGender
import com.aicallagent.app.ui.components.ChipFlow
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.LimeButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.NoirChip
import com.aicallagent.app.ui.components.NoirField
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.theme.Noir

private val QUICK_ADDS = listOf(
    "I'm in a meeting till 6 pm.",
    "Never share OTPs or bank details.",
    "Deliveries: leave it with the guard.",
    "If it's family, say I'll call back soon.",
    "Ask unknown callers for their name first.",
)

@Composable
fun PersonaScreen(state: AppState, onTry: () -> Unit, inSheet: Boolean = false) {
    val c = state.config
    LazyColumn(
        Modifier.fillMaxSize().then(if (inSheet) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)).imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonoLabel("${c.assistant} / persona", Modifier.padding(top = 4.dp)) }
        item {
            Text(
                buildAnnotatedString { append("How I talk\nfor "); withStyle(SpanStyle(color = Noir.Lime)) { append("you.") } },
                style = MaterialTheme.typography.displayMedium, color = Noir.Text, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        item {
            Smoke {
                MonoLabel("01 — identity")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoirField(c.assistantName, { v ->
                        if (v.trim().equals(c.voice, ignoreCase = true)) {
                            state.toast("“${c.voice}” is your voice assistant. Pick another name for calls.", ToastKind.ERROR)
                        } else {
                            state.update { it.copy(assistantName = v.take(24)) }
                        }
                    }, "Assistant on calls", Modifier.weight(1f))
                    NoirField(c.ownerName, { v -> state.update { it.copy(ownerName = v.take(32)) } }, "Your name", Modifier.weight(1f), placeholder = "Rahul")
                }
                Spacer(Modifier.height(10.dp))
                MonoLabel("voice")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    VoiceGender.entries.forEach { g -> NoirChip(g.label, c.gender == g) { state.update { it.copy(gender = g) } } }
                }
            }
        }
        item {
            Smoke {
                MonoLabel("02 — language")
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    ReplyLanguage.entries.forEach { l ->
                        val label = when (l) {
                            ReplyLanguage.MATCH -> "Auto"
                            ReplyLanguage.HINDI, ReplyLanguage.MARATHI -> l.native
                            else -> l.label
                        }
                        NoirChip(label, c.language == l) { state.update { it.copy(language = l) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (c.language) {
                        ReplyLanguage.HINDI, ReplyLanguage.MARATHI -> "Set Samsung Text call to a Hindi voice so Devanagari is read aloud. Marathi uses the same voice."
                        ReplyLanguage.HINGLISH -> "Roman-script Hinglish works with an English (India) voice."
                        ReplyLanguage.MARATHI_ROMAN -> "Marathi in English letters, read by the English (India) Text call voice."
                        ReplyLanguage.MATCH -> "Replies in the caller's language. Hindi and Marathi come out in English letters so the English (India) Text call voice can read them."
                        ReplyLanguage.ENGLISH -> "Always simple Indian English."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Noir.Faint,
                )
            }
        }
        item {
            Smoke {
                MonoLabel("03 — style")
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    Style.entries.forEach { st -> NoirChip(st.label, c.style == st) { state.update { it.copy(style = st) } } }
                }
                Spacer(Modifier.height(10.dp))
                Text(c.style.prompt, style = MaterialTheme.typography.bodySmall, color = Noir.Faint)
            }
        }
        item {
            Smoke {
                MonoLabel("04 — instructions")
                Spacer(Modifier.height(10.dp))
                NoirField(
                    c.instructions, { v -> state.update { it.copy(instructions = v.take(6000)) } }, "Tell ${c.assistant} what to do",
                    singleLine = false, minLines = 4, mono = true,
                    placeholder = "e.g. If Mom calls, say I'll call back after 7.",
                )
                Spacer(Modifier.height(10.dp))
                MonoLabel("quick add")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    QUICK_ADDS.filterNot { c.instructions.contains(it) }.forEach { line ->
                        NoirChip("+ $line", false) {
                            state.update { it.copy(instructions = (it.instructions.trimEnd() + "\n" + line).trim()) }
                        }
                    }
                }
            }
        }
        item {
            val greeting = PromptBuilder.suggestedGreeting(c)
            Smoke {
                MonoLabel("05 — greeting")
                Spacer(Modifier.height(10.dp))
                Text("“$greeting”", style = MaterialTheme.typography.bodyMedium, color = Noir.Text)
                Spacer(Modifier.height(10.dp))
                Text("Samsung speaks this the moment ${c.assistant} switches to Text call. Paste it into Phone → Settings → Text call → greeting.", style = MaterialTheme.typography.bodySmall, color = Noir.Faint)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HairButton("Copy", icon = Icons.Rounded.ContentCopy, onClick = { state.copy("Greeting", greeting) })
                    HairButton("Call settings", onClick = { state.openCallSettings() })
                }
            }
        }
        item {
            LimeButton("Run a test call", Modifier.fillMaxWidth().padding(top = 4.dp), icon = Icons.Rounded.Mic, outlined = true, onClick = onTry)
        }
    }
}
