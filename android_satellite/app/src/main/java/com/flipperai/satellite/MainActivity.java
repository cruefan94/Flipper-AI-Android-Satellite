package com.flipperai.satellite;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
  // Bluetooth scan callbacks can arrive dozens of times per second. The
  // spinner is refreshed at most once per short interval, and keeps its ID.
  private final Handler pickerUi=new Handler(Looper.getMainLooper());
  private BlePickerRows pairedRows=BlePickerRows.empty();
  private BlePickerRows nearbyRows=BlePickerRows.empty();
  private boolean nearbyRefreshScheduled=false;
  private final Runnable nearbyRefresh=()->{
    nearbyRefreshScheduled=false;
    if(isFinishing() || isDestroyed())return;
    refreshList(nearbyDevices,nearbyLabels());
    showStatus("Discovered "+nearby.size()+" nearby BLE device(s). Select the Flipper you want.");
  };
  // Offline models are managed in MODELS, but BLE must recognize an import
  // after returning from that screen without recreating the whole Activity.
  private volatile String activeModelRevision="";
  private boolean scanAfterPermission;
  private boolean refreshAfterPermission;
  private final String[] labels={"OFFLINE","HYBRID","ONLINE"};
  private Spinner mode;
  private final WorkerCloudEngine cloud=new WorkerCloudEngine();
  private final LocalModelEngine localModel=new LocalModelEngine();
  private EmbeddedLlmEngine embeddedModel;
  private InferenceRouter router;
  private final ExecutorService inferenceWorker=Executors.newSingleThreadExecutor();
  private final BleSessionJobs bleSessionJobs=new BleSessionJobs(inferenceWorker);
  private CheckBox cloudConsent;
  private final ConsentEpoch consentEpoch=new ConsentEpoch();
  private SatelliteRequestHandler requests;
  private final ProtocolSession session=new ProtocolSession();
  private final SessionEpoch connectionEpoch=new SessionEpoch();
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    embeddedModel=new EmbeddedLlmEngine(getCacheDir().getAbsolutePath());
    if(EmbeddedModelStore.installed(this))
      embeddedModel.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
    activeModelRevision=modelRevision();
    router=new InferenceRouter(
      new OfflineModelOrDiagnostics(new EmbeddedOrLocalEngine(embeddedModel,localModel),
        new OfflineDiagnosticEngine()),cloud);
    // Restore connection details only. BLE/cloud consent is never persisted.
    CloudCredentialStore.Credentials savedCloud=CloudCredentialStore.load(this);
    if(savedCloud!=null) {
      try {cloud.configure(savedCloud.endpoint,savedCloud.token);}
      catch(Exception invalid){CloudCredentialStore.clear(this);cloud.clear();}
    }
    requests=new SatelliteRequestHandler(router);
    ble=new BleSatellite(this,this);
    transports.register(ble);
    ScrollView scroller=new ScrollView(this);
    scroller.setBackgroundColor(SatelliteUi.BACKGROUND);
    root=new LinearLayout(this);
    root.setPadding(SatelliteUi.dp(this,15),SatelliteUi.dp(this,15),
      SatelliteUi.dp(this,15),SatelliteUi.dp(this,24));
    root.setOrientation(LinearLayout.VERTICAL);
    scroller.addView(root);
    setContentView(scroller);
    TextView header=new TextView(this);
    header.setText("ᛒ  FLIPPER / BLE");
    header.setTextColor(SatelliteUi.ORANGE);
    header.setTextSize(25);root.addView(header);
    SatelliteUi.backHome(this,root);
    TextView sectionIntro=SatelliteUi.text(this,
      "Pair and connect your Flipper Zero. AI model management and chat are on separate screens.",
      14,SatelliteUi.MUTED,false);
    root.addView(sectionIntro);
    SatelliteUi.sectionTitle(this,root,"CONNECTION STATUS");
    status=SatelliteUi.text(this,"No AI connection",15,SatelliteUi.ORANGE,false);
    root.addView(status);
    TextView offlineHelp=SatelliteUi.text(this,
      "Offline AI runs on your Android phone when a compatible model is installed. Bluetooth pairing does not by itself verify the custom AI channel.",
      14,SatelliteUi.MUTED,false);
    offlineHelp.setPadding(0,SatelliteUi.dp(this,8),0,SatelliteUi.dp(this,10));
    root.addView(offlineHelp);
    connectionState=SatelliteUi.text(this,"",19,SatelliteUi.WHITE,true);
    connectionState.setText("AI CONNECTION: NOT CONNECTED");
    connectionState.setPadding(SatelliteUi.dp(this,12),SatelliteUi.dp(this,14),
      SatelliteUi.dp(this,12),SatelliteUi.dp(this,14));
    connectionState.setBackground(SatelliteUi.panel(this,false));
    root.addView(connectionState);
    connectionHistory=SatelliteUi.text(this,"",14,SatelliteUi.MUTED,false);
    connectionHistory.setText("Bluetooth device discovery is separate from the AI channel.");
    root.addView(connectionHistory);
    SatelliteUi.sectionTitle(this,root,"SELECT DEVICE");
    TextView categoryPrompt=SatelliteUi.text(this,
      "Choose paired or discoverable devices, then select the Flipper you want.",
      14,SatelliteUi.MUTED,false);
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
    pairedSection.setPadding(12,12,12,12);
    pairedSection.setBackground(SatelliteUi.panel(this,false));
    root.addView(pairedSection);
    pairedHeading=SatelliteUi.text(this,"PAIRED / CONNECTED DEVICES",14,SatelliteUi.ORANGE,true);
    pairedSection.addView(pairedHeading);
    devices=new Spinner(this);pairedSection.addView(devices);
    sectionButton(pairedSection,"Select paired device",()->selectFrom(devices,false));
    sectionButton(pairedSection,"Refresh paired devices",()->{
      if(permissions())showPairedDevices();
      else refreshAfterPermission=true;
    });
    discoverableSection=new LinearLayout(this);
    discoverableSection.setOrientation(LinearLayout.VERTICAL);
    discoverableSection.setPadding(12,12,12,12);
    discoverableSection.setBackground(SatelliteUi.panel(this,false));
    root.addView(discoverableSection);
    nearbyHeading=SatelliteUi.text(this,"DISCOVERABLE DEVICES",14,SatelliteUi.ORANGE,true);
    discoverableSection.addView(nearbyHeading);
    nearbyDevices=new Spinner(this);discoverableSection.addView(nearbyDevices);
    sectionButton(discoverableSection,"Select discovered device",()->selectFrom(nearbyDevices,true));
    sectionButton(discoverableSection,"Scan for nearby devices",()->{
      if(permissions())beginScan();
      else scanAfterPermission=true;
    });
    deviceInfo=SatelliteUi.text(this,"No device selected.",14,SatelliteUi.WHITE,false);
    deviceInfo.setPadding(0,SatelliteUi.dp(this,10),0,SatelliteUi.dp(this,8));
    root.addView(deviceInfo);
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
    SatelliteUi.sectionTitle(this,root,"BLUETOOTH ACTIONS");
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
    button("Disconnect",()->{if(permissions())ble.disconnect();});
    button("TEST MOMENTUM RPC PING",()->ble.sendStockRpcPing());
    SatelliteUi.sectionTitle(this,root,"ADVANCED");
    button("Advanced AI settings and diagnostics",()->{
      advancedVisible=!advancedVisible;
      advancedSection.setVisibility(advancedVisible?android.view.View.VISIBLE:android.view.View.GONE);
    });
    advancedSection=new LinearLayout(this);
    advancedSection.setOrientation(LinearLayout.VERTICAL);
    advancedSection.setPadding(12,12,12,12);
    advancedSection.setBackground(SatelliteUi.panel(this,false));
    advancedSection.setVisibility(android.view.View.GONE);
    root.addView(advancedSection);
    mode=new Spinner(this);
    mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    advancedSection.addView(mode);
    TextView builtInLabel=SatelliteUi.text(this,
      "Manage imported .litertlm/ZIP models in OFFLINE MODELS. " +
      "Import and live testing are kept on that dedicated screen; Bluetooth " +
      "will use the installed model when you return.",
      14,SatelliteUi.MUTED,false);
    advancedSection.addView(builtInLabel);
    advancedButton("OPEN OFFLINE MODELS",()->SatelliteUi.open(this,ModelsActivity.class));
    TextView localLabel=new TextView(this);
    localLabel.setText("Alternative offline backend: an OpenAI-compatible server on this phone, restricted to 127.0.0.1. Embedded model takes priority when installed.");
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
    note.setText("Optional cloud: ASK prompts go to your HTTPS Worker only with consent. Worker address and device token are encrypted on this phone; the Gemini API key stays only in Cloudflare.");
    advancedSection.addView(note);
    EditText url=new EditText(this);
    url.setSingleLine(true);url.setHint("https://YOUR-WORKER.workers.dev/ask");
    if(savedCloud!=null)url.setText(savedCloud.endpoint);
    advancedSection.addView(url);
    EditText token=new EditText(this);
    token.setSingleLine(true);token.setHint("Worker device token (encrypted on phone)");
    if(savedCloud!=null)token.setText(savedCloud.token);
    token.setInputType(129);advancedSection.addView(token);
    advancedButton("Save cloud endpoint",()->{
      try {
        String endpoint=url.getText().toString();
        String deviceToken=token.getText().toString();
        // A saved token never grants new permission to send ASK prompts.
        CloudCredentialStore.save(this,endpoint,deviceToken);
        cloud.configure(endpoint,deviceToken);
        showStatus("Cloud Worker settings saved securely. Grant consent separately.");
      }catch(Exception e){cloud.clear();cloudConsent.setChecked(false);showStatus("Cloud settings not saved: "+e.getMessage());}
    });
    cloudConsent=new CheckBox(this);
    cloudConsent.setText("I authorize sending ASK prompts to my cloud Worker and AI provider");
    cloudConsent.setChecked(false);
    cloudConsent.setOnCheckedChangeListener((button,checked)->{
      consentEpoch.changed();
      if(!checked)cloud.revokeActive();
    });
    advancedSection.addView(cloudConsent);
    advancedButton("Disable and clear cloud",()->{
      cloudConsent.setChecked(false);
      cloud.clear();
      CloudCredentialStore.clear(this);
      url.setText("");
      token.setText("");
      showStatus("Saved Worker credentials deleted and cloud consent revoked");
    });
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
          ? "AI GATT transport ready; HELLO/ASK exchange must still be verified on the Flipper"
          : ble.hasStockRpc() ? "Stock Momentum RPC detected. Indications: "+(ble.isStockRpcSubscribed()?"enabled":"not enabled")+". Received: "+ble.stockRpcNotificationCount()+". AI bridge not installed."
          : "No authorized AI GATT or stock Momentum RPC session.");
    });
    advancedButton("Copy BLE hardware test report",()->{
      // No MAC addresses, tokens, device IDs or user prompts are included.
      String summary="Flipper AI BLE hardware test (phone-side snapshot)\n"
        +"AI GATT transport: "+(transports.isReady()?"READY (HELLO/ASK not confirmed)":"NOT READY")+"\n"
        +"Stock Momentum RPC: "+(ble.hasStockRpc()?"DETECTED":"NOT DETECTED")+"\n"
        +"RPC indications: "+(ble.isStockRpcSubscribed()?"ENABLED":"NOT ENABLED")+"\n"
        +"RPC notifications received: "+ble.stockRpcNotificationCount()+"\n"
        +"Offline model file: "+(EmbeddedModelStore.installed(this)?"IMPORTED":"NOT IMPORTED")+"\n"
        +"Note: model file presence does not prove live AI inference.\n"
        +"Dedicated AI BLE and protocol must be tested on physical custom Momentum firmware.";
      android.content.ClipboardManager clipboard=
        (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if(clipboard!=null){
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Flipper AI BLE test",summary));
        showStatus("Bluetooth test report copied. Paste it into the project chat.");
      } else showStatus("Android clipboard unavailable.");
    });
    TextView caveat=new TextView(this);
    caveat.setText("Momentum functions remain on Flipper. Full offline AI uses an imported embedded .litertlm model or your optional localhost model server. Without a working model, diagnostic rules remain available. Cloud AI always requires opt-in.");
    advancedSection.addView(caveat);
  }
  private void beginScan(){
    pickerUi.removeCallbacks(nearbyRefresh);
    nearbyRefreshScheduled=false;
    nearby.clear();nearbyNames.clear();
    selectedAddress=null;
    deviceInfo.setText("No device selected. Scanning for nearby BLE devices...");
    refreshList(nearbyDevices,new LinkedHashMap<String,String>());
    showStatus("Scanning for nearby devices...");
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
        discovered.clear();deviceLabels.clear();
        refreshList(devices,deviceLabels);
        showStatus("Enable Bluetooth to view paired devices");return;
      }
      java.util.Set<BluetoothDevice> bonded=adapter.getBondedDevices();
      discovered.clear();deviceLabels.clear();
      if(bonded==null || bonded.isEmpty()){
        refreshList(devices,deviceLabels);
        showStatus("No paired devices found. Pair your Flipper in Android Bluetooth settings.");return;
      }
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
    BlePickerRows previous=spinner==nearbyDevices?nearbyRows:pairedRows;
    String selectedId=previous.idAt(spinner.getSelectedItemPosition());
    BlePickerRows next=BlePickerRows.from(labels);
    if(spinner==nearbyDevices)nearbyRows=next;
    else pairedRows=next;
    spinner.setAdapter(new ArrayAdapter<String>(this,
      android.R.layout.simple_spinner_dropdown_item,next.labels()));
    // Never mistake two same-named devices for one another or reset the user's
    // pending selection each time a BLE advertisement arrives.
    spinner.setSelection(next.positionFor(selectedId));
  }
  private void selectFrom(Spinner spinner,boolean fromNearby){
    if(fromNearby!=discoverableMode){
      showStatus("Switch device category before selecting.");return;
    }
    BlePickerRows rows=fromNearby?nearbyRows:pairedRows;
    String id=rows.idAt(spinner.getSelectedItemPosition());
    if(id==null){
      selectedAddress=null;
      deviceInfo.setText("No device selected.");
      return;
    }
    BluetoothDevice device=fromNearby?nearby.get(id):discovered.get(id);
    if(device==null){
      selectedAddress=null;
      deviceInfo.setText("Device no longer in this list. Refresh or scan again.");
      return;
    }
    selectedAddress=id;
    (fromNearby?devices:nearbyDevices).setSelection(0);
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
    SatelliteUi.action(this,section,name,action);
  }
  private void advancedButton(String name,Runnable action){
    SatelliteUi.action(this,advancedSection,name,action);
  }
  private void button(String name,Runnable action){
    SatelliteUi.action(this,root,name,action);
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
      // Stop queued and running BLE requests and revoke any HTTP transmission.
      // Unrelated Android model imports/tests stay on the same worker untouched.
      bleSessionJobs.cancelAll();
      cloud.revokeActive();
      localModel.revokeActive();
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
    if(!bleSessionJobs.submit(()->{
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
    }))showStatus("BLE request rejected: AI worker has stopped");
  }
  @Override public void device(BluetoothDevice device,String advertisedName){
    runOnUiThread(()->{
      try{
        String address=device.getAddress();
        if(address==null)return;
        boolean changed=!nearby.containsKey(address);
        nearby.put(address,device);
        if(advertisedName!=null && !advertisedName.trim().isEmpty()){
          if(!advertisedName.equals(nearbyNames.get(address)))changed=true;
          nearbyNames.put(address,advertisedName);
        }
        if(changed && !nearbyRefreshScheduled){
          nearbyRefreshScheduled=true;
          pickerUi.postDelayed(nearbyRefresh,300);
        }
      }catch(SecurityException e){showStatus("Allow Nearby devices permission to view results.");}
    });
  }
  private String modelRevision(){
    java.io.File installed=EmbeddedModelStore.modelFile(this);
    return installed.isFile() && installed.length()>0
      ?installed.length()+":"+installed.lastModified():"not-installed";
  }
  @Override protected void onResume(){
    super.onResume();
    final String revision=modelRevision();
    if(!revision.equals(activeModelRevision) && !inferenceWorker.isShutdown()){
      activeModelRevision=revision;
      inferenceWorker.execute(()->{
        // If a model is being imported while returning from MODELS, don't
        // reconfigure from an obsolete snapshot or interrupt active inference.
        if(!revision.equals(modelRevision()))return;
        try{
          if(EmbeddedModelStore.installed(this))
            embeddedModel.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
          else embeddedModel.clear();
          showStatus(EmbeddedModelStore.installed(this)
            ?"Updated offline model is available to BLE AI."
            :"No offline model installed. Basic diagnostics remain available.");
        }catch(Exception e){
          showStatus("Offline model could not be loaded for BLE: "+e.getMessage());
        }
      });
    }
  }
  @Override public void onDestroy(){
    scanEpoch++;
    pickerUi.removeCallbacks(nearbyRefresh);
    nearbyRefreshScheduled=false;
    connectionEpoch.advance();
    bleSessionJobs.close();
    ble.stopScan();transports.close();
    cloud.clear();localModel.clear();
    // Release native model after queued inference completes; never block UI.
    if(!inferenceWorker.isShutdown()){
      inferenceWorker.execute(()->embeddedModel.clear());
      inferenceWorker.shutdown();
    }
    super.onDestroy();
  }
}
