package com.flipperai.satellite;

/** Last *completed* Android GATT discovery, without device IDs or UUID inventories.
 * A cached phone discovery is not proof of the firmware's actual GATT registration.
 * Retain the last result through a link drop for troubleshooting, but label it stale.
 */
public final class GattDiscoveryEvidence {
  private boolean observed,connected,aiSeen,rpcSeen,successful;
  private int status,serviceCount;

  /** Explicitly selecting a device clears evidence that might belong to another Flipper. */
  public synchronized void clear() {
    observed=false;connected=false;aiSeen=false;rpcSeen=false;
    successful=false;status=0;serviceCount=0;
  }
  /** Reconnect should not erase the last useful completed callback. */
  public synchronized void beginAttempt() { connected=false; }
  public synchronized void closeLink() { connected=false; }

  public synchronized void succeeded(boolean ai,boolean rpc,int count) {
    observed=true;connected=true;successful=true;status=0;
    aiSeen=ai;rpcSeen=rpc;serviceCount=Math.max(0,count);
  }
  public synchronized void failed(int gattStatus) {
    observed=true;connected=true;successful=false;status=gattStatus;
    aiSeen=false;rpcSeen=false;serviceCount=0;
  }

  public synchronized String report() {
    if(!observed)
      return "Android GATT discovery: NOT COMPLETED (no service result yet).";
    String freshness=connected?"CURRENT LINK":"LAST RESULT (link dropped/reconnecting)";
    if(!successful)
      return "Android GATT discovery: FAILED (status "+status+"); "+freshness+
        ". Service presence not established.";
    return "Android GATT discovery: COMPLETED; "+freshness+
      "; AI service="+(aiSeen?"FOUND":"NOT FOUND")+
      "; Momentum RPC="+(rpcSeen?"FOUND":"NOT FOUND")+
      "; service count="+serviceCount+".";
  }

  public synchronized String nextAction() {
    if(!observed)
      return "Select the paired Flipper, connect, and let Android finish GATT service discovery. Pairing alone does not establish an AI1 service.";
    if(!successful)
      return "Android GATT discovery failed (status "+status+"). Check Bluetooth and Nearby Devices permission, then retry. This does not prove AI firmware registration failed.";
    if(aiSeen)
      return "Android found the custom AI service. Next verify RX/TX characteristics, notifications, MTU, and the AI1 HELLO/ACK. Discovery alone does not prove AI chat works.";
    if(rpcSeen)
      return "Android discovered Momentum RPC but not AI GATT. Compare with Flipper AI's 'AI GATT registered' diagnostic after approved firmware installation. If Flipper says Yes, reconnect to refresh Android discovery. If No, inspect firmware service startup. Do not automatically unpair.";
    return "Android discovered neither the expected AI nor Momentum RPC profile. Confirm the selected device and firmware, then retry discovery. No radio or pairing changes were performed.";
  }
}
