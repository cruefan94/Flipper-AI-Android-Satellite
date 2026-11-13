package com.flipperai.satellite;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.LinkedHashMap;

/** Native Android AI Satellite control surface. No language model is bundled. */
public final class MainActivity extends Activity implements BleSatellite.Events {
  private BleSatellite ble;
  private final SatelliteTransportCoordinator transports=new SatelliteTransportCoordinator();
  private TextView status;
  private TextView connectionState;
  private TextView connectionHistory;
  private LinearLayout advancedSection;
  private boolean advancedVisible=false;
  private boolean discoverableMode=false;
  private LinearLayout pairedSection,discoverableSection;
  private LinearLayout root;
  private final LinkedHashMap<String,BluetoothDevice> discovered=new LinkedHashMap<>();
  private final LinkedHashMap<String,String> deviceLabels=new LinkedHashMap<>();
  private Spinner devices;
  private Spinner nearbyDevices;
  private TextView pairedHeading,nearbyHeading,deviceInfo;
  private final LinkedHashMap<String,BluetoothDevice> nearby=new LinkedHashMap<>();
  private final LinkedHashMap<String,String> nearbyNames=new LinkedHashMap<>();
  private String selectedAddress;
  private int scanEpoch;
  private boolean scanAfterPermission;
  private boolean refreshAfterPermission;
  private final String[] labels={"OFFLINE","HYBRID","ONLINE"};
  private Spinner mode;
  private final WorkerCloudEngine cloud=new WorkerCloudEngine();
  private final LocalModelEngine localModel=new LocalModelEngine();
  private final InferenceRouter router=new InferenceRouter(
    new OfflineModelOrDiagnostics(localModel,new OfflineDiagnosticEngine()),cloud);
  private final ExecutorService inferenceWorker=Executors.newSingleThreadExecutor();
  private CheckBox cloudConsent;
  private final ConsentEpoch consentEpoch=new ConsentEpoch();
  private SatelliteRequestHandler requests;
  private final ProtocolSession session=new ProtocolSession();
  private final SessionEpoch connectionEpoch=new SessionEpoch();
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    requests=new SatelliteRequestHandler(router);
    ble=new BleSatellite(this,this);
    transports.register(ble);
    ScrollView scroller=new ScrollView(this);
    root=new LinearLayout(this);
    root.setPadding(26,30,26,30);
    root.setOrientation(LinearLayout.VERTICAL);
    scroller.addView(root);
    setContentView(scroller);
    TextView header=new TextView(this);
    header.setText("Flipper AI Satellite\nBLE-first development build");
    header.setTextSize(22);root.addView(header);
    status=new TextView(this);
    status.setText("No AI connection");root.addView(status);
    TextView offlineHelp=new TextView(this);
    offlineHelp.setText("Offline AI runs on this Android phone when a compatible local model is installed and running. The Flipper accesses it over Bluetooth. Without the phone, Flipper tools remain available but full AI chat does not.");
    root.addView(offlineHelp);
    connectionState=new TextView(this);connectionState.setTextSize(18);
    connectionState.setText("AI CONNECTION: NOT CONNECTED");root.addView(connectionState);
    connectionHistory=new TextView(this);
    connectionHistory.setText("Bluetooth device discovery is separate from the AI channel.");
    root.addView(connectionHistory);
    TextView categoryPrompt=new TextView(this);
    categoryPrompt.setText("Choose ONE device source:");
    root.addView(categoryPrompt);
    RadioGroup sources=new RadioGroup(this);
    sources.setOrientation(RadioGroup.HORIZONTAL);
    RadioButton pairedChoice=new RadioButton(this);
    pairedChoice.setText("Paired devices");
    RadioButton discoverableChoice=new RadioButton(this);
    discoverableChoice.setText("Discoverable devices");
    sources.addView(pairedChoice);
    sources.addView(discoverableChoice);
    root.addView(sources);
    pairedSection=new LinearLayout(this);
    pairedSection.setOrientation(LinearLayout.VERTICAL);
    root.addView(pairedSection);
    pairedHeading=new TextView(this);pairedHeading.setText("PAIRED / CONNECTED DEVICES");pairedSection.addView(pairedHeading);
    devices=new Spinner(this);pairedSection.addView(devices);
    sectionButton(pairedSection,"Select paired device",()->selectFrom(devices,false));
    sectionButton(pairedSection,"Refresh paired devices",()->{
      if(permissions())showPairedDevices();
      else refreshAfterPermission=true;
    });
    discoverableSection=new LinearLayout(this);
    discoverableSection.setOrientation(LinearLayout.VERTICAL);
    root.addView(discoverableSection);
    nearbyHeading=new TextView(this);nearbyHeading.setText("DISCOVERABLE DEVICES");discoverableSection.addView(nearbyHeading);
    nearbyDevices=new Spinner(this);discoverableSection.addView(nearbyDevices);
    sectionButton(discoverableSection,"Select discovered device",()->selectFrom(nearbyDevices,true));
    sectionButton(discoverableSection,"Scan for nearby devices",()->{
      if(permissions())beginScan();
      else scanAfterPermission=true;
    });
    deviceInfo=new TextView(this);deviceInfo.setText("No device selected.");root.addView(deviceInfo);
    button("Clear device selection",()->{
      ble.disconnect();
      selectedAddress=null;
      deviceInfo.setText("No device selected.");
      showStatus("Device selection cleared.");
    });
    pairedChoice.setId(android.view.View.generateViewId());
    discoverableChoice.setId(android.view.View.generateViewId());
    sources.setOnCheckedChangeListener((group,id)->{
      boolean newDiscoverable=id==discoverableChoice.getId();
      if(discoverableMode!=newDiscoverable){
        ble.disconnect();
        selectedAddress=null;
        deviceInfo.setText("No device selected. Choose a device from this category.");
        connectionState.setText("AI CONNECTION: NOT CONNECTED");
      }
      discoverableMode=newDiscoverable;
      pairedSection.setVisibility(discoverableMode?android.view.View.GONE:android.view.View.VISIBLE);
      discoverableSection.setVisibility(discoverableMode?android.view.View.VISIBLE:android.view.View.GONE);
    });
    pairedChoice.setChecked(true);
    discoverableSection.setVisibility(android.view.View.GONE);
    if(permissionsAlreadyGranted())showPairedDevices();
    button("Connect selected",()->{
      if(!permissions())return;
      BluetoothDevice selected=selectedDevice();
      if(selected==null){showStatus("Scan and select a Flipper first");return;}
      connectionState.setText("AI CONNECTION: CONNECTING...");
      ble.connect(selected);
    });
    button("Pair selected",()->{
      if(!permissions())return;
      BluetoothDevice selected=selectedDevice();
      if(selected==null){showStatus("Scan and select a Flipper first");return;}
      ble.pair(selected);
    });
    button("Reconnect",()->{
      if(!permissions())return;
      BluetoothDevice selected=selectedDevice();
      if(selected==null){showStatus("Select a device in the active category before reconnecting");return;}
      connectionState.setText("AI CONNECTION: CONNECTING...");
      ble.connect(selected);
    });
    button("Disconnect",()->{if(permissions())ble.disconnect();});
    button("TEST MOMENTUM RPC PING",()->ble.sendStockRpcPing());
    button("Show / hide AI settings and diagnostics",()->{
      advancedVisible=!advancedVisible;
      advancedSection.setVisibility(advancedVisible?android.view.View.VISIBLE:android.view.View.GONE);
    });
    advancedSection=new LinearLayout(this);
    advancedSection.setOrientation(LinearLayout.VERTICAL);
    advancedSection.setVisibility(android.view.View.GONE);
    root.addView(advancedSection);
    mode=new Spinner(this);
    mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    advancedSection.addView(mode);
    TextView localLabel=new TextView(this);
    localLabel.setText("Offline language model (optional): install a local OpenAI-compatible server and model on this phone. Only 127.0.0.1 is accepted; no internet URL is allowed.");
    advancedSection.addView(localLabel);
    EditText localUrl=new EditText(this);
    localUrl.setSingleLine(true);
    localUrl.setHint("http://127.0.0.1:8080/v1/chat/completions");
    advancedSection.addView(localUrl);
    EditText localName=new EditText(this);
    localName.setSingleLine(true);localName.setHint("Local model name (e.g. local)");
    advancedSection.addView(localName);
    advancedButton("Use installed offline model",()->{
      try{
        localModel.configure(localUrl.getText().toString(),localName.getText().toString());
        showStatus("Local model address saved for this session (loopback only). This does not install or verify a model. Tap Test local model connection to confirm real inference.");
      }catch(Exception e){showStatus(e.getMessage());}
    });
    advancedButton("Disable local model",()->{
      localModel.clear();showStatus("Local model disabled; offline diagnostic rules remain available");
    });
    advancedButton("Test local model connection",()->{
      if(!localModel.ready()){
        showStatus("Configure the local on-device model endpoint first");return;
      }
      showStatus("Testing local model response...");
      inferenceWorker.execute(()->{
        try{
          String sample=localModel.answer("Reply with OK.");
          showStatus("Verified live localhost model response: "+sample);
        }catch(Exception e){
          showStatus("Local model not responding: "+e.getMessage());
        }
      });
    });
    TextView note=new TextView(this);
    note.setText("Optional cloud: ASK prompts go to your configured HTTPS Worker only when consent is enabled. Device tokens stay in memory; offline diagnostics use no internet.");
    advancedSection.addView(note);
    EditText url=new EditText(this);
    url.setSingleLine(true);url.setHint("https://YOUR-WORKER.workers.dev/ask");advancedSection.addView(url);
    EditText token=new EditText(this);
    token.setSingleLine(true);token.setHint("Worker device token (memory only)");
    token.setInputType(129);advancedSection.addView(token);
    advancedButton("Configure cloud endpoint",()->{
      try {cloud.configure(url.getText().toString(),token.getText().toString());token.setText("");showStatus("Cloud endpoint configured in memory");}
      catch(Exception e){cloud.clear();showStatus(e.getMessage());}
    });
    cloudConsent=new CheckBox(this);
    cloudConsent.setText("I authorize sending ASK prompts to my cloud Worker and AI provider");
    cloudConsent.setChecked(false);
    cloudConsent.setOnCheckedChangeListener((button,checked)->{
      consentEpoch.changed();
      if(!checked)cloud.revokeActive();
    });
    advancedSection.addView(cloudConsent);
    advancedButton("Disable and clear cloud",()->{cloudConsent.setChecked(false);cloud.clear();showStatus("Cloud credentials cleared and consent revoked");});
    EditText input=new EditText(this);
    input.setHint("Protocol test text (no private data)");advancedSection.addView(input);
    advancedButton("Run offline diagnostic",()->{
      final String prompt=input.getText().toString();
      showStatus("Running on-device inference...");
      inferenceWorker.execute(()->{
        try {
          String answer=router.ask(prompt,InferenceRouter.Mode.OFFLINE,false);
          showStatus("On-device: "+answer);
        } catch(Exception e) {
          showStatus("Offline inference unavailable: "+e.getMessage());
        }
      });
    });
    TextView transportNote=new TextView(this);
    transportNote.setText("AI transport: BLE only. Wi-Fi and USB-C relay adapters are reserved for future builds; no automatic fallback.");
    advancedSection.addView(transportNote);
    advancedButton("Check AI BLE channel",()->{
      if(!permissions())return;
      showStatus(transports.isReady()
          ? "AI GATT connected; Flipper must initiate HELLO by notification"
          : ble.hasStockRpc() ? "Stock Momentum RPC detected. Indications: "+(ble.isStockRpcSubscribed()?"enabled":"not enabled")+". Received: "+ble.stockRpcNotificationCount()+". AI bridge not installed."
          : "No authorized AI GATT or stock Momentum RPC session.");
    });
    TextView caveat=new TextView(this);
    caveat.setText("Momentum functions remain on Flipper. Offline LLM inference works only with a separately installed local model server; otherwise the app uses diagnostic rules. Localhost model requests never access the internet.");
    advancedSection.addView(caveat);
  }
  private void beginScan(){
    nearby.clear();nearbyNames.clear();
    nearbyDevices.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"None — scanning for nearby devices..."}));
    final int epoch=++scanEpoch;
    ble.startScan();
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
      if(isFinishing() || isDestroyed() || epoch!=scanEpoch)return;
      if(nearby.isEmpty())showStatus("No discoverable BLE devices found. Switch to Paired devices to view previously paired devices.");
      else showStatus("Discovered "+nearby.size()+" nearby BLE device(s).");
    },12500);
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode!=22)return;
    boolean granted=grantResults.length>0;
    for(int result:grantResults)if(result!=PackageManager.PERMISSION_GRANTED)granted=false;
    if(!granted){scanAfterPermission=false;refreshAfterPermission=false;showStatus("Bluetooth scan requires Nearby devices permission (or Location on older Android).");return;}
    if(scanAfterPermission){scanAfterPermission=false;refreshAfterPermission=false;beginScan();}
    else if(refreshAfterPermission){refreshAfterPermission=false;showPairedDevices();}
    else showStatus("Bluetooth permission granted. Tap Scan for Flipper.");
  }
  private void showPairedDevices(){
    try{
      BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
      BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
      if(adapter==null || !adapter.isEnabled()){
        showStatus("Enable Bluetooth to view paired devices");return;
      }
      java.util.Set<BluetoothDevice> bonded=adapter.getBondedDevices();
      if(bonded==null || bonded.isEmpty()){
        showStatus("No paired devices found. Pair your Flipper in Android Bluetooth settings.");return;
      }
      discovered.clear();deviceLabels.clear();
      java.util.Set<String> connected=new java.util.HashSet<>();
      try{
        for(BluetoothDevice active:manager.getConnectedDevices(BluetoothProfile.GATT))
          connected.add(active.getAddress());
      }catch(SecurityException | IllegalArgumentException ignored){}
      for(BluetoothDevice device:bonded){
        String address=device.getAddress();
        discovered.put(address,device);
        deviceLabels.put(address,deviceName(device,null)+" — "+(connected.contains(address)?"GATT connected":"Paired"));
      }
      refreshList(devices,deviceLabels);
      showStatus("Found "+bonded.size()+" paired device(s). GATT connection does not imply AI readiness.");
    }catch(SecurityException | IllegalStateException e){
      showStatus("Cannot list paired devices. Allow Nearby devices permission.");
    }
  }
  private String deviceName(BluetoothDevice device,String advertised){
    String name=null;
    try{name=device.getName();}catch(SecurityException ignored){}
    if(name==null || name.trim().isEmpty())name=advertised;
    return name==null || name.trim().isEmpty()?"Unknown Bluetooth device":name.trim();
  }
  private void refreshList(Spinner spinner,LinkedHashMap<String,String> labels){
    java.util.ArrayList<String> values=new java.util.ArrayList<>();
    values.add("None — no device selected");
    values.addAll(labels.values());
    spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values));
  }
  private void selectFrom(Spinner spinner,boolean fromNearby){
    String label=spinner.getSelectedItem()==null?null:spinner.getSelectedItem().toString();
    if(label==null || label.startsWith("None —")){
      selectedAddress=null;
      deviceInfo.setText("No device selected.");return;
    }
    if(fromNearby!=discoverableMode){showStatus("Switch device category before selecting.");return;}
    LinkedHashMap<String,String> names=fromNearby?nearbyLabels():deviceLabels;
    for(java.util.Map.Entry<String,String> entry:names.entrySet()){
      if(entry.getValue().equals(label)){
        selectedAddress=entry.getKey();
        (fromNearby?devices:nearbyDevices).setSelection(0);
        BluetoothDevice device=fromNearby?nearby.get(selectedAddress):discovered.get(selectedAddress);
        if(device==null)return;
        String kind="Unknown";
        try{
          int type=device.getType();
          kind=type==BluetoothDevice.DEVICE_TYPE_LE?"Bluetooth Low Energy":
            type==BluetoothDevice.DEVICE_TYPE_DUAL?"Bluetooth Classic + LE":
            type==BluetoothDevice.DEVICE_TYPE_CLASSIC?"Bluetooth Classic":"Unknown Bluetooth type";
        }catch(SecurityException ignored){}
        int bond=BluetoothDevice.BOND_NONE;
        try{bond=device.getBondState();}catch(SecurityException ignored){}
        deviceInfo.setText("Selected: "+deviceName(device,null)+"\nType: "+kind+
          "\nPairing: "+(bond==BluetoothDevice.BOND_BONDED?"Paired":bond==BluetoothDevice.BOND_BONDING?"Pairing":"Not paired")+
          "\nSource: "+(fromNearby?"BLE discovery":"Android paired devices")+
          "\nAI channel: "+(ble.isReady()?"Ready":"Not connected"));
        return;
      }
    }
    showStatus("Choose a device from the list first.");
  }
  private LinkedHashMap<String,String> nearbyLabels(){
    LinkedHashMap<String,String> names=new LinkedHashMap<>();
    for(java.util.Map.Entry<String,BluetoothDevice> e:nearby.entrySet())
      names.put(e.getKey(),deviceName(e.getValue(),nearbyNames.get(e.getKey())));
    return names;
  }
  private BluetoothDevice selectedDevice(){
    if(selectedAddress==null)return null;
    return discoverableMode?nearby.get(selectedAddress):discovered.get(selectedAddress);
  }
  private void sectionButton(LinearLayout section,String name,Runnable action){
    Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());section.addView(b);
  }
  private void advancedButton(String name,Runnable action){
    Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());advancedSection.addView(b);
  }
  private void button(String name,Runnable action){
    Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());root.addView(b);
  }
  private boolean permissionsAlreadyGranted(){
    if(Build.VERSION.SDK_INT>=31)
      return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
  }
  private boolean permissions(){
    if(Build.VERSION.SDK_INT>=31){
      String[] p={Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT};
      if(checkSelfPermission(p[0])!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(p[1])!=PackageManager.PERMISSION_GRANTED){
        requestPermissions(p,22);return false;
      }
    } else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},22);return false;
    }
    return true;
  }
  private void showStatus(String text){runOnUiThread(()->status.setText(text));}
  @Override public void status(String text){
    runOnUiThread(()->{
      if(connectionState==null)return;
      if(text.startsWith("RPC PING RESPONSE RECEIVED"))connectionState.setText("MOMENTUM RPC PING RESPONSE RECEIVED — AI NOT READY");
      else if(text.startsWith("Stock Momentum RPC indications enabled"))connectionState.setText("BLUETOOTH CONNECTED — MOMENTUM RPC INDICATIONS ACTIVE (AI NOT READY)");
      else if(text.startsWith("BLUETOOTH CONNECTED: Stock Momentum RPC"))connectionState.setText("BLUETOOTH CONNECTED — STOCK MOMENTUM RPC DETECTED (AI NOT READY)");
      else if(text.startsWith("AI BLE ready"))connectionState.setText("AI CONNECTION: CONNECTED / READY");
      else if(text.startsWith("Stock Momentum RPC"))connectionState.setText("BLUETOOTH CONNECTED — MOMENTUM RPC DETECTED (AI NOT READY)");
      else if(text.startsWith("Connected;"))connectionState.setText("BLUETOOTH CONNECTED — CHECKING AI SERVICE");
      else if(text.startsWith("Connecting") || text.contains("Retrying once"))connectionState.setText("AI CONNECTION: CONNECTING...");
      else if(text.startsWith("Disconnected") || text.startsWith("GATT") || text.contains("service is not installed"))
        connectionState.setText("AI CONNECTION: NOT CONNECTED");
      if(text.startsWith("Disconnected") || text.startsWith("GATT") || text.startsWith("Connected;") || text.startsWith("AI BLE ready") || text.startsWith("BLUETOOTH CONNECTED: Stock Momentum RPC") || text.startsWith("Stock Momentum RPC") || text.contains("service is not installed"))
        connectionHistory.setText("Last connection event: "+text);
    });
    if(text.startsWith("Disconnected") || text.startsWith("Connecting")){
      transports.invalidateSession();
      connectionEpoch.advance();
      /* Android can dispatch delayed GATT callbacks while Activity is closing.
       * Never schedule work on a worker already shut down by onDestroy(). */
      if(!inferenceWorker.isShutdown()) {
        try { inferenceWorker.execute(session::reset); }
        catch(java.util.concurrent.RejectedExecutionException ignored) {
          /* Shutdown raced this callback; no new session will be used. */
        }
      }
    }
    showStatus(text);
  }
  @Override public void received(String text){
    /* Snapshot on the BLE callback thread, not after a UI callback delay.
     * A notification queued before disconnect must never be processed in
     * the next authenticated connection's protocol session. */
    final int receivedEpoch=connectionEpoch.current();
    runOnUiThread(()->{
      if(connectionEpoch.matches(receivedEpoch) && ble.isReady())
        processReceived(text,receivedEpoch);
    });
  }
  private void processReceived(String text,int receivedEpoch){
    if(!connectionEpoch.matches(receivedEpoch) || !ble.isReady())return;
    showStatus("Flipper request received");
    InferenceRouter.Mode selected=InferenceRouter.Mode.valueOf(labels[mode.getSelectedItemPosition()]);
    boolean consent=cloudConsent.isChecked();
    final int consentGeneration=consentEpoch.snapshot();
    final int epoch=receivedEpoch;
    int frameLimit=ble.maxFrameBytes();
    final long transportGeneration=transports.generation();
    inferenceWorker.execute(()->{
      if(!connectionEpoch.matches(epoch))return;
      /* The worker can be queued behind a slow model request. Never use a
       * stale UI consent snapshot to start a later cloud transmission. */
      if(consent && !consentEpoch.current(consentGeneration))return;
      requests.configure(selected,consent);
      String response=session.dispatch(text,frameLimit,requests);
      runOnUiThread(()->{
        if(!connectionEpoch.matches(epoch) || !ble.isReady())return;
        if(consent && (!cloudConsent.isChecked() || !consentEpoch.current(consentGeneration))){
          showStatus("Cloud consent revoked; response discarded");return;
        }
        if(response!=null && !transports.sendFrameIfCurrent(response,transportGeneration))
          showStatus("Cannot respond: AI transport changed or disconnected");
      });
    });
  }
  @Override public void device(BluetoothDevice device,String advertisedName){
    runOnUiThread(()->{
      try{
        String address=device.getAddress();
        if(address==null)return;
        nearby.put(address,device);
        if(advertisedName!=null && !advertisedName.trim().isEmpty())nearbyNames.put(address,advertisedName);
        LinkedHashMap<String,String> labels=new LinkedHashMap<>();
        for(java.util.Map.Entry<String,BluetoothDevice> e:nearby.entrySet()){
          String name=deviceName(e.getValue(),nearbyNames.get(e.getKey()));
          labels.put(e.getKey(),name);
        }
        refreshList(nearbyDevices,labels);
        showStatus("Discovered "+nearby.size()+" nearby BLE device(s).");
      }catch(SecurityException e){showStatus("Allow Nearby devices permission to view results.");}
    });
  }
  @Override public void onDestroy(){
    connectionEpoch.advance();
    ble.stopScan();transports.close();
    cloud.clear();localModel.clear();inferenceWorker.shutdownNow();
    super.onDestroy();
  }
}
