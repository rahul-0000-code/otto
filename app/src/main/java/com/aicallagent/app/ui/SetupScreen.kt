package com.aicallagent.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.ui.components.HairButton
import com.aicallagent.app.ui.components.LimeButton
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.components.NoirDivider
import com.aicallagent.app.ui.components.Smoke
import com.aicallagent.app.ui.theme.Noir

private class Step(val key: String, val title: String, val sub: String, val why: String, val done: Boolean, val doneLabel: String)

/**
 * "Seven taps to ready": the setup checklist. The current step opens with a short reason and one
 * button; finished steps collapse to a tick. Opens on first run, and from the avatar's phone setup.
 */
@Composable
fun SetupScreen(state: AppState, onClose: () -> Unit, onBrain: () -> Unit) {
    val s = state.status
    val c = state.config
    val name = c.assistant
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state.refresh() }
    val steps = listOf(
        Step("access", "Accessibility", "Reads the call screen", "$name reads the caller's words from Samsung's Text call screen and taps the buttons for you. Android asks you to switch this on once.", s.accessibility, "on"),
        Step("phone", "Phone & calls", "Knows when a call rings", "Lets $name see that a call is ringing so it can offer to answer.", s.phonePerm, "allowed"),
        Step("brain", "Brain", "Writes the replies", "Add a free Gemini key for fast replies, or use the on-device model if it's downloaded.", s.brainReady, if (c.provider == Provider.LOCAL) "on-device" else c.provider.label.lowercase()),
        Step("names", "Caller names", "So $name knows Mom from a stranger", "Uses your call log to show who called next to each summary.", s.callLogPerm, "allowed"),
        Step("notif", "Notifications", "Call summaries the moment calls end", "You get a short summary as soon as $name hangs up.", s.notifPerm, "allowed"),
        Step("battery", "Background battery", "Unrestricted, so Android won't stop it", "Samsung can put apps to sleep. Unrestricted keeps $name ready for calls.", s.battery, "unrestricted"),
        Step("samsung", "Samsung call settings", "Text call on · Call display: Full screen", "In the Phone app, open ⋮ › Settings and set both. Apps can't read these, so confirm here once they're set.", state.samsungConfirmed, "done"),
    )
    val doneCount = steps.count { it.done }
    var focus by rememberSaveable { mutableStateOf<String?>(null) }
    val current = steps.firstOrNull { it.key == focus && !it.done } ?: steps.firstOrNull { !it.done }

    fun act(step: Step) {
        when (step.key) {
            "access" -> state.openAccessibilitySettings()
            "phone" -> perms.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.POST_NOTIFICATIONS))
            "brain" -> onBrain()
            "names" -> perms.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
            "notif" -> perms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            "battery" -> state.requestBatteryExemption()
            "samsung" -> state.openCallSettings()
        }
    }

    Box(Modifier.fillMaxSize().background(Noir.Bg)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("setup · $doneCount of 7 done", Modifier.weight(1f))
                    Text(
                        if (doneCount == 7) "Done" else "Later", style = MaterialTheme.typography.titleSmall, color = Noir.Dim,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEach { step ->
                        Box(
                            Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(
                                when { step.done -> Noir.Lime; step == current -> Noir.Text; else -> Color(0x24FFFFFF) }
                            )
                        )
                    }
                }
            }
            item {
                Text(
                    buildAnnotatedString {
                        if (doneCount == 7) { append("You're all\n"); withStyle(SpanStyle(color = Noir.Lime)) { append("set.") } }
                        else { append("Seven taps\nto "); withStyle(SpanStyle(color = Noir.Lime)) { append("ready.") } }
                    },
                    style = MaterialTheme.typography.displayMedium, color = Noir.Text, modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Smoke(padding = 0.dp, radius = 18.dp) {
                    steps.forEachIndexed { i, step ->
                        if (i > 0 && step != current && steps[i - 1] != current) NoirDivider()
                        if (step == current) CurrentStep(i + 1, step, state, onAct = { act(step) })
                        else StepRow(i + 1, step) { focus = step.key }
                    }
                }
            }
            if (doneCount == 7) {
                item {
                    LimeButton("Start answering calls", Modifier.fillMaxWidth()) {
                        state.update(reapply = true) { it.copy(onboarded = true) }
                        state.toast("$name is ready · ${c.mode.name.lowercase().replace("ask", "ask me")} mode")
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(n: Int, step: Step, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !step.done, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepMark(n, step.done, current = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.titleSmall, color = if (step.done) Noir.Text else Noir.Dim)
            Text(step.sub, style = MaterialTheme.typography.bodySmall, color = Noir.Faint, maxLines = 1)
        }
        if (step.done) MonoLabel(step.doneLabel, color = Noir.Lime)
    }
}

@Composable
private fun CurrentStep(n: Int, step: Step, state: AppState, onAct: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(8.dp).clip(shape)
            .background(Noir.Lime.copy(alpha = 0.04f))
            .border(1.5.dp, Noir.Lime.copy(alpha = 0.55f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepMark(n, done = false, current = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = Noir.Text)
                Text(step.sub, style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(step.why, style = MaterialTheme.typography.bodySmall, color = Noir.Dim)
        Spacer(Modifier.height(12.dp))
        val label = when (step.key) {
            "access" -> "Turn on"
            "brain" -> "Choose a brain"
            "battery" -> "Set unrestricted"
            "samsung" -> "Open call settings"
            else -> "Allow"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LimeButton(label, height = 44.dp, onClick = onAct)
            if (step.key == "samsung") HairButton("Done") { state.confirmSamsung(true) }
        }
    }
}

@Composable
private fun StepMark(n: Int, done: Boolean, current: Boolean) {
    if (done) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Noir.Lime), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Done", tint = Noir.Ink, modifier = Modifier.size(14.dp))
        }
    } else {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (current) Noir.Text else Color(0x47FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelSmall, color = if (current) Noir.Text else Noir.Dim) }
    }
}
