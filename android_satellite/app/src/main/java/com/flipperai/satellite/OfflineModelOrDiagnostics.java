package com.flipperai.satellite;

/** Local inference wins when installed. Diagnostics remain available without a model.
 * No cloud calls are made by this class, including when local inference fails.
 */
public final class OfflineModelOrDiagnostics implements InferenceRouter.OfflineEngine {
  private final LocalModelEngine model;
  private final OfflineDiagnosticEngine diagnostic;
  public OfflineModelOrDiagnostics(LocalModelEngine model,OfflineDiagnosticEngine diagnostic){
    if(model==null || diagnostic==null)
      throw new IllegalArgumentException("Local model and diagnostic fallback are required");
    this.model=model;this.diagnostic=diagnostic;
  }
  @Override public boolean ready(){return true;}
  @Override public boolean supports(String prompt){return model.ready() || diagnostic.supports(prompt);}
  @Override public String answerForHybrid(String prompt) throws Exception {
    /* Unlike OFFLINE mode, a failed actual model can trigger cloud fallback,
     * but only when the router separately verifies explicit cloud consent.
     */
    return model.ready()?model.answer(prompt):diagnostic.answer(prompt);
  }
  @Override public String answer(String prompt) throws Exception {
    if(model.ready()){
      try{return model.answer(prompt);}
      catch(Exception e){return "Local model unavailable. "+diagnostic.answer(prompt);}
    }
    return diagnostic.answer(prompt);
  }
}
