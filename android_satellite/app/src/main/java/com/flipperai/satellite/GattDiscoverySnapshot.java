package com.flipperai.satellite;

import java.util.Collection;
import java.util.UUID;

/** Redacted evidence of Android's last GATT service-discovery callback.
 * Captures service categories and count only; never stores device identifiers,
 * raw UUID lists, MAC addresses, prompts, or BLE keys.
 *
 * A missing service can be caused by Android's stale cached GATT database,
 * or by missing Flipper-side registration. This snapshot cannot distinguish
 * those causes and does not prove an authenticated AI1 link.
 */
public final class GattDiscoverySnapshot {
  public enum Kind { NOT_CHECKED, DISCOVERY_FAILED, AI_PRESENT, MOMENTUM_RPC_ONLY, NO_EXPECTED_SERVICE }
  public final Kind kind;
  public final int serviceCount;
  public final boolean aiPresent;
  public final boolean momentumRpcPresent;

  private GattDiscoverySnapshot(Kind kind,int count,boolean ai,boolean rpc) {
    this.kind=kind;
    this.serviceCount=count;
    this.aiPresent=ai;
    this.momentumRpcPresent=rpc;
  }

  public static GattDiscoverySnapshot notChecked() {
    return new GattDiscoverySnapshot(Kind.NOT_CHECKED,0,false,false);
  }
  public static GattDiscoverySnapshot failed() {
    return new GattDiscoverySnapshot(Kind.DISCOVERY_FAILED,0,false,false);
  }
  public static GattDiscoverySnapshot observed(
      Collection<UUID> services,UUID aiUuid,UUID rpcUuid) {
    if(services==null || aiUuid==null || rpcUuid==null)
      throw new IllegalArgumentException("Missing GATT discovery input");
    boolean ai=false,rpc=false;
    int count=0;
    for(UUID uuid:services) {
      if(uuid==null)continue;
      count++;
      if(uuid.equals(aiUuid))ai=true;
      if(uuid.equals(rpcUuid))rpc=true;
    }
    Kind kind=ai ? Kind.AI_PRESENT :
      rpc ? Kind.MOMENTUM_RPC_ONLY : Kind.NO_EXPECTED_SERVICE;
    return new GattDiscoverySnapshot(kind,count,ai,rpc);
  }
  /** Does not indicate AI HELLO/ASK or pairing success. */
  public String report() {
    switch(kind) {
      case NOT_CHECKED: return "NOT CHECKED (no completed discovery)";
      case DISCOVERY_FAILED: return "DISCOVERY FAILED (no verified service inventory)";
      case AI_PRESENT:
        return "AI service PRESENT; Momentum RPC "+(momentumRpcPresent?"PRESENT":"ABSENT")
          +"; "+serviceCount+" services; AI1 handshake UNVERIFIED";
      case MOMENTUM_RPC_ONLY:
        return "AI service ABSENT; Momentum RPC PRESENT; "+serviceCount
          +" services (Android GATT cache or firmware service registration may be responsible)";
      default:
        return "AI service ABSENT; Momentum RPC ABSENT; "+serviceCount
          +" services (wrong device, cache, or service registration)";
    }
  }
}
