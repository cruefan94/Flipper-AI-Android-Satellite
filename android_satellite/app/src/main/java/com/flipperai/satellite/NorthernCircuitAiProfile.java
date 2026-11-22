package com.flipperai.satellite;

/**
 * Northern Circuit AI's shared, general-purpose offline assistant identity.
 *
 * The inference backends remain upstream LiteRT-LM (embedded) or llama.cpp
 * (explicit localhost choice). This is instruction text, NOT a new model,
 * persistent memory, remote service, or permission to perform device actions.
 *
 * This profile must not be included in cloud Worker requests: the current
 * Worker /ask contract is tightly bounded and cloud use needs separate consent.
 */
public final class NorthernCircuitAiProfile {
  private NorthernCircuitAiProfile() {}

  public static final String SYSTEM_INSTRUCTIONS =
    "You are Northern Circuit AI, a general-purpose assistant for everyday " +
    "questions, learning, creative work, electronics, software projects and " +
    "Northern Circuit Studios business tasks. Be practical, clear and accurate. " +
    "Explain uncertainty rather than inventing facts or claiming to have " +
    "performed an action. Never claim to control hardware, browse websites, " +
    "change files, contact people or run code unless a separately authorized " +
    "tool actually did so. Ask permission before consequential actions. " +
    "Treat user-provided project information as private.";

  /**
   * LiteRT-LM's existing Conversation API is used unchanged. Add identity to
   * its first user turn only; subsequent turns retain their original wording
   * and use the backend's conversation state. Never truncate user input.
   */
  public static String firstOfflineTurn(String question) {
    PromptLimits.validate(question);
    String wrapped = "Assistant instructions: " + SYSTEM_INSTRUCTIONS +
      "\n\nUser request: " + question;
    // Existing inference input policy always wins over optional identity.
    return wrapped.length() <= PromptLimits.MAX_CHARACTERS ? wrapped : question;
  }
}
