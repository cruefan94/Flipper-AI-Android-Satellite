package com.flipperai.satellite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.Objects;

/**
 * Single process-owned GATT owner. Started in the foreground only after an
 * explicit Connect action, so leaving a screen never destroys BluetoothGatt.
 * A live MainActivity may attach as the AI1 message consumer; while it is
 * absent, incoming AI frames are discarded, never replayed to a new session.
 */
public final class BleConnectionService extends Service implements BleSatellite.Events {
  public static final String ACTION_KEEP="com.flipperai.satellite.action.BLE_KEEP";
  public static final String ACTION_DISCONNECT="com.flipperai.satellite.action.BLE_DISCONNECT";
  private static final String CHANNEL="flipper_ble_link_v1";
  private static final int NOTICE_ID=1203;
  public final class LocalBinder extends Binder {
    public BleConnectionService service(){return BleConnectionService.this;}
  }
  private final IBinder binder=new LocalBinder();
  private BleSatellite transport;
  private volatile BleSatellite.Events currentListener;
  private volatile String lastStatus="Flipper Bluetooth not connected";
  private boolean foreground;
  private final BleLinkHealth linkHealth=new BleLinkHealth();

  @Override public void onCreate(){
    super.onCreate();
    transport=new BleSatellite(this,this);
    NotificationManager manager=getSystemService(NotificationManager.class);
    if(manager!=null && Build.VERSION.SDK_INT>=26)
      manager.createNotificationChannel(new NotificationChannel(
        CHANNEL,"Flipper AI Bluetooth",NotificationManager.IMPORTANCE_LOW));
  }
  @Override public IBinder onBind(Intent intent){return binder;}
  public BleSatellite transport(){return transport;}
  /** A redacted report for user-led physical BLE stability testing. */
  public String linkHealthReport(){
    return linkHealth.snapshot(SystemClock.elapsedRealtime()).report();
  }
  /** Track intentional device switches separately from unplanned link drops. */
  public void connectFromUser(BluetoothDevice chosen) {
    if(chosen==null || transport==null)return;
    linkHealth.userDisconnect(SystemClock.elapsedRealtime());
    transport.connect(chosen);
  }
  public void attach(BleSatellite.Events listener){
    currentListener=Objects.requireNonNull(listener);
    listener.status(lastStatus);
  }
  public void detach(BleSatellite.Events listener){
    if(currentListener==listener)currentListener=null;
  }
  private Notification notice(){
    Intent back=new Intent(this,MainActivity.class)
      .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent open=PendingIntent.getActivity(this,101,back,
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Intent stop=new Intent(this,BleConnectionService.class).setAction(ACTION_DISCONNECT);
    PendingIntent disconnect=PendingIntent.getService(this,102,stop,
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("Flipper AI Bluetooth")
      .setContentText(lastStatus)
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .addAction(new Notification.Action.Builder(
        android.R.drawable.ic_menu_close_clear_cancel,"Disconnect",disconnect).build())
      .build();
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    String action=intent==null?"":intent.getAction();
    if(ACTION_DISCONNECT.equals(action)){
      stopFromUser();
      return START_NOT_STICKY;
    }
    if(ACTION_KEEP.equals(action)){
      try{
        // Must be started by a foreground user gesture, not automatically on boot.
        startForeground(NOTICE_ID,notice());
        foreground=true;
      }catch(SecurityException | IllegalStateException denied){
        foreground=false;
        status("Android did not allow background BLE retention; keep the app open");
        stopSelf(startId);
      }
    }
    return START_NOT_STICKY; // Never resurrect a disconnected session after process death.
  }
  public void stopFromUser(){
    linkHealth.userDisconnect(SystemClock.elapsedRealtime());
    if(transport!=null)transport.manualDisconnect();
    if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}
    stopSelf();
  }
  @Override public void onDestroy(){
    linkHealth.userDisconnect(SystemClock.elapsedRealtime());
    foreground=false;
    currentListener=null;
    if(transport!=null)transport.close();
    super.onDestroy();
  }
  @Override public void status(String message){
    lastStatus=message;
    long now=SystemClock.elapsedRealtime();
    if(message.startsWith("Reconnecting to selected Flipper"))
      linkHealth.recoveryAttempt();
    if(message.startsWith("Disconnected") || message.startsWith("BLE disconnected"))
      linkHealth.unexpectedDisconnect(now);
    // Only genuinely bonded subscribed channels become verified sessions.
    if(message.startsWith("AI BLE ready") ||
        message.startsWith("Stock Momentum RPC indications enabled")){
      try {
        BluetoothDevice confirmed=transport.confirmedBondedDevice();
        if(confirmed!=null){
          linkHealth.verified(now,message.startsWith("AI BLE ready")
            ?BleLinkHealth.Channel.CUSTOM_AI1:BleLinkHealth.Channel.STOCK_RPC);
          RecentFlipperStore.remember(this,confirmed);
        }
      }catch(SecurityException | IllegalStateException ignored){}
    }
    BleSatellite.Events listener=currentListener;
    if(listener!=null)listener.status(message);
    if(foreground){
      NotificationManager manager=getSystemService(NotificationManager.class);
      if(manager!=null && (message.startsWith("AI BLE ready")
          || message.startsWith("Stock Momentum RPC indications enabled")
          || message.startsWith("Disconnected")
          || message.startsWith("Reconnecting")))
        manager.notify(NOTICE_ID,notice());
    }
  }
  @Override public void device(BluetoothDevice device,String advertisedName){
    BleSatellite.Events listener=currentListener;
    if(listener!=null)listener.device(device,advertisedName);
  }
  @Override public void received(String frame){
    BleSatellite.Events listener=currentListener;
    if(listener!=null)listener.received(frame);
    // No listener: do NOT process commands, persist prompts, or replay stale AI1.
  }
}
