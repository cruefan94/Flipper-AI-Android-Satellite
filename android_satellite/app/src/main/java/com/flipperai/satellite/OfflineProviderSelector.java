package com.flipperai.satellite;

/**
 * Explicit offline-backend selection. Default is the pre-existing LiteRT-first
 * chain; llama.cpp is opt-in and never silently falls back to another model.
 * Only InferenceRouter, not this class, controls optional cloud consent.
 */
public final class OfflineProviderSelector implements InferenceRouter.OfflineEngine {
  private final InferenceRouter.OfflineEngine existing, llama;
  private volatile boolean llamaSelected;

  public OfflineProviderSelector(InferenceRouter.OfflineEngine existing,
      InferenceRouter.OfflineEngine llama, boolean useLlama) {
    if(existing==null || llama==null)
      throw new IllegalArgumentException("Both offline providers are required");
    this.existing=existing;
    this.llama=llama;
    llamaSelected=useLlama;
  }

  public void selectLlama(boolean selected) { llamaSelected=selected; }
  public boolean isLlamaSelected() { return llamaSelected; }
  private InferenceRouter.OfflineEngine selected() {
    return llamaSelected?llama:existing;
  }

  @Override public boolean ready() { return selected().ready(); }
  @Override public boolean supports(String prompt) { return selected().supports(prompt); }
  @Override public String answer(String prompt) throws Exception {
    return selected().answer(prompt);
  }
  @Override public String answerForHybrid(String prompt) throws Exception {
    return selected().answerForHybrid(prompt);
  }
}
