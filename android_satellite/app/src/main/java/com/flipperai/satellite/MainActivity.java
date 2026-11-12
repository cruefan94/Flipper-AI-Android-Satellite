package com.flipperai.satellite;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
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
  private LinearLayout root;
  private final LinkedHashMap<String,BluetoothDevice> discovered=new LinkedHashMap<>();
  private final LinkedHashMap<String,String> deviceLabels=new LinkedHashMap<>();
  private Spinner devices;
  private boolean scanAfterPermission;
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
    status.setText("Idle — no Flipper AI BLE service installed yet");root.addView(status);
    devices=new Spinner(this);root.addView(devices);
    button("Scan for Flipper",()->{
      if(permissions())beginScan();
      else scanAfterPermission=true;
    });
    button("Connect selected",()->{
      if(!permissions())return;
      BluetoothDevice selected=selectedDevice();
      if(selected==null){showStatus("Scan and select a Flipper first");return;}
      ble.connect(selected);
    });
    button("Pair selected",()->{
      if(!permissions())return;
      BluetoothDevice selected=selectedDevice();
      if(selected==null){showStatus("Scan and select a Flipper first");return;}
      ble.pair(selected);
    });
    button("Reconnect",()->{if(permissions())ble.reconnect();});
    button("Disconnect",()->{if(permissions())ble.disconnect();});
    mode=new Spinner(this);
    mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    root.addView(mode);
    TextView localLabel=new TextView(this);
    localLabel.setText("Offline language model (optional): install a local OpenAI-compatible server and model on this phone. Only 127.0.0.1 is accepted; no internet URL is allowed.");
    root.addView(localLabel);
    EditText localUrl=new EditText(this);
    localUrl.setSingleLine(true);
    localUrl.setHint("http://127.0.0.1:8080/v1/chat/completions");
    root.addView(localUrl);
    EditText localName=new EditText(this);
    localName.setSingleLine(true);localName.setHint("Local model name (e.g. local)");
    root.addView(localName);
    button("Use installed offline model",()->{
      try{
        localModel.configure(localUrl.getText().toString(),localName.getText().toString());
        showStatus("On-device local model endpoint configured; network access restricted to loopback");
      }catch(Exception e){showStatus(e.getMessage());}
    });
    button("Disable local model",()->{
      localModel.clear();showStatus("Local model disabled; offline diagnostic rules remain available");
    });
    button("Test local model connection",()->{
      if(!localModel.ready()){
        showStatus("Configure the local on-device model endpoint first");return;
      }
      showStatus("Testing local model response...");
      inferenceWorker.execute(()->{
        try{
          String sample=localModel.answer("Reply with OK.");
          showStatus("Local model responded: "+sample);
        }catch(Exception e){
          showStatus("Local model not responding: "+e.getMessage());
        }
      });
    });
    TextView note=new TextView(this);
    note.setText("Optional cloud: ASK prompts go to your configured HTTPS Worker only when consent is enabled. Device tokens stay in memory; offline diagnostics use no internet.");
    root.addView(note);
    EditText url=new EditText(this);
    url.setSingleLine(true);url.setHint("https://YOUR-WORKER.workers.dev/ask");root.addView(url);
    EditText token=new EditText(this);
    token.setSingleLine(true);token.setHint("Worker device token (memory only)");
    token.setInputType(129);root.addView(token);
    button("Configure cloud endpoint",()->{
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
    root.addView(cloudConsent);
    button("Disable and clear cloud",()->{cloudConsent.setChecked(false);cloud.clear();showStatus("Cloud credentials cleared and consent revoked");});
    EditText input=new EditText(this);
    input.setHint("Protocol test text (no private data)");root.addView(input);
    button("Run offline diagnostic",()->{
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
    root.addView(transportNote);
    button("Check AI BLE channel",()->{
      if(!permissions())return;
      showStatus(transports.isReady()
          ? "AI GATT connected; Flipper must initiate HELLO by notification"
          : "No authorized AI GATT session. Firmware service and pairing required.");
    });
    TextView caveat=new TextView(this);
    caveat.setText("Momentum functions remain on Flipper. Offline LLM inference works only with a separately installed local model server; otherwise the app uses diagnostic rules. Localhost model requests never access the internet.");
    root.addView(caveat);
  }
  private void beginScan(){
    discovered.clear();
    deviceLabels.clear();
    devices.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Scanning for nearby BLE devices..."}));
    ble.startScan();
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
      if(isFinishing() || isDestroyed())return;
      if(discovered.isEmpty())showStatus("Scan finished: no BLE devices found. Check Flipper Bluetooth, Android Nearby devices permission, and keep devices close.");
      else showStatus("Scan finished: "+discovered.size()+" BLE device(s) found. Select your Flipper by Bluetooth address.");
    },12500);
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode!=22)return;
    boolean granted=grantResults.length>0;
    for(int result:grantResults)if(result!=PackageManager.PERMISSION_GRANTED)granted=false;
    if(!granted){scanAfterPermission=false;showStatus("Bluetooth scan requires Nearby devices permission (or Location on older Android).");return;}
    if(scanAfterPermission){scanAfterPermission=false;beginScan();}
    else showStatus("Bluetooth permission granted. Tap Scan for Flipper.");
  }
  private BluetoothDevice selectedDevice(){
    if(devices==null || devices.getSelectedItem()==null)return null;
    // Lookup by the displayed address, not scan discovery order.
    String label=devices.getSelectedItem().toString();
    for(java.util.Map.Entry<String,String> entry:deviceLabels.entrySet())
      if(entry.getValue().equals(label))return discovered.get(entry.getKey());
    return null;
  }
  private void button(String name,Runnable action){
    Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());root.addView(b);
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
  @Override public void device(BluetoothDevice device){
    runOnUiThread(()->{
      String address;
      try{address=device.getAddress();}
      catch(SecurityException unavailable){showStatus("Nearby devices permission required to display scan results");return;}
      if(address==null)return;
      String name=null;
      try{name=device.getName();}
      catch(SecurityException ignored){/* Name may be unavailable without CONNECT permission. */}
      if(name==null || name.trim().isEmpty())name="Unknown BLE device";
      // Keep the address visible to distinguish devices with the same name.
      String display=name.trim()+" ("+address+")";
      String previousAddress=null;
      if(devices.getSelectedItem()!=null){
        String previousLabel=devices.getSelectedItem().toString();
        for(java.util.Map.Entry<String,String> entry:deviceLabels.entrySet())
          if(previousLabel.equals(entry.getValue())){previousAddress=entry.getKey();break;}
      }
      discovered.put(address,device);
      deviceLabels.put(address,display);
      String[] values=deviceLabels.values().toArray(new String[0]);
      devices.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values));
      if(previousAddress!=null){
        int i=0;
        for(String key:deviceLabels.keySet()){
          if(previousAddress.equals(key)){devices.setSelection(i);break;}
          i++;
        }
      }
      showStatus("Found "+discovered.size()+" nearby BLE device(s). Select Sarah or your Flipper's advertised name.");
    });
  }
  @Override public void onDestroy(){
    connectionEpoch.advance();
    ble.stopScan();transports.close();
    cloud.clear();localModel.clear();inferenceWorker.shutdownNow();
    super.onDestroy();
  }
}
