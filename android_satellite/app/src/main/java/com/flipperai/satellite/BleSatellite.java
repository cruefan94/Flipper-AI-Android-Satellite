package com.flipperai.satellite;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;

/** Android BLE central for a FUTURE dedicated Momentum AI GATT profile.
 * Standard Flipper Bluetooth RPC is not compatible with this profile.
 */
@SuppressLint("MissingPermission")
public final class BleSatellite implements SatelliteTransport {
  public static final UUID MOMENTUM_RPC_SERVICE=UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000");
  public static final UUID MOMENTUM_RPC_WRITE=UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000");
  public static final UUID MOMENTUM_RPC_FLOW=UUID.fromString("19ed82ae-ed21-4c9d-4145-228e63fe0000");
  public static final UUID MOMENTUM_RPC_NOTIFY=UUID.fromString("19ed82ae-ed21-4c9d-4145-228e61fe0000");
  public static final UUID SERVICE=UUID.fromString("c8c7ab10-6355-4ea8-a76a-0a4a64de0001");
  public static final UUID RX=UUID.fromString("c8c7ab10-6355-4ea8-a76a-0a4a64de0002");
  public static final UUID TX=UUID.fromString("c8c7ab10-6355-4ea8-a76a-0a4a64de0003");
  private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
  public interface Events {
    void status(String text);
    void device(BluetoothDevice device,String advertisedName);
    void received(String frame);
    // Separate from the future custom AI GATT channel. The stock RPC app must
    // have been explicitly launched and acknowledged before AI1 is eligible.
    default void stockRpcAiStarted(){}
    default void stockRpcAiFrame(String frame){}
    default void stockRpcAiClosed(String reason){}
  }
  private final Context context;
  private final Events events;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private final TransportState state=new TransportState();
  private final GattDiscoveryEvidence discoveryEvidence=new GattDiscoveryEvidence();
  private final ArrayDeque<byte[]> pending=new ArrayDeque<>();
  private BluetoothLeScanner scanner;
  private volatile BluetoothGatt gatt;
  private volatile BluetoothDevice lastDevice;
  private int scanGeneration;
  private int connectionGeneration;
  private boolean retry147Used;
  private boolean userDisconnected=true;
  private int recoveryAttempts=0;
  private int recoveryToken=0;
  // Bounded backoff avoids rapid GATT reconnect loops and battery drain.
  private static final long[] RECOVERY_DELAYS_MS={
    2000L,4000L,8000L,12000L,20000L,30000L,45000L,60000L
  };
  /* Read by UI and written from Android Bluetooth Binder callbacks. */
  private volatile boolean authorized,subscribed,mtuPending;
  private volatile boolean stockRpcDetected,stockRpcSubscribed;
  private volatile int rpcNotificationsReceived;
  private volatile int rpcFlowCredits=-1;
  private volatile boolean rpcPingPending=false;
  private volatile boolean stockRpcFlowSubscribed=false;
  private final StockMomentumRpcAi1Session rpcAiSession=new StockMomentumRpcAi1Session();
  private final ArrayDeque<byte[]> rpcQueue=new ArrayDeque<>();
  private int rpcOffset=0;
  private boolean rpcWritePending=false;
  private int rpcWriteToken=0;
  private static final int RPC_SAFE_ATT_CHUNK=20;
  private static final int RPC_MAX_QUEUE=8;
  private static final long RPC_WRITE_TIMEOUT_MS=6000L;
  private static final long RPC_LAUNCH_TIMEOUT_MS=12000L;
  // A BLE write callback proves only that GATT accepted the bytes. Require
  // Momentum's matching AppDataExchange RPC ACK or fail this AI session.
  private static final long RPC_REPLY_ACK_TIMEOUT_MS=12000L;
  private final StockMomentumRpcAi1Session.Events rpcEvents=
      new StockMomentumRpcAi1Session.Events() {
        @Override public void ready() {
          events.status("Flipper AI app launched over stock RPC; AI1 HELLO pending.");
          events.stockRpcAiStarted();
        }
        @Override public void received(String frame) {
          events.stockRpcAiFrame(frame);
        }
        @Override public void closed(String cause) {
          events.stockRpcAiClosed(cause);
          events.status("Flipper AI stock RPC session closed: "+cause);
        }
      };
  private final BleWriteGuard writeGuard=new BleWriteGuard();
  private static final int MAX_QUEUED=8;
  private static final long GATT_WRITE_TIMEOUT_MS=6000L;
  private static final long GATT_SETUP_TIMEOUT_MS=25000L;
  public BleSatellite(Context context,Events events){this.context=context;this.events=events;}
  private final ScanCallback scan=new ScanCallback(){
    @Override public void onScanResult(int type,ScanResult item){
      if(item.getDevice()!=null){
        ScanRecord record=item.getScanRecord();
        String advertised=record==null?null:record.getDeviceName();
        events.device(item.getDevice(),advertised);
      }
    }
    @Override public void onScanFailed(int code){events.status("BLE scan failed: "+code);}
  };
  public synchronized void startScan(){
    stopScan();
    try{
      BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
      BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
      if(adapter==null || !adapter.isEnabled()){events.status("Enable Bluetooth first");return;}
      scanner=adapter.getBluetoothLeScanner();
      if(scanner==null){events.status("BLE scanner unavailable");return;}
      scanner.startScan(scan);
      events.status("Scanning for Flipper");
      final int startedGeneration=scanGeneration;
      ui.postDelayed(()->{
        synchronized(BleSatellite.this){
          if(startedGeneration==scanGeneration)stopScan();
        }
      },12000);
    }catch(SecurityException | IllegalStateException failure){
      events.status("Cannot scan: Bluetooth permission or adapter unavailable");
      stopScan();
    }
  }
  public synchronized void stopScan(){
    scanGeneration++;
    BluetoothLeScanner old=scanner;
    scanner=null;
    if(old!=null){
      try{old.stopScan(scan);}
      catch(SecurityException | IllegalStateException ignored){
        /* Permission or adapter state may change while a scan is active. */
      }
    }
  }
  public synchronized void connect(BluetoothDevice device){
    // An empty UI selection must never tear down a healthy GATT session or
    // invalidate its pending callbacks and recovery state.
    if(device==null){events.status("Select a Flipper before connecting");return;}
    // A new explicitly selected connection must not reuse another device's evidence.
    discoveryEvidence.clear();
    recoveryToken++;
    recoveryAttempts=0;
    userDisconnected=false;
    retry147Used=false;
    openConnection(device);
  }
  /** Explicit user stop: never reconnect after this or after Activity shutdown. */
  public synchronized void manualDisconnect(){
    userDisconnected=true;
    recoveryToken++;
    recoveryAttempts=0;
    disconnect();
  }
  private synchronized void scheduleRecovery(BluetoothDevice failedDevice,int gattStatus){
    if(userDisconnected || failedDevice==null || failedDevice!=lastDevice)return;
    final int token=++recoveryToken;
    // Retry indefinitely while the user-selected device remains bonded and the
    // foreground service is alive. Saturate the backoff at 60 seconds rather
    // than silently abandoning an out-of-range Flipper after eight attempts.
    // Manual disconnect and a different selection invalidate the retry token.
    recoveryAttempts=Math.min(recoveryAttempts+1,RECOVERY_DELAYS_MS.length);
    final int stage=recoveryAttempts;
    final long delay=RECOVERY_DELAYS_MS[stage-1];
    events.status("BLE disconnected (GATT "+gattStatus+"); reconnecting in "
      +(delay/1000)+"s (backoff "+stage+"/"+RECOVERY_DELAYS_MS.length+")");
    ui.postDelayed(()->{
      synchronized(BleSatellite.this){
        // A delayed retry must never replace a newer live GATT handle.
        if(userDisconnected || token!=recoveryToken || failedDevice!=lastDevice || gatt!=null)return;
        events.status("Reconnecting to selected Flipper (backoff "+stage+"/"+RECOVERY_DELAYS_MS.length+")");
        openConnection(failedDevice);
      }
    },delay);
  }
  private synchronized void openConnection(BluetoothDevice device){
    stopScan();disconnect();lastDevice=device;
    // Automatic retries retain the previous completed result, marked historical.
    discoveryEvidence.beginAttempt();
    if(device==null)return;
    events.status("Connecting to selected Flipper");
    final int bond;
    try{bond=device.getBondState();}
    catch(SecurityException | IllegalStateException permissionOrAdapterLost){
      events.status("Cannot inspect pairing: Bluetooth permission or adapter unavailable");
      return;
    }
    if(bond!=BluetoothDevice.BOND_BONDED){
      events.status("Pair the Flipper in Android first");return;
    }
    authorized=true;state.begin();
    try {
      BluetoothGatt link=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
      if(link==null){
        events.status("Cannot start Bluetooth GATT connection");
        disconnect();
        // Android can transiently decline a GATT handle without a callback.
        // Route this through the same bounded, cancelable recovery policy as
        // an asynchronous link failure; a manual stop invalidates the token.
        scheduleRecovery(device,-9);
        return;
      }
      gatt=link;
      /* Covers connect, service discovery, CCCD write and MTU negotiation.
       * Old callbacks cannot tear down a later connection. */
      ui.postDelayed(()->expireSetup(link),GATT_SETUP_TIMEOUT_MS);
    } catch(SecurityException | IllegalStateException failure){
      events.status("Cannot start Bluetooth GATT connection; check permission and Bluetooth");
      disconnect();
    }
  }
  private synchronized void expireSetup(BluetoothGatt link){
    if(link!=gatt || isReady() || (stockRpcDetected && stockRpcSubscribed))return;
    events.status("AI GATT connection setup timed out");
    final BluetoothDevice timedOutDevice=lastDevice;
    disconnect();
    scheduleRecovery(timedOutDevice,-1);
  }
  public synchronized void reconnect(){if(lastDevice!=null)connect(lastDevice);}
  /** Return a device only after actual authenticated AI or stock-RPC BLE readiness. */
  public synchronized BluetoothDevice confirmedBondedDevice(){
    if(gatt==null || !approved() || !(isReady() || isStockRpcSubscribed()))return null;
    return lastDevice;
  }
  public synchronized boolean hasVerifiedLink(){return confirmedBondedDevice()!=null;}
  /** Pair the device explicitly chosen from scan results, without connecting first. */
  public synchronized void pair(BluetoothDevice device){
    if(device==null){events.status("Select a scanned Flipper before pairing");return;}
    // Do not replace the trusted device identity beneath an active GATT session.
    // A different device must be selected through connect(), which invalidates
    // old discovery evidence, retries and callbacks before opening its link.
    if(gatt!=null && device!=lastDevice){
      events.status("Disconnect before pairing a different Flipper");
      return;
    }
    lastDevice=device;
    final int bond;
    try{bond=device.getBondState();}
    catch(SecurityException | IllegalStateException permissionOrAdapterLost){
      events.status("Cannot inspect pairing: Bluetooth permission or adapter unavailable");
      return;
    }
    if(bond==BluetoothDevice.BOND_BONDED){
      events.status("Selected device is already paired");return;
    }
    if(bond==BluetoothDevice.BOND_BONDING){
      events.status("Pairing is already in progress");return;
    }
    try{
      if(!device.createBond())events.status("Unable to start Bluetooth pairing");
      else events.status("Requested Android Bluetooth pairing; confirm on both devices");
    }catch(SecurityException | IllegalStateException permissionOrAdapterLost){
      events.status("Cannot start pairing: Bluetooth permission or adapter unavailable");
    }
  }
  public void pair(){pair(lastDevice);}
  private synchronized void recoverTemporaryFailure(int status){
    BluetoothDevice failedDevice=lastDevice;
    disconnect();
    scheduleRecovery(failedDevice,status);
  }
  private boolean approved(){
    if(!authorized || lastDevice==null)return false;
    try{return BondGate.canUseAiChannel(lastDevice.getBondState(),authorized);}
    catch(SecurityException | IllegalStateException permissionOrAdapterLost){
      return false; // Fail closed if trusted Android pairing state is unreadable.
    }
  }
  public synchronized void disconnect(){
    discoveryEvidence.closeLink();
    connectionGeneration++;
    state.disconnected();authorized=false;subscribed=false;mtuPending=false;stockRpcDetected=false;stockRpcSubscribed=false;stockRpcFlowSubscribed=false;rpcNotificationsReceived=0;rpcFlowCredits=-1;rpcPingPending=false;writeGuard.reset();
    boolean hadRpcAi=rpcAiSession.state()!=StockMomentumRpcAi1Session.State.IDLE;
    rpcAiSession.reset();
    rpcQueue.clear();rpcOffset=0;rpcWritePending=false;rpcWriteToken++;
    pending.clear();BluetoothGatt old=gatt;gatt=null;
    if(hadRpcAi)events.stockRpcAiClosed("Bluetooth link reset");
    events.status("Disconnected (local)");
    if(old!=null){
      /* Permission may have been revoked while the connection was active.
       * Always try to release the GATT handle even if disconnect throws. */
      try{old.disconnect();}
      catch(SecurityException | IllegalStateException ignored){
        events.status("Bluetooth permission or connection lost during disconnect");
      }finally{
        try{old.close();}
        catch(SecurityException | IllegalStateException ignored){
          events.status("Bluetooth handle could not be closed normally");
        }
      }
    }
  }
  /** Safe to include in support reports: no address, bond ID, token or prompt. */
  public synchronized String gattDiscoveryReport(){return discoveryEvidence.report();}
  public synchronized String gattDiscoveryNextStep(){return discoveryEvidence.nextAction();}
  public int maxFrameBytes(){return state.payloadLimit();}
  @Override public Kind kind(){return Kind.BLE;}
  public synchronized boolean hasStockRpc(){return stockRpcDetected && gatt!=null && approved();}
  public synchronized boolean isStockRpcSubscribed(){return hasStockRpc() && stockRpcSubscribed;}
  public synchronized int stockRpcNotificationCount(){return rpcNotificationsReceived;}
  public synchronized int stockRpcFlowCredits(){return rpcFlowCredits;}
  public synchronized boolean sendStockRpcPing(){
    if(rpcAiSession.state()!=StockMomentumRpcAi1Session.State.IDLE || rpcWritePending ||
       !rpcQueue.isEmpty()){events.status("RPC diagnostic ping is unavailable during a Phase 2 AI session.");return false;}
    if(!isStockRpcSubscribed() || rpcFlowCredits<5 || rpcPingPending){events.status("RPC ping unavailable: connect and wait for flow-control value.");return false;}
    BluetoothGattService service=gatt.getService(MOMENTUM_RPC_SERVICE);
    BluetoothGattCharacteristic ch=service==null?null:service.getCharacteristic(MOMENTUM_RPC_WRITE);
    if(ch==null){events.status("RPC write characteristic missing.");return false;}
    byte[] payload=new byte[]{4,8,1,42,0}; // length-delimited PB.Main: command_id=1, system_ping_request={}
    boolean started=false;
    try{
      if(Build.VERSION.SDK_INT>=33)started=gatt.writeCharacteristic(ch,payload,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
      else {ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);ch.setValue(payload);started=gatt.writeCharacteristic(ch);}
    }catch(SecurityException | IllegalStateException ignored){}
    if(started){rpcPingPending=true;rpcFlowCredits-=payload.length;events.status("Momentum RPC ping sent; awaiting validated response.");}
    else events.status("Momentum RPC ping write could not start.");
    return started;
  }


  /** Transport readiness is not AI1 HELLO_ACK and is not a model readiness claim. */
  public synchronized boolean isStockRpcAiReady(){
    return isStockRpcSubscribed() && stockRpcFlowSubscribed &&
      rpcAiSession.isReady() && approved();
  }

  /** Called only by an explicit Android button; does not alter Momentum OS. */
  public synchronized boolean startStockRpcAi(){
    if(!isStockRpcSubscribed() || !stockRpcFlowSubscribed || gatt==null ||
       rpcFlowCredits<=0 || rpcPingPending ||
       rpcAiSession.state()!=StockMomentumRpcAi1Session.State.IDLE){
      events.status("Connect a bonded Flipper and wait for stock RPC flow control before launching AI.");
      return false;
    }
    final byte[] start=rpcAiSession.begin();
    final BluetoothGatt link=gatt;
    if(!queueStockRpc(start)){
      // A partial AppStart may already have reached the radio. Never leave
      // its remaining queued bytes available to a later RPC session.
      events.status("Cannot send Flipper AI AppStart; resetting Bluetooth link.");
      recoverTemporaryFailure(-20);
      return false;
    }
    final int generation=connectionGeneration;
    ui.postDelayed(()->{
      synchronized(BleSatellite.this){
        if(link!=gatt || generation!=connectionGeneration ||
           rpcAiSession.state()!=StockMomentumRpcAi1Session.State.STARTING)return;
        events.status("Flipper AI AppStart timed out without ACK and APP_STARTED; reconnecting.");
        recoverTemporaryFailure(-11);
      }
    },RPC_LAUNCH_TIMEOUT_MS);
    events.status("Starting Flipper AI v0.8 via stock Momentum RPC (explicit request).");
    return true;
  }

  /** Called only by the persistent Android Service's AI1 inference worker. */
  public synchronized boolean sendStockRpcAiFrame(String frame){
    if(!isStockRpcAiReady() || rpcQueue.size()>=RPC_MAX_QUEUE)return false;
    final byte[] packet;
    try{packet=rpcAiSession.sendAiResponse(frame);}
    catch(IllegalArgumentException | IllegalStateException rejected){return false;}
    if(!queueStockRpc(packet)){
      events.status("RPC AI response could not be queued; reset this BLE link.");
      recoverTemporaryFailure(-12);
      return false;
    }
    // A characteristic write callback is not an end-to-end RPC confirmation.
    // Keep the pending reply bounded even if all fragments were sent but the
    // Flipper never acknowledges processing them.
    final BluetoothGatt link=gatt;
    final int generation=connectionGeneration;
    final long commandId=rpcAiSession.pendingResponseId();
    ui.postDelayed(()->{
      synchronized(BleSatellite.this){
        if(link!=gatt || generation!=connectionGeneration ||
           !isStockRpcAiReady() || commandId==0 ||
           rpcAiSession.pendingResponseId()!=commandId)return;
        events.status("Flipper AI reply RPC ACK timed out; reconnecting safely.");
        recoverTemporaryFailure(-17);
      }
    },RPC_REPLY_ACK_TIMEOUT_MS);
    return true;
  }

  private synchronized boolean queueStockRpc(byte[] packet){
    if(!isStockRpcSubscribed() || gatt==null || packet==null ||
       packet.length==0 || packet.length>StockMomentumRpcCodec.MAX_RPC_MESSAGE+5 ||
       rpcQueue.size()>=RPC_MAX_QUEUE)return false;
    rpcQueue.addLast(packet);
    return drainStockRpc();
  }

  /** One write-with-response at a time. Use 20-byte fragments even at MTU 23.
   * Credits track the pinned Momentum GATT RX stream's free-byte allowance.
   */
  private synchronized boolean drainStockRpc(){
    if(rpcWritePending)return true;
    if(rpcQueue.isEmpty())return true;
    if(!isStockRpcSubscribed() || !stockRpcFlowSubscribed || gatt==null)return false;
    if(rpcFlowCredits<=0)return true; // Wait for authenticated flow notification.
    BluetoothGattService service=gatt.getService(MOMENTUM_RPC_SERVICE);
    BluetoothGattCharacteristic rx=service==null?null:service.getCharacteristic(MOMENTUM_RPC_WRITE);
    if(rx==null)return false;
    final byte[] frame=rpcQueue.peekFirst();
    if(frame==null || rpcOffset>=frame.length)return false;
    int count=Math.min(RPC_SAFE_ATT_CHUNK,Math.min(rpcFlowCredits,frame.length-rpcOffset));
    if(count<=0)return true;
    byte[] bytes=java.util.Arrays.copyOfRange(frame,rpcOffset,rpcOffset+count);
    final BluetoothGatt link=gatt;
    final int generation=connectionGeneration;
    final int token=++rpcWriteToken;
    rpcWritePending=true; // Reserve before submitting to handle immediate callbacks.
    boolean started=false;
    try{
      if(Build.VERSION.SDK_INT>=33)started=
        link.writeCharacteristic(rx,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==
        BluetoothStatusCodes.SUCCESS;
      else{
        rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        rx.setValue(bytes);
        started=link.writeCharacteristic(rx);
      }
    }catch(SecurityException | IllegalStateException gone){
      started=false;
    }
    if(!started){
      rpcWritePending=false;
      events.status("Stock RPC Bluetooth write failed to start.");
      return false;
    }
    rpcOffset+=count;
    rpcFlowCredits-=count;
    ui.postDelayed(()->{
      synchronized(BleSatellite.this){
        if(link!=gatt || generation!=connectionGeneration ||
           !rpcWritePending || token!=rpcWriteToken)return;
        events.status("Stock RPC write timed out; reconnecting.");
        recoverTemporaryFailure(-13);
      }
    },RPC_WRITE_TIMEOUT_MS);
    return true;
  }

  @Override public synchronized boolean isReady(){return subscribed && approved() && state.phase()==TransportState.Phase.READY;}
  @Override public boolean sendFrame(String data){return send(data);}
  @Override public void close(){stopScan();manualDisconnect();}
  public synchronized boolean send(String frame){
    if(frame==null || !isReady() || gatt==null){events.status("AI channel not ready");return false;}
    byte[] bytes=frame.getBytes(StandardCharsets.UTF_8);
    if(!state.canWrite(bytes.length)){events.status("AI packet exceeds negotiated MTU");return false;}
    if(pending.size()>=MAX_QUEUED){events.status("AI output queue full");return false;}
    pending.addLast(bytes);return drain();
  }
  private synchronized boolean drain(){
    if(writeGuard.isOutstanding())return true; // Another frame is in flight.
    if(!isReady() || pending.isEmpty() || gatt==null)return false;
    BluetoothGatt link=gatt;
    BluetoothGattService service=link.getService(SERVICE);
    BluetoothGattCharacteristic rx=service==null?null:service.getCharacteristic(RX);
    if(rx==null){
      events.status("AI GATT service unavailable");
      recoverTemporaryFailure(-7);
      return false;
    }
    byte[] bytes=pending.peekFirst();
    /* Reserve before submitting to avoid losing an unusually fast callback
     * that could arrive before the call to writeCharacteristic returns. */
    final int token=writeGuard.begin();
    boolean sent=false;
    try {
      if(Build.VERSION.SDK_INT>=33)
        sent=link.writeCharacteristic(rx,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
      else{
        rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        rx.setValue(bytes);sent=link.writeCharacteristic(rx);
      }
    } catch(SecurityException | IllegalStateException invalidLink) {
      /* Bluetooth permission may be revoked during an active GATT session,
       * or Android may close the link between status and submission. */
      events.status("AI GATT write could not start: Bluetooth permission or link lost");
    }
    if(!sent){
      writeGuard.complete();
      events.status("AI GATT write failed to start");
      recoverTemporaryFailure(-8);
      return false;
    }
    ui.postDelayed(()->expireWrite(link,token),GATT_WRITE_TIMEOUT_MS);
    return true;
  }
  private synchronized void expireWrite(BluetoothGatt link,int token){
    /* An old timeout can never disconnect a newly connected phone or a
     * later GATT write. The token and active GATT instance must both match. */
    if(link!=gatt || !writeGuard.isPending(token))return;
    events.status("AI GATT write timed out; resetting BLE connection");
    final BluetoothDevice failedDevice=lastDevice;
    disconnect();
    scheduleRecovery(failedDevice,-2);
  }
  private synchronized boolean transitionConnected(){
    try{
      state.connected();
      return true;
    }catch(IllegalStateException invalidTransition){
      return false;
    }
  }
  private synchronized boolean transitionSubscribing(){
    try{
      state.subscribing();
      return true;
    }catch(IllegalStateException invalidTransition){
      return false;
    }
  }
  private synchronized boolean transitionReady(){
    try{
      state.ready();
      return true;
    }catch(IllegalStateException invalidTransition){
      return false;
    }
  }
  private void deliver(byte[] bytes){
    try{events.received(FrameCodec.decode(bytes));}
    catch(IllegalArgumentException invalid){events.status("Rejected malformed AI BLE frame");}
  }
  private final BluetoothGattCallback callback=new BluetoothGattCallback(){
    @Override public void onConnectionStateChange(BluetoothGatt link,int code,int status){
      synchronized(BleSatellite.this) {
      /* connect() holds this monitor while connectGatt() returns and
       * publishes gatt; an immediate Binder callback cannot see null. */
      if(link!=gatt)return;
      if(status==BluetoothProfile.STATE_CONNECTED && code==BluetoothGatt.GATT_SUCCESS){
        /* Android may deliver a duplicate callback or an old transition.
         * Reject invalid transitions without throwing on a Binder thread. */
        if(state.alreadyConnected()){
           events.status("Duplicate BLE connected callback ignored");
           return;
         }
         if(!transitionConnected()){
          events.status("Unexpected BLE connection state; recovering Bluetooth link");
          // A stale/invalid connected transition is a transient GATT failure.
          // Close this handle and use the same cancelable, bounded backoff as
          // other link failures. Manual disconnect still cancels the retry.
          recoverTemporaryFailure(-10);
          return;
        }
        events.status("Connected; discovering AI service");
        boolean discoveryStarted=false;
        try{discoveryStarted=link.discoverServices();}
        catch(SecurityException | IllegalStateException lostAccess){
          events.status("Bluetooth permission or link lost during service discovery");
        }
        if(!discoveryStarted)recoverTemporaryFailure(-3);
      }else{
        // Invalidate any request from this connection, including delayed callbacks.
        if(code==147 && !retry147Used && approved()){
          retry147Used=true;
          final BluetoothDevice retryDevice=lastDevice;
          disconnect();
          final int generation=connectionGeneration;
          final int retryToken=recoveryToken;
          events.status("GATT 147: connection rejected. Retrying once after a short delay...");
          ui.postDelayed(()->{
            synchronized(BleSatellite.this){
              if(userDisconnected || generation!=connectionGeneration || retryToken!=recoveryToken || retryDevice!=lastDevice)return;
              openConnection(retryDevice);
            }
          },1800);
        }else{
          final BluetoothDevice failedDevice=lastDevice;
          disconnect();
          events.status(code==147
            ? "GATT 147 persists: Bluetooth link rejected. Try the paired Flipper entry; AI service is still required."
            : "Disconnected (GATT "+code+")");
          scheduleRecovery(failedDevice,code);
        }
      }
      }
    }
    @Override public void onServicesDiscovered(BluetoothGatt link,int code){
      synchronized(BleSatellite.this) {
      if(link!=gatt)return;
      if(!approved()){disconnect();return;}
      if(code!=BluetoothGatt.GATT_SUCCESS){
        discoveryEvidence.failed(code);
        events.status("Bluetooth service discovery failed (GATT "+code+")");
        recoverTemporaryFailure(code);return;
      }
      BluetoothGattService service=link.getService(SERVICE);
      BluetoothGattService rpc=link.getService(MOMENTUM_RPC_SERVICE);
      java.util.List<BluetoothGattService> services=link.getServices();
      discoveryEvidence.succeeded(service!=null,rpc!=null,
        services==null?0:services.size());
      if(service==null){
        if(rpc!=null && rpc.getCharacteristic(MOMENTUM_RPC_WRITE)!=null &&
           rpc.getCharacteristic(MOMENTUM_RPC_NOTIFY)!=null){
          stockRpcDetected=true;
          BluetoothGattCharacteristic notify=rpc.getCharacteristic(MOMENTUM_RPC_NOTIFY);
          BluetoothGattDescriptor cccd=notify.getDescriptor(CCCD);
          if(cccd==null || (notify.getProperties() & (BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_NOTIFY))==0){
            events.status("Stock Momentum RPC detected; neither indications nor notifications are supported.");
            return;
          }
          boolean enabled=false;
          final byte[] subscription=(notify.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0
            ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
          try{
            enabled=link.setCharacteristicNotification(notify,true);
            if(enabled){
              if(Build.VERSION.SDK_INT>=33)
                enabled=link.writeDescriptor(cccd,subscription)==BluetoothStatusCodes.SUCCESS;
              else{
                cccd.setValue(subscription);
                enabled=link.writeDescriptor(cccd);
              }
            }
          }catch(SecurityException | IllegalStateException failure){
            enabled=false;
          }
          events.status(enabled
            ? "Stock Momentum RPC found; enabling RPC indications..."
            : "Stock Momentum RPC detected, but notification setup failed.");
          return;
        }
        events.status("Bluetooth connected, but neither stock Momentum RPC nor AI service was found.");
        disconnect();return;
      }
      BluetoothGattCharacteristic tx=service.getCharacteristic(TX),rx=service.getCharacteristic(RX);
      if(tx==null || rx==null || !GattProfilePolicy.valid(rx.getProperties(),tx.getProperties(),tx.getDescriptor(CCCD)!=null)){
        events.status("Incompatible AI BLE GATT profile");disconnect();return;
      }
      boolean notificationEnabled=false;
      try{notificationEnabled=link.setCharacteristicNotification(tx,true);}
      catch(SecurityException | IllegalStateException lostAccess){
        events.status("Bluetooth permission or link lost enabling AI notifications");
      }
      if(!notificationEnabled){
        events.status("Cannot enable AI notifications");
        recoverTemporaryFailure(-4);return;
      }
      BluetoothGattDescriptor descriptor=tx.getDescriptor(CCCD);
      if(!transitionSubscribing()){
        events.status("Unexpected BLE discovery state");
        disconnect();return;
      }
      boolean started=false;
      try {
        if(Build.VERSION.SDK_INT>=33)
          started=link.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
        else{
          descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
          started=link.writeDescriptor(descriptor);
        }
      } catch(SecurityException | IllegalStateException permissionOrLinkLost){
        events.status("Bluetooth permission or link lost during subscription");
      }
      if(!started){events.status("Notification subscription failed");recoverTemporaryFailure(-5);}
      }
    }
    @Override public void onDescriptorWrite(BluetoothGatt link,BluetoothGattDescriptor d,int code){
      synchronized(BleSatellite.this) {
      /* Only the subscribed AI TX characteristic's CCCD can advance
       * connection setup; callbacks from other descriptors are ignored. */
      if(link!=gatt || d==null || !CCCD.equals(d.getUuid()))return;
      BluetoothGattCharacteristic owner=d.getCharacteristic();
      if(owner!=null && MOMENTUM_RPC_FLOW.equals(owner.getUuid()) && stockRpcDetected){
        stockRpcFlowSubscribed=code==BluetoothGatt.GATT_SUCCESS;
        events.status(stockRpcFlowSubscribed
          ? "Stock Momentum RPC flow notifications enabled; Phase 2 launch can be requested."
          : "Stock Momentum RPC flow subscription failed (GATT "+code+").");
        if(stockRpcFlowSubscribed && !drainStockRpc())recoverTemporaryFailure(-16);
        return;
      }
      if(owner!=null && MOMENTUM_RPC_NOTIFY.equals(owner.getUuid()) && stockRpcDetected){
        stockRpcSubscribed=code==BluetoothGatt.GATT_SUCCESS;
        if(stockRpcSubscribed)recoveryAttempts=0;
        events.status(stockRpcSubscribed
          ? "Stock Momentum RPC indications enabled; awaiting protocol messages (AI not ready)."
          : "Stock Momentum RPC indication subscription failed (GATT "+code+").");
        if(stockRpcSubscribed){
          BluetoothGattService rpc=link.getService(MOMENTUM_RPC_SERVICE);
          BluetoothGattCharacteristic flow=rpc==null?null:rpc.getCharacteristic(MOMENTUM_RPC_FLOW);
          if(flow!=null){
            try{if(!link.readCharacteristic(flow))events.status("RPC flow-control read did not start.");}
            catch(SecurityException | IllegalStateException e){events.status("RPC flow-control read unavailable.");}
          }else events.status("RPC flow-control characteristic missing.");
        }
        return;
      }
      if(owner==null || !TX.equals(owner.getUuid()) ||
         state.phase()!=TransportState.Phase.SUBSCRIBING || mtuPending)return;
      if(code!=BluetoothGatt.GATT_SUCCESS){recoverTemporaryFailure(code);return;}
      mtuPending=true;
      boolean requested=false;
      try{requested=link.requestMtu(185);}
      catch(SecurityException | IllegalStateException permissionOrLinkLost){
        events.status("Bluetooth permission or link lost during MTU request");
      }
      if(!requested){mtuPending=false;recoverTemporaryFailure(-6);}
      }
    }
    @Override public void onMtuChanged(BluetoothGatt link,int mtu,int code){
      synchronized(BleSatellite.this) {
      if(link!=gatt || !mtuPending)return;
      mtuPending=false;
      if(code!=BluetoothGatt.GATT_SUCCESS){recoverTemporaryFailure(code);return;}
      state.mtu(mtu);
      if(!state.hasHandshakeCapacity()){
        events.status("Insufficient BLE MTU: AI bridge requires at least 32 ATT bytes");
        disconnect();return;
      }
      if(!approved()){disconnect();return;}
      if(!transitionReady()){
        events.status("Unexpected BLE subscription state");
        disconnect();return;
      }
      subscribed=true;
      recoveryAttempts=0;
      events.status("AI BLE ready, max frame "+state.payloadLimit()+" bytes");
      }
    }
    private void receiveRpcIndication(byte[] data){
      if(data==null || data.length==0)return;
      if(rpcAiSession.state()!=StockMomentumRpcAi1Session.State.IDLE){
        try{
          boolean wasConfirmed=rpcAiSession.ai1HelloAckConfirmed();
          rpcAiSession.feed(data,rpcEvents);
          if(!wasConfirmed && rpcAiSession.ai1HelloAckConfirmed())
            events.status("Flipper AI1 HELLO_ACK delivered over Momentum RPC; ready to test PING.");
          if(rpcAiSession.state()==StockMomentumRpcAi1Session.State.FAILED){
            // Failure/AppExit must discard any queued or in-flight replies.
            events.status("Stock RPC AI session stopped; reconnect before restarting.");
            recoverTemporaryFailure(-19);
          }
        }catch(IllegalArgumentException invalid){
          events.status("Stock RPC AI1 frame rejected; reconnect required.");
          recoverTemporaryFailure(-14);
        }
        return;
      }
      // Diagnostic ping is independent of the Phase 2 AI1 app session. The
      // older debug parser never marks the AI channel ready.
      int len=data[0]&255;
      if(len!=data.length-1){events.status("RPC diagnostic indication requires framing.");return;}
      boolean id=false,ping=false;
      for(int i=1;i<data.length-1;i++){
        if((data[i]&255)==8 && (data[i+1]&255)==1)id=true;
        if((data[i]&255)==50 && (data[i+1]&255)==0)ping=true;
      }
      if(rpcPingPending && id && ping){
        rpcPingPending=false;
        events.status("RPC PING RESPONSE RECEIVED (command 1); AI1 app not launched.");
      }else{
        events.status("Stock RPC diagnostic response; AI1 app not launched.");
      }
    }

    /** Momentum stores REVERSE_BYTES_U32(buffer_size) on the F7: big-endian. */
    private void readRpcFlow(byte[] data){
      if(data==null || data.length!=4){
        rpcFlowCredits=-1;
        events.status("RPC flow-control value length unexpected.");return;
      }
      long n=(((long)data[0]&255L)<<24)|(((long)data[1]&255L)<<16)|
             (((long)data[2]&255L)<<8)|((long)data[3]&255L);
      if(n>0 && n<=65536L){
        rpcFlowCredits=(int)n;
        if(stockRpcFlowSubscribed && !drainStockRpc())
          recoverTemporaryFailure(-15);
      }else{
        rpcFlowCredits=-1;
        events.status("Unexpected stock RPC flow-control capacity; link not ready.");
      }
    }

    private void subscribeRpcFlow(BluetoothGatt link){
      if(link!=gatt || !stockRpcSubscribed || !approved())return;
      BluetoothGattService service=link.getService(MOMENTUM_RPC_SERVICE);
      BluetoothGattCharacteristic flow=service==null?null:
        service.getCharacteristic(MOMENTUM_RPC_FLOW);
      if(flow==null || (flow.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0){
        events.status("Momentum RPC flow notifications are not available.");
        return;
      }
      BluetoothGattDescriptor descriptor=flow.getDescriptor(CCCD);
      if(descriptor==null){
        events.status("Momentum RPC flow CCCD is missing.");return;
      }
      boolean started=false;
      try{
        if(link.setCharacteristicNotification(flow,true)){
          if(Build.VERSION.SDK_INT>=33)
            started=link.writeDescriptor(descriptor,
              BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
          else{
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            started=link.writeDescriptor(descriptor);
          }
        }
      }catch(SecurityException | IllegalStateException gone){
        started=false;
      }
      if(!started)events.status("Momentum RPC flow subscription could not start.");
    }
    @Override public void onCharacteristicRead(
        BluetoothGatt link,BluetoothGattCharacteristic ch,byte[] value,int code){
      synchronized(BleSatellite.this){
        if(link!=gatt || !stockRpcSubscribed || ch==null ||
           !MOMENTUM_RPC_FLOW.equals(ch.getUuid()))return;
        if(code==BluetoothGatt.GATT_SUCCESS)readRpcFlow(value);
        else events.status("RPC flow-control read failed (GATT "+code+").");
        subscribeRpcFlow(link);
      }
    }
    @Override public void onCharacteristicRead(
        BluetoothGatt link,BluetoothGattCharacteristic ch,int code){
      if(Build.VERSION.SDK_INT<33){
        synchronized(BleSatellite.this){
          if(link!=gatt || !stockRpcSubscribed || ch==null ||
             !MOMENTUM_RPC_FLOW.equals(ch.getUuid()))return;
          if(code==BluetoothGatt.GATT_SUCCESS)readRpcFlow(ch.getValue());
          else events.status("RPC flow-control read failed (GATT "+code+").");
          subscribeRpcFlow(link);
        }
      }
    }
    @Override public void onCharacteristicWrite(BluetoothGatt link,BluetoothGattCharacteristic ch,int code){
      synchronized(BleSatellite.this){
        // A stale callback must not complete a write pending on a new GATT.
        if(link!=gatt || ch==null)return;
        if(MOMENTUM_RPC_WRITE.equals(ch.getUuid())){
          if(rpcWritePending){
            rpcWritePending=false;
            if(code!=BluetoothGatt.GATT_SUCCESS){
              events.status("Phase 2 stock RPC write failed (GATT "+code+").");
              recoverTemporaryFailure(code);
              return;
            }
            byte[] first=rpcQueue.peekFirst();
            if(first!=null && rpcOffset>=first.length){
              rpcQueue.removeFirst();
              rpcOffset=0;
            }
            if(!drainStockRpc())recoverTemporaryFailure(-18);
          }else if(code!=BluetoothGatt.GATT_SUCCESS){
            rpcPingPending=false;
            events.status("Momentum RPC diagnostic ping write failed (GATT "+code+").");
          }
          return;
        }
        if(!RX.equals(ch.getUuid()) || !writeGuard.complete())return;
        if(code==BluetoothGatt.GATT_SUCCESS){pending.pollFirst();drain();}
        else{
          events.status("AI BLE write failed: "+code);
          recoverTemporaryFailure(code);
        }
      }
    }
    @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch,byte[] value){
      synchronized(BleSatellite.this){
        // Serialize notification delivery with disconnect/reconnect state changes.
        // A callback from an obsolete GATT must never update the new session.
        if(link!=gatt || ch==null)return;
        if(MOMENTUM_RPC_NOTIFY.equals(ch.getUuid()) && stockRpcSubscribed){
          rpcNotificationsReceived++;
          receiveRpcIndication(value);
        }else if(MOMENTUM_RPC_FLOW.equals(ch.getUuid()) && stockRpcFlowSubscribed){
          readRpcFlow(value);
        }else if(TX.equals(ch.getUuid()) && isReady())deliver(value);
      }
    }
    @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch){
      if(Build.VERSION.SDK_INT>=33)return;
      synchronized(BleSatellite.this){
        if(link!=gatt || ch==null)return;
        if(MOMENTUM_RPC_NOTIFY.equals(ch.getUuid()) && stockRpcSubscribed){
          rpcNotificationsReceived++;
          receiveRpcIndication(ch.getValue());
        }else if(MOMENTUM_RPC_FLOW.equals(ch.getUuid()) && stockRpcFlowSubscribed){
          readRpcFlow(ch.getValue());
        }else if(TX.equals(ch.getUuid()) && isReady())deliver(ch.getValue());
      }
    }
  };
}
