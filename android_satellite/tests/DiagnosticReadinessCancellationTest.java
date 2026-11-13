import com.flipperai.satellite.InferenceRouter;

/** Regression: cancellation during diagnostic readiness must not dispatch local inference. */
public final class DiagnosticReadinessCancellationTest {
  static void check(boolean condition) {
    if (!condition) throw new AssertionError("diagnostic cancellation");
  }
  public static void main(String[] args) throws Exception {
    for (boolean cloudConsent : new boolean[]{false, true}) {
      final int[] calls = new int[3];
      InferenceRouter.OfflineEngine local = new InferenceRouter.OfflineEngine() {
        int readyCalls;
        public boolean ready() {
          calls[0]++;
          // First readiness is the HYBRID capability probe; second is the
          // diagnostic fallback after an unavailable cloud.
          if (++readyCalls == 2) Thread.currentThread().interrupt();
          return true;
        }
        public boolean supports(String prompt) { return false; }
        public String answer(String prompt) { calls[1]++; return "LOCAL"; }
      };
      InferenceRouter.OnlineEngine cloud = new InferenceRouter.OnlineEngine() {
        public boolean ready() { return false; }
        public String answer(String prompt) { calls[2]++; return "CLOUD"; }
      };
      boolean cancelled = false;
      try {
        new InferenceRouter(local, cloud).ask("diagnostic", InferenceRouter.Mode.HYBRID, cloudConsent);
      } catch (InterruptedException expected) {
        cancelled = true;
      } finally {
        Thread.interrupted();
      }
      check(cancelled && calls[0] == 2 && calls[1] == 0 && calls[2] == 0);
    }
    // When cloud inference fails, the last-resort diagnostic readiness
    // probe must also honor interruption before dispatching to local.
    final int[] calls = new int[2];
    InferenceRouter.OfflineEngine local = new InferenceRouter.OfflineEngine() {
      int readyCalls;
      public boolean ready() {
        if (++readyCalls == 2) Thread.currentThread().interrupt();
        return true;
      }
      public boolean supports(String prompt) { return false; }
      public String answer(String prompt) { calls[0]++; return "LOCAL"; }
    };
    InferenceRouter.OnlineEngine cloud = new InferenceRouter.OnlineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) throws Exception { calls[1]++; throw new Exception("network failure"); }
    };
    boolean cancelled = false;
    try {
      new InferenceRouter(local, cloud).ask("diagnostic", InferenceRouter.Mode.HYBRID, true);
    } catch (InterruptedException expected) {
      cancelled = true;
    } finally {
      Thread.interrupted();
    }
    check(cancelled && calls[0] == 0 && calls[1] == 1);
    System.out.println("Diagnostic readiness cancellation tests passed");
  }
}
