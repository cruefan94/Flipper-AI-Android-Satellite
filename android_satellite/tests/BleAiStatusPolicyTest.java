import com.flipperai.satellite.BleAiStatusPolicy;
import com.flipperai.satellite.BleAiStatusPolicy.Transition;

public final class BleAiStatusPolicyTest {
  static void expect(String status, Transition expected) {
    if(BleAiStatusPolicy.classify(status)!=expected)
      throw new AssertionError("Wrong AI1 transition for: "+status);
  }
  public static void main(String[] args) {
    expect("AI BLE ready (custom)",Transition.READY);
    expect("Stock Momentum RPC indications enabled",Transition.INVALIDATE);
    expect("Connecting to selected Flipper",Transition.INVALIDATE);
    expect("Reconnecting to selected Flipper",Transition.INVALIDATE);
    expect("Disconnected",Transition.INVALIDATE);
    expect("BLE disconnected",Transition.INVALIDATE);
    expect("AI GATT write timed out",Transition.INVALIDATE);
    expect("Flipper Bluetooth not connected",Transition.NONE);
    expect("AI NOT READY",Transition.NONE);
    expect(null,Transition.NONE);
    System.out.println("BleAiStatusPolicy tests passed");
  }
}
