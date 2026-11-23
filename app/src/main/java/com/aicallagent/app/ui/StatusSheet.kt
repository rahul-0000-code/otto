package com.aicallagent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicallagent.app.agent.AgentConfig
import com.aicallagent.app.agent.OwnerStatus
import com.aicallagent.app.agent.OwnerStatuses
import com.aicallagent.app.agent.PromptBuilder
import com.aicallagent.app.ui.components.ChipFlow
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.NoirChip
import com.aicallagent.app.ui.components.NoirDivider
import com.aicallagent.app.ui.components.NoirField
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.theme.Noir
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How long a status lasts. People forget to switch "Driving" off, so most of these end by themselves. */
private class StatusLength(val label: String, val until: (now: Long) -> Long)

private val LENGTHS = listOf(
    StatusLength("30 min") { it + 30 * 60_000L },
    StatusLength("1 hour") { it + 60 * 60_000L },
    StatusLength("2 hours") { it + 2 * 60 * 60_000L },
    StatusLength("Rest of today") { now ->
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
    },
    StatusLength("Until I change it") { 0L },
)

/** "until 3:30 PM", "until Sat 9:00 AM" or "until you change it". */
internal fun untilText(until: Long): String {
    if (until <= 0) return "until you change it"
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).let { it.format(Date(until)) == it.format(Date()) }
    return "until " + SimpleDateFormat(if (sameDay) "h:mm a" else "EEE h:mm a", Locale.ENGLISH).format(Date(until))
}

/** Pick what you're doing; Otto answers every call while it's on and tells callers this message. */
@Composable
fun StatusContent(state: AppState, onDone: () -> Unit) {
    val c = state.config
    val active = state.activeStatus
    var length by rememberSaveable { mutableIntStateOf(LENGTHS.lastIndex) }
    var editing by remember { mutableStateOf<OwnerStatus?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Tell ${c.assistant} what you're doing. While a status is on, ${c.assistant} answers every call " +
                    "after ${c.autoDelaySec}s and gives the caller your message.",
                style = MaterialTheme.typography.bodySmall, color = Noir.Dim,
            )
        }
        if (active != null) {
            item {
                Smoke(padding = 14.dp, radius = 18.dp, accent = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(active.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(active.label, style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                            MonoLabel("on · ${untilText(c.statusUntil)}", color = Noir.Lime)
                        }
                        HairButton("Clear") { state.setStatus(null); onDone() }
                    }
                }
            }
        }
        item { MonoLabel("[ for how long ]", Modifier.padding(top = 8.dp)) }
        item {
            ChipFlow { LENGTHS.forEachIndexed { i, l -> NoirChip(l.label, i == length) { length = i } } }
        }
        item { MonoLabel("[ pick one ]", Modifier.padding(top = 8.dp)) }
        item {
            Smoke(padding = 0.dp, radius = 18.dp) {
                c.statuses.forEachIndexed { i, status ->
                    if (i > 0) NoirDivider()
                    StatusRow(c, status, selected = status.id == active?.id, onEdit = { editing = status }) {
                        state.setStatus(status, LENGTHS[length].until(System.currentTimeMillis()))
                        onDone()
                    }
                }
            }
        }
        item {
            HairButton("Add your own", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) {
                editing = OwnerStatus("custom-${System.currentTimeMillis()}", "📌", "", "", custom = true)
            }
        }
        item {
            Text(
                "Your usual “when a call rings” setting comes back when the status ends.",
                style = MaterialTheme.typography.bodySmall, color = Noir.Faint, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }

    editing?.let { status ->
        StatusEditor(
            state, status,
            isNew = c.statuses.none { it.id == status.id },
            onClose = { editing = null },
        )
    }
}

@Composable
private fun StatusRow(c: AgentConfig, status: OwnerStatus, selected: Boolean, onEdit: () -> Unit, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(status.emoji.ifBlank { "•" }, fontSize = 22.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.label, style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Check, "On", tint = Noir.Lime, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "“${c.statusMessage(status)}”", style = MaterialTheme.typography.bodySmall, color = Noir.Dim,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit ${status.label}", tint = Noir.Faint, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun StatusEditor(state: AppState, status: OwnerStatus, isNew: Boolean, onClose: () -> Unit) {
    var emoji by remember { mutableStateOf(status.emoji) }
    var label by remember { mutableStateOf(status.label) }
    var message by remember { mutableStateOf(status.message) }
    val preset = OwnerStatuses.preset(status.id)
    val owner = PromptBuilder.spokenName(state.config.owner)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Noir.Raised,
        title = { Text(if (isNew) "New status" else "Edit status", style = MaterialTheme.typography.titleLarge, color = Noir.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoirField(emoji, { emoji = it.take(8) }, "Emoji", Modifier.width(92.dp))
                    NoirField(label, { label = it.take(30) }, "Status", Modifier.weight(1f), placeholder = "At the doctor")
                }
                NoirField(
                    message, { message = it.take(240) }, "What ${state.config.assistant} tells callers",
                    singleLine = false, minLines = 3,
                    placeholder = "${OwnerStatuses.OWNER} is at the doctor right now. I'll pass on your message.",
                )
                Text(
                    "Write ${OwnerStatuses.OWNER} for your name (callers hear “$owner”). ${state.config.assistant} says it in the caller's language.",
                    style = MaterialTheme.typography.bodySmall, color = Noir.Faint,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && message.isNotBlank(),
                onClick = {
                    state.saveStatus(status.copy(emoji = emoji.trim(), label = label.trim(), message = message.trim()))
                    state.toast(if (isNew) "Status added" else "Status saved")
                    onClose()
                },
            ) { Text("Save", color = if (label.isNotBlank() && message.isNotBlank()) Noir.Lime else Noir.Faint) }
        },
        dismissButton = {
            Row {
                when {
                    status.custom && !isNew -> TextButton(onClick = { state.deleteStatus(status.id); onClose() }) { Text("Delete", color = Noir.Red) }
                    preset != null && preset != status -> TextButton(onClick = { emoji = preset.emoji; label = preset.label; message = preset.message }) {
                        Text("Reset", color = Noir.Dim)
                    }
                }
                TextButton(onClick = onClose) { Text("Cancel", color = Noir.Dim) }
            }
        },
    )
}
