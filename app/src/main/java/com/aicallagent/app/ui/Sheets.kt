package com.aicallagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import com.aicallagent.app.AccessibilitySwitch
import com.aicallagent.app.agent.CallRecord
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.agent.ReplyLanguage
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.NoirChip
import com.aicallagent.app.ui.components.NoirDivider
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.theme.Noir

/** Where the avatar leads. Each sheet slides up over Today; back returns to the one below. */
enum class SheetPage(val title: String) { YOU("You"), PERSONA("Persona"), BRAIN("Brain"), CALLS("All calls"), STATUS("Status") }

@Composable
fun SheetFrame(
    page: SheetPage,
    below: SheetPage?,
    onBack: () -> Unit,
    onCloseAll: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xA8000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCloseAll)
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
                .clip(shape)
                .background(Color(0xFF0E0E0E))
                .border(1.dp, Noir.HairHi, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (below != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Noir.Dim, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(below.title, style = MaterialTheme.typography.titleSmall, color = Noir.Dim)
                    }
                } else {
                    IconButton(onClick = onCloseAll) { Icon(Icons.Rounded.Close, "Close", tint = Noir.Text) }
                }
                Spacer(Modifier.weight(1f))
                if (page != SheetPage.YOU) Text(page.title, style = MaterialTheme.typography.titleLarge, color = Noir.Text)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(if (below != null) 72.dp else 48.dp))
            }
            Column(Modifier.weight(1f), content = content)
        }
    }
}

/** The profile hub: who you are, how Otto talks, its brain, phone setup and data. */
@Composable
fun YouContent(state: AppState, open: (SheetPage) -> Unit, onSetup: () -> Unit, onTestCall: () -> Unit) {
    val c = state.config
    val s = state.status
    val perms = listOf(s.accessibility, s.phonePerm, s.callLogPerm, s.notifPerm, s.battery).count { it }
    var grantHelp by remember { mutableStateOf(false) }
    if (grantHelp) GrantHelpDialog(state) { grantHelp = false }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val owner = c.ownerName.trim()
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Noir.Lime),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(owner.firstOrNull()?.uppercaseChar()?.toString() ?: "•", style = MaterialTheme.typography.headlineMedium, color = Noir.Ink)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(owner.ifBlank { "You" }.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } },
                        style = MaterialTheme.typography.headlineMedium, color = Noir.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MonoLabel(state.deviceName(), Modifier.padding(top = 4.dp))
                }
            }
        }
        item { MonoLabel("[ 01 — ${c.assistant} ]", Modifier.padding(top = 10.dp)) }
        item {
            Smoke(padding = 0.dp, radius = 18.dp) {
                val language = when (c.language) {
                    ReplyLanguage.MATCH -> "Auto language"
                    ReplyLanguage.HINDI, ReplyLanguage.MARATHI -> c.language.native
                    else -> c.language.label
                }
                HubRow(Icons.Rounded.Person, "Persona", "${c.assistant} · ${c.gender.label} · $language · ${c.style.label}") { open(SheetPage.PERSONA) }
                NoirDivider()
                val brain = when {
                    !s.brainReady -> "Not set up yet"
                    c.provider == Provider.LOCAL -> "Gemma · on this phone"
                    else -> "${c.provider.label} · ${c.model()}"
                }
                HubRow(
                    if (c.provider == Provider.LOCAL) Icons.Rounded.Memory else Icons.Rounded.AutoAwesome, "Brain", brain,
                    accent = s.brainReady, tag = if (s.brainReady) "ready" else "set up", tagColor = if (s.brainReady) Noir.Lime else Noir.Amber,
                ) { open(SheetPage.BRAIN) }
                NoirDivider()
                HubRow(Icons.Rounded.Forum, "Test call", "Chat with ${c.assistant} as if you were calling", onClick = onTestCall)
                NoirDivider()
                val status = state.activeStatus
                HubRow(
                    Icons.Rounded.DoNotDisturbOn, "Status",
                    if (status != null) "${status.emoji} ${status.label} · ${untilText(c.statusUntil)}" else "Driving, in a meeting… tell callers why",
                    accent = status != null, tag = if (status != null) "on" else null,
                ) { open(SheetPage.STATUS) }
                NoirDivider()
                val isAssistant = state.isPhoneAssistant()
                HubRow(
                    Icons.Rounded.Mic, "Voice commands",
                    if (isAssistant) "Swipe up from a bottom corner anywhere: “${c.voice}, call Pooja”"
                    else "Set ${c.voice} as the phone's assistant to call, text and navigate by voice",
                    accent = isAssistant, tag = if (isAssistant) "on" else "set up", tagColor = if (isAssistant) Noir.Lime else Noir.Amber,
                ) { if (isAssistant) state.openSamy() else state.openAssistantSettings() }
            }
        }
        item { MonoLabel("[ 02 — phone setup ]", Modifier.padding(top = 10.dp)) }
        item {
            Smoke(padding = 0.dp, radius = 18.dp) {
                HubRow(Icons.Rounded.Shield, "Permissions", "$perms of 5 granted", done = perms == 5, onClick = onSetup)
                NoirDivider()
                HubRow(
                    Icons.Rounded.Phone, "Samsung call settings",
                    if (state.samsungConfirmed) "Text call on · Call display: Full screen" else "Turn on Text call and Full screen",
                    done = state.samsungConfirmed, onClick = onSetup,
                )
                NoirDivider()
                HubRow(Icons.Rounded.BatteryChargingFull, "Run in background", if (s.battery) "Battery · Unrestricted" else "Battery saver may stop ${c.assistant}", done = s.battery) {
                    if (!s.battery) state.requestBatteryExemption()
                }
                NoirDivider()
                val canSwitch = AccessibilitySwitch.canSwitch(state.appContext())
                val onlyDuringCalls = c.accessibilityOnlyDuringCalls && canSwitch
                HubRow(
                    Icons.Rounded.VisibilityOff, "Accessibility only on calls",
                    when {
                        onlyDuringCalls -> "Off between calls, so banking apps stay quiet"
                        canSwitch -> "Always on · banking apps may flag ${c.assistant}"
                        else -> "Stop banking apps flagging ${c.assistant} · one-time setup"
                    },
                    accent = onlyDuringCalls,
                    tag = when { onlyDuringCalls -> "on"; canSwitch -> "off"; else -> "set up" },
                    tagColor = when { onlyDuringCalls -> Noir.Lime; canSwitch -> Noir.Faint; else -> Noir.Amber },
                ) {
                    if (!state.setAccessibilityOnlyDuringCalls(!onlyDuringCalls)) grantHelp = true
                }
            }
        }
        item { MonoLabel("[ 03 — data ]", Modifier.padding(top = 10.dp)) }
        item {
            Smoke(padding = 0.dp, radius = 18.dp) {
                HubRow(Icons.Rounded.Lock, "Calls & privacy", "${state.calls.size} calls · kept only on this phone") { open(SheetPage.CALLS) }
            }
        }
    }
}

/** Android lets an app switch accessibility on by itself only after the owner allows it from a computer. */
@Composable
private fun GrantHelpDialog(state: AppState, onClose: () -> Unit) {
    val name = state.config.assistant
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Noir.Raised,
        title = { Text("One-time setup", style = MaterialTheme.typography.titleLarge, color = Noir.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Banking apps flag any app that keeps accessibility on. $name can switch it on only while a call rings " +
                        "and off again after, but Android needs your OK once, from a computer.",
                    style = MaterialTheme.typography.bodyMedium, color = Noir.Dim,
                )
                Text("Connect the phone with USB debugging on and run:", style = MaterialTheme.typography.bodyMedium, color = Noir.Dim)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Noir.Smoke).padding(12.dp)) {
                    Text(AccessibilitySwitch.GRANT_COMMAND, style = MaterialTheme.typography.labelMedium, color = Noir.Lime)
                }
                Text("Then come back and tap this setting again.", style = MaterialTheme.typography.bodyMedium, color = Noir.Dim)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.appContext().getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("adb command", AccessibilitySwitch.GRANT_COMMAND))
                state.toast("Command copied")
            }) { Text("Copy command", color = Noir.Lime) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = Noir.Dim) } },
    )
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    sub: String,
    accent: Boolean = false,
    done: Boolean? = null,
    tag: String? = null,
    tagColor: Color = Noir.Lime,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val warn = done == false
        val tint = when { warn -> Noir.Amber; accent -> Noir.Lime; else -> Noir.Text }
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x0FFFFFFF))
                .border(1.dp, if (warn) Noir.Amber.copy(alpha = 0.4f) else if (accent) Noir.Lime.copy(alpha = 0.4f) else Noir.Hair, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                if (tag != null) { Spacer(Modifier.width(8.dp)); MonoLabel(tag, color = tagColor) }
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = if (warn) Noir.Amber else Noir.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        when (done) {
            true -> Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Noir.Lime), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Done", tint = Noir.Ink, modifier = Modifier.size(15.dp))
            }
            false -> MonoLabel("fix", color = Noir.Amber)
            null -> Icon(Icons.Rounded.ChevronRight, null, tint = Noir.Faint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Every saved call, filterable, newest first. */
@Composable
fun CallsContent(state: AppState, onOpenCall: (CallRecord) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val shown = when (filter) {
        1 -> state.calls.filter { it.urgent }
        2 -> state.calls.filter { it.spam }
        else -> state.calls
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoirChip("All · ${state.calls.size}", filter == 0) { filter = 0 }
                NoirChip("Urgent · ${state.calls.count { it.urgent }}", filter == 1) { filter = 1 }
                NoirChip("Spam · ${state.calls.count { it.spam }}", filter == 2) { filter = 2 }
            }
        }
        item {
            if (shown.isEmpty()) {
                Smoke(padding = 18.dp) { Text("No calls here yet.", style = MaterialTheme.typography.bodyMedium, color = Noir.Dim) }
            } else {
                Smoke(padding = 0.dp) {
                    shown.forEachIndexed { i, record ->
                        if (i > 0) NoirDivider()
                        CallRow(record) { onOpenCall(record) }
                    }
                }
            }
        }
        item {
            Text(
                "Transcripts and summaries stay on this phone. Open a call to delete it.",
                style = MaterialTheme.typography.bodySmall, color = Noir.Faint, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
