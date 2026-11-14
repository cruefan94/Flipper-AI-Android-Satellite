package com.flipperai.satellite;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Read-only phone status: no hidden permissions, radio hardware, or BLE session claims. */
public final class PhoneCompanionActivity extends Activity {
  private TextView status,bluetoothStatus;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable refresh=new Runnable(){
    @Override public void run(){
      if(!isFinishing()&&!isDestroyed()){
        update();
        handler.postDelayed(this,15000);
      }
    }
  };

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    LinearLayout root=SatelliteUi.scrollPage(this,"PHONE COMPANION",
      "Live, read-only information from this Android phone. Bluetooth pairing is managed on FLIPPER / BLE.");
    SatelliteUi.sectionTitle(this,root,"PHONE STATUS");
    status=SatelliteUi.text(this,"Checking phone...",16,SatelliteUi.WHITE,false);
    status.setPadding(SatelliteUi.dp(this,12),SatelliteUi.dp(this,14),
      SatelliteUi.dp(this,12),SatelliteUi.dp(this,14));
    status.setBackground(SatelliteUi.panel(this,false));
    root.addView(status);
    SatelliteUi.action(this,root,"REFRESH PHONE STATUS",this::update);

    SatelliteUi.sectionTitle(this,root,"BLUETOOTH");
    bluetoothStatus=SatelliteUi.text(this,"Checking Bluetooth...",15,SatelliteUi.WHITE,false);
    bluetoothStatus.setPadding(SatelliteUi.dp(this,12),SatelliteUi.dp(this,12),
      SatelliteUi.dp(this,12),SatelliteUi.dp(this,12));
    bluetoothStatus.setBackground(SatelliteUi.panel(this,false));
    root.addView(bluetoothStatus);
    SatelliteUi.sectionTitle(this,root,"FEATURE AVAILABILITY");
    TextView features=SatelliteUi.text(this,
      "AI chat: separate AI CHAT screen\n"+
      "Spoken answers: local Android text-to-speech\n"+
      "Microphone: on-device speech where supported, only after tapping TALK\n"+
      "Radio: public internet streams on RADIO LISTENING; individual feeds unverified\n"+
      "Flipper AI BLE: connection and hardware verification remain on FLIPPER / BLE\n"+
      "GitHub: public repository viewing only; account authorization not linked",
      14,SatelliteUi.MUTED,false);
    features.setLineSpacing(SatelliteUi.dp(this,5),1.0f);
    root.addView(features);

    SatelliteUi.sectionTitle(this,root,"ANDROID SETTINGS");
    SatelliteUi.action(this,root,"BLUETOOTH SETTINGS",()->
      startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    SatelliteUi.action(this,root,"WI-FI SETTINGS",()->
      startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    SatelliteUi.action(this,root,"NOTIFICATION ACCESS SETTINGS",()->
      startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
    SatelliteUi.backHome(this,root);
    update();
  }

  private void update(){
    try{
      PhoneFacts.Snapshot snapshot=AndroidPhoneFacts.snapshot(this);
      String battery=PhoneFacts.answer("phone battery",snapshot);
      String network=PhoneFacts.answer("network status",snapshot);
      if(status!=null)status.setText((battery==null?"Battery unavailable":battery)
        +"\n\n"+(network==null?"Network status unavailable":network));
    }catch(Exception error){
      if(status!=null)status.setText("Phone status unavailable. Try refreshing.");
    }
    try{
      BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
      BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
      String state=adapter==null?"Unavailable":adapter.isEnabled()?"Enabled":"Disabled";
      if(bluetoothStatus!=null)bluetoothStatus.setText("Phone Bluetooth: "+state+
        "\nFlipper AI session: check the dedicated FLIPPER / BLE screen");
    }catch(SecurityException error){
      if(bluetoothStatus!=null)bluetoothStatus.setText("Bluetooth permission required. Check the FLIPPER / BLE screen.");
    }
  }
  @Override protected void onResume(){
    super.onResume();
    handler.removeCallbacks(refresh);
    handler.post(refresh);
  }
  @Override protected void onPause(){
    handler.removeCallbacks(refresh);
    super.onPause();
  }
}
