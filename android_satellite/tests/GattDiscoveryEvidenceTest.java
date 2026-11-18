import com.flipperai.satellite.GattDiscoveryEvidence;

/** JVM tests for safe phone-side BLE observations, not a physical GATT run. */
public final class GattDiscoveryEvidenceTest {
  private static void check(boolean ok,String reason){
    if(!ok)throw new AssertionError(reason);
  }
  public static void main(String[] args){
    GattDiscoveryEvidence evidence=new GattDiscoveryEvidence();
    check(evidence.report().contains("NOT COMPLETED"),"No callback is not no-service");
    check(evidence.nextAction().contains("Pairing alone"),"Start guidance");
    evidence.beginAttempt();
    check(evidence.report().contains("NOT COMPLETED"),"Attempt not outcome");

    evidence.succeeded(false,true,5);
    check(evidence.report().contains("COMPLETED"),"Callback outcome");
    check(evidence.report().contains("AI service=NOT FOUND"),"Missing AI");
    check(evidence.report().contains("Momentum RPC=FOUND"),"Known RPC");
    check(evidence.report().contains("CURRENT LINK"),"Active observation");
    check(evidence.nextAction().contains("Do not automatically unpair"),"No unpair");

    evidence.closeLink();
    check(evidence.report().contains("LAST RESULT"),"Link loss must mark stale");
    evidence.beginAttempt();
    check(evidence.report().contains("LAST RESULT"),"Auto retry must preserve evidence");
    evidence.succeeded(true,true,7);
    check(evidence.report().contains("AI service=FOUND"),"AI discovered");
    check(evidence.nextAction().contains("HELLO/ACK"),"Service discovery is not ready");

    evidence.failed(133);
    check(evidence.report().contains("FAILED (status 133)"),"GATT failure evidence");
    check(evidence.report().contains("Service presence not established"),"Failure must not assert missing profile");
    check(evidence.nextAction().contains("does not prove"),"Failure is not firmware proof");
    evidence.closeLink();
    check(evidence.report().contains("LAST RESULT"),"Failed result remains stale");

    evidence.succeeded(false,false,-3);
    check(evidence.report().contains("service count=0"),"Counts are bounded below");
    check(evidence.nextAction().contains("neither"),"Unknown device guidance");

    // A reconnect must not turn an old RPC-only result into custom AI1 readiness.
    evidence.succeeded(false,true,4);
    for(int retry=0;retry<16;retry++) {
      evidence.beginAttempt();
      String stale=evidence.report();
      check(stale.contains("LAST RESULT"),"Reconnect must mark old discovery stale");
      check(stale.contains("AI service=NOT FOUND"),"RPC-only discovery is never AI1");
      check(stale.contains("Momentum RPC=FOUND"),"Retain diagnostic RPC observation");
      check(!stale.contains("CURRENT LINK"),"Old discovery cannot describe current link");
    }
    evidence.failed(133);
    check(!evidence.report().contains("AI service=FOUND"),"Failed discovery cannot imply AI1");
    evidence.closeLink();
    check(evidence.report().contains("LAST RESULT"),"Failed reconnect result is stale");
    evidence.succeeded(true,false,2);
    check(evidence.report().contains("AI service=FOUND"),"Fresh AI1 discovery is distinct from RPC");
    check(evidence.report().contains("Momentum RPC=NOT FOUND"),"Do not infer stock RPC");
    check(evidence.nextAction().contains("HELLO/ACK"),"AI1 discovery alone cannot prove handshake");
    evidence.beginAttempt();
    check(evidence.report().contains("LAST RESULT"),"AI1 discovery must expire on reconnect");

    evidence.clear();
    check(evidence.report().contains("NOT COMPLETED"),"New selected device clears previous evidence");
    check(!evidence.report().contains("Momentum RPC=FOUND"),"No cross-device leakage");

    String report=evidence.report()+evidence.nextAction();
    check(!report.contains("00:11:22"),"Never include device identifiers");
    System.out.println("GATT discovery evidence tests passed");
  }
}
