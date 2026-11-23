package com.northerncircuitstudios.ai;

import java.util.ArrayList;
import java.util.List;

/** Android-independent policy so local inference contracts can be JVM-tested. */
public final class AssistantPolicy {
    private AssistantPolicy() {}

    public static final String MODEL_ALIAS = "flipper-local";
    public static final String LOCAL_ENDPOINT = "http://127.0.0.1:9931/v1/chat/completions";
    public static final String LOCAL_MODELS_ENDPOINT = "http://127.0.0.1:9931/v1/models";
    public static final int MAX_INPUT_CHARS = 1600;
    public static final int MAX_STORED_ENTRIES = 30;
    public static final int MAX_CONTEXT_CHARS = 5000;

    public static final String SYSTEM_PROMPT =
        "You are Northern Circuit AI, an independently branded, general-purpose " +
        "personal and business assistant powered by a locally hosted open-weight " +
        "language model. Help with general questions, explanations, writing, " +
        "planning, programming and electronics. Be helpful, clear and factual. " +
        "Say when you do not know something or cannot verify it. Never pretend " +
        "to browse the web, run code, control hardware, access user files or " +
        "complete actions without an authenticated tool confirming the action. " +
        "You have no live tools connected in this version. Do not expose " +
        "private information or make claims about company decisions not provided.";

    public static final class Entry {
        public final String role;
        public final String text;
        public Entry(String role, String text) {
            if (!"user".equals(role) && !"assistant".equals(role))
                throw new IllegalArgumentException("Invalid conversation role");
            if (text == null || text.isEmpty() || text.length() > 12000)
                throw new IllegalArgumentException("Invalid conversation text");
            this.role = role;
            this.text = text;
        }
    }

    public static void validateInput(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()
                || prompt.length() > MAX_INPUT_CHARS)
            throw new IllegalArgumentException("Message must be 1–1600 characters");
        for (int i = 0; i < prompt.length(); i++) {
            char c = prompt.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= prompt.length() || !Character.isLowSurrogate(prompt.charAt(i)))
                    throw new IllegalArgumentException("Invalid message Unicode");
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Invalid message Unicode");
            }
        }
    }

    /** Keep latest turns without cutting text in the middle of a message. */
    public static List<Entry> recentContext(List<Entry> all) {
        if (all == null || all.isEmpty())
            throw new IllegalArgumentException("Conversation cannot be empty");
        ArrayList<Entry> chosen = new ArrayList<>();
        int total = 0;
        for (int i = all.size() - 1; i >= 0 && chosen.size() < 8; i--) {
            Entry e = all.get(i);
            if (chosen.isEmpty() && "user".equals(e.role)) validateInput(e.text);
            if (total + e.text.length() > MAX_CONTEXT_CHARS && !chosen.isEmpty())
                break;
            chosen.add(0, e);
            total += e.text.length();
        }
        if (chosen.isEmpty() || !"user".equals(chosen.get(chosen.size() - 1).role))
            throw new IllegalArgumentException("Newest message must be from user");
        return chosen;
    }

    /** Local-history cap, separate from the smaller inference context. */
    public static List<Entry> boundedHistory(List<Entry> all) {
        if (all == null) throw new IllegalArgumentException("Missing history");
        int start = Math.max(0, all.size() - MAX_STORED_ENTRIES);
        return new ArrayList<>(all.subList(start, all.size()));
    }
}
