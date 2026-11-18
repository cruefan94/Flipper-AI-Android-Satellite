package com.flipperai.satellite;

/** Tracks one outstanding Android GATT write.
 * Pure Java so stale timer and completion behavior can be tested without a phone.
 * Tokens are never reused after a reset within normal app lifetime.
 */
public final class BleWriteGuard {
  private int epoch=0;
  private int pending=0;
  public synchronized boolean isOutstanding(){return pending!=0;}
  public synchronized int begin(){
    if(pending!=0)throw new IllegalStateException("GATT write already pending");
    epoch++;
    if(epoch==0)epoch++;
    pending=epoch;
    return pending;
  }
  public synchronized boolean isPending(int token){return token!=0 && pending==token;}
  public synchronized boolean complete(){
    if(pending==0)return false;
    pending=0;return true;
  }
  public synchronized void reset(){
    pending=0;
    epoch++;
    if(epoch==0)epoch++;
  }
}
