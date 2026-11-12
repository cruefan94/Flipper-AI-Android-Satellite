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
  private final ArrayDeque<byte[]> pending=new ArrayDeque<>();
  private BluetoothLeScanner scanner;
  private volatile BluetoothGatt gatt;
  private volatile BluetoothDevice lastDevice;
  private int scanGeneration;
  private int connectionGeneration;
  private boolean retry147Used;
  /* Read by UI and written from Android Bluetooth Binder callbacks. */
  private volatile boolean authorized,subscribed,mtuPending;
  private volatile boolean stockRpcDetected,stockRpcSubscribed;
  private volatile int rpcNotificationsReceived;
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
    retry147Used=false;
    openConnection(device);
  }
  private synchronized void openConnection(BluetoothDevice device){
    stopScan();disconnect();lastDevice=device;
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
      if(link==null){events.status("Cannot start Bluetooth GATT connection");disconnect();return;}
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
    if(link!=gatt || isReady())return;
    events.status("AI GATT connection setup timed out");
    disconnect();
  }
  public void reconnect(){if(lastDevice!=null)connect(lastDevice);}
  /** Pair the device explicitly chosen from scan results, without connecting first. */
  public void pair(BluetoothDevice device){
    if(device==null){events.status("Select a scanned Flipper before pairing");return;}
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
  private boolean approved(){
    if(!authorized || lastDevice==null)return false;
    try{return BondGate.canUseAiChannel(lastDevice.getBondState(),authorized);}
    catch(SecurityException | IllegalStateException permissionOrAdapterLost){
      return false; // Fail closed if trusted Android pairing state is unreadable.
    }
  }
  public synchronized void disconnect(){
    connectionGeneration++;
    state.disconnected();authorized=false;subscribed=false;mtuPending=false;stockRpcDetected=false;stockRpcSubscribed=false;rpcNotificationsReceived=0;writeGuard.reset();
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
  public int maxFrameBytes(){return state.payloadLimit();}
  @Override public Kind kind(){return Kind.BLE;}
  public boolean hasStockRpc(){return stockRpcDetected && gatt!=null && approved();}
  public boolean isStockRpcSubscribed(){return hasStockRpc() && stockRpcSubscribed;}
  public int stockRpcNotificationCount(){return rpcNotificationsReceived;}
  @Override public boolean isReady(){return subscribed && approved() && state.phase()==TransportState.Phase.READY;}
  @Override public boolean sendFrame(String data){return send(data);}
  @Override public void close(){stopScan();disconnect();}
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
      disconnect();
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
      disconnect();
      return false;
    }
    ui.postDelayed(()->expireWrite(link,token),GATT_WRITE_TIMEOUT_MS);
    return true;
  }
  private synchronized void expireWrite(BluetoothGatt link,int token){
    /* An old timeout can never disconnect a newly connected phone or a
     * later GATT write. The token and active GATT instance must both match. */
    if(link!=gatt || !writeGuard.isPending(token))return;
    events.status("AI GATT write timed out; reconnecting is required");
    disconnect();
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
        if(!discoveryStarted)disconnect();
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
              if(generation!=connectionGeneration || retryDevice!=lastDevice)return;
              openConnection(retryDevice);
            }
          },1800);
        }else{
          disconnect();
          events.status(code==147
            ? "GATT 147 persists: Bluetooth link rejected. Try the paired Flipper entry; AI service is still required."
            : "Disconnected (GATT "+code+")");
        }
      }
      }
    }
    @Override public void onServicesDiscovered(BluetoothGatt link,int code){
      synchronized(BleSatellite.this) {
      if(link!=gatt)return;
      if(!approved()){disconnect();return;}
      BluetoothGattService service=link.getService(SERVICE);
      if(code!=BluetoothGatt.GATT_SUCCESS){
        events.status("Bluetooth service discovery failed (GATT "+code+")");disconnect();return;
      }
      if(service==null){
        BluetoothGattService rpc=link.getService(MOMENTUM_RPC_SERVICE);
        if(rpc!=null && rpc.getCharacteristic(MOMENTUM_RPC_WRITE)!=null &&
           rpc.getCharacteristic(MOMENTUM_RPC_NOTIFY)!=null){
          stockRpcDetected=true;
          BluetoothGattCharacteristic notify=rpc.getCharacteristic(MOMENTUM_RPC_NOTIFY);
          BluetoothGattDescriptor cccd=notify.getDescriptor(CCCD);
          if(cccd==null || (notify.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0){
            events.status("Stock Momentum RPC detected; notifications are unavailable.");
            return;
          }
          boolean enabled=false;
          try{
            enabled=link.setCharacteristicNotification(notify,true);
            if(enabled){
              if(Build.VERSION.SDK_INT>=33)
                enabled=link.writeDescriptor(cccd,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
              else{
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                enabled=link.writeDescriptor(cccd);
              }
            }
          }catch(SecurityException | IllegalStateException failure){
            enabled=false;
          }
          events.status(enabled
            ? "Stock Momentum RPC found; enabling RPC notifications..."
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
        events.status("Cannot enable AI notifications");disconnect();return;
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
      if(!started){events.status("Notification subscription failed");disconnect();}
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
        events.status(stockRpcSubscribed
          ? "Stock Momentum RPC notifications enabled; awaiting protocol messages (AI not ready)."
          : "Stock Momentum RPC notification subscription failed (GATT "+code+").");
        return;
      }
      if(owner==null || !TX.equals(owner.getUuid()) ||
         state.phase()!=TransportState.Phase.SUBSCRIBING || mtuPending)return;
      if(code!=BluetoothGatt.GATT_SUCCESS){disconnect();return;}
      mtuPending=true;
      boolean requested=false;
      try{requested=link.requestMtu(185);}
      catch(SecurityException | IllegalStateException permissionOrLinkLost){
        events.status("Bluetooth permission or link lost during MTU request");
      }
      if(!requested){mtuPending=false;disconnect();}
      }
    }
    @Override public void onMtuChanged(BluetoothGatt link,int mtu,int code){
      synchronized(BleSatellite.this) {
      if(link!=gatt || !mtuPending)return;
      mtuPending=false;
      if(code!=BluetoothGatt.GATT_SUCCESS){disconnect();return;}
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
      events.status("AI BLE ready, max frame "+state.payloadLimit()+" bytes");
      }
    }
    @Override public void onCharacteristicWrite(BluetoothGatt link,BluetoothGattCharacteristic ch,int code){
      if(link!=gatt || !RX.equals(ch.getUuid()))return;
      synchronized(BleSatellite.this){
        if(!writeGuard.complete())return;
        if(code==BluetoothGatt.GATT_SUCCESS){pending.pollFirst();drain();}
        else{
          events.status("AI BLE write failed: "+code);
          disconnect();
        }
      }
    }
    @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch,byte[] value){
      if(link==gatt && MOMENTUM_RPC_NOTIFY.equals(ch.getUuid()) && stockRpcSubscribed){
        rpcNotificationsReceived++;
        events.status("Stock Momentum RPC notification received ("+rpcNotificationsReceived+"); protobuf decoding not yet enabled.");
      }else if(link==gatt && TX.equals(ch.getUuid()) && isReady())deliver(value);
    }
    @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch){
      if(Build.VERSION.SDK_INT<33 && link==gatt){
        if(MOMENTUM_RPC_NOTIFY.equals(ch.getUuid()) && stockRpcSubscribed){
          rpcNotificationsReceived++;
          events.status("Stock Momentum RPC notification received ("+rpcNotificationsReceived+"); protobuf decoding not yet enabled.");
        }else if(TX.equals(ch.getUuid()) && isReady())deliver(ch.getValue());
      }
    }
  };
}
