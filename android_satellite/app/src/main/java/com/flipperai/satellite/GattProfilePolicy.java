package com.flipperai.satellite;

/** Validate AI GATT capabilities; not proof of encrypted link. */
public final class GattProfilePolicy {
 private GattProfilePolicy() {}
 public static final int WRITE=0x08, NOTIFY=0x10;
 public static boolean valid(int rxProperties,int txProperties,boolean hasCccd) {
  return (rxProperties & WRITE)!=0 && (txProperties & NOTIFY)!=0 && hasCccd;
 }
}
