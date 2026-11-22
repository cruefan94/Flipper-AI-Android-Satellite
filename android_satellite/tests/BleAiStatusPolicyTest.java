import com.flipperai.satellite.BleAiStatusPolicy;
import com.flipperai.satellite.BleAiStatusPolicy.Transition;

public final class BleAiStatusPolicyTest {
  static void expect(String status, Transition expected) {
    if(BleAiStatusPolicy.classify(status)!=expected)
      throw new AssertionError("Wrong AI1 transition for: "+status);
  }
  static void channel(String status, BleAiStatusPolicy.Channel expected) {
    if(BleAiStatusPolicy.verifiedChannel(status)!=expected)
      throw new AssertionError("Wrong verified channel for: "+status);
  }
  public static void main(String[] args) {
    channel("AI BLE ready",BleAiStatusPolicy.Channel.CUSTOM_AI1);
    channel("AI BLE ready: subscribed",BleAiStatusPolicy.Channel.CUSTOM_AI1);
    channel("Stock Momentum RPC indications enabled",BleAiStatusPolicy.Channel.STOCK_RPC);
    channel("Stock Momentum RPC indications enabled (fallback)",BleAiStatusPolicy.Channel.STOCK_RPC);
    channel("AI BLE readyish",BleAiStatusPolicy.Channel.NONE);
    channel("AI BLE ready_not",BleAiStatusPolicy.Channel.NONE);
    channel("Stock Momentum RPC indications enabledish",BleAiStatusPolicy.Channel.NONE);
    channel("Disconnected",BleAiStatusPolicy.Channel.NONE);
    channel(null,BleAiStatusPolicy.Channel.NONE);
    expect("AI BLE ready (custom)",Transition.READY);
    expect("Stock Momentum RPC indications enabled",Transition.INVALIDATE);
    expect("Connecting to selected Flipper",Transition.INVALIDATE);
    expect("Reconnecting to selected Flipper",Transition.INVALIDATE);
    expect("Disconnected",Transition.INVALIDATE);
    expect("BLE disconnected",Transition.INVALIDATE);
    expect("AI GATT write timed out",Transition.INVALIDATE);
    expect("Flipper Bluetooth not connected",Transition.NONE);
    expect("AI NOT READY",Transition.NONE);
    // Prefix collisions must never create a fake custom AI1 READY session
    // or revoke a live request based on an unrelated diagnostic label.
    expect("AI BLE readyish",Transition.NONE);
    expect("AI BLE ready_not",Transition.NONE);
    expect("Stock Momentum RPC indications enabledish",Transition.NONE);
    expect("Disconnectedness",Transition.NONE);
    expect("Reconnecting to selected Flipperish",Transition.NONE);
    expect("AI BLE ready: subscribed",Transition.READY);
    expect("AI BLE ready(custom)",Transition.READY);
    expect("Stock Momentum RPC indications enabled: fallback",Transition.INVALIDATE);
    expect("Disconnected (remote)",Transition.INVALIDATE);
    // Adversarial channel and lifecycle strings must not manufacture readiness,
    // stock RPC evidence, or a reconnect invalidation from lookalike labels.
    String[] misleading = {
      "AI BLE readiness", "AI BLE readyish", "AI BLE ready_not",
      "AI BLE ready\tcustom", "AI BLE ready\ncustom", "AI BLE ready\u00a0custom",
      "ai BLE ready", " AI BLE ready", "AI BLE readyish: subscribed",
      "Stock Momentum RPC indications enabledish: fallback",
      "Stock Momentum RPC indications enabled_not",
      "Stock Momentum RPC indications enabled\t(fallback)",
      "Disconnectedness: old link", "Reconnecting to selected Flipperish: stale",
      "AI GATT write timed outish"
    };
    for(String label:misleading) {
      expect(label,Transition.NONE);
      channel(label,BleAiStatusPolicy.Channel.NONE);
    }
    // A complete prefix with a control-bearing suffix is not verified evidence.
    String[] controlBearing = {
      "AI BLE ready: subscribed\nDisconnected",
      "AI BLE ready (custom)\r",
      "AI BLE ready: subscribed\t",
      "AI BLE ready: subscribed\u0000",
      "Stock Momentum RPC indications enabled: fallback\nAI BLE ready",
      "Stock Momentum RPC indications enabled (fallback)\u007f",
      "Disconnected (remote)\nAI BLE ready",
      "Reconnecting to selected Flipper: retry\r"
    };
    for(String label:controlBearing) {
      expect(label,Transition.NONE);
      channel(label,BleAiStatusPolicy.Channel.NONE);
    }
    // Diagnostics after a complete label remain supported.
    channel("AI BLE ready (subscribed)",BleAiStatusPolicy.Channel.CUSTOM_AI1);
    channel("Stock Momentum RPC indications enabled: fallback",BleAiStatusPolicy.Channel.STOCK_RPC);
    expect("AI GATT write timed out (session)",Transition.INVALIDATE);
    expect(null,Transition.NONE);
    System.out.println("BleAiStatusPolicy tests passed");
  }
}
