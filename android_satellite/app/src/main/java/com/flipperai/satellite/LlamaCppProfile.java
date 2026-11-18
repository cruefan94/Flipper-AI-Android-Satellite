package com.flipperai.satellite;

/**
 * Fixed on-device llama.cpp OpenAI-compatible server profile.
 * GGUF weights live in Termux; none are bundled in the APK.
 */
public final class LlamaCppProfile {
  private LlamaCppProfile() {}
  public static final String ENDPOINT="http://127.0.0.1:9931/v1/chat/completions";
  public static final String MODEL_ALIAS="flipper-local";
  public static final String PROMPT_TEST="Reply with OK.";
}
