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
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

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
  // The Android Service—not MainActivity—owns the entire AI1 handshake and
  // request worker. This allows Flipper ASK while users view another screen.
  private final ThreadPoolExecutor inferenceWorker=new ThreadPoolExecutor(
    1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(8));
  private BleAiRequestProcessor aiRequests;
  private EmbeddedLlmEngine embedded;
  private LocalModelEngine loopback;
  private LocalModelEngine llama;
  private OfflineProviderSelector offline;
  private WorkerCloudEngine cloud;
  private volatile String loadedModelRevision="";


  @Override public void onCreate(){
    super.onCreate();
    transport=new BleSatellite(this,this);
    embedded=new EmbeddedLlmEngine(getCacheDir().getAbsolutePath());
    loopback=new LocalModelEngine();
    llama=new LocalModelEngine();
    cloud=new WorkerCloudEngine();
    boolean useLlama=LlamaCppSettings.enabled(this);
    offline=new OfflineProviderSelector(
      new EmbeddedOrLocalEngine(embedded,loopback),llama,useLlama);
    InferenceRouter router=new InferenceRouter(
      new OfflineModelOrDiagnostics(offline,new OfflineDiagnosticEngine()),cloud);
    aiRequests=new BleAiRequestProcessor(
      inferenceWorker,new SatelliteRequestHandler(router),
      new BleAiRequestProcessor.Link(){
        @Override public boolean isReady(){return transport!=null && transport.isReady();}
        @Override public int maxFrameBytes(){
          return transport==null?0:transport.maxFrameBytes();
        }
        @Override public boolean sendFrame(String response){
          return transport!=null && transport.send(response);
        }
      },
      ()->{
        if(cloud!=null)cloud.revokeActive();
        if(loopback!=null)loopback.revokeActive();
        if(llama!=null)llama.revokeActive();
      });
    refreshOfflineModels();
    NotificationManager manager=getSystemService(NotificationManager.class);
    if(manager!=null && Build.VERSION.SDK_INT>=26)
      manager.createNotificationChannel(new NotificationChannel(
        CHANNEL,"Flipper AI Bluetooth",NotificationManager.IMPORTANCE_LOW));
  }
  /**
   * Refresh a newly imported model without reloading its native inference
   * engine on the UI thread. Model selection never turns cloud on.
   */
  public void refreshOfflineModels(){
    BleAiRequestProcessor processor=aiRequests;
    if(processor==null)return;
    processor.submitModelRefresh(()->{
      try{
        File file=EmbeddedModelStore.modelFile(this);
        String revision=file.isFile() && file.length()>0
          ?file.length()+":"+file.lastModified():"not-installed";
        if(!revision.equals(loadedModelRevision)){
          if(EmbeddedModelStore.installed(this))
            embedded.configure(file.getAbsolutePath());
          else embedded.clear();
          loadedModelRevision=revision;
        }
        boolean preferLlama=LlamaCppSettings.enabled(this);
        if(preferLlama)LlamaCppSettings.configure(llama);
        else llama.clear();
        offline.selectLlama(preferLlama);
      }catch(Exception invalid){
        status("Flipper offline model unavailable. Check MODELS; local diagnostics remain.");
      }
    });
  }

  /** The normal Bluetooth connection remains alive across screen changes;
   * cloud authorization does not. Offline stays the safe background default. */
  public void leaveAiSettingsScreen(){
    if(aiRequests!=null)aiRequests.setPolicy(InferenceRouter.Mode.OFFLINE,false);
    if(cloud!=null)cloud.clear();
  }

  /** Only call from a visible UI that collected explicit per-session consent. */
  public void setVisibleAiPolicy(InferenceRouter.Mode mode,boolean consent){
    if(aiRequests==null || mode==null)return;
    if(!consent || mode==InferenceRouter.Mode.OFFLINE){
      aiRequests.setPolicy(mode,false);
      if(cloud!=null)cloud.clear();
      return;
    }
    try{
      CloudCredentialStore.Credentials credentials=CloudCredentialStore.load(this);
      if(credentials==null)throw new IllegalStateException("Worker settings missing");
      cloud.configure(credentials.endpoint,credentials.token);
      aiRequests.setPolicy(mode,true);
    }catch(Exception missingOrInvalid){
      aiRequests.setPolicy(InferenceRouter.Mode.OFFLINE,false);
      cloud.clear();
      status("Flipper cloud AI is disabled. Configure and authorize the Worker first.");
    }
  }

  /** Explicitly selected localhost alternative; never changes cloud consent. */
  public void configureLoopback(String endpoint,String modelName){
    if(aiRequests!=null)aiRequests.submitModelRefresh(()->{
      try{loopback.configure(endpoint,modelName);}
      catch(Exception invalid){status("Flipper localhost model configuration rejected.");}
    });
  }
  public void disableLoopback(){
    if(aiRequests!=null)aiRequests.submitModelRefresh(loopback::clear);
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
    if(aiRequests!=null)aiRequests.close();
    if(transport!=null)transport.close();
    // FutureTask cancellation does not immediately remove queued tasks.
    // Purge them before scheduling native model cleanup on the same worker.
    inferenceWorker.purge();
    // Close native LiteRT resources after any cancelled worker jobs return.
    try{
      inferenceWorker.execute(()->{
        if(embedded!=null)embedded.clear();
        if(cloud!=null)cloud.clear();
        if(loopback!=null)loopback.clear();
        if(llama!=null)llama.clear();
      });
    }catch(java.util.concurrent.RejectedExecutionException ignored){}
    inferenceWorker.shutdown();
    super.onDestroy();
  }
  @Override public void status(String message){
    lastStatus=message;
    // A GATT transition invalidates previous HELLO/request IDs and in-flight
    // inference even when no Activity has been attached for hours.
    if(aiRequests!=null){
      if(message.startsWith("Connecting to selected Flipper") ||
         message.startsWith("Reconnecting to selected Flipper") ||
         message.startsWith("Disconnected") ||
         message.startsWith("BLE disconnected") ||
         message.startsWith("AI GATT write timed out")){
        aiRequests.disconnected();
      }else if(message.startsWith("AI BLE ready")){
        aiRequests.resetLink();
      }
    }
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
    // Never delegate AI1 inference to a transient Activity. The dedicated
    // service worker processes frames for the lifetime of the BLE link.
    // Invalid/outdated GATT sessions cannot dispatch new requests.
    if(aiRequests==null || transport==null || !transport.isReady())return;
    if(!aiRequests.receive(frame))
      status("Flipper AI request dropped: Android AI worker is unavailable.");
  }
}
