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
  private boolean useNano=true;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Handler ui=new Handler(Looper.getMainLooper());
  private TextView history,status;
  private EditText prompt,endpoint,modelName;
  private Button send;
  private boolean busy=false;
  private ScrollView transcriptScroll;

  private TextView modelHint;
  private int completedRequests=0;
  private final StringBuilder transcript=new StringBuilder();
  private static final String PREFS="ai_local_settings_v1";
  private int generation=0;
  private final int bg=Color.rgb(8,17,23),panel=Color.rgb(19,37,45),cyan=Color.rgb(50,231,218);
  @Override public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setPadding(dp(14),dp(8),dp(14),dp(12));frame.setBackgroundColor(bg);
    frame.setFitsSystemWindows(true);setContentView(frame);
    TextView title=label("AI ASSISTANT",22,cyan);frame.addView(title);
    status=label("OFFLINE • No local language model configured",13,Color.LTGRAY);frame.addView(status);
    modelHint=label("Checking built-in Android Gemini Nano availability. No cloud prompt upload.",12,Color.LTGRAY);frame.addView(modelHint);
    try{nano=new NanoModelEngine();}catch(Throwable e){modelHint.setText("Gemini Nano initialization unavailable: "+e.getClass().getSimpleName());}
    button(frame,"CHECK ON-DEVICE AI",()->checkNano());
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
    endpoint.setText(saved.getString("endpoint",""));
    modelName.setText(saved.getString("model",""));
    button(setup,"USE GEMINI NANO",()->{useNano=true;status.setText("Gemini Nano selected; check on-device AI availability.");});
    button(setup,"USE LOCAL SERVER",()->{useNano=false;status.setText("Local server selected; configure a loopback endpoint.");});
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
    button(setup,"DISABLE LOCAL MODEL",()->{cancel();model.clear();saved.edit().clear().apply();endpoint.setText("");modelName.setText("");status.setText("OFFLINE • Model disabled and settings cleared");});
    if(!saved.getString("endpoint","").isEmpty()&&!saved.getString("model","").isEmpty()){
      try{model.configure(saved.getString("endpoint",""),saved.getString("model",""));status.setText("OFFLINE • Saved local endpoint restored; not yet verified");}
      catch(Exception ignored){saved.edit().clear().apply();status.setText("OFFLINE • Saved model settings invalid");}
    }

  }
  private LinearLayout setup;
  private ScrollView setupScroll;
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
      try{int state=nano.status();result=state==com.google.mlkit.genai.prompt.FeatureStatus.AVAILABLE?"Gemini Nano ready on this phone":state==com.google.mlkit.genai.prompt.FeatureStatus.DOWNLOADABLE?"Gemini Nano supported but model download required":state==com.google.mlkit.genai.prompt.FeatureStatus.DOWNLOADING?"Gemini Nano downloading":"Gemini Nano unavailable on this device";}
      catch(Exception e){result="Gemini Nano status failed: "+e.getMessage();}
      final String message=result;ui.post(()->{if(!isDestroyed())status.setText(message);});
    });
  }
  private void ask(){
    if(!useNano&&!model.ready()){status.setText("Configure a local model server or switch to Gemini Nano.");setup.setVisibility(View.VISIBLE);setupScroll.setVisibility(View.VISIBLE);return;}
    if(busy){status.setText("A response is already processing. Press Stop first.");return;}
    final String text=prompt.getText().toString().trim();
    if(text.isEmpty()){status.setText("Type a question first.");return;}
    if(text.length()>127){status.setText("Maximum prompt length is 127 characters.");return;}
    prompt.setText("");
    request(text,true);
  }
  private void request(String text,boolean visible){
    if(busy)return;
    busy=true;
    final int request=++generation;
    if(visible){transcript.append("\n\nYOU: ").append(text);showTranscript();}
    status.setText("OFFLINE • Processing on phone");
    send.setEnabled(false);
    worker.execute(()->{
      String response;boolean success=true;
      try{response=useNano?(nano==null?missingNano():nano.answer(text)):model.answer(text);}
      catch(Exception e){response="Local model unavailable: "+e.getMessage();success=false;}
      final String result=response;final boolean ok=success;
      ui.post(()->{
        if(isFinishing()||isDestroyed()||request!=generation)return;
        busy=false;send.setEnabled(true);
        if(visible){transcript.append("\nAI: ").append(result);completedRequests++;showTranscript();}
        status.setText(ok?(visible?"OFFLINE • Response received":"OFFLINE • Model test responded: "+result):"OFFLINE • "+result);
      });
    });
  }
  private String missingNano(){throw new IllegalStateException("Gemini Nano unavailable. Select local server in Model Setup.");}
  private void showTranscript(){history.setText(transcript.toString());transcriptScroll.post(()->transcriptScroll.fullScroll(View.FOCUS_DOWN));}
  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
  private TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(4,8,4,8);return v;}
  private Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(dp(42));b.setMinimumHeight(dp(42));b.setPadding(dp(8),0,dp(8),0);b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));return b;}
  @Override protected void onDestroy(){generation++;worker.shutdownNow();model.clear();super.onDestroy();}
}
