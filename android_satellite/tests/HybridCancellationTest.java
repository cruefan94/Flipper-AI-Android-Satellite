import com.flipperai.satellite.InferenceRouter;

/** Cancellation must not turn a failed local request into a cloud request. */
public final class HybridCancellationTest {
  static void check(boolean ok, String message) { if(!ok) throw new AssertionError(message); }
  public static void main(String[] args) throws Exception {
    final int[] cloudCalls = {0};
    InferenceRouter.OnlineEngine cloud = new InferenceRouter.OnlineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) { cloudCalls[0]++; return "CLOUD"; }
    };
    InferenceRouter.OfflineEngine cancelled = new InferenceRouter.OfflineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) throws Exception { throw new InterruptedException("cancelled"); }
    };
    InferenceRouter router = new InferenceRouter(cancelled, cloud);
    boolean interrupted = false;
    try { router.ask("test", InferenceRouter.Mode.HYBRID, true); }
    catch(InterruptedException expected) { interrupted = true; }
    check(interrupted, "local interruption must propagate");
    check(Thread.currentThread().isInterrupted(), "interrupt status must be preserved");
    check(cloudCalls[0] == 0, "interrupted local request must not reach cloud");
    Thread.interrupted(); // clear test thread interrupt flag

    Thread.currentThread().interrupt();
    interrupted = false;
    try { router.ask("test", InferenceRouter.Mode.HYBRID, true); }
    catch(InterruptedException expected) { interrupted = true; }
    check(interrupted && cloudCalls[0] == 0, "pre-cancelled request must not reach cloud");
    Thread.interrupted();

    InferenceRouter.OfflineEngine failed = new InferenceRouter.OfflineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) throws Exception { throw new Exception("ordinary model failure"); }
    };
    check(new InferenceRouter(failed, cloud).ask("test", InferenceRouter.Mode.HYBRID, true).equals("CLOUD"),
          "ordinary model failure should still permit consented fallback");
    check(cloudCalls[0] == 1, "only ordinary failure should reach cloud");
    // A pre-cancelled OFFLINE request must not invoke local inference.
    final int[] localCalls = {0};
    InferenceRouter.OfflineEngine trackingLocal = new InferenceRouter.OfflineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) { localCalls[0]++; return "LOCAL"; }
    };
    Thread.currentThread().interrupt();
    interrupted = false;
    try { new InferenceRouter(trackingLocal, cloud).ask("test", InferenceRouter.Mode.OFFLINE, true); }
    catch (InterruptedException expected) { interrupted = true; }
    check(interrupted && localCalls[0] == 0, "pre-cancelled offline request must not run");
    Thread.interrupted();

    // Cloud failure that sets interrupt status must not start diagnostic fallback.
    InferenceRouter.OnlineEngine interruptingCloud = new InferenceRouter.OnlineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) throws Exception {
        Thread.currentThread().interrupt();
        throw new Exception("cloud failed after cancellation");
      }
    };
    interrupted = false;
    try { new InferenceRouter(trackingLocal, interruptingCloud)
        .ask("test", InferenceRouter.Mode.HYBRID, true); }
    catch (InterruptedException expected) { interrupted = true; }
    check(interrupted && localCalls[0] == 0, "cancelled cloud request must not retry locally");
    Thread.interrupted();

    System.out.println("Hybrid cancellation tests passed");
  }
}
