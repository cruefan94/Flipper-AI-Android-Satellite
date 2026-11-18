package com.flipperai.satellite;

import java.util.Locale;

/** Small, stable application identity shared by native inference backends.
 * Never contains secrets, live device telemetry, or a claim that optional hardware works.
 */
public final class AppIdentity {
  private AppIdentity() {}
  public static final String NAME="Flipper AI Android Satellite";
  public static final String SYSTEM_INSTRUCTION =
      "You are the assistant inside Flipper AI Android Satellite, part of a custom "
    + "Flipper Zero Momentum ecosystem. The Android phone provides local offline AI "
    + "and optional consent-gated online AI. Flipper Zero is the portable interface "
    + "and hardware controller; ESP32, Arduino and Raspberry Pi are separate optional "
    + "project components. Prefer proven third-party AI runtimes. Distinguish source "
    + "implemented, CI-tested, real-device verified and planned features. Never claim "
    + "a Bluetooth link, installed firmware, hardware reading or cloud consent without "
    + "an actual application diagnostic. Do not execute hardware commands.";

  /** Route exact self-identity/status questions to read-only application facts.
   * General questions about Bluetooth, electronics etc. still reach the chosen AI.
   */
  public static boolean isAppQuestion(String question) {
    if(question==null)return false;
    String s=question.toLowerCase(Locale.ROOT).trim();
    return s.contains("what app are you") || s.contains("which app are you") ||
        s.contains("what application are you") ||
        s.contains("where are you installed") ||
        s.contains("what app is this") ||
        s.contains("about my flipper ai") ||
        s.contains("your application") ||
        s.contains("your app") ||
        s.contains("what can you do") ||
        s.contains("who are you") ||
        s.contains("your capabilities") ||
        s.contains("your diagnostics");
  }
}
