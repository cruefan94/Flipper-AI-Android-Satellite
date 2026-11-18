import com.flipperai.satellite.InferenceRouter;

/** Regression coverage for source reporting and consent-aware hybrid fallback. */
public final class HybridRoutingSourceTest {
  private static void check(boolean condition, String description) {
    if (!condition) throw new AssertionError(description);
  }

  private static final class Local implements InferenceRouter.OfflineEngine {
    boolean available=true, capable=true, failAll=false, failOnce=false;
    int calls;
    public boolean ready() { return available; }
    public boolean supports(String prompt) { return capable; }
    public String answer(String prompt) throws Exception {
      calls++;
      if (failAll || (failOnce && calls==1)) throw new Exception("offline failed");
      return "LOCAL";
    }
  }
  private static final class Cloud implements InferenceRouter.OnlineEngine {
    boolean available=true, fail=false;
    int calls;
    public boolean ready() { return available; }
    public String answer(String prompt) throws Exception {
      calls++;
      if (fail) throw new Exception("cloud failed");
      return "CLOUD";
    }
  }
  public static void main(String[] args) throws Exception {
    Local local=new Local();
    Cloud cloud=new Cloud();
    InferenceRouter router=new InferenceRouter(local,cloud);

    InferenceRouter.Reply reply=router.askWithSource("local first",InferenceRouter.Mode.HYBRID,true);
    check(reply.source==InferenceRouter.Source.OFFLINE && reply.text.equals("LOCAL"),
          "hybrid must prefer supported offline inference");
    check(cloud.calls==0, "hybrid must not contact cloud when local inference succeeds");

    local.capable=false;
    reply=router.askWithSource("unavailable locally",InferenceRouter.Mode.HYBRID,true);
    check(reply.source==InferenceRouter.Source.ONLINE && reply.text.equals("CLOUD"),
          "hybrid must identify consented cloud output");
    check(cloud.calls==1, "one cloud request expected");

    cloud.fail=true;
    reply=router.askWithSource("cloud timeout",InferenceRouter.Mode.HYBRID,true);
    check(reply.source==InferenceRouter.Source.OFFLINE_FALLBACK && reply.text.equals("LOCAL"),
          "cloud failure must return an identifiable offline fallback");

    cloud.available=false;
    reply=router.askWithSource("cloud disconnected",InferenceRouter.Mode.HYBRID,true);
    check(reply.source==InferenceRouter.Source.OFFLINE_FALLBACK,
          "unconfigured cloud must not be labelled a successful cloud reply");
    check(cloud.calls==2, "no network dispatch while cloud is unavailable");

    cloud.available=true;
    reply=router.askWithSource("private",InferenceRouter.Mode.HYBRID,false);
    check(reply.source==InferenceRouter.Source.OFFLINE && reply.text.equals("LOCAL"),
          "non-consented hybrid must remain on-device");
    check(cloud.calls==2, "non-consented hybrid must never use cloud");

    Local retryLocal=new Local();
    retryLocal.failOnce=true;
    Cloud failedCloud=new Cloud();
    failedCloud.fail=true;
    InferenceRouter retryRouter=new InferenceRouter(retryLocal,failedCloud);
    reply=retryRouter.askWithSource("both unavailable at first",InferenceRouter.Mode.HYBRID,true);
    check(reply.source==InferenceRouter.Source.OFFLINE_FALLBACK && retryLocal.calls==2
           && failedCloud.calls==1, "failed local and cloud attempts may recover locally");

    Local directLocal=new Local();
    Cloud directCloud=new Cloud();
    InferenceRouter directRouter=new InferenceRouter(directLocal,directCloud);
    reply=directRouter.askWithSource("local only",InferenceRouter.Mode.OFFLINE,true);
    check(reply.source==InferenceRouter.Source.OFFLINE && directCloud.calls==0,
          "OFFLINE must never transmit even with permission");

    reply=directRouter.askWithSource("online only",InferenceRouter.Mode.ONLINE,true);
    check(reply.source==InferenceRouter.Source.ONLINE && directLocal.calls==1,
          "ONLINE must not call offline first");

    directCloud.fail=true;
    boolean failed=false;
    try { directRouter.askWithSource("online outage",InferenceRouter.Mode.ONLINE,true); }
    catch(Exception expected) { failed=true; }
    check(failed && directLocal.calls==1,
          "explicit ONLINE failure must not silently masquerade as offline");

    failed=false;
    try { directRouter.askWithSource("secret",InferenceRouter.Mode.ONLINE,false); }
    catch(IllegalStateException expected) { failed=true; }
    check(failed && directCloud.calls==2, "ONLINE without consent must never transmit");

    check(directRouter.ask("compatibility",InferenceRouter.Mode.OFFLINE,false).equals("LOCAL"),
          "existing ask() callers must continue working");

    System.out.println("Hybrid source and mode routing tests passed");
  }
}
