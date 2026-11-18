package com.flipperai.satellite;

/** Maps BLE status notifications to AI1 worker lifecycle transitions.
 * Stock Momentum RPC is not a custom AI1 session.
 */
public final class BleAiStatusPolicy {
  public enum Transition { NONE, INVALIDATE, READY }
  private BleAiStatusPolicy() {}
  public static Transition classify(String message) {
    if(message==null)return Transition.NONE;
    if(message.startsWith("Connecting to selected Flipper") ||
       message.startsWith("Reconnecting to selected Flipper") ||
       message.startsWith("Disconnected") ||
       message.startsWith("BLE disconnected") ||
       message.startsWith("AI GATT write timed out") ||
       message.startsWith("Stock Momentum RPC indications enabled"))
      return Transition.INVALIDATE;
    if(message.startsWith("AI BLE ready"))return Transition.READY;
    return Transition.NONE;
  }
}
