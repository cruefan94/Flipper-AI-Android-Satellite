package com.flipperai.satellite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.content.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Dedicated opt-in on-device AI chat. No prompts leave the phone. */
public final class AiChatActivity extends Activity {
  private final LocalModelEngine model=new LocalModelEngine();
  private NanoModelEngine nano;
  private EmbeddedModelEngine embedded;
  private boolean useEmbedded=true;
  private volatile boolean closing=false;
  private volatile boolean loadingEmbedded=false;
  private static final int IMPORT_TASK=402;
  private boolean useNano=false;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Handler ui=new Handler(Looper.getMainLooper());
  private TextView history,status;
  private EditText prompt,endpoint,modelName;
  private Button send;
  private boolean busy=false;
  private ScrollView transcriptScroll;
  private static final int IMPORT_MODEL=401;
  private String expectedModelSha="";

  private TextView modelHint;
  private int completedRequests=0;
  private final StringBuilder transcript=new StringBuilder();
  private static final String PREFS="ai_local_settings_v1";
  private int generation=0;
  private final int bg=Color.rgb(10,12,16),panel=Color.rgb(28,32,39),cyan=Color.rgb(255,151,22);
  @Override public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setPadding(dp(14),dp(8),dp(14),dp(12));frame.setBackgroundColor(bg);
    frame.setFitsSystemWindows(true);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);setContentView(frame);
    TextView title=label("AI ASSISTANT",22,cyan);frame.addView(title);
    status=label("OFFLINE • Independent model runtime not configured",13,Color.LTGRAY);frame.addView(status);
    modelHint=label("Independent offline AI is preferred. Configure a local runtime in Model Setup. Gemini Nano is optional.",12,Color.LTGRAY);frame.addView(modelHint);
    embedded=new EmbeddedModelEngine(getApplicationContext());
    try{nano=new NanoModelEngine();}catch(Throwable e){modelHint.setText("Gemini Nano initialization unavailable: "+e.getClass().getSimpleName());}

    modelHint.setOnClickListener(v->{setup.setVisibility(View.VISIBLE);setupScroll.setVisibility(View.VISIBLE);});
    transcriptScroll=new ScrollView(this);history=label("Conversation will appear here. Configure a local model to begin.",15,Color.WHITE);history.setTextIsSelectable(true);history.setPadding(14,16,14,16);history.setBackgroundColor(panel);transcriptScroll.addView(history);
    frame.addView(transcriptScroll,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);frame.addView(actions);
    button(actions,"BLE",()->setPrompt("Explain Bluetooth Low Energy."));
    button(actions,"GATT",()->setPrompt("What is a GATT service?"));
    button(actions,"CLEAR",()->{cancel();transcript.setLength(0);completedRequests=0;history.setText("Conversation cleared.");});
    button(actions,"COPY",()->{
      if(transcript.length()==0){status.setText("Nothing to copy.");return;}
      android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if(cb!=null){cb.setPrimaryClip(android.content.ClipData.newPlainText("AI conversation",transcript.toString()));status.setText("Copied. Clipboard may be visible to other apps.");}
    });
    LinearLayout composer=new LinearLayout(this);composer.setOrientation(LinearLayout.HORIZONTAL);composer.setGravity(Gravity.BOTTOM);frame.addView(composer);
    prompt=new EditText(this);prompt.setSingleLine(false);prompt.setMaxLines(3);prompt.setMinLines(1);prompt.setHint("Ask your AI...");prompt.setHintTextColor(Color.LTGRAY);prompt.setTextColor(Color.WHITE);
    composer.addView(prompt,new LinearLayout.LayoutParams(0,-2,1));
    send=button(composer,"SEND",this::ask);
    LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);frame.addView(tools);
    button(tools,"STOP",this::cancel);
    button(tools,"MODEL SETUP",()->{boolean show=setupScroll.getVisibility()!=View.VISIBLE;setupScroll.setVisibility(show?View.VISIBLE:View.GONE);setup.setVisibility(show?View.VISIBLE:View.GONE);});
    button(tools,"BACK",this::finish);
    setup=new LinearLayout(this);setup.setOrientation(LinearLayout.VERTICAL);setup.setVisibility(View.GONE);
    setupScroll=new ScrollView(this);setupScroll.setVisibility(View.GONE);setupScroll.addView(setup);frame.addView(setupScroll,new LinearLayout.LayoutParams(-1,0,0.8f));
    TextView instructions=label("LOCAL AI SETUP: A compatible on-phone model server is required. This app does not install one. Enter a loopback URL and model ID only after installing a runtime. No cloud access is enabled here.",13,Color.LTGRAY);setup.addView(instructions);
    endpoint=new EditText(this);endpoint.setSingleLine(true);endpoint.setHint("http://127.0.0.1:8080/v1/chat/completions");endpoint.setTextColor(Color.WHITE);endpoint.setHintTextColor(Color.LTGRAY);setup.addView(endpoint);
    modelName=new EditText(this);modelName.setSingleLine(true);modelName.setHint("Local model ID (required)");modelName.setTextColor(Color.WHITE);modelName.setHintTextColor(Color.LTGRAY);setup.addView(modelName);
    android.content.SharedPreferences saved=getSharedPreferences(PREFS,MODE_PRIVATE);
    useNano=saved.getBoolean("use_nano",false);
    useEmbedded=saved.getBoolean("use_embedded",true);
    java.io.File[] stored=new java.io.File(getFilesDir(),"ai_models").listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.task"));
    final String selectedModel=saved.getString("embedded_model","");
    java.io.File restore=null;
    if(stored!=null){for(java.io.File candidate:stored)if(candidate.getName().equals(selectedModel))restore=candidate;
      if(restore==null&&stored.length==1)restore=stored[0];}
    if(restore!=null){loadingEmbedded=true;final java.io.File selectedRestore=restore;status.setText("Restoring imported offline AI model...");worker.execute(()->{String message;try{embedded.load(selectedRestore);message="Embedded offline AI ready.";}catch(Exception e){message="Imported model could not load: "+e.getMessage();}final String result=message;loadingEmbedded=false;ui.post(()->{if(!isFinishing()&&!isDestroyed())status.setText(result);});});}
    if(useNano)modelHint.setText("Gemini Nano selected (optional). This device may not support it. Choose independent model in setup.");
    endpoint.setText(saved.getString("endpoint",""));
    modelName.setText(saved.getString("model",""));
    button(setup,"IMPORT OFFLINE .TASK MODEL",()->{
      Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("application/octet-stream");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,IMPORT_TASK);
    });
    button(setup,"SELECT IMPORTED .TASK MODEL",()->{
      java.io.File folder=new java.io.File(getFilesDir(),"ai_models");
      java.io.File[] choices=folder.listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.task"));
      if(choices==null||choices.length==0){status.setText("No .task model imported. Use IMPORT OFFLINE .TASK MODEL.");return;}
      java.util.Arrays.sort(choices,(a,b)->a.getName().compareTo(b.getName()));
      String[] labels=new String[choices.length];
      for(int i=0;i<choices.length;i++)labels[i]=choices[i].getName().substring(0,12)+"… ("+(choices[i].length()/1048576)+" MiB)";
      new android.app.AlertDialog.Builder(this).setTitle("Choose offline AI model").setItems(labels,(dialog,index)->{
        if(busy||loadingEmbedded){status.setText("Wait for current inference or model loading.");return;}
        final java.io.File chosen=choices[index];loadingEmbedded=true;status.setText("Loading selected offline model...");
        worker.execute(()->{String message;boolean ok=false;
          try{embedded.load(chosen);ok=true;message="Offline AI model loaded. Ready to chat.";}catch(Exception e){message="Model load failed: "+e.getMessage();}
          final boolean loaded=ok;final String result=message;loadingEmbedded=false;
          ui.post(()->{if(isFinishing()||isDestroyed())return;
            if(loaded){useEmbedded=true;useNano=false;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("embedded_model",chosen.getName()).putBoolean("use_embedded",true).putBoolean("use_nano",false).apply();}
            status.setText(result);
          });
        });
      }).setNegativeButton("CANCEL",null).show();
    });
    button(setup,"TEST EMBEDDED AI",()->{
      if(loadingEmbedded){status.setText("Model loading; please wait.");return;}
      if(!embedded.ready()){status.setText("Import a compatible .task model first.");return;}
      useEmbedded=true;useNano=false;saved.edit().putBoolean("use_embedded",true).putBoolean("use_nano",false).apply();
      if(busy){status.setText("Wait for current response.");return;}
      request("Say hello.",false);
    });
    button(setup,"USE EMBEDDED OFFLINE AI",()->{useEmbedded=true;useNano=false;saved.edit().putBoolean("use_embedded",true).putBoolean("use_nano",false).apply();status.setText(embedded.ready()?"Embedded AI ready":"Import a compatible .task model to start offline AI.");});
    button(setup,"CHECK GEMINI NANO",this::checkNano);
    button(setup,"AI ENGINE: GEMINI NANO",()->{useNano=true;useEmbedded=false;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("use_nano",true).putBoolean("use_embedded",false).apply();status.setText("Gemini Nano selected; check on-device AI availability.");});
    button(setup,"AI ENGINE: OWN LOCAL MODEL",()->{useNano=false;useEmbedded=false;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("use_nano",false).putBoolean("use_embedded",false).apply();status.setText("Independent model selected; requires installed local runtime.");});
    button(setup,"MANAGE IMPORTED MODELS",()->{
      java.io.File[] models=VerifiedModelStore.list(this);
      if(models.length==0){status.setText("No verified GGUF weights imported.");return;}
      String[] entries=new String[models.length];
      for(int i=0;i<models.length;i++)entries[i]=models[i].getName().substring(9,21)+"… ("+(models[i].length()/1048576)+" MiB)";
      new android.app.AlertDialog.Builder(this).setTitle("Imported model weights").setItems(entries,(dialog,index)->{
        final String name=models[index].getName();
        new android.app.AlertDialog.Builder(this).setTitle("Remove imported model?").setMessage("Delete "+name+" from private app storage? This cannot be undone.").setNegativeButton("CANCEL",null).setPositiveButton("DELETE",(confirm,which)->{
          try{boolean removed=VerifiedModelStore.delete(this,name);status.setText(removed?"Model weights deleted.":"Model already absent.");}
          catch(Exception e){status.setText("Deletion failed: "+e.getMessage());}
        }).show();
      }).setNegativeButton("CLOSE",null).show();
    });
    button(setup,"IMPORTED MODEL STATUS",()->{
      java.io.File folder=new java.io.File(getFilesDir(),"models");java.io.File[] files=folder.listFiles((dir,name)->name.startsWith("verified-")&&name.endsWith(".gguf"));
      status.setText(files==null||files.length==0?"No verified model weights imported.":"Verified model weights stored: "+files.length+". Native inference runtime still required.");
    });
    button(setup,"VERIFY & IMPORT GGUF",()->{
      final EditText hash=new EditText(this);hash.setSingleLine(true);hash.setHint("Expected SHA-256 (64 hex characters)");
      new android.app.AlertDialog.Builder(this).setTitle("Verify independent model").setMessage("Choose a GGUF file and supply its trusted publisher checksum. Verified weights will be copied to private app storage. This does not install an inference runtime.").setView(hash).setNegativeButton("CANCEL",null).setPositiveButton("CHOOSE FILE",(d,w)->{
        String value=hash.getText().toString().trim();if(!value.matches("[0-9a-fA-F]{64}")){status.setText("A trusted 64-character SHA-256 checksum is required.");return;}
        expectedModelSha=value;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("application/octet-stream");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,IMPORT_MODEL);
      }).show();
    });
    button(setup,"CHECK MODEL STORAGE",()->status.setText(LocalModelReadiness.inspect(getFilesDir(),2L*1024*1024*1024)));
    button(setup,"INDEPENDENT MODEL OPTIONS",()->{
      StringBuilder choices=new StringBuilder("Model candidates (not installed):");
      for(AiModelCatalog.Model item:AiModelCatalog.candidates())choices.append("\n").append(item.family).append(" — ").append(item.format).append(" — ").append(item.note);
      new android.app.AlertDialog.Builder(this).setTitle("Independent AI model roadmap").setMessage(choices.toString()).setPositiveButton("OK",null).show();
    });
    button(setup,"CONFIGURE LOCAL MODEL",()->{
      try{model.configure(endpoint.getText().toString().trim(),modelName.getText().toString().trim());
        saved.edit().putString("endpoint",endpoint.getText().toString().trim()).putString("model",modelName.getText().toString().trim()).apply();
        status.setText("OFFLINE • Local endpoint configured (not yet verified)");}
      catch(Exception e){status.setText("Model configuration failed: "+e.getMessage());}
    });
    button(setup,"MODEL STATUS",()->status.setText(model.ready()?"OFFLINE • Local model endpoint configured; connection not verified":"OFFLINE • No local model configured"));
    button(setup,"TEST LOCAL MODEL",()->{
      if(!model.ready()){status.setText("Configure the local model first.");return;}
      if(busy){status.setText("Wait for the current response or press Stop.");return;}
      request("Reply with OK.",false);
    });
    button(setup,"DISABLE LOCAL MODEL",()->{cancel();model.clear();saved.edit().remove("endpoint").remove("model").apply();endpoint.setText("");modelName.setText("");status.setText("OFFLINE • Model disabled and settings cleared");});
    if(!saved.getString("endpoint","").isEmpty()&&!saved.getString("model","").isEmpty()){
      try{model.configure(saved.getString("endpoint",""),saved.getString("model",""));status.setText("OFFLINE • Saved local endpoint restored; not yet verified");}
      catch(Exception ignored){saved.edit().clear().apply();status.setText("OFFLINE • Saved model settings invalid");}
    }

  }
  private LinearLayout setup;
  private ScrollView setupScroll;
  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
    if(requestCode==IMPORT_TASK){
      loadingEmbedded=true;status.setText("Importing and loading embedded AI model...");final android.net.Uri selected=data.getData();
      worker.execute(()->{String message;
        try{ImportedModelStore.Imported imported=ImportedModelStore.importTask(getApplicationContext(),selected);embedded.load(imported.file);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("embedded_model",imported.file.getName()).apply();message="Embedded offline AI loaded. Ready for chat.";
        }catch(Exception e){message="Embedded model import/load failed: "+e.getMessage();}
        final String outcome=message;loadingEmbedded=false;ui.post(()->{if(!isDestroyed()&&!isFinishing()){if(embedded.ready()){useEmbedded=true;useNano=false;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("use_embedded",true).putBoolean("use_nano",false).apply();}status.setText(outcome);}});
      });return;
    }
    if(requestCode!=IMPORT_MODEL)return;
    final android.net.Uri uri=data.getData();final String expected=expectedModelSha;expectedModelSha="";
    status.setText("Verifying model checksum locally...");
    worker.execute(()->{
      String message;
      try{
        VerifiedModelImporter.Result imported=VerifiedModelImporter.importFile(getApplicationContext(),uri,expected);
        message="Verified GGUF imported privately ("+(imported.bytes/1048576)+" MiB). Native inference runtime is not yet installed.";
      }catch(Exception e){message="Model import failed: "+e.getMessage();}
      final String result=message;ui.post(()->{if(!isFinishing()&&!isDestroyed())status.setText(result);});
    });
  }
  private void cancel(){
    generation++;
    model.revokeActive();
    busy=false;
    send.setEnabled(true);
    status.setText("OFFLINE • Request stopped");
  }
  private void setPrompt(String value){prompt.setText(value);prompt.setSelection(value.length());}
  private void checkNano(){
    if(nano==null){status.setText("Gemini Nano SDK unavailable; use local server option.");return;}
    status.setText("Checking device model support...");
    worker.execute(()->{
      String result;
      try{int state=nano.status();result=state==com.google.mlkit.genai.common.FeatureStatus.AVAILABLE?"Gemini Nano ready on this phone":state==com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE?"Gemini Nano supported but model download required":state==com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING?"Gemini Nano downloading":"Gemini Nano unavailable on this device";}
      catch(Exception e){result="Gemini Nano status failed: "+e.getMessage();}
      final String message=result;ui.post(()->{if(!isDestroyed())status.setText(message);});
    });
  }
  private void ask(){
    if(useEmbedded&&loadingEmbedded){status.setText("Offline model is loading; please wait.");return;}
    if(useEmbedded&&!embedded.ready()){status.setText("Import and load a compatible .task model in Model Setup first.");setup.setVisibility(View.VISIBLE);setupScroll.setVisibility(View.VISIBLE);return;}
    if(!useEmbedded&&!useNano&&!model.ready()){status.setText("No independent model runtime is connected. Open Model Setup to configure a local server.");setup.setVisibility(View.VISIBLE);setupScroll.setVisibility(View.VISIBLE);return;}
    if(busy){status.setText("A response is already processing. Press Stop first.");return;}
    final String text=prompt.getText().toString().trim();
    if(text.isEmpty()){status.setText("Type a question first.");return;}
    if(text.length()>(useNano?1500:127)){status.setText("Prompt too long for selected engine.");return;}
    prompt.setText("");
    request(text,true);
  }
  private void request(String text,boolean visible){
    if(busy)return;
    busy=true;
    final int request=++generation;
    if(visible){transcript.append("\n\nYOU: ").append(text);showTranscript();}
    status.setText("AI • Processing with selected engine");
    send.setEnabled(false);
    final boolean selectedEmbedded=useEmbedded,selectedNano=useNano;
    worker.execute(()->{
      String response;boolean success=true;
      try{response=selectedEmbedded?embedded.answer(text):(selectedNano?(nano==null?missingNano():nano.answer(text)):model.answer(text));}
      catch(Exception e){response="Local model unavailable: "+e.getMessage();success=false;}
      final String result=response;final boolean ok=success;
      ui.post(()->{
        if(isFinishing()||isDestroyed()||request!=generation)return;
        busy=false;send.setEnabled(true);
        if(visible){transcript.append(ok?"\nAI: ":"\nSYSTEM: ").append(result);completedRequests++;showTranscript();}
        status.setText(ok?(visible?"OFFLINE • Response received":"OFFLINE • Model test responded: "+result):"OFFLINE • "+result);
      });
    });
  }
  private String missingNano(){throw new IllegalStateException("Gemini Nano unavailable. Select local server in Model Setup.");}
  private void showTranscript(){history.setText(transcript.toString());transcriptScroll.post(()->transcriptScroll.fullScroll(View.FOCUS_DOWN));}
  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
  private TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(4,8,4,8);return v;}
  private Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(cyan);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));b.setMinHeight(dp(40));b.setMinimumHeight(dp(40));b.setPadding(dp(4),0,dp(4),0);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));parent.addView(b,p);return b;}
  @Override protected void onDestroy(){generation++;closing=true;model.clear();worker.execute(()->{if(embedded!=null)embedded.close();});worker.shutdown();super.onDestroy();}
}
