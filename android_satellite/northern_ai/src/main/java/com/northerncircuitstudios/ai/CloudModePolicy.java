package com.northerncircuitstudios.ai;

/** Explicit consent and fixed-endpoint boundaries for optional cloud AI. */
public final class CloudModePolicy {
    private CloudModePolicy() {}

    public static final String WORKER_ORIGIN =
        "https://flipper-ai-satellite.guichet-chris94.workers.dev";
    // /ask keeps the 127-char legacy contract. /ask-v2 raises this only after deployment.
    public static final int LEGACY_MAX_CLOUD_CHARS = 127;
    public static final int MAX_CLOUD_CHARS = 1600;

    public enum Mode { OFFLINE, CLOUD, HYBRID }

    public static String validateToken(String value) {
        if (value == null || value.length() < 16 || value.length() > 256)
            throw new IllegalArgumentException("Device token must contain 16–256 characters.");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 33 || c > 126)
                throw new IllegalArgumentException("Device token must use visible ASCII without spaces.");
        }
        return value;
    }

    public static void validateCloudPrompt(String value) {
        AssistantPolicy.validateInput(value);
        if (value.length() > MAX_CLOUD_CHARS)
            throw new IllegalArgumentException(
                "Cloud accepts up to 1600 characters. Shorten this message or " +
                "split it into separate requests.");
    }

    public static boolean maySendToCloud(Mode mode, String token) {
        if (mode == null || mode == Mode.OFFLINE) return false;
        try { validateToken(token); return true; }
        catch (IllegalArgumentException error) { return false; }
    }

    /** Explicitly selected CLOUD or HYBRID is the only permission to transmit. */
    public static Mode parseMode(String text) {
        try { return Mode.valueOf(text); }
        catch (Exception error) { return Mode.OFFLINE; }
    }
}
