package com.flipperai.satellite;

/** Never silently sends data to internet. Offline engine is an injected, on-device runtime. */
public final class InferenceRouter {
  public enum Mode { OFFLINE, ONLINE, HYBRID }
  public interface OfflineEngine { boolean ready(); String answer(String prompt) throws Exception; default boolean supports(String prompt) { return ready(); } default String answerForHybrid(String prompt) throws Exception { return answer(prompt); } }
  public interface OnlineEngine { boolean ready(); String answer(String prompt) throws Exception; }
  private final OfflineEngine local; private final OnlineEngine cloud;
  public InferenceRouter(OfflineEngine local, OnlineEngine cloud) {
    if (local == null || cloud == null)
      throw new IllegalArgumentException("Offline and online engines are required");
    this.local=local; this.cloud=cloud;
  }
  public String ask(String prompt, Mode mode, boolean cloudConsent) throws Exception {
    // Enforce a single bounded prompt policy before any offline or cloud dispatch.
    PromptLimits.validate(prompt);
    // An absent/invalid mode must never implicitly become HYBRID and reach cloud.
    if (mode == null) throw new IllegalArgumentException("Inference mode is required");
    if (mode == Mode.OFFLINE) return offline(prompt);
    if (mode == Mode.ONLINE) {
      if (!cloudConsent) throw new IllegalStateException("Cloud transmission not approved");
      if (!cloud.ready()) throw new IllegalStateException("Cloud unavailable");
      return cloud.answer(prompt);
    }
    // Local failure never authorizes transmission; fallback needs explicit consent.
    Exception localFailure = null;
    if (local.ready() && local.supports(prompt)) {
      try { return local.answerForHybrid(prompt); }
      catch (Exception e) { localFailure = e; }
    }
    if (cloudConsent && cloud.ready()) {
      try { return cloud.answer(prompt); }
      catch (Exception cloudFailure) {
        if(local.ready()) {
          try { return local.answer(prompt); }
          catch(Exception offlineFailure) { cloudFailure.addSuppressed(offlineFailure); }
        }
        throw cloudFailure;
      }
    }
    // Diagnostic fallback stays on-device when consent is absent, or when a
    // model fails and no approved cloud endpoint is available.
    if (local.ready()) return local.answer(prompt);
    if (localFailure != null)
      throw new IllegalStateException("Local inference failed; cloud fallback disabled or unavailable", localFailure);
    throw new IllegalStateException("No local model available; cloud disabled or unavailable");
  }
  private String offline(String prompt) throws Exception {
    if (!local.ready()) throw new IllegalStateException("Install and configure an Android on-device model runtime");
    return local.answer(prompt);
  }
}
