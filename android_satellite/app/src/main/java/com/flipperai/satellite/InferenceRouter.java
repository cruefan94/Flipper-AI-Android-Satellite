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
  /** Origin is reported so the UI never labels an offline fallback as a cloud response. */
  public enum Source { OFFLINE, ONLINE, OFFLINE_FALLBACK }
  public static final class Reply {
    public final String text;
    public final Source source;
    private Reply(String text, Source source) { this.text=text; this.source=source; }
  }
  /** Compatibility entry point for BLE and other existing callers. */
  public String ask(String prompt, Mode mode, boolean cloudConsent) throws Exception {
    return askWithSource(prompt, mode, cloudConsent).text;
  }
  public Reply askWithSource(String prompt, Mode mode, boolean cloudConsent) throws Exception {
    // Enforce a single bounded prompt policy before any offline or cloud dispatch.
    PromptLimits.validate(prompt);
    // An absent/invalid mode must never implicitly become HYBRID and reach cloud.
    if (mode == null) throw new IllegalArgumentException("Inference mode is required");
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    if (mode == Mode.OFFLINE) return new Reply(offline(prompt), Source.OFFLINE);
    // Cancellation must be honored in every mode, including direct ONLINE.
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    if (mode == Mode.ONLINE) {
      if (!cloudConsent) throw new IllegalStateException("Cloud transmission not approved");
      if (!cloud.ready()) throw new IllegalStateException("Cloud unavailable");
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
      return new Reply(cloud.answer(prompt), Source.ONLINE);
    }
    // An interrupted request must never initiate cloud fallback.
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    // Local failure never authorizes transmission; fallback needs explicit consent.
    Exception localFailure = null;
    boolean localReady = local.ready();
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    if (localReady && local.supports(prompt)) {
      // supports() can be slow or cancel the request; never dispatch afterward.
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
      try { return new Reply(local.answerForHybrid(prompt), Source.OFFLINE); }
      catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw cancelled; }
      catch (Exception e) { localFailure = e; }
    }
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    if (cloudConsent && cloud.ready()) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
      try { return new Reply(cloud.answer(prompt), Source.ONLINE); }
      catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw cancelled; }
      catch (Exception cloudFailure) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
        if(local.ready()) {
          if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
          try { return new Reply(local.answer(prompt), Source.OFFLINE_FALLBACK); }
          catch(InterruptedException cancelled) { Thread.currentThread().interrupt(); throw cancelled; }
          catch(Exception offlineFailure) { cloudFailure.addSuppressed(offlineFailure); }
        }
        throw cloudFailure;
      }
    }
    // Diagnostic fallback stays on-device when consent is absent, or when a
    // model fails and no approved cloud endpoint is available.
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    if (local.ready()) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
      return new Reply(local.answer(prompt), cloudConsent ? Source.OFFLINE_FALLBACK : Source.OFFLINE);
    }
    if (localFailure != null)
      throw new IllegalStateException("Local inference failed; cloud fallback disabled or unavailable", localFailure);
    throw new IllegalStateException("No local model available; cloud disabled or unavailable");
  }
  private String offline(String prompt) throws Exception {
    boolean ready = local.ready();
    // A readiness probe may interrupt the request even when it reports unavailable.
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    if (!ready) throw new IllegalStateException("Install and configure an Android on-device model runtime");
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("AI request cancelled");
    return local.answer(prompt);
  }
}
