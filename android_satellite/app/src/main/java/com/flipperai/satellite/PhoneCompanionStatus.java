package com.flipperai.satellite;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;

/** Read-only Android phone state. Does not read private app data. */
public final class PhoneCompanionStatus {
  private PhoneCompanionStatus(){}
  public static String snapshot(Context context) {
    String battery="unknown";
    Intent b=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if(b!=null) {
      int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
      int scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
      if(level>=0 && scale>0)battery=(100*level/scale)+"%";
    }
    ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    String network="Disconnected";
    if(cm!=null) {
      NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());
      if(cap!=null) {
        String type=cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":
          cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Mobile data":
          cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"Ethernet":"Other";
        network=type+(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?" (internet validated)":" (internet unverified)");
      }
    }
    BluetoothManager bm=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
    String bluetooth=adapter==null?"Unavailable":adapter.isEnabled()?"Enabled":"Disabled";
    return "Phone battery: "+battery+"\nNetwork: "+network+"\nBluetooth: "+bluetooth;
  }
}
