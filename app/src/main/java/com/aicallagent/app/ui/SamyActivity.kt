package com.aicallagent.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aicallagent.app.agent.AgentSettings
import com.aicallagent.app.assistant.ContactEntry
import com.aicallagent.app.assistant.ContactMatcher
import com.aicallagent.app.assistant.SamyActions
import com.aicallagent.app.assistant.SamyHistory
import com.aicallagent.app.assistant.VoiceCommand
import com.aicallagent.app.ui.components.MonoLabel
import com.aicallagent.app.ui.theme.Noir
import com.aicallagent.app.ui.theme.NoirTheme
import com.aicallagent.app.ui.theme.SamyColors
import java.util.Locale
import java.util.concurrent.Executors

/**
 * "Samy, call Pooja." A violet sheet over whatever is on screen (from the phone's assistant gesture,
 * the quick-settings tile, the Samy tab or its icon shortcut): it listens, works out the command, then
 * counts down with a Cancel before calling, texting, sending on WhatsApp or starting Maps.
 */
class SamyActivity : ComponentActivity() {

    private sealed interface Phase {
        data class Listening(val partial: String = "") : Phase
        data class Thinking(val heard: String) : Phase
        data class Confirm(val heard: String, val command: VoiceCommand, val contact: ContactEntry?, val seconds: Int) : Phase
        data class Pick(val heard: String, val command: VoiceCommand, val choices: List<ContactEntry>) : Phase
        data class Note(val heard: String, val text: String, val retry: Boolean = true) : Phase
    }

    private val phase = mutableStateOf<Phase>(Phase.Listening())
    // Microphone loudness, 0–1: the orb swells as you talk.
    private val level = mutableFloatStateOf(0f)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var asked = false
    private var pendingSay: String? = null
    private val voice by lazy { AgentSettings.load(this).voice }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { proceed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSay = intent.getStringExtra(EXTRA_SAY)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN") }
        setContent {
            NoirTheme {
                Sheet(
                    phase.value,
                    onClose = ::finish,
                    onRetry = ::listen,
                    onGo = ::go,
                    onPick = { p, contact -> show(Phase.Confirm(p.heard, p.command, contact, ACT_SECONDS)) },
                )
            }
        }
        start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handler.removeCallbacksAndMessages(null)
        pendingSay = intent.getStringExtra(EXTRA_SAY)
        proceed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.shutdown()
        worker.shutdownNow()
        super.onDestroy()
    }

    /** Microphone and contacts to understand; phone and SMS to act without extra taps. Asked once. */
    private fun start() {
        val needed = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty() && !asked) {
            asked = true
            permissions.launch(needed.toTypedArray())
        } else {
            proceed()
        }
    }

    /** A command passed in (a "try saying" chip) runs straight away; otherwise listen. */
    private fun proceed() {
        val say = pendingSay
        pendingSay = null
        if (!say.isNullOrBlank()) understand(say) else listen()
    }

    private fun listen() {
        handler.removeCallbacksAndMessages(null)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            phase.value = Phase.Note("", "$voice needs the microphone to hear you. Allow it in Settings › Apps › Otto › Permissions.", retry = false)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            phase.value = Phase.Note("", "Speech recognition isn't available on this phone.", retry = false)
            return
        }
        recognizer?.destroy()
        phase.value = Phase.Listening()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(partial: Bundle?) {
                    val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank() && phase.value is Phase.Listening) phase.value = Phase.Listening(text)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) phase.value = Phase.Note("", "I didn't catch that.") else understand(text)
                }
                override fun onError(error: Int) {
                    if (phase.value !is Phase.Listening) return
                    phase.value = Phase.Note("", when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet for speech right now."
                        else -> "I couldn't listen just now."
                    })
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { level.floatValue = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { level.floatValue = 0f }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
    }

    private fun understand(heard: String) {
        phase.value = Phase.Thinking(heard)
        worker.execute {
            val command = SamyActions.interpret(applicationContext, heard)
            val next: Phase = when {
                command.action == VoiceCommand.Action.UNKNOWN -> Phase.Note(heard,
                    "I can call, text, WhatsApp or start Maps. Try “call Pooja” or “take me to Phoenix Mall by bike”.")
                command.action == VoiceCommand.Action.WHATSAPP && !SamyActions.hasWhatsApp(this) -> Phase.Note(heard, "WhatsApp isn't installed on this phone.")
                !command.needsContact -> Phase.Confirm(heard, command, null, NAVIGATE_SECONDS)
                else -> {
                    val contacts = SamyActions.contacts(this)
                    val best = ContactMatcher.best(command.contact, contacts)
                    val ranked = if (best == null) ContactMatcher.rank(command.contact, contacts).take(5) else emptyList()
                    when {
                        contacts.isEmpty() -> Phase.Note(heard, "$voice needs your contacts to find “${command.contact}”. Allow Contacts for Otto in Settings.", retry = false)
                        best != null -> Phase.Confirm(heard, command, best, ACT_SECONDS)
                        ranked.isEmpty() -> Phase.Note(heard, "No contact called “${command.contact}”.")
                        else -> Phase.Pick(heard, command, ranked)
                    }
                }
            }
            runOnUiThread { if (!isFinishing) show(next) }
        }
    }

    private fun show(next: Phase) {
        phase.value = next
        if (next is Phase.Confirm) {
            tts?.speak(spoken(next.command, next.contact), TextToSpeech.QUEUE_FLUSH, null, "samy")
            tick(next)
        }
    }

    private fun tick(p: Phase.Confirm) {
        handler.postDelayed({
            if (phase.value != p) return@postDelayed
            if (p.seconds <= 1) go() else {
                val next = p.copy(seconds = p.seconds - 1)
                phase.value = next
                tick(next)
            }
        }, 1000)
    }

    private fun go() {
        val p = phase.value as? Phase.Confirm ?: return
        handler.removeCallbacksAndMessages(null)
        val line = runCatching { SamyActions.execute(applicationContext, p.command, p.contact) }
            .onSuccess {
                val (title, detail) = done(p.command, p.contact)
                SamyHistory.add(applicationContext, SamyHistory.Entry(System.currentTimeMillis(), p.command.action, p.heard, title, detail))
            }
            .getOrElse { "Couldn't do that: ${it.javaClass.simpleName}" }
        if (line.isNotBlank()) Toast.makeText(applicationContext, line, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun spoken(c: VoiceCommand, contact: ContactEntry?): String = when (c.action) {
        VoiceCommand.Action.CALL -> "Calling ${contact?.name}"
        VoiceCommand.Action.SMS -> "Texting ${contact?.name}"
        VoiceCommand.Action.WHATSAPP -> "Sending to ${contact?.name} on WhatsApp"
        VoiceCommand.Action.NAVIGATE -> "Starting navigation to ${c.destination}"
        VoiceCommand.Action.UNKNOWN -> ""
    }

    /** How the Samy tab's "recent" list shows a finished command. */
    private fun done(c: VoiceCommand, contact: ContactEntry?): Pair<String, String> = when (c.action) {
        VoiceCommand.Action.CALL -> "Called ${contact?.name}" to contact?.number.orEmpty()
        VoiceCommand.Action.SMS -> "Text to ${contact?.name}" to "“${c.message}”"
        VoiceCommand.Action.WHATSAPP -> "WhatsApp to ${contact?.name}" to "“${c.message}”"
        VoiceCommand.Action.NAVIGATE -> c.destination to "Google Maps · ${c.mode.label}"
        VoiceCommand.Action.UNKNOWN -> "" to ""
    }

    @Composable
    private fun Sheet(p: Phase, onClose: () -> Unit, onRetry: () -> Unit, onGo: () -> Unit, onPick: (Phase.Pick, ContactEntry) -> Unit) {
        Box(
            Modifier.fillMaxSize().background(Color(0x9E000000))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(SamyColors.Surface)
                    .border(1.dp, SamyColors.Line, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(end = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(SamyColors.Line))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel(
                        when (p) {
                            is Phase.Listening -> "$voice · listening"
                            is Phase.Thinking -> "$voice · thinking"
                            is Phase.Confirm -> "$voice · ${verb(p.command).lowercase()} in ${p.seconds}"
                            is Phase.Pick -> "$voice · which one?"
                            is Phase.Note -> voice
                        },
                        Modifier.weight(1f), color = SamyColors.Violet,
                    )
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = SamyColors.Text2) }
                }
                Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Steps(
                        when (p) {
                            is Phase.Listening -> 0
                            is Phase.Thinking, is Phase.Pick -> 1
                            is Phase.Confirm -> 2
                            is Phase.Note -> if (p.heard.isBlank()) 0 else 1
                        },
                    )
                    when (p) {
                        is Phase.Listening -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                SamyOrb(Modifier.size(190.dp), level = level.floatValue)
                            }
                            Text(
                                p.partial.ifBlank { "Listening… say “call Pooja”, “WhatsApp Soham where have you reached” or “take me to Phoenix Mall by bike”." },
                                style = if (p.partial.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                                color = if (p.partial.isBlank()) SamyColors.Text2 else Noir.Text,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is Phase.Thinking -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                SamyOrb(Modifier.size(130.dp), busy = true)
                            }
                            Text("“${p.heard}”", style = MaterialTheme.typography.headlineSmall, color = Noir.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        is Phase.Confirm -> {
                            Heard(p.heard)
                            Plan(p.command, p.contact)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Pill("Cancel", filled = false, onClick = onClose)
                                Pill(verb(p.command) + " now", filled = true, count = p.seconds, onClick = onGo)
                            }
                        }
                        is Phase.Pick -> {
                            Heard(p.heard)
                            Column(Modifier.clip(RoundedCornerShape(22.dp)).background(SamyColors.Soft)) {
                                p.choices.forEachIndexed { i, c ->
                                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(SamyColors.Line))
                                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(p, c) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text(c.name, style = MaterialTheme.typography.titleSmall, color = Noir.Text)
                                        Text(c.number, style = MaterialTheme.typography.bodySmall, color = SamyColors.Text2)
                                    }
                                }
                            }
                        }
                        is Phase.Note -> {
                            if (p.heard.isNotBlank()) Heard(p.heard)
                            Text(p.text, style = MaterialTheme.typography.bodyLarge, color = Noir.Text)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Pill("Close", filled = false, onClick = onClose)
                                if (p.retry) Pill("Try again", filled = true, onClick = onRetry)
                            }
                        }
                    }
                }
            }
        }
    }

    /** heard → check → done, so you can see where Samy is. */
    @Composable
    private fun Steps(at: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("heard", "check", "done").forEachIndexed { i, label ->
                if (i > 0) Box(Modifier.weight(1f).height(1.dp).background(if (i <= at) SamyColors.Violet else SamyColors.Line))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                    when {
                        i < at -> Box(Modifier.size(18.dp).clip(CircleShape).background(SamyColors.Violet), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = Noir.Ink, modifier = Modifier.size(12.dp))
                        }
                        i == at -> Box(Modifier.size(18.dp).border(2.dp, SamyColors.Violet, CircleShape))
                        else -> Box(Modifier.size(18.dp).border(1.5.dp, SamyColors.Line, CircleShape))
                    }
                    Spacer(Modifier.width(6.dp))
                    MonoLabel(label, color = if (i <= at) Noir.Text else SamyColors.Text2)
                }
            }
        }
    }

    @Composable
    private fun RowScope.Pill(text: String, filled: Boolean, count: Int? = null, onClick: () -> Unit) {
        Row(
            Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(50))
                .background(if (filled) SamyColors.Violet else Color.Transparent)
                .then(if (filled) Modifier else Modifier.border(1.dp, SamyColors.Line, RoundedCornerShape(50)))
                .clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = if (filled) Noir.Ink else SamyColors.Light)
            if (count != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(26.dp).border(2.dp, Noir.Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = Noir.Ink)
                }
            }
        }
    }

    @Composable
    private fun Heard(text: String) {
        Text("“$text”", style = MaterialTheme.typography.bodyMedium, color = SamyColors.Text2)
    }

    @Composable
    private fun Plan(c: VoiceCommand, contact: ContactEntry?) {
        val (icon, title, detail) = when (c.action) {
            VoiceCommand.Action.CALL -> Triple(Icons.Rounded.Call, "Call ${contact?.name}", contact?.number.orEmpty())
            VoiceCommand.Action.SMS -> Triple(Icons.Rounded.Sms, "Text ${contact?.name}", "“${c.message}”")
            VoiceCommand.Action.WHATSAPP -> Triple(Icons.AutoMirrored.Rounded.Chat, "WhatsApp ${contact?.name}", "“${c.message}”")
            VoiceCommand.Action.NAVIGATE -> Triple(Icons.Rounded.Navigation, c.destination, "Google Maps · ${c.mode.label}")
            VoiceCommand.Action.UNKNOWN -> Triple(Icons.Rounded.Close, "", "")
        }
        Row(verticalAlignment = Alignment.Top) {
            PlanIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Noir.Text)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyLarge, color = SamyColors.Light)
                if (c.action == VoiceCommand.Action.SMS || c.action == VoiceCommand.Action.WHATSAPP) {
                    Spacer(Modifier.height(4.dp))
                    MonoLabel(if (c.action == VoiceCommand.Action.WHATSAPP) "grammar fixed · otto presses send" else "grammar fixed", color = SamyColors.Text2)
                }
            }
        }
    }

    @Composable
    private fun PlanIcon(icon: ImageVector) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(SamyColors.Violet), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Noir.Ink, modifier = Modifier.size(26.dp))
        }
    }

    private fun verb(c: VoiceCommand) = when (c.action) {
        VoiceCommand.Action.CALL -> "Call"
        VoiceCommand.Action.NAVIGATE -> "Go"
        else -> "Send"
    }

    companion object {
        /** A command to run without listening, e.g. a "try saying" chip on the Samy tab. */
        const val EXTRA_SAY = "samy_say"

        // Time to say "no" before Samy calls or sends; starting Maps can't hurt, so it's quicker.
        private const val ACT_SECONDS = 3
        private const val NAVIGATE_SECONDS = 2
    }
}
