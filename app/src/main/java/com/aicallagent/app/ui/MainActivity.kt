package com.aicallagent.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.app.role.RoleManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.aicallagent.app.AccessibilitySwitch
import com.aicallagent.app.R
import com.aicallagent.app.CallAccessibilityService
import com.aicallagent.app.agent.AgentConfig
import com.aicallagent.app.agent.AgentSettings
import com.aicallagent.app.agent.AnswerMode
import com.aicallagent.app.agent.CallFinisher
import com.aicallagent.app.agent.CallRecord
import com.aicallagent.app.agent.CallRecords
import com.aicallagent.app.agent.ModelDownloads
import com.aicallagent.app.assistant.SamyHistory
import com.aicallagent.app.agent.OwnerStatus
import com.aicallagent.app.ui.components.NoirToast
import com.aicallagent.app.agent.Provider
import com.aicallagent.app.local.LocalModelActivity
import com.aicallagent.app.local.LocalModelRuntime
import com.aicallagent.app.model.AutoReplyFlow
import com.aicallagent.app.ui.components.noirBackground
import com.aicallagent.app.ui.theme.Noir
import com.aicallagent.app.ui.theme.NoirTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val openCallId = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCallId.longValue = intent.getLongExtra(EXTRA_CALL_ID, 0L)
        CallFinisher.backfillSummaries(this)
        // Long-press the app icon: "Ask Samy".
        runCatching {
            getSystemService(ShortcutManager::class.java)?.dynamicShortcuts = listOf(
                ShortcutInfo.Builder(this, "samy")
                    .setShortLabel("Ask ${AgentSettings.load(this).voice}")
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_stat_spark))
                    .setIntent(Intent(this, SamyActivity::class.java).setAction(Intent.ACTION_VIEW))
                    .build()
            )
        }
        // The opening animation plays on a fresh launch only: not after rotation, not when a call
        // notification opens a transcript, and not when the phone has animations turned off.
        val playIntro = savedInstanceState == null && openCallId.longValue == 0L &&
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        setContent {
            NoirTheme {
                var showIntro by rememberSaveable { mutableStateOf(playIntro) }
                Box(Modifier.fillMaxSize()) {
                    SaathiApp(remember { AppState(applicationContext) }, openCallId.longValue) { openCallId.longValue = 0 }
                    AnimatedVisibility(showIntro, enter = EnterTransition.None, exit = fadeOut(tween(320))) {
                        MirrorIntro { showIntro = false }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openCallId.longValue = intent.getLongExtra(EXTRA_CALL_ID, 0L)
    }

    companion object { const val EXTRA_CALL_ID = "call_id" }
}

data class LiveStatus(
    val accessibility: Boolean = false,
    val phonePerm: Boolean = false,
    val callLogPerm: Boolean = false,
    val notifPerm: Boolean = false,
    val battery: Boolean = false,
    val brainReady: Boolean = false,
    val localModel: Boolean = false,
    val armed: Boolean = false,
    val preparing: Boolean = false,
    val onCall: Boolean = false,
    val stage: AutoReplyFlow.Stage = AutoReplyFlow.Stage.OFF,
    val failure: String = "",
) {
    val coreReady get() = accessibility && phonePerm && brainReady
    val allReady get() = coreReady && callLogPerm && notifPerm && battery
}

enum class ToastKind { OK, ERROR, INFO }
data class ToastMsg(val text: String, val kind: ToastKind = ToastKind.OK, val id: Long = System.nanoTime())

class AppState(private val context: Context) {
    var config by mutableStateOf(AgentSettings.load(context))
        private set
    var toast by mutableStateOf<ToastMsg?>(null)
        private set
    var download by mutableStateOf(ModelDownloads.state)
        private set

    fun toast(text: String, kind: ToastKind = ToastKind.OK) { toast = ToastMsg(text, kind) }
    fun clearToast(id: Long) { if (toast?.id == id) toast = null }
    fun appContext() = context
    var status by mutableStateOf(LiveStatus())
        private set
    var calls by mutableStateOf<List<CallRecord>>(emptyList())
        private set
    private var callsVersion = -1L

    fun update(reapply: Boolean = false, transform: (AgentConfig) -> AgentConfig) {
        config = transform(config)
        AgentSettings.save(context, config)
        if (reapply) CallAccessibilityService.applySettings()
        refresh()
    }

    fun apiKey(p: Provider) = AgentSettings.apiKey(context, p)

    fun setApiKey(p: Provider, key: String) {
        AgentSettings.setApiKey(context, p, key)
        if (p == config.provider) CallAccessibilityService.applySettings()
        refresh()
    }

    fun deleteCall(id: Long) { CallRecords.delete(context, id); refresh() }

    // Ticks every 30 s so a status that runs out leaves the screen without a restart.
    var clock by mutableLongStateOf(System.currentTimeMillis() / 30_000 * 30_000)
        private set
    val activeStatus: OwnerStatus? get() = config.activeStatus(clock)

    /** What the owner is doing; [untilMs] 0 keeps it until changed, null clears it. */
    fun setStatus(status: OwnerStatus?, untilMs: Long = 0) {
        clock = System.currentTimeMillis()
        update(reapply = true) { it.copy(statusId = status?.id.orEmpty(), statusUntil = if (status == null) 0 else untilMs) }
        if (status == null) toast("Status cleared · ${config.assistant} is back to your usual mode", ToastKind.INFO)
        else toast("${status.emoji} ${status.label} · ${config.assistant} answers every call")
    }

    fun saveStatus(status: OwnerStatus) = update(reapply = true) { c ->
        c.copy(statuses = if (c.statuses.any { it.id == status.id }) c.statuses.map { if (it.id == status.id) status else it } else c.statuses + status)
    }

    fun deleteStatus(id: String) = update(reapply = true) { c ->
        c.copy(statuses = c.statuses.filterNot { it.id == id }, statusId = if (c.statusId == id) "" else c.statusId)
    }

    // Small UI-only memory: urgent calls you've already dealt with, and the Samsung settings check,
    // which no app can read, so the owner confirms it once in setup.
    private val ui = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    var handledCalls by mutableStateOf(ui.getStringSet("handled_calls", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
        private set
    var samsungConfirmed by mutableStateOf(ui.getBoolean("samsung_confirmed", false))
        private set

    fun markHandled(id: Long, handled: Boolean = true) {
        handledCalls = if (handled) handledCalls + id else handledCalls - id
        ui.edit().putStringSet("handled_calls", handledCalls.map { it.toString() }.toSet()).apply()
    }

    fun confirmSamsung(done: Boolean) {
        samsungConfirmed = done
        ui.edit().putBoolean("samsung_confirmed", done).apply()
    }

    /** Device name as the owner set it ("Galaxy S21 FE"), else the model code. */
    fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL

    fun refresh() {
        clock = System.currentTimeMillis() / 30_000 * 30_000
        val granted = { p: String -> context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED }
        val localModel = runCatching { LocalModelRuntime.get(context).hasModel() }.getOrDefault(false)
        // With "only during calls", Otto's accessibility is off between calls by design: that's
        // ready, not missing, as long as Otto can still switch it on when a call rings.
        val serviceOn = CallAccessibilityService.isServiceConnected()
        val autoSwitch = config.accessibilityOnlyDuringCalls && AccessibilitySwitch.canSwitch(context)
        status = LiveStatus(
            accessibility = serviceOn || autoSwitch,
            phonePerm = granted(Manifest.permission.READ_PHONE_STATE),
            callLogPerm = granted(Manifest.permission.READ_CALL_LOG),
            notifPerm = granted(Manifest.permission.POST_NOTIFICATIONS),
            battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            brainReady = if (config.provider == Provider.LOCAL) localModel else AgentSettings.apiKey(context, config.provider).isNotEmpty(),
            localModel = localModel,
            armed = CallAccessibilityService.isAutoReplyEnabled() || (!serviceOn && autoSwitch && config.mode != AnswerMode.OFF),
            preparing = CallAccessibilityService.isModelPreparing(),
            onCall = CallAccessibilityService.isAiOnCall(),
            stage = CallAccessibilityService.getReplyStage(),
            failure = CallAccessibilityService.getLastFailure(),
        )
        download = ModelDownloads.state
        if (SamyHistory.version != samyVersion) {
            samyVersion = SamyHistory.version
            samyHistory = SamyHistory.all(context)
        }
        if (CallRecords.version != callsVersion) {
            callsVersion = CallRecords.version
            calls = CallRecords.all(context)
        }
    }

    fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** Opens Samy listening, or with [say] runs that command straight away (still with the countdown). */
    fun openSamy(say: String? = null) = launch(Intent(context, SamyActivity::class.java).apply {
        if (say != null) putExtra(SamyActivity.EXTRA_SAY, say)
    })

    var samyHistory by mutableStateOf<List<SamyHistory.Entry>>(emptyList())
        private set
    private var samyVersion = -1L

    fun clearSamyHistory() {
        SamyHistory.clear(context)
        refresh()
        toast("${config.voice}'s history cleared", ToastKind.INFO)
    }

    /** The voice assistant's name. It can't be the call persona's: callers would hear the wrong one. */
    fun setVoiceName(name: String): Boolean {
        if (name.trim().equals(config.assistant, ignoreCase = true)) {
            toast("“${config.assistant}” answers your calls. Pick another name for the voice assistant.", ToastKind.ERROR)
            return false
        }
        update { it.copy(voiceName = name.take(24)) }
        return true
    }

    /** True when Otto is the phone's digital assistant, so the corner swipe opens Samy anywhere. */
    fun isPhoneAssistant(): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

    /** Android won't let an app make itself the assistant: open Default apps for the owner to pick Otto. */
    fun openAssistantSettings() {
        launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        toast("Tap Digital assistant app › Device assistance app › Otto", ToastKind.INFO)
    }

    /** False when Otto lacks the one-time permission, so the caller shows how to grant it. */
    fun setAccessibilityOnlyDuringCalls(on: Boolean): Boolean {
        if (on && !AccessibilitySwitch.canSwitch(context)) return false
        update(reapply = true) { it.copy(accessibilityOnlyDuringCalls = on) }
        if (on) {
            toast("Accessibility now switches on only during calls")
        } else {
            // Back to always on. Without the permission the owner turns it on in Settings.
            if (!AccessibilitySwitch.turnOn(context)) openAccessibilitySettings()
            toast("Accessibility stays on all the time")
        }
        refresh()
        return true
    }

    fun requestBatteryExemption() = launch(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
    )

    fun openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun dial(number: String) = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

    /** Samsung's call settings host Text Call and "Call display while using apps". */
    fun openCallSettings() {
        if (!launch(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))) launch(Intent(Intent.ACTION_DIAL))
    }

    fun openLocalModel() = launch(Intent(context, LocalModelActivity::class.java))

    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun launch(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}

/**
 * v3 layout: one Today screen with no tab bar. The avatar opens "You" (persona, brain, phone setup,
 * calls) as sheets stacked over Today; first run opens the "Seven taps to ready" setup.
 */
@Composable
private fun SaathiApp(state: AppState, openCallId: Long, onCallOpened: () -> Unit) {
    var sheets by remember { mutableStateOf(listOf<SheetPage>()) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CallRecord?>(null) }
    var playground by rememberSaveable { mutableStateOf(false) }
    // Opens on the two doors; a call notification goes straight into Otto.
    var space by rememberSaveable { mutableStateOf(if (openCallId != 0L) Space.OTTO else Space.HOME) }
    var samySettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state.refresh()
        if (!state.config.onboarded && !state.status.coreReady) setupOpen = true
        while (true) { state.refresh(); delay(700) }
    }
    LaunchedEffect(openCallId, state.calls) {
        if (openCallId != 0L) {
            state.calls.firstOrNull { it.id == openCallId }?.let { space = Space.OTTO; detail = it; onCallOpened() }
        }
    }
    fun open(page: SheetPage) { sheets = sheets.takeWhile { it != page } + page }
    BackHandler(enabled = sheets.isNotEmpty()) { sheets = sheets.dropLast(1) }
    BackHandler(enabled = sheets.isEmpty() && setupOpen) { setupOpen = false }
    BackHandler(enabled = sheets.isEmpty() && !setupOpen && space != Space.HOME) { space = Space.HOME }
    BackHandler(enabled = samySettings) { samySettings = false }

    Box(Modifier.fillMaxSize().noirBackground()) {
        Crossfade(space, animationSpec = tween(220), label = "space") { current ->
            when (current) {
                Space.HOME -> HomeDoors(
                    state,
                    onOtto = { space = Space.OTTO },
                    onSamy = { space = Space.SAMY },
                    onAvatar = { open(SheetPage.YOU) },
                )
                Space.OTTO -> TodayScreen(
                    state,
                    onAvatar = { open(SheetPage.YOU) },
                    onBrain = { open(SheetPage.BRAIN) },
                    onSetup = { setupOpen = true },
                    onAllCalls = { open(SheetPage.CALLS) },
                    onOpenCall = { detail = it },
                    onStatus = { open(SheetPage.STATUS) },
                    onBack = { space = Space.HOME },
                )
                Space.SAMY -> SamyHome(state, onSettings = { samySettings = true }, onBack = { space = Space.HOME })
            }
        }
        AnimatedVisibility(samySettings, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            SamySettings(state, onClose = { samySettings = false })
        }
        AnimatedVisibility(setupOpen, enter = fadeIn(), exit = fadeOut()) {
            SetupScreen(state, onClose = { setupOpen = false }, onBrain = { open(SheetPage.BRAIN) })
        }
        AnimatedVisibility(sheets.isNotEmpty(), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            val page = sheets.lastOrNull() ?: SheetPage.YOU
            AnimatedContent(page, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "sheet") { current ->
                SheetFrame(
                    current,
                    below = sheets.getOrNull(sheets.indexOf(current) - 1),
                    onBack = { sheets = sheets.dropLast(1) },
                    onCloseAll = { sheets = emptyList() },
                ) {
                    when (current) {
                        SheetPage.YOU -> YouContent(
                            state, open = ::open,
                            onSetup = { sheets = emptyList(); setupOpen = true },
                            onTestCall = { playground = true },
                        )
                        SheetPage.PERSONA -> PersonaScreen(state, onTry = { playground = true }, inSheet = true)
                        SheetPage.BRAIN -> BrainScreen(state, onTry = { playground = true }, inSheet = true)
                        SheetPage.CALLS -> CallsContent(state, onOpenCall = { detail = it })
                        SheetPage.STATUS -> StatusContent(state, onDone = { sheets = sheets.dropLast(1) })
                    }
                }
            }
        }
        NoirToast(state.toast, Modifier.align(Alignment.TopCenter)) { state.clearToast(it) }
    }

    detail?.let { record -> CallDetailDialog(state, record, onDismiss = { detail = null }) }
    if (playground) PlaygroundDialog(state, onDismiss = { playground = false })
}
