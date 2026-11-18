package com.flipperai.satellite;

/** Shared inference input limits, independent of the Android framework. */
public final class PromptLimits {
  public static final int MAX_CHARACTERS = 8192;
  private PromptLimits() {}
  public static void validate(String prompt) {
    if (prompt == null || prompt.trim().isEmpty() || prompt.length() > MAX_CHARACTERS)
      throw new IllegalArgumentException("AI prompt must be 1–8192 characters");
    // Reject malformed UTF-16 before dispatch to any engine. Java's default
    // UTF-8 encoding can silently replace isolated surrogates, changing the
    // prompt between local and cloud execution.
    for (int i = 0; i < prompt.length(); i++) {
      char c = prompt.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= prompt.length() || !Character.isLowSurrogate(prompt.charAt(i)))
          throw new IllegalArgumentException("Malformed prompt Unicode");
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("Malformed prompt Unicode");
      }
    }
  }
}
