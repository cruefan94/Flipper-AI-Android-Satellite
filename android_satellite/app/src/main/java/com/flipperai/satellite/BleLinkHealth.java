package com.flipperai.satellite;

/**
 * Thread-safe, process-local BLE reliability counters. Never records device
 * identifiers, prompts, access tokens or characteristic payloads.
 *
 * Time comes from a monotonic elapsed-time clock (never wall clock).
 * Counters reset when Android destroys the service process. Only a physical
 * device run can establish real stability; synthetic tests check bookkeeping.
 */
public final class BleLinkHealth {
  public enum Channel { NONE, STOCK_RPC, CUSTOM_AI1 }
  public static final class Snapshot {
    public final Channel channel;
    public final boolean connected;
    public final int verifiedSessions, unexpectedDrops, userStops, automaticRetries;
    public final long verifiedUptimeMs, currentSessionMs;

    private Snapshot(Channel channel,boolean connected,int verifiedSessions,
        int unexpectedDrops,int userStops,int automaticRetries,
        long verifiedUptimeMs,long currentSessionMs) {
      this.channel=channel;
      this.connected=connected;
      this.verifiedSessions=verifiedSessions;
      this.unexpectedDrops=unexpectedDrops;
      this.userStops=userStops;
      this.automaticRetries=automaticRetries;
      this.verifiedUptimeMs=verifiedUptimeMs;
      this.currentSessionMs=currentSessionMs;
    }
    public String report(){
      return "Flipper AI Bluetooth reliability (since service start)\n"
        +"Current link: "+(connected?channel.toString():"DISCONNECTED")+"\n"
        +"Verified sessions: "+verifiedSessions+"\n"
        +"Unexpected verified-link drops: "+unexpectedDrops+"\n"
        +"Explicit disconnects/switches: "+userStops+"\n"
        +"Automatic reconnect attempts: "+automaticRetries+"\n"
        +"Total verified-link uptime (seconds): "+verifiedUptimeMs/1000+"\n"
        +"Current verified-link uptime (seconds): "+currentSessionMs/1000+"\n"
        +"Only actual AI1 HELLO/ASK device testing can confirm full AI communication.\n"
        +"These counters reset when Android destroys the Bluetooth service.";
    }
  }

  private Channel channel=Channel.NONE;
  private long sessionStartMs=-1, accumulatedMs=0;
  private int sessions=0, drops=0, userStops=0, retries=0;

  /** Called only after a bonded GATT channel is genuinely ready. */
  public synchronized void verified(long nowMs,Channel confirmed) {
    if(confirmed==null || confirmed==Channel.NONE)
      throw new IllegalArgumentException("verified channel required");
    if(nowMs<0)throw new IllegalArgumentException("negative clock");
    if(channel!=Channel.NONE) {
      // Duplicate status callbacks must not create artificial new sessions.
      channel=confirmed;
      return;
    }
    channel=confirmed;
    sessionStartMs=nowMs;
    sessions++;
  }

  public synchronized void unexpectedDisconnect(long nowMs) {
    if(channel==Channel.NONE)return;
    accumulate(nowMs);
    channel=Channel.NONE;
    sessionStartMs=-1;
    drops++;
  }

  /** User pressed Disconnect or switched to another selected Flipper. */
  public synchronized void userDisconnect(long nowMs) {
    if(channel==Channel.NONE)return;
    accumulate(nowMs);
    channel=Channel.NONE;
    sessionStartMs=-1;
    userStops++;
  }

  public synchronized void recoveryAttempt() { retries++; }

  private void accumulate(long nowMs) {
    if(sessionStartMs>=0 && nowMs>=sessionStartMs)
      accumulatedMs+=nowMs-sessionStartMs;
  }

  public synchronized Snapshot snapshot(long nowMs) {
    long current=channel==Channel.NONE || sessionStartMs<0
      ?0:Math.max(0,nowMs-sessionStartMs);
    return new Snapshot(channel,channel!=Channel.NONE,sessions,drops,
      userStops,retries,accumulatedMs+current,current);
  }
}
