package com.flipperai.satellite;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
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
  private TextView status,transcript,voiceToggle;
  private AiSpeech speech;
  private EditText prompt;
  private Button send;
  private ScrollView chatScroll;
  private final StringBuilder history=new StringBuilder();
  private final SpannableStringBuilder styledHistory=new SpannableStringBuilder();
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
    applyChatWindowInsets(page);

    TextView title=SatelliteUi.text(this,"✧  AI CHAT",25,SatelliteUi.ORANGE,true);
    page.addView(title);
    status=SatelliteUi.text(this,"Offline AI • Checking imported model...",13,SatelliteUi.MUTED,false);
    status.setPadding(0,dp(4),0,dp(9));
    page.addView(status);
    speech=new AiSpeech(this,this::setStatus);

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

    LinearLayout voiceControls=new LinearLayout(this);
    voiceControls.setOrientation(LinearLayout.HORIZONTAL);
    page.addView(voiceControls);
    voiceToggle=smallAction(voiceControls,speech.enabled()?"VOICE ON":"VOICE OFF",()->{
      speech.setEnabled(!speech.enabled());
      voiceToggle.setText(speech.enabled()?"VOICE ON":"VOICE OFF");
      setStatus(speech.enabled()?"Spoken AI replies enabled":"Spoken AI replies muted");
    });
    smallAction(voiceControls,"CHOOSE VOICE",()->speech.pickVoice(this));
    smallAction(voiceControls,"STOP",()->speech.stop());
    smallAction(voiceControls,"TEST AI",this::chooseEngineSelfTest);

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
      speech.stop();
      history.setLength(0);
      styledHistory.clear();
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
    composer.setPadding(0,dp(8),0,dp(4));
    page.addView(composer);
    prompt=new EditText(this);
    prompt.setSingleLine(false);
    prompt.setMaxLines(3);
    prompt.setMinLines(1);
    prompt.setTextSize(16);
    prompt.setGravity(Gravity.CENTER_VERTICAL);
    prompt.setMinHeight(dp(52));
    prompt.setTextColor(SatelliteUi.WHITE);
    prompt.setHintTextColor(SatelliteUi.MUTED);
    prompt.setHint("Ask your AI...");
    prompt.setBackground(SatelliteUi.panel(this,false));
    prompt.setPadding(dp(13),dp(10),dp(13),dp(10));
    prompt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PromptLimits.MAX_CHARACTERS)});
    composer.addView(prompt,new LinearLayout.LayoutParams(0,-2,1));
    send=new Button(this);
    send.setText("SEND");
    send.setAllCaps(false);
    send.setTextSize(16);
    send.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    send.setTextColor(SatelliteUi.BACKGROUND);
    send.setBackgroundTintList(null);
    android.graphics.drawable.GradientDrawable sendBackground=new android.graphics.drawable.GradientDrawable();
    sendBackground.setColor(SatelliteUi.ORANGE);
    sendBackground.setCornerRadius(dp(12));
    send.setBackground(sendBackground);
    send.setElevation(0);
    LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(dp(96),dp(52));
    sendLp.leftMargin=dp(10);
    composer.addView(send,sendLp);
    send.setOnClickListener(v->ask());
  }
  /** Android 15+ edge-to-edge can place app content under system bars.
   * Honor both status/nav bars and IME so SEND stays tappable with the keyboard.
   * Padding is derived from immutable base values, never accumulated. */
  private void applyChatWindowInsets(LinearLayout page) {
    page.setOnApplyWindowInsetsListener((view,insets)->{
      final int safeTop,safeBottom;
      if(Build.VERSION.SDK_INT>=30) {
        android.graphics.Insets bars=insets.getInsets(
          WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
        safeTop=bars.top;
        safeBottom=Math.max(bars.bottom,ime.bottom);
      } else {
        safeTop=insets.getSystemWindowInsetTop();
        safeBottom=insets.getSystemWindowInsetBottom();
      }
      view.setPadding(dp(15),dp(15)+Math.max(0,safeTop),
                      dp(15),dp(12)+Math.max(0,safeBottom));
      return insets;
    });
    page.requestApplyInsets();
  }
  @Override protected void onResume(){
    super.onResume();
    worker.execute(()->{
      try{
        embedded.clear();
        if(EmbeddedModelStore.installed(this)){
          embedded.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
          setStatus("Model imported • Inference not yet verified on this phone • Use TEST AI");
        }else setStatus("OFFLINE • No model file • Basic diagnostics only");
      }catch(Exception e){setStatus("Embedded AI unavailable: "+e.getMessage());}
    });
  }
  private TextView smallAction(LinearLayout row,String label,Runnable callback){
    TextView item=SatelliteUi.text(this,label,12,SatelliteUi.ORANGE,true);
    item.setGravity(Gravity.CENTER);
    item.setPadding(dp(6),dp(12),dp(6),dp(12));
    item.setMinHeight(dp(48));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
    lp.setMargins(dp(1),0,dp(1),0);
    row.addView(item,lp);
    item.setOnClickListener(v->callback.run());
    return item;
  }
  private void chooseMode(){
    // Keep displayed options mapped explicitly to their modes: enum order differs.
    final InferenceRouter.Mode[] displayedModes={
      InferenceRouter.Mode.OFFLINE,InferenceRouter.Mode.HYBRID,InferenceRouter.Mode.ONLINE
    };
    String[] choices={"OFFLINE • on this phone","HYBRID • offline then allowed cloud","ONLINE • requires cloud permission"};
    int selected=0;
    for(int i=0;i<displayedModes.length;i++){
      if(displayedModes[i]==mode){selected=i;break;}
    }
    new android.app.AlertDialog.Builder(this).setTitle("AI processing mode")
      .setSingleChoiceItems(choices,selected,(dialog,which)->{
        mode=displayedModes[which];
        setStatus(mode+" • Cloud "+(consent?"allowed":"disabled"));
        dialog.dismiss();
      }).setNegativeButton("CANCEL",null).show();
  }
  /** Explicit in-chat live engine checks. Tests never silently cross transports:
   * offline calls the actual embedded model (not canned diagnostics); cloud
   * uses the configured HTTPS Worker only after in-session consent.
   */
  private void chooseEngineSelfTest(){
    if(busy||closing){setStatus("Finish the current AI request before testing.");return;}
    new android.app.AlertDialog.Builder(this)
      .setTitle("Verify AI engine")
      .setItems(new String[]{
        "TEST EMBEDDED OFFLINE • no internet",
        "TEST ONLINE WORKER • sends a fixed test prompt"
      },(dialog,which)->testEngineLive(which==1))
      .setNegativeButton("CANCEL",null).show();
  }

  private void testEngineLive(boolean online){
    if(busy||closing)return;
    if(online && (!consent || !cloud.ready())){
      setStatus("Online test requires CLOUD configuration and explicit cloud permission.");
      return;
    }
    if(!online && !EmbeddedModelStore.installed(this)){
      setStatus("Offline model missing. Import a compatible .litertlm file under MODELS.");
      return;
    }
    final int generation=++requestGeneration;
    final boolean consentAtStart=consent;
    busy=true;send.setEnabled(false);
    setStatus(online?"Testing online Worker with an explicit fixed prompt..."
                    :"Testing actual offline model inference (no cloud)...");
    worker.execute(()->{
      String message;
      boolean verified=false;
      try {
        final String reply;
        if(online){
          // No actual user prompt is sent by this explicit test.
          reply=router.ask("Reply with OK.",InferenceRouter.Mode.ONLINE,consentAtStart);
        } else {
          if(!embedded.ready())
            embedded.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
          reply=embedded.answer("Reply with OK.");
        }
        if(reply==null || reply.trim().isEmpty())
          throw new IllegalStateException("Engine returned no text");
        message=online ? "Verified LIVE online Worker response (not a BLE AI1 test)."
                       : "Verified LIVE embedded offline response (no cloud).";
        verified=true;
      }catch(Exception e){
        message=(online?"Online":"Offline")+" AI engine test FAILED: "+e.getMessage();
      }
      final String result=message;
      runOnUiThread(()->{
        if(closing)return;
        if(generation==requestGeneration && (!online || (consentAtStart && consent))){
          setStatus(result);
        }else if(!closing)setStatus("AI engine test result discarded after settings changed.");
        busy=false;send.setEnabled(true);
      });
    });
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
      boolean succeeded=false;
      try{
        if(requestedMode==InferenceRouter.Mode.OFFLINE && !embedded.ready()
            && !new OfflineDiagnosticEngine().supports(question))
          throw new IllegalStateException("Import a compatible model for general questions.");
        result=router.ask(question,requestedMode,approved);
        succeeded=true;
      }catch(Exception e){result="AI unavailable: "+e.getMessage();}
      final String answer=result;
      final boolean responseSucceeded=succeeded;
      runOnUiThread(()->{
        if(closing)return;
        if(generation==requestGeneration && (!approved||consent)){
          if(responseSucceeded){
            append("AI",answer);
            speech.speak(ChatMarkdownFormatter.plainSpeech(answer));
            setStatus(requestedMode+" • Response complete");
          }else{
            append("SYSTEM",answer);
            setStatus(requestedMode+" • Request failed");
          }
        }else setStatus("Old response discarded due to changed settings.");
        busy=false;send.setEnabled(true);
      });
    });
  }
  private void append(String speaker,String message){
    if(history.length()>14000) {
      history.delete(0,history.length()-7000);
      styledHistory.delete(0,Math.max(0,styledHistory.length()-7000));
    }
    history.append(speaker).append(": ").append(message).append("\n\n");
    int labelStart=styledHistory.length();
    styledHistory.append(speaker);
    styledHistory.setSpan(new StyleSpan(Typeface.BOLD),labelStart,
      styledHistory.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    styledHistory.append(": ");
    if("AI".equals(speaker))styledHistory.append(ChatMarkdownFormatter.render(message));
    else styledHistory.append(message);
    styledHistory.append("\n\n");
    transcript.setText(styledHistory);
    chatScroll.post(()->chatScroll.fullScroll(android.view.View.FOCUS_DOWN));
  }
  private void setStatus(String content){
    runOnUiThread(()->{
      if(!closing&&!isFinishing()&&!isDestroyed()&&status!=null)
        status.setText(content);
    });
  }
  @Override protected void onStop(){
    if(speech!=null)speech.stop();
    super.onStop();
  }
  @Override protected void onDestroy(){
    closing=true;
    if(speech!=null)speech.close();
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
