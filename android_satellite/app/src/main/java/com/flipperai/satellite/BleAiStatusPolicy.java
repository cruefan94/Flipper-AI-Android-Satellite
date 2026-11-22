package com.flipperai.satellite;

/** Maps BLE status notifications to AI1 worker lifecycle transitions.
 * Stock Momentum RPC is not a custom AI1 session.
 */
public final class BleAiStatusPolicy {
  public enum Transition { NONE, INVALIDATE, READY }
  public enum Channel { NONE, CUSTOM_AI1, STOCK_RPC }
  /** Only a complete ready label can mark a verified channel. */
  public static Channel verifiedChannel(String message) {
    if(message==null)return Channel.NONE;
    if(statusIs(message,"AI BLE ready"))return Channel.CUSTOM_AI1;
    if(statusIs(message,"Stock Momentum RPC indications enabled"))return Channel.STOCK_RPC;
    return Channel.NONE;
  }
  private BleAiStatusPolicy() {}

  /** Require a complete status label, not an arbitrary startsWith collision.
   * Parenthesized details and colon-separated diagnostics remain supported. */
  private static boolean statusIs(String message,String label) {
    // Reject embedded control characters even after a valid label. A multiline
    // diagnostic must never manufacture AI1 readiness or RPC verification.
    for(int i=0;i<message.length();i++) {
      char ch=message.charAt(i);
      if(ch<0x20 || ch==0x7f)return false;
    }
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
