package com.flipperai.satellite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Dedicated opt-in on-device AI chat. No prompts leave the phone. */
public final class AiChatActivity extends Activity {
  private final LocalModelEngine model=new LocalModelEngine();
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Handler ui=new Handler(Looper.getMainLooper());
  private TextView history,status;
  private EditText prompt,endpoint,modelName;
  private Button send;
  private boolean busy=false;
  private ScrollView transcriptScroll;
  private int completedRequests=0;
  private final StringBuilder transcript=new StringBuilder();
  private static final String PREFS="ai_local_settings_v1";
  private int generation=0;
  private final int bg=Color.rgb(8,17,23),panel=Color.rgb(19,37,45),cyan=Color.rgb(50,231,218);
  @Override public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setPadding(18,18,18,12);frame.setBackgroundColor(bg);setContentView(frame);
    TextView title=label("AI ASSISTANT",24,cyan);frame.addView(title);
    status=label("OFFLINE • No local language model configured",13,Color.LTGRAY);frame.addView(status);
    TextView note=label("Private text chat. A separately installed OpenAI-compatible model server must run on this phone at 127.0.0.1. No automatic cloud uploads.",13,Color.LTGRAY);frame.addView(note);
    transcriptScroll=new ScrollView(this);history=label("Conversation will appear here. Configure a local model to begin.",15,Color.WHITE);history.setTextIsSelectable(true);history.setPadding(14,16,14,16);history.setBackgroundColor(panel);transcriptScroll.addView(history);
    frame.addView(transcriptScroll,new LinearLayout.LayoutParams(-1,0,1));
    prompt=new EditText(this);prompt.setSingleLine(false);prompt.setMaxLines(4);prompt.setHint("Ask your AI...");prompt.setHintTextColor(Color.LTGRAY);prompt.setTextColor(Color.WHITE);frame.addView(prompt);
    send=button(frame,"SEND TO OFFLINE AI",this::ask);
    button(frame,"QUICK PROMPT: EXPLAIN BLE",()->setPrompt("Explain Bluetooth Low Energy."));
    button(frame,"QUICK PROMPT: EXPLAIN GATT",()->setPrompt("What is a GATT service?"));
    button(frame,"STOP CURRENT RESPONSE",this::cancel);
    button(frame,"CLEAR CONVERSATION",()->{cancel();transcript.setLength(0);completedRequests=0;history.setText("Conversation cleared. No messages saved.");status.setText("OFFLINE • Conversation cleared");});
    Button configure=button(frame,"SHOW / HIDE LOCAL MODEL SETUP",()->{int v=setup.getVisibility();setup.setVisibility(v==View.VISIBLE?View.GONE:View.VISIBLE);});
    setup=new LinearLayout(this);setup.setOrientation(LinearLayout.VERTICAL);setup.setVisibility(View.GONE);frame.addView(setup);
    endpoint=new EditText(this);endpoint.setSingleLine(true);endpoint.setHint("http://127.0.0.1:8080/v1/chat/completions");endpoint.setTextColor(Color.WHITE);endpoint.setHintTextColor(Color.LTGRAY);setup.addView(endpoint);
    modelName=new EditText(this);modelName.setSingleLine(true);modelName.setHint("Local model name");modelName.setTextColor(Color.WHITE);modelName.setHintTextColor(Color.LTGRAY);setup.addView(modelName);
    android.content.SharedPreferences saved=getSharedPreferences(PREFS,MODE_PRIVATE);
    endpoint.setText(saved.getString("endpoint",""));
    modelName.setText(saved.getString("model",""));
    button(setup,"CONFIGURE LOCAL MODEL",()->{
      try{model.configure(endpoint.getText().toString().trim(),modelName.getText().toString().trim());
        saved.edit().putString("endpoint",endpoint.getText().toString().trim()).putString("model",modelName.getText().toString().trim()).apply();
        status.setText("OFFLINE • Local endpoint configured (not yet verified)");}
      catch(Exception e){status.setText("Model configuration failed: "+e.getMessage());}
    });
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
    button(frame,"BACK TO DASHBOARD",this::finish);
  }
  private LinearLayout setup;
  private void cancel(){
    generation++;
    model.revokeActive();
    busy=false;
    send.setEnabled(true);
    status.setText("OFFLINE • Request stopped");
  }
  private void setPrompt(String value){prompt.setText(value);prompt.setSelection(value.length());}
  private void ask(){
    if(!model.ready()){status.setText("Install and configure an on-phone model server before sending.");return;}
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
      try{response=model.answer(text);}
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
  private void showTranscript(){history.setText(transcript.toString());transcriptScroll.post(()->transcriptScroll.fullScroll(View.FOCUS_DOWN));}
  private TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(4,8,4,8);return v;}
  private Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());parent.addView(b);return b;}
  @Override protected void onDestroy(){generation++;worker.shutdownNow();model.clear();super.onDestroy();}
}
