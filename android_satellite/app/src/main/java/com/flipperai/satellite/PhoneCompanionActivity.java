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
    ScrollView scroller=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
    scroller.addView(root);setContentView(scroller);
    TextView heading=new TextView(this);heading.setText("PHONE COMPANION");heading.setTextSize(22);root.addView(heading);
    TextView explanation=new TextView(this);
    explanation.setText("Read-only Android status. Flipper BLE pairing and authenticated AI connection remain managed by the existing Connection screen. No automatic device actions or private notifications are accessed.");
    root.addView(explanation);
    status=new TextView(this);status.setTextSize(17);status.setPadding(0,24,0,24);root.addView(status);
    button(root,"REFRESH PHONE STATUS",this::update);
    button(root,"ANDROID BLUETOOTH SETTINGS",()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    button(root,"ANDROID WI-FI SETTINGS",()->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    button(root,"ANDROID NOTIFICATION ACCESS SETTINGS",()->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
    button(root,"BACK TO SATELLITE",this::finish);
    update();
  }
  private void button(LinearLayout root,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());root.addView(b);}
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
