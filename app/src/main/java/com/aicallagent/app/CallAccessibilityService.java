package com.aicallagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.content.Intent;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.aicallagent.app.model.CallStateModel;
import com.aicallagent.app.model.AutoReplyFlow;
import com.aicallagent.app.model.CallLifecycle;
import com.aicallagent.app.model.ReplyDiagnostics;
import com.aicallagent.app.model.IncomingCallGate;
import com.aicallagent.app.model.ReplySendController;
import com.aicallagent.app.model.CallMenuController;
import com.aicallagent.app.model.SendEventDiagnostics;
import com.aicallagent.app.model.InputTrace;
import com.aicallagent.app.policy.CallPackagePolicy;
import com.aicallagent.app.policy.SpeechBusyNotice;

import com.aicallagent.app.local.LocalModelRuntime;
import com.aicallagent.app.local.ReplyEngine;
import com.aicallagent.app.local.ReplyRequest;
import com.aicallagent.app.local.CallReplyGate;
import com.aicallagent.app.agent.AgentConfig;
import com.aicallagent.app.agent.AgentSettings;
import com.aicallagent.app.agent.AnswerMode;
import com.aicallagent.app.agent.CallFinisher;
import com.aicallagent.app.agent.CloudReplyEngine;
import com.aicallagent.app.agent.PromptBuilder;
import com.aicallagent.app.agent.Provider;
import com.aicallagent.app.model.CallConversation;
import com.aicallagent.app.overlay.CallOverlay;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Observes Samsung's call UI. While the assistant is armed it offers a "Let AI answer" button on
 * incoming calls (or answers after a countdown in Auto mode), switches to Text Call and replies.
 * Transcripts of AI-answered calls go only to the app's private call log; logs carry no content.
 */
public class CallAccessibilityService extends AccessibilityService {

    private static final String TAG = "CallAccessibility";
    public static final String PKG_INCALLUI = CallPackagePolicy.PKG_INCALLUI;
    public static final String PKG_TELEPHONYUI = CallPackagePolicy.PKG_TELEPHONYUI;

    private static volatile boolean serviceConnected = false;
    private static volatile CallStateModel.CallState lastObservedCallState = CallStateModel.CallState.IDLE;
    private static volatile CallAccessibilityService liveService;
    private static volatile boolean autoReplyEnabled;
    private static volatile AutoReplyFlow.Stage replyStage = AutoReplyFlow.Stage.OFF;
    private static volatile AutoReplyFlow.Failure replyFailure = AutoReplyFlow.Failure.NONE;
    public enum WindowObservation { UNOBSERVED, NO_WINDOW, OTHER_APP, PHONE_NO_CONTROLS, CALL_VISIBLE, SYSTEM_IDLE }
    private static volatile WindowObservation windowObservation = WindowObservation.UNOBSERVED;
    private static volatile int observedSpeechCount, generationRequestCount, generationResponseCount, sentReplyCount;
    private static volatile boolean generationRunning;
    private static volatile String lastFailure = "";
    private static volatile boolean modelPreparing;
    private static volatile Boolean systemCallActive;
    private static volatile boolean diagnosticInputReady, diagnosticInputMatches, diagnosticSubmitReady;
    public static boolean isDiagnosticInputReady() { return diagnosticInputReady; }
    public static boolean isDiagnosticInputMatches() { return diagnosticInputMatches; }
    public static boolean isDiagnosticSubmitReady() { return diagnosticSubmitReady; }
    public static Boolean getSystemCallActive() { return systemCallActive; }
    public static boolean hasPhoneStatePermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }
    public static WindowObservation getWindowObservation() { return windowObservation; }
    public static int getObservedSpeechCount() { return observedSpeechCount; }
    public static int getGenerationRequestCount() { return generationRequestCount; }
    public static int getGenerationResponseCount() { return generationResponseCount; }
    public static int getSentReplyCount() { return sentReplyCount; }
    public static boolean isGenerationRunning() { return generationRunning; }
    public static String getLastFailure() { return lastFailure; }
    public static boolean isModelPreparing() { return modelPreparing; }
    private static void clearDiagnostics() {
        observedSpeechCount = generationRequestCount = generationResponseCount = sentReplyCount = 0;
        generationRunning = false;
        lastFailure = "";
    }
    private final AutoReplyFlow flow = new AutoReplyFlow();
    private CallLifecycle callLifecycle = new CallLifecycle();
    private AutoReplyFlow.Snapshot diagnosticScreen = new AutoReplyFlow.Snapshot();
    private String lastDiagnostic = "";
    private String lastSpeechIdentityDiagnostic = "";
    private boolean diagnosticEventsExpanded;
    private final SendEventDiagnostics sendEventDiagnostics = new SendEventDiagnostics();
    private final IncomingCallGate incomingCallGate = new IncomingCallGate();
    private final InputTrace inputTrace = new InputTrace(SystemClock::uptimeMillis,
            line -> note("InputTrace " + line));
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FlowTrace trace;
    private static final long CALL_SCREEN_MISSING_MS = 1_000;
    private static final long CALL_SCREEN_NUDGE_MS = 3_000;
    private long lastCallScreenNudgeAt = -CALL_SCREEN_NUDGE_MS;
    private long callScreenMissingSince = -1;
    private boolean ringNudged;
    // "Accessibility only during calls": switch off this long after the last call ended.
    private static final long AUTO_OFF_AFTER_IDLE_MS = 15_000;
    private boolean onlyDuringCalls;
    private long idleSince = -1;
    // Samy's "WhatsApp Soham …": WhatsApp lets no app send, so after Otto opens the chat with the
    // message typed, the service presses Send once, and only if the box holds exactly that message.
    private static final long WHATSAPP_SEND_WINDOW_MS = 15_000;
    private static final long WHATSAPP_POLL_MS = 300;
    private static volatile String whatsAppMessage;
    private static volatile long whatsAppUntil;
    private long whatsAppClickedAt;
    private final Runnable whatsAppPoll = this::pollWhatsApp;
    // Whether the saved settings asked for the assistant to be on (a status counts as Auto).
    private boolean armedFor;
    private long statusCheckedAt;
    private static final long STATUS_CHECK_MS = 15_000;
    // Start the reply while the caller pauses, so it's ready when the turn-end silence ends.
    // Capped per turn: every draft counts against the Gemini key's per-minute request limit, and
    // running out leaves the call summary without a request to spend.
    private static final long SPECULATE_AFTER_MS = 450;
    private static final int MAX_SPECULATIONS_PER_TURN = 2;
    private String speculatedTurn = "";
    private int speculationsThisTurn;
    private long generation;
    private boolean driving;
    private LocalModelRuntime localRuntime;
    private ReplyEngine replyEngine;
    private final CallReplyGate replyGate = new CallReplyGate();
    private CallReplyGate.Ticket activeReply;
    private Runnable pendingReplyStart;
    private CallOverlay overlay;
    private CloudReplyEngine cloudEngine;
    private AgentConfig ringConfig;
    private long ringingSince = -1, aiCallStartedAt;
    private boolean answerApproved, promptDismissed;
    private static volatile boolean aiOnCall;
    public static boolean isAiOnCall() { return aiOnCall; }

    private void cancelLlm() {
        boolean pending = activeReply != null;
        replyGate.cancel(); activeReply = null;
        if (pendingReplyStart != null) handler.removeCallbacks(pendingReplyStart);
        pendingReplyStart = null;
        if (pending && replyEngine != null) replyEngine.cancel(this);
        generationRunning = false;
    }
    private void releaseModel() {
        modelPreparing = false;
        if (replyEngine != null) replyEngine.release(this);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            drive();
            if (liveService == CallAccessibilityService.this) {
                handler.postDelayed(this, flow.isEnabled() ? 100 : 1000);
            }
        }
    };

    // Samsung's ringing screen animates and fires content-change events many times a second.
    // Reading the whole call UI for each one starved the main thread, so taps on the
    // "Let … answer" card were dropped. Events now trigger at most one read per interval.
    private static final long EVENT_DRIVE_INTERVAL_MS = 80;
    private static final long TURN_END_SILENCE_MS = 1500;
    private static final long QUESTION_END_SILENCE_MS = 900;
    private long lastDriveAt;
    private boolean driveQueued;
    private final Runnable coalescedDrive = () -> {
        driveQueued = false;
        drive();
    };

    private void requestDrive() {
        long since = SystemClock.uptimeMillis() - lastDriveAt;
        if (since >= EVENT_DRIVE_INTERVAL_MS) {
            drive();
        } else if (!driveQueued) {
            driveQueued = true;
            handler.postDelayed(coalescedDrive, EVENT_DRIVE_INTERVAL_MS - since);
        }
    }

    public static boolean isAutoReplyEnabled() { return autoReplyEnabled; }
    public static AutoReplyFlow.Stage getReplyStage() { return replyStage; }
    public static AutoReplyFlow.Failure getReplyFailure() { return replyFailure; }

    /** Re-applies the saved answer mode and AI provider. Main thread only. */
    public static void applySettings() {
        CallAccessibilityService service = liveService;
        if (service == null) return;
        setAutoReplyEnabled(AgentSettings.load(service).effectiveMode() != AnswerMode.OFF);
    }

    /** Main thread only. The armed state persists through AgentSettings and is restored on connect. */
    public static void setAutoReplyEnabled(boolean enabled) {
        CallAccessibilityService service = liveService;
        if (service == null || Looper.myLooper() != Looper.getMainLooper()) return;
        service.armedFor = enabled;
        service.generation++;
        service.incomingCallGate.reset();
        service.cancelLlm();
        service.flow.disable();
        service.releaseModel();
        AgentConfig config = AgentSettings.load(service);
        service.onlyDuringCalls = config.getAccessibilityOnlyDuringCalls();
        service.flow.setNotices(PromptBuilder.retryNotice(config), PromptBuilder.fallbackNotice(config));
        // Let the caller finish: Samsung's speech-to-text splits one sentence into several bubbles.
        service.flow.setTurnEndSilence(TURN_END_SILENCE_MS, QUESTION_END_SILENCE_MS);
        if (enabled && hasPhoneStatePermission(service) && config.getProvider() != Provider.LOCAL) {
            clearDiagnostics();
            if (service.cloudEngine == null) service.cloudEngine = new CloudReplyEngine(service);
            service.cloudEngine.claim(service);
            service.replyEngine = service.cloudEngine;
            if (service.replyEngine.isReady(service)) {
                service.flow.enable(SamsungCallUi.read(service.callRoot(), false), SystemClock.uptimeMillis());
            } else {
                lastFailure = "NO_KEY";
            }
        } else if (enabled && hasPhoneStatePermission(service)) {
            clearDiagnostics();
            try {
                service.localRuntime = LocalModelRuntime.get(service);
                service.replyEngine = service.localRuntime;
                prepareLocal(service, service.generation, LOCAL_PREPARE_RETRIES);
            } catch (IOException failure) { modelPreparing = false; lastFailure = "STORAGE"; }
        }
        service.publish();
        service.handler.removeCallbacks(service.tick);
        service.handler.post(service.tick);
    }

    // releaseModel() above closes the previous on-device session on the model's worker thread, so the
    // runtime stays busy for a moment. Loading straight away was refused as LOCAL_BUSY and nothing
    // retried: changing any setting with the on-device brain selected left the assistant switched off.
    private static final int LOCAL_PREPARE_RETRIES = 40;
    private static final long LOCAL_PREPARE_RETRY_MS = 250;

    private static void prepareLocal(CallAccessibilityService service, long preparation, int retriesLeft) {
        if (liveService != service || preparation != service.generation) return;
        modelPreparing = true;
        boolean started = service.localRuntime.prepareForCalls(service, (unused, error) -> service.handler.post(() -> {
            if (liveService != service || preparation != service.generation || !modelPreparing) return;
            modelPreparing = false;
            if (error.isEmpty() && service.replyEngine.isReady(service)) {
                service.flow.enable(SamsungCallUi.read(service.callRoot(), false), SystemClock.uptimeMillis());
            } else {
                lastFailure = error.isEmpty() ? "LOAD_FAILED" : error;
                service.releaseModel();
            }
            service.publish();
            service.drive();
        }));
        if (started) return;
        String reason = service.localRuntime.preparationError();
        if ("LOCAL_BUSY".equals(reason) && retriesLeft > 0) {
            service.handler.postDelayed(() -> prepareLocal(service, preparation, retriesLeft - 1), LOCAL_PREPARE_RETRY_MS);
            return;
        }
        modelPreparing = false;
        lastFailure = reason;
        service.publish();
    }

    public static boolean isServiceConnected() {
        return serviceConnected;
    }

    public static CallStateModel.CallState getLastObservedCallState() {
        return lastObservedCallState;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (trace == null) trace = FlowTrace.get(this);
        onlyDuringCalls = AgentSettings.load(this).getAccessibilityOnlyDuringCalls();
        reset();
        liveService = this;
        serviceConnected = true;
        overlay = new CallOverlay(this);
        handler.post(tick);
        if (AgentSettings.load(this).effectiveMode() != AnswerMode.OFF) handler.post(() -> setAutoReplyEnabled(true));
        if (whatsAppMessage != null) handler.post(whatsAppPoll);
        note("AccessibilityService connected.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        reset();
        note("AccessibilityService unbound.");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        reset();
        note("AccessibilityService destroyed.");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        CharSequence eventPkg = event.getPackageName();
        if (diagnosticEventsExpanded && SendEventDiagnostics.shouldExpand(flow.isEnabled(), flow.getStage())) {
            boolean knownSource = CallPackagePolicy.isAllowedPackage(eventPkg)
                    || "com.android.systemui".contentEquals(eventPkg == null ? "" : eventPkg);
            boolean toast = knownSource && "android.widget.Toast".contentEquals(
                    event.getClassName() == null ? "" : event.getClassName());
            note("SendEventTrace " + sendEventDiagnostics.record(eventPkg, event.getEventType(), toast, () -> {
                for (CharSequence text : event.getText()) {
                    if (SpeechBusyNotice.matchesText(text)) return true;
                }
                return false;
            }));
        }
        if (eventPkg == null || !isAllowedPackage(eventPkg.toString())) {
            return;
        }

        boolean notificationEvent = event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        boolean windowStateEvent = event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        boolean toastClass = "android.widget.Toast".contentEquals(
                event.getClassName() == null ? "" : event.getClassName());
        boolean notificationData = event.getParcelableData() != null;
        boolean transientNotice = SpeechBusyNotice.isTransientNotice(notificationEvent, windowStateEvent,
                event.getClassName(), notificationData);
        boolean matched = false;
        if (transientNotice && flow.isEnabled()) {
            for (CharSequence text : event.getText()) {
                if (SpeechBusyNotice.matches(eventPkg, true, text)) {
                    matched = true;
                    boolean accepted = Boolean.TRUE.equals(readSystemCallActive())
                            && flow.speechRejected(SamsungCallUi.read(callRoot(), true,
                            flow.getReplyText()), event.getEventTime(), SystemClock.uptimeMillis());
                    note("SpeechBusyNotice accepted=" + accepted);
                    publish();
                    break;
                }
            }
        }
        if (flow.isEnabled() && (notificationEvent || windowStateEvent)
                && (flow.getStage() == AutoReplyFlow.Stage.VERIFY_SENT
                || flow.getStage() == AutoReplyFlow.Stage.WAIT_SPEECH)) {
            long age = SystemClock.uptimeMillis() - event.getEventTime();
            note("SpeechNoticeEvent kind=" + (notificationEvent ? "NOTIFICATION" : "WINDOW_STATE")
                    + " toast_class=" + toastClass + " notification_data=" + notificationData
                    + " eligible=" + transientNotice + " matched=" + matched
                    + " age_ms=" + Math.min(60_001, Math.max(-1, age)));
        }
        requestDrive();
    }

    @Override
    public void onInterrupt() {
        generation++;
        incomingCallGate.reset();
        cancelLlm();
        releaseModel();
        flow.disable();
        publish();
        note("AccessibilityService interrupted.");
    }

    private void reset() {
        generation++;
        incomingCallGate.reset();
        cancelLlm();
        releaseModel();
        flow.disable();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(coalescedDrive);
        driveQueued = false;
        resetIncomingPrompt();
        aiOnCall = false;
        ringNudged = false;
        callScreenMissingSince = -1;
        dropSpeculation();
        if (liveService == this) liveService = null;
        serviceConnected = false;
        lastObservedCallState = CallStateModel.CallState.IDLE;
        systemCallActive = null;
        callLifecycle = new CallLifecycle();
        diagnosticScreen = new AutoReplyFlow.Snapshot();
        windowObservation = WindowObservation.UNOBSERVED;
        clearDiagnostics();
        publish();
    }

    private void publish() {
        updateDiagnosticEventScope();
        autoReplyEnabled = flow.isEnabled();
        replyStage = flow.getStage();
        replyFailure = flow.getFailure();
        diagnosticInputReady = diagnosticScreen.inputReady;
        diagnosticInputMatches = diagnosticScreen.inputMatchesReply;
        diagnosticSubmitReady = diagnosticScreen.submitReady;
        String diagnostic = ReplyDiagnostics.describe(replyStage, replyFailure, diagnosticScreen,
                generationRequestCount, generationResponseCount, sentReplyCount);
        if (!diagnostic.equals(lastDiagnostic)) {
            lastDiagnostic = diagnostic;
            note("FlowDiagnostic " + diagnostic);
        }
    }

    private void updateDiagnosticEventScope() {
        boolean expand = Boolean.TRUE.equals(systemCallActive)
                && SendEventDiagnostics.shouldExpand(flow.isEnabled(), flow.getStage());
        if (expand == diagnosticEventsExpanded) return;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            diagnosticEventsExpanded = false;
            sendEventDiagnostics.clear();
            return;
        }
        info.eventTypes = expand ? AccessibilityEvent.TYPES_ALL_MASK
                : AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.packageNames = expand ? null : new String[]{PKG_INCALLUI, PKG_TELEPHONYUI};
        try {
            setServiceInfo(info);
            diagnosticEventsExpanded = expand;
            sendEventDiagnostics.clear();
            note("SendEventScope expanded=" + expand);
        } catch (IllegalStateException | SecurityException exception) {
            note("SendEventScope update_failed=true");
            // On a failed scope change stop automation and let the next publish restore the filter.
            flow.disable();
        }
    }

    private void observeDiagnostic(AutoReplyFlow.Snapshot screen) {
        String identityDiagnostic = "available=" + AutoReplyFlow.hasRemoteIdentities(screen)
                + " observed=" + screen.remoteCount;
        if (!identityDiagnostic.equals(lastSpeechIdentityDiagnostic)) {
            lastSpeechIdentityDiagnostic = identityDiagnostic;
            note("SpeechIdentity " + identityDiagnostic);
        }
        // Retain only flags/counts. No node, bounds, greeting, caller or reply strings.
        diagnosticScreen = new AutoReplyFlow.Snapshot();
        diagnosticScreen.allowedWindow = screen.allowedWindow;
        diagnosticScreen.textCall = screen.textCall;
        diagnosticScreen.inputReady = screen.inputReady;
        diagnosticScreen.inputFocused = screen.inputFocused;
        diagnosticScreen.inputEmpty = screen.inputEmpty;
        diagnosticScreen.inputMatchesReply = screen.inputMatchesReply;
        diagnosticScreen.submitReady = screen.submitReady;
        diagnosticScreen.remoteCount = screen.remoteCount;
    }

    /**
     * Samsung's call window. Touching our own overlay can briefly make it the active window,
     * so fall back to the on-screen window list before concluding the call UI is gone.
     */
    private AccessibilityNodeInfo callRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (SamsungCallUi.trusted(root)) return root;
        try {
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo candidate = window.getRoot();
                if (SamsungCallUi.trusted(candidate)) return candidate;
            }
        } catch (RuntimeException ignored) {
            // Window list unavailable; keep the active-window result.
        }
        return root;
    }

    private Boolean readSystemCallActive() {
        if (!hasPhoneStatePermission(this)) return null;
        try {
            TelecomManager telecom = getSystemService(TelecomManager.class);
            return telecom == null ? null : telecom.isInCall();
        } catch (SecurityException | IllegalStateException exception) {
            return null;
        }
    }

    private Boolean readSystemRinging() {
        if (!hasPhoneStatePermission(this)) return null;
        try {
            TelephonyManager telephony = getSystemService(TelephonyManager.class);
            return telephony == null ? null
                    : telephony.getCallStateForSubscription() == TelephonyManager.CALL_STATE_RINGING;
        } catch (SecurityException | IllegalStateException | UnsupportedOperationException exception) {
            return null;
        }
    }

    private void drive() {
        if (driving || liveService != this) return;
        driving = true;
        lastDriveAt = SystemClock.uptimeMillis();
        try {
            systemCallActive = readSystemCallActive();
            boolean idle = callLifecycle.observe(systemCallActive, SystemClock.uptimeMillis());
            inputTrace.systemState(systemCallActive, idle);
            if (systemCallActive == null && flow.isEnabled()) {
                incomingCallGate.reset();
                generation++;
                cancelLlm();
                releaseModel();
                flow.disable();
            }
            if (idle) {
                ringNudged = false;
                dropSpeculation();
                incomingCallGate.reset();
                resetIncomingPrompt();
                observeDiagnostic(new AutoReplyFlow.Snapshot());
                if (flow.confirmCallEnded()) generation++;
                archiveFinishedCall();
                aiCallStartedAt = 0;
                syncActiveOverlay();
                cancelLlm();
                if (lastObservedCallState != CallStateModel.CallState.IDLE) {
                    lastObservedCallState = CallStateModel.CallState.ENDED;
                }
                // A retained text-call hierarchy cannot revive a call the system ended.
                windowObservation = WindowObservation.SYSTEM_IDLE;
                publish();
                applyExpiredStatus();
                switchOffWhenIdle();
                return;
            }
            if (Boolean.FALSE.equals(systemCallActive)) {
                incomingCallGate.reset();
                resetIncomingPrompt();
                // Await stable idle without acting on a hierarchy retained after hangup.
                publish();
                return;
            }
            idleSince = -1;
            AccessibilityNodeInfo root = callRoot();
            if (SamsungCallUi.trusted(root)) {
                Set<String> ids = extractVisibleKnownViewIds(root);
                windowObservation = CallStateModel.determineState(ids) == CallStateModel.CallState.IDLE
                        ? WindowObservation.PHONE_NO_CONTROLS : WindowObservation.CALL_VISIBLE;
                lastObservedCallState = CallStateModel.lastObserved(lastObservedCallState, ids);
            } else {
                windowObservation = root == null ? WindowObservation.NO_WINDOW : WindowObservation.OTHER_APP;
            }
            nudgeCallScreen(SamsungCallUi.trusted(root));
            AutoReplyFlow.Snapshot screen = SamsungCallUi.read(root, flow.isEnabled(), flow.getReplyText(),
                    flow.getOwnedDraft());
            inputTrace.observe(flow.isEnabled(), flow.getStage(), screen);
            if (flow.isEnabled() && flow.getStage() == AutoReplyFlow.Stage.WAIT_INCOMING) {
                Boolean ringing = readSystemRinging();
                updateIncomingPrompt(Boolean.TRUE.equals(ringing),
                        screen.allowedWindow && !screen.ended && screen.isRinging());
                // Hide the incoming trigger until the owner approves and fresh UI and system
                // proof survive the delay. Answering manually leaves the call to the owner.
                boolean stable = incomingCallGate.observe(true, ringing, screen, SystemClock.uptimeMillis());
                screen.incoming = stable && answerApproved;
            } else {
                incomingCallGate.reset();
                if (overlay != null && overlay.isIncomingShown()) overlay.hide();
            }
            observeDiagnostic(screen);
            if (screen.allowedWindow && screen.textCall) observedSpeechCount = screen.remoteCount;
            AutoReplyFlow.Stage before = flow.getStage();
            AutoReplyFlow.Action action = flow.step(screen, SystemClock.uptimeMillis());
            if (before != AutoReplyFlow.Stage.WAIT_INCOMING
                    && flow.getStage() == AutoReplyFlow.Stage.WAIT_INCOMING) generation++;
            if ((before == AutoReplyFlow.Stage.VERIFY_SENT || before == AutoReplyFlow.Stage.WAIT_SPEECH)
                    && (flow.getStage() == AutoReplyFlow.Stage.WAIT_REMOTE
                    || flow.getStage() == AutoReplyFlow.Stage.WAIT_END)) {
                sentReplyCount++;
            }
            if (before == AutoReplyFlow.Stage.WAIT_LLM && flow.getStage() == AutoReplyFlow.Stage.FAILED
                    && flow.getFailure() == AutoReplyFlow.Failure.TIMEOUT) {
                lastFailure = "TIMEOUT";
                warn("GenerationError " + ReplyDiagnostics.generationError(lastFailure, false));
                flow.disable();
                releaseModel();
            }
            if (before == AutoReplyFlow.Stage.WAIT_END && flow.getStage() == AutoReplyFlow.Stage.WAIT_REMOTE) {
                note("TextCallAdopted owner_answered=true");
            }
            if (flow.isAiHandling() && aiCallStartedAt == 0) aiCallStartedAt = System.currentTimeMillis();
            if (flow.getStage() != AutoReplyFlow.Stage.WAIT_LLM) cancelLlm();
            publish();
            if (action != AutoReplyFlow.Action.NONE) execute(action, screen);
            archiveFinishedCall();
            syncActiveOverlay();
            publish();
            speculateReply();
        } finally {
            driving = false;
        }
    }

    /**
     * Shows or updates the incoming prompt; in Auto mode approves once the countdown ends.
     * Only the system ringing state ends a prompt: a momentary UI misread must not drop approval.
     */
    private void updateIncomingPrompt(boolean systemRinging, boolean uiRinging) {
        if (!systemRinging) {
            resetIncomingPrompt();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (ringingSince < 0) {
            if (!uiRinging) return;
            ringingSince = now;
            flow.noteRinging();
            answerApproved = promptDismissed = false;
            ringConfig = AgentSettings.load(this);
            flow.setOpeningLine(PromptBuilder.openingLine(ringConfig));
            if (replyEngine == cloudEngine && cloudEngine != null) cloudEngine.warmUp();
        }
        if (answerApproved || promptDismissed || overlay == null) return;
        AnswerMode ringMode = ringConfig.effectiveMode();
        if (ringMode == AnswerMode.OFF) {
            // A status ran out while the assistant was still armed: the owner's saved mode is Off.
            promptDismissed = true;
            return;
        }
        int autoSec = ringMode == AnswerMode.AUTO ? Math.max(1, ringConfig.getAutoDelaySec()) : 0;
        overlay.showIncoming(ringConfig.getAssistant(), autoSec, () -> {
            if (ringingSince < 0) return;
            approveAnswer();
            drive();
        }, () -> {
            promptDismissed = true;
            overlay.hide();
            note("IncomingPrompt dismissed");
        });
        if (autoSec > 0) {
            long left = autoSec * 1000L - (now - ringingSince);
            if (left <= 0) approveAnswer();
            else overlay.updateCountdown((int) ((left + 999) / 1000));
        }
    }

    private void approveAnswer() {
        if (answerApproved) return;
        answerApproved = true;
        aiCallStartedAt = System.currentTimeMillis();
        if (overlay != null) overlay.showConnecting(ringConfig == null ? "AI" : ringConfig.getAssistant());
        note("IncomingPrompt approved");
    }

    private void resetIncomingPrompt() {
        if (ringingSince < 0 && !answerApproved) return;
        ringingSince = -1;
        answerApproved = promptDismissed = false;
        if (overlay != null && overlay.isIncomingShown()) overlay.hide();
    }

    /** The incoming pill must be gone before menu gestures; the call pill sits clear of the controls. */
    private void syncActiveOverlay() {
        boolean handling = flow.isAiHandling();
        aiOnCall = handling;
        if (overlay == null) return;
        if (handling) {
            String name = ringConfig == null ? AgentSettings.load(this).getAssistant() : ringConfig.getAssistant();
            overlay.showActive(name, () -> {
                flow.takeOver();
                generation++;
                cancelLlm();
                overlay.hide();
                aiOnCall = false;
                note("Owner took over the call");
                publish();
            });
        } else if (!overlay.isIncomingShown()) {
            overlay.hide();
        }
    }

    /**
     * A call that rings while another app is open (Otto's own screen included) stays in Samsung's
     * small pop-up, even after answering. Otto reads and taps the full call screen, so it answered
     * and then sat silent. When the call screen has been out of sight for a moment while Otto is
     * taking or handling the call, bring it forward. A ringing call is nudged once, so the owner can
     * still shrink it back. Never nudged while the screen is visible: that could reset Text call.
     */
    private void nudgeCallScreen(boolean callScreenVisible) {
        long now = SystemClock.uptimeMillis();
        if (callScreenVisible || !flow.isEnabled()) {
            callScreenMissingSince = -1;
            return;
        }
        if (callScreenMissingSince < 0) callScreenMissingSince = now;
        if (now - callScreenMissingSince < CALL_SCREEN_MISSING_MS
                || now - lastCallScreenNudgeAt < CALL_SCREEN_NUDGE_MS) return;
        AutoReplyFlow.Stage stage = flow.getStage();
        boolean handling = flow.isAiHandling() || answerApproved;
        boolean ringing = !handling && !ringNudged && stage == AutoReplyFlow.Stage.WAIT_INCOMING
                && Boolean.TRUE.equals(readSystemRinging());
        if (!handling && !ringing) return;
        lastCallScreenNudgeAt = now;
        if (ringing) ringNudged = true;
        boolean shown = false;
        try {
            TelecomManager telecom = getSystemService(TelecomManager.class);
            if (telecom != null) {
                telecom.showInCallScreen(false);
                shown = true;
            }
        } catch (SecurityException | IllegalStateException exception) {
            shown = false;
        }
        note("CallScreenNudge stage=" + stage.name() + " ringing=" + ringing + " shown=" + shown);
    }

    /**
     * Asks the cloud brain for a reply once the caller has paused briefly. Nothing is typed early:
     * the flow still waits out the full turn-end silence, then uses this answer if the turn is
     * unchanged. If the caller keeps talking, the next pause starts a new one.
     */
    private void speculateReply() {
        String turn = flow.getPendingTurn();
        if (turn.isEmpty() || cloudEngine == null || replyEngine != cloudEngine) {
            speculatedTurn = "";
            speculationsThisTurn = 0;
            return;
        }
        if (turn.equals(speculatedTurn) || speculationsThisTurn >= MAX_SPECULATIONS_PER_TURN
                || flow.getPendingTurnQuietMs(SystemClock.uptimeMillis()) < SPECULATE_AFTER_MS) return;
        speculatedTurn = turn;
        speculationsThisTurn++;
        List<CallConversation.Message> history = new ArrayList<>(flow.getConversation());
        history.add(new CallConversation.Message("user", turn));
        try {
            cloudEngine.speculate(this, ReplyRequest.forAssistant(
                    PromptBuilder.systemPrompt(AgentSettings.load(this)), history, 0));
        } catch (IllegalArgumentException invalid) {
            // Not a valid request yet; the real request reports any real problem.
        }
    }

    private void dropSpeculation() {
        speculatedTurn = "";
        speculationsThisTurn = 0;
        if (cloudEngine != null) cloudEngine.dropSpeculation();
    }

    /**
     * With "accessibility only during calls", give accessibility back once calls have been over
     * for a while, so banking apps don't see it. Only while Otto can switch itself on again.
     */
    private void switchOffWhenIdle() {
        long now = SystemClock.uptimeMillis();
        if (!onlyDuringCalls || !AccessibilitySwitch.canSwitch(this) || whatsAppMessage != null) {
            idleSince = -1;
            return;
        }
        if (idleSince < 0) idleSince = now;
        if (now - idleSince < AUTO_OFF_AFTER_IDLE_MS || Boolean.TRUE.equals(readSystemRinging())) return;
        note("AccessibilityAutoOff idle_ms=" + (now - idleSince));
        idleSince = -1;
        disableSelf();
    }

    /** A status that ends by itself hands back the owner's usual mode (which may be Off). Between calls only. */
    private void applyExpiredStatus() {
        long now = SystemClock.uptimeMillis();
        if (now - statusCheckedAt < STATUS_CHECK_MS) return;
        statusCheckedAt = now;
        boolean wanted = AgentSettings.load(this).effectiveMode() != AnswerMode.OFF;
        if (wanted == armedFor) return;
        note("StatusChanged armed=" + wanted);
        handler.post(CallAccessibilityService::applySettings);
    }

    /**
     * Presses Send in WhatsApp once the chat Otto opened shows exactly [message]. False when the
     * service can't be reached; the owner then taps Send.
     */
    public static boolean sendWhatsAppWhenReady(Context context, String message) {
        whatsAppMessage = message;
        whatsAppUntil = SystemClock.uptimeMillis() + WHATSAPP_SEND_WINDOW_MS;
        CallAccessibilityService service = liveService;
        if (service != null) {
            service.handler.post(() -> {
                service.whatsAppClickedAt = 0;
                service.pollWhatsApp();
            });
            return true;
        }
        // With "accessibility only during calls" the service is off now: switch it on, and it
        // starts watching for the chat as soon as it connects.
        if (AccessibilitySwitch.turnOn(context)) return true;
        whatsAppMessage = null;
        return false;
    }

    private void pollWhatsApp() {
        handler.removeCallbacks(whatsAppPoll);
        String message = whatsAppMessage;
        if (message == null || liveService != this) return;
        long now = SystemClock.uptimeMillis();
        if (now > whatsAppUntil || Boolean.TRUE.equals(readSystemCallActive())) {
            finishWhatsApp(whatsAppClickedAt > 0 ? "UNCONFIRMED" : "NOT_READY");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && WhatsAppUi.isWhatsApp(root.getPackageName())) {
            AccessibilityNodeInfo entry = WhatsAppUi.entry(root);
            if (whatsAppClickedAt > 0) {
                if (WhatsAppUi.empty(entry)) {
                    finishWhatsApp("SENT");
                    return;
                }
            } else if (WhatsAppUi.holds(entry, message)) {
                // One press only: a second could send the message twice.
                AccessibilityNodeInfo send = WhatsAppUi.send(root);
                if (send != null && send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) whatsAppClickedAt = now;
            }
        }
        handler.postDelayed(whatsAppPoll, WHATSAPP_POLL_MS);
    }

    private void finishWhatsApp(String result) {
        whatsAppMessage = null;
        whatsAppClickedAt = 0;
        note("WhatsAppSend result=" + result);
        if ("NOT_READY".equals(result)) {
            Toast.makeText(this, "Couldn't press Send in WhatsApp. Tap it to send.", Toast.LENGTH_LONG).show();
        } else if ("UNCONFIRMED".equals(result)) {
            Toast.makeText(this, "Check WhatsApp: the message may not have gone.", Toast.LENGTH_LONG).show();
        }
    }

    private void note(String line) {
        Log.i(TAG, line);
        if (trace != null) trace.add(line);
    }

    private void warn(String line) {
        Log.w(TAG, line);
        if (trace != null) trace.add("W " + line);
    }

    private void archiveFinishedCall() {
        List<CallConversation.Message> finished = flow.consumeFinishedTranscript();
        if (finished == null) return;
        CallFinisher.onAiCallFinished(this, finished,
                aiCallStartedAt > 0 ? aiCallStartedAt : System.currentTimeMillis());
        aiCallStartedAt = 0;
    }

    private void execute(AutoReplyFlow.Action action, AutoReplyFlow.Snapshot observed) {
        if (!Boolean.TRUE.equals(readSystemCallActive())) {
            flow.actionResult(false, SystemClock.uptimeMillis());
            return;
        }
        if (action == AutoReplyFlow.Action.REQUEST_REPLY) {
            requestReply();
            return;
        }
        // Re-read immediately before an effect. Never act on a stored AccessibilityNodeInfo.
        AccessibilityNodeInfo root = callRoot();
        AutoReplyFlow.Snapshot fresh = SamsungCallUi.read(root, true, flow.getReplyText(), flow.getOwnedDraft());
        if (!fresh.allowedWindow || fresh.ended || !flow.isEnabled()) {
            flow.actionResult(false, SystemClock.uptimeMillis());
            return;
        }
        boolean accepted = false;
        if (action == AutoReplyFlow.Action.ANSWER) {
            clearDiagnostics();
            accepted = incomingCallGate.dispatch(readSystemRinging(), fresh, SystemClock.uptimeMillis(),
                    () -> inputTrace.dispatch(action, () -> performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)));
            note("AnswerAction method=HEADSETHOOK accepted=" + accepted);
        } else if (action == AutoReplyFlow.Action.CLEAR_DRAFT) {
            AccessibilityNodeInfo input = SamsungCallUi.unique(root, CallStateModel.ID_TEXT_MESSAGE_EDIT);
            if (input != null && flow.canClearDraft(fresh)) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                accepted = inputTrace.dispatch(action,
                        () -> input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
            }
        } else if (action == AutoReplyFlow.Action.SET_REPLY || action == AutoReplyFlow.Action.FOCUS_INPUT) {
            AccessibilityNodeInfo input = SamsungCallUi.unique(root, CallStateModel.ID_TEXT_MESSAGE_EDIT);
            if (fresh.textCall && fresh.inputReady && fresh.inputEmpty && input != null) {
                if (action == AutoReplyFlow.Action.FOCUS_INPUT) {
                    accepted = SamsungCallUi.supports(input, AccessibilityNodeInfo.ACTION_FOCUS)
                            && inputTrace.dispatch(action, () -> input.performAction(AccessibilityNodeInfo.ACTION_FOCUS));
                } else if (fresh.inputFocused) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            flow.getReplyText());
                    accepted = inputTrace.dispatch(action,
                            () -> input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
                }
            }
        } else if (action == AutoReplyFlow.Action.SEND_REPLY) {
            AccessibilityNodeInfo target = SamsungCallUi.buttonTarget(root, action);
            if (target != null) {
                try {
                    accepted = ReplySendController.dispatch(fresh, observed.replyCount,
                            () -> inputTrace.dispatch(action, () -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)));
                } catch (IllegalStateException exception) {
                    accepted = false;
                }
            }
            note("SendAction method=ACCESSIBILITY_CLICK accepted=" + accepted);
        } else if (action == AutoReplyFlow.Action.OPEN_ASSIST || action == AutoReplyFlow.Action.OPEN_TEXT) {
            final AccessibilityNodeInfo target = SamsungCallUi.buttonTarget(root, action);
            // Once a call connects, Samsung lets the proximity sensor blank the screen, and with the
            // phone face down or in a pocket it stays off. Android drops touch gestures then, so the
            // Text call tap went nowhere. An accessibility click reaches the button with the screen
            // off; retries alternate with a real tap in case a Samsung build ignores one of them.
            if (action == AutoReplyFlow.Action.OPEN_TEXT && target != null && flow.getTextTapAttempt() % 2 == 0) {
                boolean clicked = CallMenuController.dispatch(action, fresh, () -> inputTrace.dispatch(action,
                        () -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)));
                note("MenuClick action=OPEN_TEXT method=ACCESSIBILITY_CLICK attempt="
                        + (flow.getTextTapAttempt() + 1) + " accepted=" + clicked);
                if (clicked) {
                    flow.actionResult(true, SystemClock.uptimeMillis());
                    return;
                }
            }
            dispatchMenuTouch(action, fresh, target);
            return;
        }
        flow.actionResult(accepted, SystemClock.uptimeMillis());
    }

    private void dispatchMenuTouch(AutoReplyFlow.Action action, AutoReplyFlow.Snapshot fresh,
                                   AccessibilityNodeInfo target) {
        final CallMenuController.GestureCompletion completion =
                new CallMenuController.GestureCompletion(action, generation);
        boolean scheduled = false;
        // buttonTarget already checks unique ID/label, visibility, clickability and root bounds.
        if (target != null) {
            Rect bounds = SamsungCallUi.bounds(target);
            scheduled = CallMenuController.dispatchTouch(action, fresh,
                    bounds.left, bounds.top, bounds.right, bounds.bottom, () -> {
                        Path path = new Path();
                        path.moveTo(bounds.exactCenterX(), bounds.exactCenterY());
                        GestureDescription gesture = new GestureDescription.Builder()
                                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
                        return inputTrace.dispatch(action, () -> dispatchGesture(gesture,
                                new GestureResultCallback() {
                                    @Override public void onCompleted(GestureDescription description) {
                                        finishMenuTouch(action, completion, true);
                                    }
                                    @Override public void onCancelled(GestureDescription description) {
                                        finishMenuTouch(action, completion, false);
                                    }
                                }, handler));
                    });
        }
        note("MenuTouch action=" + action.name() + " phase=SCHEDULED accepted=" + scheduled);
        // A queued gesture is not completion. Keep the flow pending until its callback.
        if (!scheduled) flow.actionResult(false, SystemClock.uptimeMillis());
    }

    private void finishMenuTouch(AutoReplyFlow.Action action,
                                 CallMenuController.GestureCompletion completion, boolean completed) {
        boolean applied = completion.accept(generation, liveService == this && flow.isEnabled(),
                flow.getStage(), readSystemCallActive());
        note("MenuTouch action=" + action.name() + " phase=" + (completed ? "COMPLETED" : "CANCELLED")
                + " applied=" + applied);
        if (applied) {
            flow.actionResult(completed, SystemClock.uptimeMillis());
            publish();
            drive();
        }
    }

    private void requestReply() {
        AccessibilityNodeInfo root = callRoot();
        AutoReplyFlow.Snapshot fresh = SamsungCallUi.read(root, false);
        if (!Boolean.TRUE.equals(readSystemCallActive()) || !flow.isEnabled()
                || !fresh.allowedWindow || fresh.ended || !fresh.textCall
                || !fresh.inputReady || !fresh.inputEmpty) {
            flow.replyResult(null, SystemClock.uptimeMillis());
            return;
        }
        final ReplyRequest request;
        final boolean local = replyEngine == localRuntime && localRuntime != null;
        try {
            request = ReplyRequest.forAssistant(PromptBuilder.systemPrompt(AgentSettings.load(this)),
                    flow.getConversation(), local ? ReplyRequest.LOCAL_ASSISTANT_BYTES : 0);
        }
        catch (IllegalArgumentException failure) {
            lastFailure = "CONTEXT_LIMIT".equals(failure.getMessage()) ? "CONTEXT_LIMIT" : "INVALID_RESPONSE";
            warn("GenerationError " + ReplyDiagnostics.generationError(lastFailure, false));
            flow.disable(); releaseModel(); return;
        }
        final CallReplyGate.Ticket ticket = replyGate.start(generation, SystemClock.uptimeMillis());
        activeReply = ticket;
        generationRunning = true;
        lastFailure = "";
        pendingReplyStart = new Runnable() {
            private boolean deferredLogged;
            @Override public void run() {
                if (!replyGate.accepts(ticket, generation, SystemClock.uptimeMillis()) || liveService != CallAccessibilityService.this
                        || flow.getStage() != AutoReplyFlow.Stage.WAIT_LLM) return;
                drive();
                if (!replyGate.accepts(ticket, generation, SystemClock.uptimeMillis()) || flow.getStage() != AutoReplyFlow.Stage.WAIT_LLM) return;
                boolean started = replyEngine != null && replyEngine.generate(CallAccessibilityService.this, request,
                        (result, diagnostic) -> handler.post(() -> {
                    if (!replyGate.accepts(ticket, generation, SystemClock.uptimeMillis()) || liveService != CallAccessibilityService.this) {
                        note("GenerationDiscard id=" + request.diagnosticId + " reason=STALE_TICKET");
                        return;
                    }
                    drive();
                    if (!replyGate.accepts(ticket, generation, SystemClock.uptimeMillis())
                            || flow.getStage() != AutoReplyFlow.Stage.WAIT_LLM || !Boolean.TRUE.equals(readSystemCallActive())) {
                        note("GenerationDiscard id=" + request.diagnosticId + " reason=STATE_CHANGED stage=" + flow.getStage().name());
                        return;
                    }
                    lastFailure = diagnostic;
                    if (diagnostic.startsWith("CLOUD_")) {
                        // Retry once, then speak the fallback notice; the assistant stays armed.
                        warn("GenerationResult id=" + request.diagnosticId + " cloud_error=" + diagnostic);
                        flow.networkFailure(SystemClock.uptimeMillis());
                    } else if (diagnostic.isEmpty()) {
                        note("GenerationResult id=" + request.diagnosticId + " disposition=MODEL_REPLY");
                        generationResponseCount++;
                        flow.replyResult(result, SystemClock.uptimeMillis());
                    } else {
                        boolean recovered = replyEngine != null && replyEngine.isReady(CallAccessibilityService.this)
                                && flow.generationFailure(diagnostic, SystemClock.uptimeMillis());
                        warn("GenerationError " + ReplyDiagnostics.generationError(diagnostic, recovered));
                        warn("GenerationResult id=" + request.diagnosticId + " "
                                + ReplyDiagnostics.generationError(diagnostic, recovered));
                        if (!recovered) {
                            flow.disable();
                            releaseModel();
                        }
                    }
                    cancelLlm();
                    publish();
                    drive();
                }));
                if (started) {
                    generationRequestCount++;
                    note("GenerationRequest id=" + request.diagnosticId + " requests=" + generationRequestCount);
                    pendingReplyStart = null;
                } else {
                    // A cancelled native call may still be draining. The flow's 30s deadline still applies.
                    if (!deferredLogged) {
                        deferredLogged = true;
                        note("GenerationDeferred id=" + request.diagnosticId + " reason=ENGINE_NOT_READY");
                    }
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.post(pendingReplyStart);
    }

    public static boolean isAllowedPackage(String packageName) {
        return CallPackagePolicy.isAllowedPackage(packageName);
    }

    private Set<String> extractVisibleKnownViewIds(AccessibilityNodeInfo root) {
        Set<String> foundIds = new HashSet<>();
        checkAndAddVisible(root, CallStateModel.ID_ANSWER_CONTAINER, foundIds);
        checkAndAddVisible(root, CallStateModel.ID_CALL_TIME, foundIds);
        checkAndAddVisible(root, CallStateModel.ID_AI_BUTTON, foundIds);
        checkAndAddVisible(root, CallStateModel.ID_TEXT_CALL_BUTTON, foundIds);
        checkAndAddVisible(root, CallStateModel.ID_TEXT_MESSAGE_EDIT, foundIds);
        checkAndAddVisible(root, CallStateModel.ID_CALL_ENDED, foundIds);
        return foundIds;
    }

    private void checkAndAddVisible(AccessibilityNodeInfo root, String viewId, Set<String> target) {
        if (SamsungCallUi.visible(root, viewId)) target.add(viewId);
    }
}
