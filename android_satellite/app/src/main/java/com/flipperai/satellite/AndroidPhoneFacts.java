package com.flipperai.satellite;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import java.time.ZonedDateTime;

/** Read-only Android state; no identifiers, GPS coordinates or background polling. */
public final class AndroidPhoneFacts {
  private AndroidPhoneFacts() {}

  public static PhoneFacts.Snapshot snapshot(Context context) {
    int batteryPercent=-1;
    boolean charging=false;
    try {
      Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      if (battery!=null) {
        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        if (level>=0 && scale>0) batteryPercent=Math.min(100,Math.max(0,(int)Math.round(level*100.0/scale)));
        int state=battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        charging=state==BatteryManager.BATTERY_STATUS_CHARGING
            || state==BatteryManager.BATTERY_STATUS_FULL;
      }
    } catch (SecurityException ignored) {
      // Unknown, rather than fabricating a battery reading.
    }

    PhoneFacts.Connection connection=PhoneFacts.Connection.UNKNOWN;
    boolean validated=false;
    try {
      ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (manager!=null) {
        Network network=manager.getActiveNetwork();
        if (network==null) connection=PhoneFacts.Connection.NONE;
        else {
          NetworkCapabilities capabilities=manager.getNetworkCapabilities(network);
          if (capabilities!=null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
              connection=PhoneFacts.Connection.WIFI;
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
              connection=PhoneFacts.Connection.CELLULAR;
            else connection=PhoneFacts.Connection.OTHER;
            validated=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
          }
        }
      }
    } catch (SecurityException ignored) {
      // Network state is optional; do not request location or other permissions.
    }
    return new PhoneFacts.Snapshot(ZonedDateTime.now(),batteryPercent,charging,connection,validated);
  }
}
