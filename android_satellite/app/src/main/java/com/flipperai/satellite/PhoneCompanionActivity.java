package com.flipperai.satellite;

import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

/** Read-only phone status. No hidden Android permissions or BLE service changes. */
public final class PhoneCompanionActivity extends Activity {
  private TextView status;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable refresh=new Runnable(){@Override public void run(){if(!isFinishing()){update();handler.postDelayed(this,15000);}}};
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    LinearLayout root=SatelliteUi.scrollPage(this,"PHONE COMPANION",
      "Read-only Android status. Bluetooth pairing and the AI session are managed on the FLIPPER / BLE screen.");
    status=SatelliteUi.text(this,"",16,SatelliteUi.WHITE,false);status.setPadding(0,24,0,24);root.addView(status);
    button(root,"REFRESH PHONE STATUS",this::update);
    button(root,"ANDROID BLUETOOTH SETTINGS",()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    button(root,"ANDROID WI-FI SETTINGS",()->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    button(root,"ANDROID NOTIFICATION ACCESS SETTINGS",()->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
    SatelliteUi.backHome(this,root);
    update();
  }
  private void button(LinearLayout root,String label,Runnable action){SatelliteUi.action(this,root,label,action);}
  private void update(){
    StringBuilder sb=new StringBuilder();
    Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if(battery!=null){
      int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
      if(level>=0&&scale>0)sb.append("Phone battery: ").append(Math.round(level*100f/scale)).append("%\n");
      int plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
      sb.append("Charging: ").append(plugged!=0?"Yes":"No").append("\n");
    }else sb.append("Phone battery: unavailable\n");
    try{
      BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
      BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
      sb.append("Phone Bluetooth: ").append(adapter==null?"Unavailable":adapter.isEnabled()?"Enabled":"Disabled").append("\n");
      sb.append("Flipper BLE: check the main Connection screen (no shared live session here)\n");
    }catch(SecurityException e){sb.append("Bluetooth status: permission required\n");}
    ConnectivityManager connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    Network network=connectivity==null?null:connectivity.getActiveNetwork();
    NetworkCapabilities caps=network==null?null:connectivity.getNetworkCapabilities(network);
    if(caps==null)sb.append("Internet: disconnected or unavailable\n");
    else {
      sb.append("Internet: ").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"validated":"not validated").append("\n");
      sb.append("Transport: ").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Cellular":caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"Ethernet":"Other").append("\n");
    }
    sb.append("Voice: available through the Satellite Voice controls\n");
    sb.append("Radio: available through the Radio Scanner screen\n");
    sb.append("Flipper button control: not authenticated/verified\n");
    sb.append("GitHub authorization: not linked; optional\n");
    status.setText(sb.toString());
  }
  @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
  @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
