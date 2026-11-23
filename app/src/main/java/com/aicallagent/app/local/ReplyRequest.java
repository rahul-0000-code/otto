package com.aicallagent.app.local;

import com.aicallagent.app.model.CallConversation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** A fresh native conversation is reconstructed from confirmed flow history for every request. */
public final class ReplyRequest {
    public static final int CONTEXT_TOKENS = 8192, OUTPUT_TOKENS = 128;
    /** UTF-8 budget for on-device assistant prompts; Devanagari is ~3 bytes/char but ~1 token. */
    public static final int LOCAL_ASSISTANT_BYTES = CONTEXT_TOKENS * 3;
    private static final int MAX_MESSAGES = 32;
    public static final String ENGLISH_TEST_SYSTEM_PROMPT = CallConversation.SYSTEM_PROMPT_EN;
    private static final AtomicLong IDS = new AtomicLong();
    public final long diagnosticId = IDS.incrementAndGet();
    public final String system, turnInstruction;
    public final int inputBudget;
    public final List<CallConversation.Message> history;
    public final boolean englishTest;
    /** Free-form assistant turn: no word chain rules, repairs or turn instruction. */
    public final boolean assistant;
    private ReplyRequest(String system, List<CallConversation.Message> history) {
        this(system, history, false);
    }
    private ReplyRequest(String system, List<CallConversation.Message> history, boolean englishTest) {
        this(system, history, englishTest, false, CONTEXT_TOKENS);
    }
    private ReplyRequest(String system, List<CallConversation.Message> history, boolean englishTest,
                         boolean assistant, int maxBytes) {
        this.system = system;
        this.englishTest = englishTest;
        this.assistant = assistant;
        if (history.isEmpty() || history.size() > MAX_MESSAGES) throw new IllegalArgumentException("CONTEXT_LIMIT");
        boolean startsWithUser = history.get(0) != null && "user".equals(history.get(0).role);
        List<CallConversation.Message> copy = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            CallConversation.Message message = history.get(i);
            String role = (i % 2 == 0) == startsWithUser ? "user" : "assistant";
            if (message == null || !role.equals(message.role) || message.content == null || message.content.trim().isEmpty())
                throw new IllegalArgumentException("INVALID_RESPONSE");
            copy.add(new CallConversation.Message(message.role, message.content));
        }
        if (!"user".equals(copy.get(copy.size()-1).role)) throw new IllegalArgumentException("INVALID_RESPONSE");
        this.history = Collections.unmodifiableList(copy);
        turnInstruction = assistant ? "" : englishTest ? WordChainPrompt.englishTestTurn() : WordChainPrompt.turn(this.history);
        int bytes = (system + turnInstruction).getBytes(StandardCharsets.UTF_8).length
                + (assistant ? 0 : WordChainPrompt.repairReserveBytes()) + 512 + OUTPUT_TOKENS;
        for (var message : copy) bytes += message.content.getBytes(StandardCharsets.UTF_8).length + 64;
        if (maxBytes > 0 && bytes > maxBytes) throw new IllegalArgumentException("CONTEXT_LIMIT");
        inputBudget = bytes;
    }
    /**
     * Assistant call turn. Long calls keep only the most recent messages; dropping from the
     * front preserves the alternation the constructor verifies. maxBytes 0 means no budget (cloud).
     */
    public static ReplyRequest forAssistant(String system, List<CallConversation.Message> history, int maxBytes) {
        List<CallConversation.Message> recent = history.size() > MAX_MESSAGES - 2
                ? history.subList(history.size() - (MAX_MESSAGES - 2), history.size()) : history;
        return new ReplyRequest(system, recent, false, true, maxBytes);
    }
    public static ReplyRequest forCall(List<CallConversation.Message> history) {
        return new ReplyRequest(CallConversation.SYSTEM_PROMPT, history, false);
    }
    public static ReplyRequest test() {
        return test(true);
    }
    public static ReplyRequest test(boolean isKorean) {
        if (isKorean) {
            return forCall(Collections.singletonList(new CallConversation.Message("user", "사과")));
        }
        return new ReplyRequest(CallConversation.SYSTEM_PROMPT_EN,
                Collections.singletonList(new CallConversation.Message("user", "Banana")), true);
    }
    public CallConversation.Message latest() { return history.get(history.size()-1); }
    public static boolean validOutput(String text, int decodedTokens) {
        return outputProblem(text, decodedTokens) == OutputProblem.NONE;
    }
    public enum OutputProblem { NONE, TOKEN_LIMIT, NO_DECODE_TOKENS, EMPTY, TOO_LONG, THINK_TAG }
    public static OutputProblem outputProblem(String text, int decodedTokens) {
        if (decodedTokens >= OUTPUT_TOKENS) return OutputProblem.TOKEN_LIMIT;
        if (decodedTokens <= 0) return OutputProblem.NO_DECODE_TOKENS;
        if (text == null || text.trim().isEmpty()) return OutputProblem.EMPTY;
        if (text.trim().length() > 1000) return OutputProblem.TOO_LONG;
        if (text.contains("<think>") || text.contains("</think>")) return OutputProblem.THINK_TAG;
        return OutputProblem.NONE;
    }
}
