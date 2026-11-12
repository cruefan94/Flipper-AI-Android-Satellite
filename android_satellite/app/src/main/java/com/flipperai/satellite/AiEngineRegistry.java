package com.flipperai.satellite;

/** Stable routing contract for the independent Android AI engine.
 * Hardware actions must not be executed from model text.
 * No remote inference is authorized by selecting an engine.
 */
public final class AiEngineRegistry {
  public enum Engine { INDEPENDENT_LOCAL, GEMINI_NANO, CLOUD_OPT_IN }
  public static final class Selection {
    public final Engine engine;
    public final boolean cloudConsent;
    public Selection(Engine engine, boolean cloudConsent) {
      if(engine==null)throw new IllegalArgumentException("Engine required");
      if(engine==Engine.CLOUD_OPT_IN&&!cloudConsent)
        throw new IllegalArgumentException("Explicit cloud consent required");
      this.engine=engine;this.cloudConsent=cloudConsent;
    }
  }
  private AiEngineRegistry(){}
  public static String describe(Engine engine){
    switch(engine){
      case INDEPENDENT_LOCAL:return "Independent model: requires a separately installed local runtime";
      case GEMINI_NANO:return "Gemini Nano: optional, device-dependent Android AICore";
      case CLOUD_OPT_IN:return "Online AI: explicit consent required";
      default:throw new IllegalArgumentException("Unknown engine");
    }
  }
}
