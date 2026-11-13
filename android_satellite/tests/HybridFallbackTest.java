import com.flipperai.satellite.InferenceRouter;
/** Standalone regression test: javac -d /tmp/ai-test android_satellite/app/src/main/java/com/flipperai/satellite/InferenceRouter.java android_satellite/tests/HybridFallbackTest.java && java -cp /tmp/ai-test HybridFallbackTest */
public final class HybridFallbackTest {
  static void check(boolean ok) { if (!ok) throw new AssertionError("hybrid fallback"); }
  public static void main(String[] args) throws Exception {
    InferenceRouter.OfflineEngine failed = new InferenceRouter.OfflineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) throws Exception { throw new Exception("local error"); }
    };
    final int[] cloudCalls = {0};
    InferenceRouter.OnlineEngine cloud = new InferenceRouter.OnlineEngine() {
      public boolean ready() { return true; }
      public String answer(String prompt) { cloudCalls[0]++; return "CLOUD"; }
    };
    InferenceRouter router = new InferenceRouter(failed, cloud);
    boolean blocked = false;
    try { router.ask("private", InferenceRouter.Mode.HYBRID, false); }
    catch (Exception expected) { blocked = true; }
    check(blocked);
    check(cloudCalls[0] == 0);
    check(router.ask("approved", InferenceRouter.Mode.HYBRID, true).equals("CLOUD"));
    check(cloudCalls[0] == 1);
    System.out.println("Hybrid fallback tests passed");
  }
}
