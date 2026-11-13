package com.flipperai.satellite;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/**
 * Restored dedicated AI chat screen. Offline is default; any network inference
 * requires user-selected ONLINE/HYBRID mode AND explicit in-session cloud consent.
 * Model weights remain in private Android storage and AI never executes Flipper commands.
 */
public final class AiChatActivity extends Activity {
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private EmbeddedLlmEngine embedded;
  private final WorkerCloudEngine cloud=new WorkerCloudEngine();
  private InferenceRouter router;
  private InferenceRouter.Mode mode=InferenceRouter.Mode.OFFLINE;
  private volatile boolean consent=false;
  private TextView status,transcript;
  private EditText prompt;
  private Button send;
  private ScrollView chatScroll;
  private final StringBuilder history=new StringBuilder();
  private boolean busy=false;
  private boolean closing=false;
  private int requestGeneration=0;
  private int dp(int n){return SatelliteUi.dp(this,n);}
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    embedded=new EmbeddedLlmEngine(getCacheDir().getAbsolutePath());
    router=new InferenceRouter(new OfflineModelOrDiagnostics(
      embedded,new OfflineDiagnosticEngine()),cloud);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    getWindow().setStatusBarColor(SatelliteUi.BACKGROUND);
    getWindow().setNavigationBarColor(SatelliteUi.BACKGROUND);
    LinearLayout page=SatelliteUi.column(this);
    page.setPadding(dp(15),dp(15),dp(15),dp(12));
    page.setBackgroundColor(SatelliteUi.BACKGROUND);
    setContentView(page);

    TextView title=SatelliteUi.text(this,"✧  AI CHAT",25,SatelliteUi.ORANGE,true);
    page.addView(title);
    status=SatelliteUi.text(this,"Offline AI • Checking imported model...",13,SatelliteUi.MUTED,false);
    status.setPadding(0,dp(4),0,dp(9));
    page.addView(status);

    LinearLayout navigation=new LinearLayout(this);
    navigation.setOrientation(LinearLayout.HORIZONTAL);
    page.addView(navigation);
    smallAction(navigation,"‹ HOME",()->{
      startActivity(new Intent(this,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
      finish();
    });
    smallAction(navigation,"MODE",this::chooseMode);
    smallAction(navigation,"CLOUD",this::cloudSettings);
    smallAction(navigation,"MODELS",()->startActivity(new Intent(this,ModelsActivity.class)));

    chatScroll=new ScrollView(this);
    chatScroll.setFillViewport(true);
    chatScroll.setBackground(SatelliteUi.panel(this,false));
    transcript=SatelliteUi.text(this,"Welcome to Flipper AI. Import an offline model under MODELS for full AI chat. Without a model, supported hardware diagnostics still work.",15,SatelliteUi.WHITE,false);
    transcript.setPadding(dp(14),dp(14),dp(14),dp(14));
    transcript.setTextIsSelectable(true);
    transcript.setLineSpacing(dp(3),1.05f);
    chatScroll.addView(transcript);
    LinearLayout.LayoutParams messagesLp=new LinearLayout.LayoutParams(-1,0,1);
    messagesLp.topMargin=dp(10);
    messagesLp.bottomMargin=dp(10);
    page.addView(chatScroll,messagesLp);

    LinearLayout actions=new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    page.addView(actions);
    smallAction(actions,"BLE HELP",()->prompt.setText("Explain BLE"));
    smallAction(actions,"BATTERY",()->prompt.setText("battery diagnostics"));
    smallAction(actions,"CLEAR",()->{
      ++requestGeneration;
      history.setLength(0);
      transcript.setText("Conversation cleared.");
    });
    smallAction(actions,"COPY",()->{
      if(history.length()==0){setStatus("Nothing to copy.");return;}
      android.content.ClipboardManager clipboard=
        (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if(clipboard!=null)clipboard.setPrimaryClip(
        ClipData.newPlainText("Flipper AI conversation",history.toString()));
      setStatus("Conversation copied to clipboard.");
    });

    LinearLayout composer=new LinearLayout(this);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    page.addView(composer);
    prompt=new EditText(this);
    prompt.setSingleLine(false);
    prompt.setMaxLines(3);
    prompt.setMinLines(1);
    prompt.setTextColor(SatelliteUi.WHITE);
    prompt.setHintTextColor(SatelliteUi.MUTED);
    prompt.setHint("Ask your AI...");
    prompt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PromptLimits.MAX_CHARACTERS)});
    composer.addView(prompt,new LinearLayout.LayoutParams(0,-2,1));
    send=new Button(this);
    send.setText("SEND");
    send.setTextColor(SatelliteUi.BACKGROUND);
    send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SatelliteUi.ORANGE));
    composer.addView(send);
    send.setOnClickListener(v->ask());
  }
  @Override protected void onResume(){
    super.onResume();
    worker.execute(()->{
      try{
        embedded.clear();
        if(EmbeddedModelStore.installed(this)){
          embedded.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
          setStatus("OFFLINE • Model file imported; live inference needs testing");
        }else setStatus("OFFLINE • No model file • Basic diagnostics only");
      }catch(Exception e){setStatus("Embedded AI unavailable: "+e.getMessage());}
    });
  }
  private void smallAction(LinearLayout row,String label,Runnable callback){
    TextView item=SatelliteUi.text(this,label,12,SatelliteUi.ORANGE,true);
    item.setGravity(Gravity.CENTER);
    item.setPadding(dp(9),dp(12),dp(9),dp(12));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
    lp.setMargins(dp(1),0,dp(1),0);
    row.addView(item,lp);
    item.setOnClickListener(v->callback.run());
  }
  private void chooseMode(){
    String[] choices={"OFFLINE • on this phone","HYBRID • offline then allowed cloud","ONLINE • requires cloud permission"};
    new android.app.AlertDialog.Builder(this).setTitle("AI processing mode")
      .setSingleChoiceItems(choices,mode.ordinal(),(dialog,which)->{
        mode=InferenceRouter.Mode.values()[which];
        setStatus(mode+" • Cloud "+(consent?"allowed":"disabled"));
        dialog.dismiss();
      }).setNegativeButton("CANCEL",null).show();
  }
  private void cloudSettings(){
    LinearLayout fields=SatelliteUi.column(this);
    int pad=dp(16);fields.setPadding(pad,pad,pad,pad);
    TextView warning=SatelliteUi.text(this,
      "Online AI sends your chat prompts to an HTTPS Worker and its AI provider only after you opt in. Token stays in memory and is cleared on exit.",
      14,SatelliteUi.WHITE,false);fields.addView(warning);
    EditText endpoint=new EditText(this);endpoint.setSingleLine(true);
    endpoint.setHint("https://your-worker.workers.dev/ask");fields.addView(endpoint);
    EditText token=new EditText(this);token.setSingleLine(true);
    token.setInputType(129);token.setHint("Device token (not stored)");fields.addView(token);
    CheckBox allow=new CheckBox(this);
    allow.setText("I authorize cloud processing of my ASK prompts");
    allow.setChecked(consent);fields.addView(allow);
    new android.app.AlertDialog.Builder(this).setTitle("Cloud AI permission")
      .setView(fields)
      .setNegativeButton("DISABLE CLOUD",(d,w)->{
        consent=false;cloud.clear();++requestGeneration;
        setStatus("Cloud permission revoked and credentials cleared.");
      })
      .setPositiveButton("SAVE",(d,w)->{
        consent=false;cloud.clear();++requestGeneration;
        if(!allow.isChecked()){setStatus("Cloud disabled.");return;}
        try{
          cloud.configure(endpoint.getText().toString(),token.getText().toString());
          consent=true;setStatus("Cloud configured for this session. Use ONLINE or HYBRID mode.");
        }catch(Exception e){setStatus("Cloud configuration rejected: "+e.getMessage());}
      }).show();
  }
  private void ask(){
    if(busy || closing)return;
    final String question=prompt.getText().toString().trim();
    try{PromptLimits.validate(question);}
    catch(Exception e){setStatus("Prompt rejected: "+e.getMessage());return;}
    final InferenceRouter.Mode requestedMode=mode;
    final boolean approved=consent;
    final int generation=++requestGeneration;
    prompt.setText("");
    append("YOU",question);
    busy=true;send.setEnabled(false);
    setStatus("Processing "+requestedMode+" request...");
    worker.execute(()->{
      String result;
      try{
        if(requestedMode==InferenceRouter.Mode.OFFLINE && !embedded.ready()
            && !new OfflineDiagnosticEngine().supports(question))
          throw new IllegalStateException("Import a compatible model for general questions.");
        result=router.ask(question,requestedMode,approved);
      }catch(Exception e){result="AI unavailable: "+e.getMessage();}
      final String answer=result;
      runOnUiThread(()->{
        if(closing)return;
        if(generation==requestGeneration && (!approved||consent)){
          append("AI",answer);
          setStatus(requestedMode+" • Response complete");
        }else setStatus("Old response discarded due to changed settings.");
        busy=false;send.setEnabled(true);
      });
    });
  }
  private void append(String speaker,String message){
    if(history.length()>14000)history.delete(0,history.length()-7000);
    history.append(speaker).append(": ").append(message).append("\n\n");
    transcript.setText(history.toString());
    chatScroll.post(()->chatScroll.fullScroll(android.view.View.FOCUS_DOWN));
  }
  private void setStatus(String content){
    runOnUiThread(()->{
      if(!closing&&!isFinishing()&&!isDestroyed()&&status!=null)
        status.setText(content);
    });
  }
  @Override protected void onDestroy(){
    closing=true;
    ++requestGeneration;
    consent=false;
    cloud.clear();
    if(!worker.isShutdown()){
      worker.execute(()->embedded.clear());
      worker.shutdown();
    }
    super.onDestroy();
  }
}
