package com.aicallagent.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.theme.Noir
import com.aicallagent.app.ui.theme.SamyColors

/**
 * Samy's own settings (the tune button on the Samy screen): its name, opening it from any app, the
 * permissions it uses and its history. Your name and the Gemini key stay in Otto's profile, shared.
 */
@Composable
fun SamySettings(state: AppState, onClose: () -> Unit) {
    val c = state.config
    val context = state.appContext()
    var name by remember { mutableStateOf(c.voiceName) }
    val isAssistant = state.isPhoneAssistant()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state.refresh() }
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    Box(
        Modifier.fillMaxSize().background(SamyColors.Ink)
            .background(Brush.radialGradient(listOf(SamyColors.Violet.copy(alpha = 0.16f), Color.Transparent), radius = 1000f))
            .clickable(enabled = false) {},
    ) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("${c.voice} · settings", Modifier.weight(1f), color = SamyColors.Violet)
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).border(1.dp, SamyColors.Line, CircleShape)
                            .clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, null, tint = SamyColors.Light, modifier = Modifier.size(20.dp)) }
                }
            }
            item { Text("Make ${c.voice}\nyours.", style = MaterialTheme.typography.displayMedium, color = Noir.Text) }

            item {
                Card {
                    MonoLabel("name", color = SamyColors.Text2)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { v -> if (state.setVoiceName(v)) name = v.take(24) },
                        singleLine = true,
                        label = { Text("Voice assistant") },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamyColors.Violet, unfocusedBorderColor = SamyColors.Line,
                            focusedLabelColor = SamyColors.Violet, unfocusedLabelColor = SamyColors.Text2,
                            cursorColor = SamyColors.Violet, focusedTextColor = Noir.Text, unfocusedTextColor = Noir.Text,
                        ),
                    )
                    Text(
                        "What you call it: “${c.voice}, call Pooja”. Calls are answered by ${c.assistant}, a different name.",
                        style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2,
                    )
                }
            }

            item {
                Card {
                    MonoLabel("open from any app", color = SamyColors.Text2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isAssistant) "Swipe up from a corner" else "Not set up yet", style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                            Text(
                                if (isAssistant) "Otto is the phone's assistant, so this opens ${c.voice} anywhere."
                                else "Make Otto the phone's digital assistant: a corner swipe then opens ${c.voice} in any app.",
                                style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        if (isAssistant) Done() else Action("Set up") { state.openAssistantSettings() }
                    }
                    Divider()
                    Text(
                        "Also: add the “Ask ${c.voice}” tile from the quick-settings edit panel, or long-press the Otto icon.",
                        style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2,
                    )
                }
            }

            item {
                Card {
                    MonoLabel("what ${c.voice.lowercase()} can use", color = SamyColors.Text2)
                    listOf(
                        Triple(Manifest.permission.RECORD_AUDIO, "Microphone", "To hear you"),
                        Triple(Manifest.permission.READ_CONTACTS, "Contacts", "To find who you mean. Never leaves the phone."),
                        Triple(Manifest.permission.CALL_PHONE, "Phone calls", "To call without an extra tap"),
                        Triple(Manifest.permission.SEND_SMS, "Text messages", "To send texts you dictate"),
                    ).forEachIndexed { i, (perm, title, why) ->
                        if (i > 0) Divider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                                Text(why, style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2)
                            }
                            Spacer(Modifier.width(10.dp))
                            if (granted(perm)) Done() else Action("Allow") { ask.launch(arrayOf(perm)) }
                        }
                    }
                }
            }

            item {
                Card {
                    MonoLabel("history", color = SamyColors.Text2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${state.samyHistory.size} recent actions", style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                            Text("Kept only on this phone", style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2)
                        }
                        if (state.samyHistory.isNotEmpty()) Action("Clear") { state.clearSamyHistory() }
                    }
                }
            }

            item {
                Text(
                    "Your name and the Gemini key are shared with Otto. Change them from Otto's profile.",
                    style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(SamyColors.Violet.copy(alpha = 0.08f))
            .border(1.dp, SamyColors.Violet.copy(alpha = 0.22f), RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SamyColors.Violet.copy(alpha = 0.16f)))
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = Noir.Ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(SamyColors.Violet).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

@Composable
private fun Done() {
    Box(Modifier.size(28.dp).clip(CircleShape).background(SamyColors.Violet), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, "On", tint = Noir.Ink, modifier = Modifier.size(16.dp))
    }
}
