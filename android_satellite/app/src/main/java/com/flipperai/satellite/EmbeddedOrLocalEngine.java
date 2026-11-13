package com.flipperai.satellite;

/** Prefer an imported LiteRT-LM model, retaining the prior localhost server as an option.
 * Both engines are offline-only. This adapter never contacts the cloud.
 */
public final class EmbeddedOrLocalEngine implements InferenceRouter.OfflineEngine {
  private final InferenceRouter.OfflineEngine embedded,loopback;
  public EmbeddedOrLocalEngine(InferenceRouter.OfflineEngine embedded,
                              InferenceRouter.OfflineEngine loopback) {
    if(embedded==null || loopback==null)
      throw new IllegalArgumentException("Two offline engines are required");
    this.embedded=embedded;this.loopback=loopback;
  }
  @Override public boolean ready(){return embedded.ready() || loopback.ready();}
  @Override public boolean supports(String prompt) {
    return embedded.ready()?embedded.supports(prompt):loopback.supports(prompt);
  }
  @Override public String answer(String prompt) throws Exception {
    if(embedded.ready())return embedded.answer(prompt);
    return loopback.answer(prompt);
  }
  @Override public String answerForHybrid(String prompt) throws Exception {
    if(embedded.ready())return embedded.answerForHybrid(prompt);
    return loopback.answerForHybrid(prompt);
  }
}
