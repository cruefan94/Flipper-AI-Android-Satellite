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
    expect(null,Transition.NONE);
    System.out.println("BleAiStatusPolicy tests passed");
  }
}
