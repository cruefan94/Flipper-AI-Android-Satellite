import com.flipperai.satellite.InferenceRouter;
/** Standalone regression test: javac -d /tmp/ai-test android_satellite/app/src/main/java/com/flipperai/satellite/InferenceRouter.java android_satellite/tests/HybridFallbackTest.java && java -cp /tmp/ai-test HybridFallbackTest */
public final class HybridFallbackTest {
  static void check(boolean ok) { if (!ok) throw new AssertionError("hybrid fallback"); }
  public static void main(String[] args) throws Exception {
    InferenceRouter.OfflineEngine failed = new InferenceRouter.OfflineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) throws Exception { throw new Exception("local error"); }
    };
    InferenceRouter.OnlineEngine cloud = new InferenceRouter.OnlineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) { return "CLOUD"; }
    };
    InferenceRouter router = new InferenceRouter(failed, cloud);
    boolean blocked = false;
    try { router.ask("private", InferenceRouter.Mode.HYBRID, false); }
    catch (Exception expected) { blocked = true; }
    check(blocked);
    check(router.ask("approved", InferenceRouter.Mode.HYBRID, true).equals("CLOUD"));
    // Capability probes may interrupt the thread after ready() succeeds.
    // Cancellation must prevent both local execution and cloud fallback.
    final int[] calls = new int[2];
    InferenceRouter.OfflineEngine interruptedProbe = new InferenceRouter.OfflineEngine() {
      public boolean ready() { return true; }
      public boolean supports(String prompt) {
        Thread.currentThread().interrupt();
        return true;
      }
      public String answer(String prompt) { calls[0]++; return "LOCAL"; }
    };
    InferenceRouter.OnlineEngine trackedCloud = new InferenceRouter.OnlineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) { calls[1]++; return "CLOUD"; }
    };
    boolean cancelled = false;
    try {
      new InferenceRouter(interruptedProbe, trackedCloud).ask("probe", InferenceRouter.Mode.HYBRID, true);
    } catch (InterruptedException expected) {
      cancelled = true;
    } finally {
      Thread.interrupted(); // clear the test thread's interrupt state
    }
    check(cancelled && calls[0] == 0 && calls[1] == 0);
    System.out.println("Hybrid fallback tests passed");
  }
}
