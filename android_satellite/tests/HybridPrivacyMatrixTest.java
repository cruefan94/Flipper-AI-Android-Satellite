import com.flipperai.satellite.InferenceRouter;

/** Pure-Java privacy and fallback regression tests; no Android device needed. */
public final class HybridPrivacyMatrixTest {
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
  static final class Local implements InferenceRouter.OfflineEngine {
    boolean ready = true, supports = true, fail = false;
    int calls = 0;
    public boolean ready() { return ready; }
    public boolean supports(String prompt) { return supports; }
    public String answer(String prompt) throws Exception {
      calls++;
      if (fail) throw new Exception("local inference failure");
      return "LOCAL";
    }
  }
  static final class Cloud implements InferenceRouter.OnlineEngine {
    boolean ready = true, fail = false;
    int calls = 0;
    public boolean ready() { return ready; }
    public String answer(String prompt) throws Exception {
      calls++;
      if (fail) throw new Exception("cloud failure");
      return "CLOUD";
    }
  }
  private static boolean rejects(InferenceRouter router, InferenceRouter.Mode mode, boolean consent) {
    try { router.ask("private diagnostics", mode, consent); return false; }
    catch (Exception expected) { return true; }
  }
  public static void main(String[] args) throws Exception {
    Local local = new Local();
    Cloud cloud = new Cloud();
    InferenceRouter router = new InferenceRouter(local, cloud);

    check("LOCAL".equals(router.ask("private", InferenceRouter.Mode.OFFLINE, false)), "offline response");
    check(cloud.calls == 0, "offline contacted cloud");
    check(rejects(router, InferenceRouter.Mode.ONLINE, false), "online without consent accepted");
    check(cloud.calls == 0, "cloud contacted without consent");
    check(rejects(router, null, true), "null mode accepted");
    check("LOCAL".equals(router.ask("private", InferenceRouter.Mode.HYBRID, true)), "hybrid did not prefer local");
    check(cloud.calls == 0, "hybrid unnecessarily contacted cloud");

    local.fail = true;
    check(rejects(router, InferenceRouter.Mode.HYBRID, false), "failed local transmitted without consent");
    check(cloud.calls == 0, "failed local contacted cloud without consent");
    check("CLOUD".equals(router.ask("approved", InferenceRouter.Mode.HYBRID, true)), "approved cloud fallback");
    check(cloud.calls == 1, "cloud fallback call count");

    local.fail = false;
    local.supports = false;
    check(rejects(router, InferenceRouter.Mode.HYBRID, false) == false, "local-only fallback unavailable");
    check(cloud.calls == 1, "unsupported prompt leaked without consent");
    check("CLOUD".equals(router.ask("approved", InferenceRouter.Mode.HYBRID, true)), "approved unsupported fallback");
    check(cloud.calls == 2, "unsupported cloud call count");

    local.supports = true;
    cloud.fail = true;
    check("LOCAL".equals(router.ask("private", InferenceRouter.Mode.HYBRID, true)), "cloud failure should preserve local preference");
    check(cloud.calls == 2, "cloud used despite working local");

    local.ready = false;
    check(rejects(router, InferenceRouter.Mode.OFFLINE, false), "offline without local runtime accepted");
    check(rejects(router, InferenceRouter.Mode.ONLINE, false), "online consent bypassed");
    check(rejects(router, InferenceRouter.Mode.HYBRID, false), "hybrid without local or consent accepted");
    check(cloud.calls == 2, "cloud used without consent when local unavailable");
    System.out.println("Hybrid privacy matrix tests passed");
  }
}
