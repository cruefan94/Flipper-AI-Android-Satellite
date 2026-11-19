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
  public interface Events { void status(String text);void device(BluetoothDevice device,String advertisedName);void received(String frame); }
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
        if(userDisconnected || token!=recoveryToken || failedDevice!=lastDevice)return;
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
    state.disconnected();authorized=false;subscribed=false;mtuPending=false;stockRpcDetected=false;stockRpcSubscribed=false;rpcNotificationsReceived=0;rpcFlowCredits=-1;rpcPingPending=false;writeGuard.reset();
    pending.clear();BluetoothGatt old=gatt;gatt=null;
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
          events.status("Unexpected BLE connection state; reset required");
          disconnect();
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
          events.status("GATT 147: connection rejected. Retrying once after a short delay...");
          ui.postDelayed(()->{
            synchronized(BleSatellite.this){
              if(userDisconnected || generation!=connectionGeneration || retryDevice!=lastDevice)return;
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
      // This initial parser only accepts a complete, single-frame ping response.
      int len=data[0]&255;
      if(len!=data.length-1){events.status("RPC indication received; fragmented/extended protobuf response requires framing.");return;}
      boolean id=false,ping=false;
      for(int i=1;i<data.length-1;i++){
        if((data[i]&255)==8 && (data[i+1]&255)==1)id=true;
        if((data[i]&255)==50 && (data[i+1]&255)==0)ping=true;
      }
      if(rpcPingPending && id && ping){rpcPingPending=false;events.status("RPC PING RESPONSE RECEIVED (command 1); basic envelope matched.");}
      else events.status("Momentum RPC indication received; response not yet validated.");
    }
    private void readRpcFlow(byte[] data){
      if(data==null || data.length!=4){events.status("RPC flow-control value length unexpected.");return;}
      long n=((long)data[0]&255L)|(((long)data[1]&255L)<<8)|(((long)data[2]&255L)<<16)|(((long)data[3]&255L)<<24);
      if(n<=1048576L){rpcFlowCredits=(int)n;events.status("Momentum RPC flow-control value: "+rpcFlowCredits+" (unsigned little-endian). No command sent.");}
      else {rpcFlowCredits=-1;events.status("Momentum RPC raw flow-control value: "+n+" (unsigned little-endian); unusually large. No command sent.");}
    }
    @Override public void onCharacteristicRead(BluetoothGatt link,BluetoothGattCharacteristic ch,byte[] value,int code){
      synchronized(BleSatellite.this){if(link==gatt && stockRpcSubscribed && ch!=null && MOMENTUM_RPC_FLOW.equals(ch.getUuid()) && code==BluetoothGatt.GATT_SUCCESS)readRpcFlow(value);}
    }
    @Override public void onCharacteristicRead(BluetoothGatt link,BluetoothGattCharacteristic ch,int code){
      if(Build.VERSION.SDK_INT<33){synchronized(BleSatellite.this){if(link==gatt && stockRpcSubscribed && ch!=null && MOMENTUM_RPC_FLOW.equals(ch.getUuid()) && code==BluetoothGatt.GATT_SUCCESS)readRpcFlow(ch.getValue());}}
    }
    @Override public void onCharacteristicWrite(BluetoothGatt link,BluetoothGattCharacteristic ch,int code){
      synchronized(BleSatellite.this){
        // A stale callback must not complete a write pending on a new GATT.
        if(link!=gatt || ch==null)return;
        if(MOMENTUM_RPC_WRITE.equals(ch.getUuid())){
          if(code!=BluetoothGatt.GATT_SUCCESS){
            rpcPingPending=false;
            events.status("Momentum RPC ping write failed (GATT "+code+").");
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
        }else if(TX.equals(ch.getUuid()) && isReady())deliver(ch.getValue());
      }
    }
  };
}
