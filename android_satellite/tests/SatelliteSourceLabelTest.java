import com.flipperai.satellite.*;

/** Native JVM checks that the Flipper screen identifies the real answer source. */
public final class SatelliteSourceLabelTest {
  private static int checks=0;
  private static void check(boolean ok,String label) {
    checks++;
    if(!ok)throw new AssertionError(label);
  }
  private static String ask(SatelliteRequestHandler handler,int id,String prompt) {
    String response=handler.handle(SatelliteProtocol.encode("ASK",id,prompt),180);
    check(response!=null,"frame must fit 180 bytes");
    String[] fields=SatelliteProtocol.decode(response);
    check(fields[1].equals("ANSWER"),"valid answer type");
    return fields[3];
  }
  private static final class Local implements InferenceRouter.OfflineEngine {
    boolean enabled=true,capable=true;
    int calls=0;
    @Override public boolean ready(){return enabled;}
    @Override public boolean supports(String prompt){return enabled&&capable;}
    @Override public String answer(String prompt){calls++;return "Local reply";}
  }
  private static final class Cloud implements InferenceRouter.OnlineEngine {
    boolean enabled=true,fail=false;
    int calls=0;
    @Override public boolean ready(){return enabled;}
    @Override public String answer(String prompt) throws Exception {
      calls++;
      if(fail)throw new IllegalStateException("provider unavailable");
      return "Internet reply";
    }
  }
  public static void main(String[] args) throws Exception {
    Local local=new Local();
    Cloud cloud=new Cloud();
    SatelliteRequestHandler h=new SatelliteRequestHandler(new InferenceRouter(local,cloud));
    h.configure(InferenceRouter.Mode.OFFLINE,false);
    check(ask(h,1,"hello").equals("OFFLINE: Local reply"),"offline labelled accurately");
    check(cloud.calls==0,"no online request in OFFLINE mode");
    h.configure(InferenceRouter.Mode.HYBRID,true);
    check(ask(h,2,"supported").equals("OFFLINE: Local reply"),
      "hybrid local-first is labelled offline");
    check(cloud.calls==0,"hybrid local-first did not call cloud");
    local.capable=false;
    check(ask(h,3,"complex prompt").equals("CLOUD: Internet reply"),
      "hybrid can label consented cloud response");
    check(cloud.calls==1,"cloud called once");
    cloud.fail=true;
    check(ask(h,4,"failed cloud").equals("OFFLINE FALLBACK: Local reply"),
      "hybrid failure is labelled offline fallback, never cloud");
    h.configure(InferenceRouter.Mode.ONLINE,true);
    String rejected=h.handle(SatelliteProtocol.encode("ASK",5,"cloud unavailable"),180);
    check(SatelliteProtocol.decode(rejected)[1].equals("ERROR"),
      "online failures are errors, not false AI success");
    h.configure(InferenceRouter.Mode.ONLINE,false);
    int previous=cloud.calls;
    rejected=h.handle(SatelliteProtocol.encode("ASK",6,"private"),180);
    check(SatelliteProtocol.decode(rejected)[1].equals("ERROR") &&
          cloud.calls==previous,"online without permission cannot call cloud");
    cloud.fail=false;
    h.configure(InferenceRouter.Mode.ONLINE,true);
    check(ask(h,7,"online").equals("CLOUD: Internet reply"),
      "direct online is correctly identified");
    System.out.println("Flipper AI source label test: "+checks+" checks passed");
  }
}
