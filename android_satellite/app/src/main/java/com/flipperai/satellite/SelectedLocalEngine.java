package com.flipperai.satellite;

/** Explicit model provider choice. All providers must execute on the phone.
 * No cloud engine is referenced here; the separate InferenceRouter owns consent.
 */
public final class SelectedLocalEngine implements InferenceRouter.OfflineEngine {
    public enum Provider { EMBEDDED, LOCALHOST }
    private final InferenceRouter.OfflineEngine embedded, localhost;
    private volatile Provider provider = Provider.EMBEDDED;

    public SelectedLocalEngine(InferenceRouter.OfflineEngine embedded,
                               InferenceRouter.OfflineEngine localhost) {
        if(embedded==null || localhost==null) throw new IllegalArgumentException("model engines");
        this.embedded=embedded; this.localhost=localhost;
    }
    public void select(Provider selected) {
        if(selected==null) throw new IllegalArgumentException("model provider");
        provider=selected;
    }
    public Provider selected() { return provider; }
    private InferenceRouter.OfflineEngine engine() {
        return provider==Provider.EMBEDDED?embedded:localhost;
    }
    @Override public boolean ready() { return engine().ready(); }
    @Override public boolean supports(String prompt) { return engine().supports(prompt); }
    @Override public String answer(String prompt) throws Exception { return engine().answer(prompt); }
}
