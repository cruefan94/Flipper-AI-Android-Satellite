package com.flipperai.satellite;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App-private, opt-in recent GATT connections. Store only a Bluetooth address
 * after a successful bonded AI1 or stock Momentum RPC subscription. Never
 * trust saved addresses alone: quick reconnect resolves the CURRENT Android
 * bonded-device list and re-verifies pairing before opening GATT.
 */
public final class RecentFlipperStore {
  private RecentFlipperStore(){}
  private static final String FILE="recent_verified_flippers_v1";
  private static final String LIST="recent_three";
  public static final class Record {
    public final String address,name;
    Record(String address,String name){this.address=address;this.name=name;}
  }
  private static List<Record> read(Context context) {
    String encoded=context.getApplicationContext()
      .getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(LIST,"");
    List<Record> result=new ArrayList<>();
    for(String line:encoded.split("\n")){
      int separator=line.indexOf('|');
      if(separator<=0)continue;
      String address=line.substring(0,separator);
      if(!RecentFlipperPolicy.validAddress(address))continue;
      if(result.size()>=RecentFlipperPolicy.MAX_RECENTS)break;
      result.add(new Record(RecentFlipperPolicy.canonicalAddress(address),
          RecentFlipperPolicy.displayName(line.substring(separator+1))));
    }
    return result;
  }
  public static synchronized List<Record> list(Context context) {
    return Collections.unmodifiableList(new ArrayList<>(read(context)));
  }
  public static synchronized void remember(Context context,BluetoothDevice device) {
    if(device==null || device.getBondState()!=BluetoothDevice.BOND_BONDED)return;
    final String address=RecentFlipperPolicy.canonicalAddress(device.getAddress());
    final String name=RecentFlipperPolicy.displayName(device.getName());
    List<Record> result=new ArrayList<>();
    result.add(new Record(address,name));
    for(Record old:read(context)){
      if(!address.equals(old.address) && result.size()<RecentFlipperPolicy.MAX_RECENTS)
        result.add(old);
    }
    StringBuilder encoded=new StringBuilder();
    for(Record item:result){
      if(encoded.length()>0)encoded.append('\n');
      encoded.append(item.address).append('|').append(item.name);
    }
    context.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE)
      .edit().putString(LIST,encoded.toString()).apply();
  }
  public static synchronized void forgetAll(Context context) {
    context.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE)
      .edit().remove(LIST).apply();
  }
}
