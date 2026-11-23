package com.aicallagent.app.model;

import org.junit.Test;

import static com.aicallagent.app.model.AutoReplyFlow.Action.*;
import static com.aicallagent.app.model.AutoReplyFlow.Stage.*;
import static org.junit.Assert.*;

/**
 * Behaviour seen on a real Galaxy S21 FE (One UI 8) call: Text call sits directly on the call
 * screen (no Call assist menu), the chat scrolls while replies are typed, and text can appear in
 * the reply box that the assistant didn't write.
 */
public class S21FeCallFlowTest {
    private final AutoReplyFlow flow = new AutoReplyFlow();
    private final AutoReplyFlow.Snapshot screen = new AutoReplyFlow.Snapshot();
    private long now = 1000;

    private AutoReplyFlow.Action step() { return flow.step(screen, now); }

    private void done() { flow.actionResult(true, now); }

    private void answer() {
        screen.allowedWindow = true;
        flow.enable(screen, now);
        screen.incoming = true;
        assertEquals(ANSWER, step());
        done();
        screen.incoming = false;
        screen.connected = true;
    }

    private void reachRemote() {
        answer();
        screen.textOptionReady = true;
        assertEquals(OPEN_TEXT, step());
        done();
        screen.textCall = true;
        screen.inputReady = true;
        screen.inputFocused = true;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
    }

    /** Visible caller bubbles as (identity, text) pairs, oldest first. */
    private void show(String... rows) {
        screen.remoteMessages.clear();
        screen.remoteIdentities.clear();
        for (int i = 0; i < rows.length; i += 2) {
            screen.remoteIdentities.add(rows[i]);
            screen.remoteMessages.add(rows[i + 1]);
        }
        screen.remoteCount = rows.length / 2;
        screen.remoteText = screen.remoteCount == 0 ? "" : rows[rows.length - 1];
    }

    private static final String OPENING = "Hi, this is Samy, Rahul's assistant. Rahul is driving right now.";

    /** Answers with a status on and opens Text call; Samsung's notice is on screen. */
    private void openTextCallWithStatus() {
        flow.setOpeningLine(OPENING);
        answer();
        screen.textOptionReady = true;
        assertEquals(OPEN_TEXT, step());
        done();
        screen.textCall = true;
        screen.inputReady = true;
        screen.inputFocused = true;
        screen.initialGreeting = NOTICE;
        assertEquals(NONE, step());
    }

    // Samsung reads this aloud first: 28 characters, ~2 s at 70 ms each. Typing starts 0.7 s early.
    private static final String NOTICE = "I'm using a voice assistant.";

    private void waitOutTheNotice() {
        now += NOTICE.length() * 70L - 700;
        assertEquals(NONE, step());
    }

    @Test public void theOpeningWaitsWhileSamsungReadsItsNotice() {
        openTextCallWithStatus();
        assertEquals(WAIT_REMOTE, flow.getStage());
        now += NOTICE.length() * 70L - 701;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
        now += 1;
        assertEquals(NONE, step());
        assertEquals(WAIT_INPUT, flow.getStage());
    }

    @Test public void aCallerWhoSpeaksDuringTheNoticeGetsTheStatusInTheReplyInstead() {
        openTextCallWithStatus();
        now += 500;
        show("row:1", "Hello?");
        assertEquals(NONE, step());
        now += 2000;
        assertEquals(REQUEST_REPLY, step());
        flow.replyResult("Hi, Rahul is driving right now.", now);
        now = ContinuousCallFlowTest.send(flow, screen, now);
        now += 20_000;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
    }

    @Test public void withAStatusTheAssistantSpeaksFirst() {
        openTextCallWithStatus();
        waitOutTheNotice();
        assertEquals(WAIT_INPUT, flow.getStage());
        assertEquals(OPENING, flow.getReplyText());
        now = ContinuousCallFlowTest.send(flow, screen, now);
        assertEquals(1, flow.getConversation().size());
        assertEquals(NOTICE + "\n" + OPENING, flow.getConversation().get(0).content);
        now += 1000;
        show("row:1", "Okay, tell him to call me");
        assertEquals(NONE, step());
        now += 2000;
        assertEquals(REQUEST_REPLY, step());
        assertEquals(2, flow.getConversation().size());
        assertEquals("user", flow.getConversation().get(1).role);
    }

    @Test public void aCallerTalkingOverTheOpeningIsAnsweredRightAfterIt() {
        openTextCallWithStatus();
        waitOutTheNotice();
        assertEquals(SET_REPLY, step());
        done();
        screen.inputEmpty = false;
        screen.inputMatchesReply = true;
        screen.submitReady = true;
        screen.submitBounds = "send";
        show("row:1", "Hello?");
        now += 200;
        assertEquals(NONE, step());
        assertEquals(VERIFY_INPUT, flow.getStage());
        now += 300;
        assertEquals(SEND_REPLY, step());
        done();
        screen.inputEmpty = true;
        screen.inputMatchesReply = false;
        screen.replyCount++;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
        now += 100;
        assertEquals(NONE, step());
        now += 2000;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Hello?", flow.getRequestText());
    }

    @Test public void withoutAStatusTheAssistantWaitsForTheCaller() {
        reachRemote();
        now += 10_000;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
    }

    @Test public void waitsForTheTextCallButtonToStopMovingBeforeTapping() {
        answer();
        screen.textOptionReady = true;
        screen.textOptionBounds = "100,1500,300,1600";
        assertEquals(NONE, step());
        now += 200;
        screen.textOptionBounds = "100,1400,300,1500";
        assertEquals(NONE, step());
        now += 399;
        assertEquals(NONE, step());
        now += 1;
        assertEquals(OPEN_TEXT, step());
    }

    @Test public void tapsTextCallAgainWhenTheFirstTapDidNotOpenIt() {
        answer();
        screen.textOptionReady = true;
        assertEquals(OPEN_TEXT, step());
        assertEquals(0, flow.getTextTapAttempt());
        done();
        now += 2999;
        assertEquals(NONE, step());
        assertEquals(WAIT_TEXT_ACTIVE, flow.getStage());
        now += 1;
        step();
        assertEquals(OPEN_TEXT, step());
        assertEquals(1, flow.getTextTapAttempt());
        done();
        screen.textCall = true;
        screen.inputReady = true;
        screen.inputFocused = true;
        now += 500;
        step();
        assertEquals(WAIT_REMOTE, flow.getStage());
    }

    @Test public void textCallTapIsRetriedTwiceAtMost() {
        answer();
        screen.textOptionReady = true;
        int taps = 0;
        for (int i = 0; i < 100; i++) {
            if (step() == OPEN_TEXT) { taps++; done(); }
            now += 500;
        }
        assertEquals(3, taps);
    }

    /** The owner swipes to answer a call that rang in, before the assistant's countdown ends. */
    private void ownerAnswersRingingCall() {
        screen.allowedWindow = true;
        flow.enable(screen, now);
        flow.noteRinging();
        assertEquals(NONE, step());
        screen.connected = true;
        assertEquals(NONE, step());
        assertEquals(WAIT_END, flow.getStage());
        assertFalse(flow.isAiHandling());
    }

    private void ownerOpensTextCall() {
        screen.textCall = true;
        screen.inputReady = true;
        screen.inputFocused = true;
        step();
    }

    @Test public void ownerAnsweringThenOpeningTextCallHandsTheCallToTheAssistant() {
        ownerAnswersRingingCall();
        now += 7000;
        ownerOpensTextCall();
        assertEquals(WAIT_REMOTE, flow.getStage());
        assertTrue(flow.isAiHandling());
        now += 5000;
        show("row:1", "Hello?");
        assertEquals(NONE, step());
        now += 2000;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Hello?", flow.getRequestText());
    }

    @Test public void ownerTalkingNormallyKeepsTheCall() {
        ownerAnswersRingingCall();
        now += 20_000;
        assertEquals(NONE, step());
        assertEquals(WAIT_END, flow.getStage());
        assertFalse(flow.isAiHandling());
    }

    @Test public void textCallOnAnOutgoingCallStaysWithTheOwner() {
        screen.allowedWindow = true;
        flow.enable(screen, now);
        screen.connected = true;
        assertEquals(NONE, step());
        ownerOpensTextCall();
        assertEquals(WAIT_END, flow.getStage());
        assertFalse(flow.isAiHandling());
    }

    @Test public void stopAiKeepsTextCallWithTheOwner() {
        reachRemote();
        flow.takeOver();
        assertEquals(NONE, step());
        assertEquals(WAIT_END, flow.getStage());
        assertFalse(flow.isAiHandling());
    }

    @Test public void opensTextCallDirectlyWhenThereIsNoCallAssistMenu() {
        answer();
        screen.textOptionReady = true;
        assertEquals(OPEN_TEXT, step());
        assertEquals(WAIT_TEXT_ACTIVE, flow.getStage());
    }

    @Test public void waitsUntilTheTextCallButtonAppears() {
        answer();
        assertEquals(NONE, step());
        assertEquals(WAIT_ASSIST, flow.getStage());
        now += 500;
        screen.textOptionReady = true;
        assertEquals(OPEN_TEXT, step());
    }

    @Test public void callAssistMenuStillWorksWhenOnlyItIsShown() {
        answer();
        screen.assistReady = true;
        assertEquals(OPEN_ASSIST, step());
        assertEquals(WAIT_TEXT, flow.getStage());
    }

    @Test public void scrollingDoesNotHoldBackAPendingReply() {
        reachRemote();
        show("row:0:column:0", "Yes.", "row:1:column:0", "Hello i'm calling him");
        assertEquals(NONE, step());
        now += 400;
        // The older bubble scrolls out of view while the turn is still settling.
        show("row:1:column:0", "Hello i'm calling him");
        assertEquals(NONE, step());
        now += 300;
        assertEquals(REQUEST_REPLY, step());
    }

    @Test public void neverTypesOverForeignTextAndResumesOnceTheBoxStaysEmpty() {
        reachRemote();
        screen.inputEmpty = false;
        assertEquals(NONE, step());
        assertEquals(FAILED, flow.getStage());
        assertEquals(AutoReplyFlow.Failure.EXISTING_INPUT, flow.getFailure());
        now += 5000;
        assertEquals(NONE, step());
        assertEquals(FAILED, flow.getStage());

        screen.inputEmpty = true;
        assertEquals(NONE, step());
        now += 1999;
        assertEquals(NONE, step());
        assertEquals(FAILED, flow.getStage());
        now += 1;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
        assertEquals(AutoReplyFlow.Failure.NONE, flow.getFailure());
    }

    @Test public void foreignTextComingBackRestartsTheWait() {
        reachRemote();
        screen.inputEmpty = false;
        step();
        screen.inputEmpty = true;
        step();
        now += 1500;
        screen.inputEmpty = false;
        step();
        now += 100;
        screen.inputEmpty = true;
        step();
        now += 1500;
        step();
        assertEquals(FAILED, flow.getStage());
        now += 600;
        step();
        assertEquals(WAIT_REMOTE, flow.getStage());
    }

    @Test public void answersTheTurnThatWasWaitingWhenForeignTextAppeared() {
        reachRemote();
        show("row:0:column:0", "Yes myself puja");
        step();
        now += 700;
        assertEquals(REQUEST_REPLY, step());

        screen.inputEmpty = false;
        flow.replyResult("Nice to meet you, Puja!", now);
        assertEquals(NONE, step());
        assertEquals(AutoReplyFlow.Failure.EXISTING_INPUT, flow.getFailure());

        screen.inputEmpty = true;
        step();
        now += 2000;
        step();
        assertEquals(WAIT_REMOTE, flow.getStage());
        assertEquals(NONE, step());
        now += 700;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Yes myself puja", flow.getRequestText());
    }

    @Test public void editedReplyIsNeverSentAndTheTurnIsAnsweredAfterTheBoxClears() {
        reachRemote();
        show("row:0:column:0", "Hello i'm calling him");
        step();
        now += 700;
        assertEquals(REQUEST_REPLY, step());
        flow.replyResult("Hello! Who is calling?", now);
        assertEquals(SET_REPLY, step());
        done();
        screen.inputEmpty = false;
        screen.inputMatchesReply = false;
        assertEquals(NONE, step());
        assertEquals(AutoReplyFlow.Failure.INPUT_CHANGED, flow.getFailure());

        screen.inputEmpty = true;
        step();
        now += 2000;
        step();
        assertEquals(WAIT_REMOTE, flow.getStage());
        assertEquals(NONE, step());
        now += 700;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Hello i'm calling him", flow.getRequestText());
    }

    @Test public void waitsForTheCallerToFinishASplitSentence() {
        flow.setTurnEndSilence(1500, 900);
        reachRemote();
        show("row:0:column:0", "Kya karya");
        assertEquals(NONE, step());
        now += 1000;
        // Speech-to-text delivers the rest of the sentence as a second bubble.
        show("row:0:column:0", "Kya karya", "row:1:column:0", "Hawa khana bhookh laga hai");
        assertEquals(NONE, step());
        now += 1000;
        assertEquals(NONE, step());
        now += 500;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Kya karya\nHawa khana bhookh laga hai", flow.getRequestText());
    }

    @Test public void answersAQuestionSooner() {
        flow.setTurnEndSilence(1500, 900);
        reachRemote();
        show("row:0:column:0", "Rahul kahan hai?");
        assertEquals(NONE, step());
        now += 900;
        assertEquals(REQUEST_REPLY, step());
    }

    @Test public void holdsATypedReplyWhenTheCallerKeepsTalking() {
        flow.setTurnEndSilence(1500, 900);
        reachRemote();
        show("row:0:column:0", "Kya karya");
        step();
        now += 1500;
        assertEquals(REQUEST_REPLY, step());
        flow.replyResult("Haan ji, boliye?", now);
        assertEquals(SET_REPLY, step());
        done();
        screen.inputEmpty = false;
        screen.inputMatchesReply = true;
        screen.inputMatchesOwnedDraft = true;

        // She speaks again before the reply is sent: the reply must not go out.
        show("row:0:column:0", "Kya karya", "row:1:column:0", "Hawa khana bhookh laga hai");
        assertEquals(CLEAR_DRAFT, step());
        done();
        screen.inputEmpty = true;
        screen.inputMatchesReply = false;
        screen.inputMatchesOwnedDraft = false;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());
        now += 1500;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Kya karya\nHawa khana bhookh laga hai", flow.getRequestText());
    }

    @Test public void afterResumingOldBubblesAreNotAnsweredAgain() {
        reachRemote();
        show("row:0:column:0", "Hello i'm calling him");
        step();
        now += 700;
        assertEquals(REQUEST_REPLY, step());
        flow.replyResult("Hello! Who is calling?", now);
        assertEquals(SET_REPLY, step());
        done();
        screen.inputEmpty = false;
        screen.inputMatchesReply = true;
        screen.submitReady = true;
        screen.submitBounds = "10 20 50 60";
        now += 250;
        assertEquals(NONE, step());
        now += 300;
        assertEquals(SEND_REPLY, step());
        done();
        screen.inputEmpty = true;
        screen.inputMatchesReply = false;
        screen.replyCount = 1;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());

        // Someone else's text shows up in the reply box.
        screen.inputEmpty = false;
        assertEquals(NONE, step());
        assertEquals(FAILED, flow.getStage());

        screen.inputEmpty = true;
        step();
        now += 2000;
        step();
        assertEquals(WAIT_REMOTE, flow.getStage());
        // Nothing new was said, so nothing is requested.
        now += 2000;
        assertEquals(NONE, step());
        assertEquals(WAIT_REMOTE, flow.getStage());

        show("row:0:column:0", "Hello i'm calling him", "row:2:column:0", "Myself Puja");
        step();
        now += 700;
        assertEquals(REQUEST_REPLY, step());
        assertEquals("Myself Puja", flow.getRequestText());
    }
}
