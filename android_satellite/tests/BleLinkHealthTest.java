import com.flipperai.satellite.BleLinkHealth;

/**
 * Deterministic monotonic time tests. These cannot establish radio stability
 * without a physical Flipper; the report is privacy-safe by construction.
 */
public final class BleLinkHealthTest {
  static void check(boolean yes,String message) {
    if(!yes)throw new AssertionError(message);
  }
  public static void main(String[] args) {
    BleLinkHealth stats=new BleLinkHealth();
    BleLinkHealth.Snapshot empty=stats.snapshot(1000);
    check(!empty.connected && empty.verifiedSessions==0,"empty initial state");
    stats.unexpectedDisconnect(1000);
    check(stats.snapshot(1000).unexpectedDrops==0,"unconnected disconnect ignored");
    stats.verified(1000,BleLinkHealth.Channel.STOCK_RPC);
    stats.verified(1100,BleLinkHealth.Channel.STOCK_RPC);
    check(stats.snapshot(1200).verifiedSessions==1,"duplicate callback idempotent");
    check(stats.snapshot(2200).currentSessionMs==1200,"monotonic duration");
    stats.unexpectedDisconnect(3200);
    BleLinkHealth.Snapshot dropped=stats.snapshot(3500);
    check(!dropped.connected && dropped.unexpectedDrops==1,"unexpected link drop recorded");
    check(dropped.verifiedUptimeMs==2200,"accumulated uptime without double-counting");
    stats.unexpectedDisconnect(4000);
    check(stats.snapshot(4000).unexpectedDrops==1,"duplicate drop idempotent");
    stats.recoveryAttempt();stats.recoveryAttempt();
    stats.verified(5000,BleLinkHealth.Channel.CUSTOM_AI1);
    check(stats.snapshot(5100).channel==BleLinkHealth.Channel.CUSTOM_AI1,
      "stock RPC must be distinguished from custom AI1");
    stats.userDisconnect(7000);
    stats.userDisconnect(8000);
    BleLinkHealth.Snapshot stopped=stats.snapshot(9000);
    check(stopped.verifiedSessions==2,"two distinct established sessions");
    check(stopped.userStops==1 && stopped.unexpectedDrops==1,
      "manual and unexpected disconnects are separate");
    check(stopped.automaticRetries==2,"two actual auto reconnect attempts");
    check(stopped.verifiedUptimeMs==4200,"correct total uptime");
    String report=stopped.report();
    check(report.contains("Unexpected verified-link drops: 1"),"drop report");
    check(report.contains("AUTOMATIC") == false || report.contains("Automatic reconnect attempts"),
      "human-readable counters");
    check(!report.contains("AA:BB:CC") && !report.contains("workers.dev"),
      "no address or token in diagnostic report");
    boolean rejected=false;
    try {stats.verified(0,BleLinkHealth.Channel.NONE);}
    catch(IllegalArgumentException expected){rejected=true;}
    check(rejected,"cannot verify an unknown channel");
    System.out.println("BLE reliability statistics tests passed");
  }
}
