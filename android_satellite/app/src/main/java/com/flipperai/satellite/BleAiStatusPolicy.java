package com.flipperai.satellite;

/** Maps BLE status notifications to AI1 worker lifecycle transitions.
 * Stock Momentum RPC is not a custom AI1 session.
 */
public final class BleAiStatusPolicy {
  public enum Transition { NONE, INVALIDATE, READY }
  private BleAiStatusPolicy() {}

  /** Require a complete status label, not an arbitrary startsWith collision.
   * Parenthesized details and colon-separated diagnostics remain supported. */
  private static boolean statusIs(String message,String label) {
    if(message.equals(label))return true;
    if(!message.startsWith(label) || message.length()<=label.length())return false;
    char next=message.charAt(label.length());
    return next==' ' || next==':' || next=='(';
  }

  public static Transition classify(String message) {
    if(message==null)return Transition.NONE;
    if(statusIs(message,"Connecting to selected Flipper") ||
       statusIs(message,"Reconnecting to selected Flipper") ||
       statusIs(message,"Disconnected") ||
       statusIs(message,"BLE disconnected") ||
       statusIs(message,"AI GATT write timed out") ||
       statusIs(message,"Stock Momentum RPC indications enabled"))
      return Transition.INVALIDATE;
    if(statusIs(message,"AI BLE ready"))return Transition.READY;
    return Transition.NONE;
  }
}
