package com.flipperai.satellite;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
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
  private static final int MICROPHONE_PERMISSION_REQUEST=643;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private EmbeddedLlmEngine embedded;
  private final WorkerCloudEngine cloud=new WorkerCloudEngine();
  private InferenceRouter router;
  private InferenceRouter.Mode mode=InferenceRouter.Mode.OFFLINE;
  private volatile boolean consent=false;
  private TextView status,transcript,voiceToggle;
  private AiSpeech speech;
  private OnDeviceVoiceInput voiceInput;
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
    // On modern Android, we apply IME insets ourselves. ADJUST_RESIZE plus
    // ime.bottom padding can resize the screen twice and cause visible flashes.
    getWindow().setSoftInputMode(Build.VERSION.SDK_INT>=30
      ?WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
      :WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    getWindow().setStatusBarColor(SatelliteUi.BACKGROUND);
    getWindow().setNavigationBarColor(SatelliteUi.BACKGROUND);
    LinearLayout page=SatelliteUi.column(this);
    page.setPadding(dp(15),dp(15),dp(15),dp(12));
    // Never auto-focus the editor simply because it is the first input view.
    page.setFocusableInTouchMode(true);
    page.setBackgroundColor(SatelliteUi.BACKGROUND);
    setContentView(page);
    applyChatWindowInsets(page);

    TextView title=SatelliteUi.text(this,"✧  AI CHAT",25,SatelliteUi.ORANGE,true);
    page.addView(title);
    status=SatelliteUi.text(this,"Offline AI • Checking imported model...",13,SatelliteUi.MUTED,false);
    status.setPadding(0,dp(4),0,dp(9));
    page.addView(status);
    speech=new AiSpeech(this,this::setStatus);
    voiceInput=new OnDeviceVoiceInput(this,new OnDeviceVoiceInput.Events() {
      @Override public void status(String message){setStatus(message);}
      @Override public void transcript(String recognized){
        if(closing || prompt==null || isFinishing())return;
        // Dictation fills the composer, never sends it. The user can edit first.
        String existing=prompt.getText().toString();
        if(!existing.isEmpty() && !Character.isWhitespace(existing.charAt(existing.length()-1)))
          prompt.append(" ");
        prompt.append(recognized);
        setStatus("Voice transcript added locally. Review it, then tap SEND.");
      }
    });

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
    smallAction(voiceControls,"STOP VOICE",()->speech.stop());

    LinearLayout microphoneControls=new LinearLayout(this);
    microphoneControls.setOrientation(LinearLayout.HORIZONTAL);
    page.addView(microphoneControls);
    smallAction(microphoneControls,"TALK",this::startVoiceInput);
    smallAction(microphoneControls,"FINISH",()->voiceInput.finishListening());
    smallAction(microphoneControls,"CANCEL MIC",()->voiceInput.cancelListening());
    TextView microphoneHint=SatelliteUi.text(this,
      "On-device microphone only • Transcript stays editable until SEND",11,SatelliteUi.MUTED,false);
    page.addView(microphoneHint);

    chatScroll=new ScrollView(this);
    chatScroll.setFillViewport(true);
    chatScroll.setSmoothScrollingEnabled(false);
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
    prompt.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    prompt.setOnFocusChangeListener((view,focused)->{
      // Do not leave a keyboard on-screen when transcript selection or another
      // control owns focus. Never hide/reopen it during normal typing.
      if(!focused) {
        android.view.inputmethod.InputMethodManager ime=
          (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(ime!=null)ime.hideSoftInputFromWindow(prompt.getWindowToken(),0);
      }
    });
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
    // The parent owns initial focus until the user explicitly taps the composer.
    page.requestFocus();
  }
  /**
   * One IME owner: API 30+ uses ADJUST_NOTHING and applies insets manually.
   * Pre-30 retains ADJUST_RESIZE with system-bar padding only. Padding changes
   * only when the inset really changes, not when the user types each character.
   */
  private void applyChatWindowInsets(LinearLayout page) {
    final int left=dp(15),top=dp(15),right=dp(15),bottom=dp(12);
    page.setOnApplyWindowInsetsListener((view,insets)->{
      int safeTop=0,safeBottom=0;
      if(Build.VERSION.SDK_INT>=30) {
        android.graphics.Insets bars=insets.getInsets(
          WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
        safeTop=bars.top;
        // IME and navigation-bar insets overlap; use the larger, not their sum.
        safeBottom=Math.max(bars.bottom,ime.bottom);
      } else {
        safeTop=insets.getSystemWindowInsetTop();
        // Legacy adjustResize already accounts for the keyboard's height.
        safeBottom=0;
      }
      int nextTop=top+Math.max(0,safeTop);
      int nextBottom=bottom+Math.max(0,safeBottom);
      if(view.getPaddingLeft()!=left || view.getPaddingTop()!=nextTop
          || view.getPaddingRight()!=right || view.getPaddingBottom()!=nextBottom)
        view.setPadding(left,nextTop,right,nextBottom);
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
          setStatus("OFFLINE • Model imported • Ready for AI chat");
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
    // Display order differs from Mode enum order: map choices explicitly.
    String[] choices={"OFFLINE • on this phone","HYBRID • offline then allowed cloud","ONLINE • requires cloud permission"};
    final InferenceRouter.Mode[] choiceModes={
      InferenceRouter.Mode.OFFLINE,InferenceRouter.Mode.HYBRID,InferenceRouter.Mode.ONLINE
    };
    int selected=mode==InferenceRouter.Mode.HYBRID?1:mode==InferenceRouter.Mode.ONLINE?2:0;
    new android.app.AlertDialog.Builder(this).setTitle("AI processing mode")
      .setSingleChoiceItems(choices,selected,(dialog,which)->{
        InferenceRouter.Mode chosen=choiceModes[which];
        if(mode!=chosen) {
          ++requestGeneration; // Invalidate any response from the previous mode.
          cloud.revokeActive(); // Disconnect a previous cloud request without clearing consent.
        }
        mode=chosen;
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
  /** No microphone access until TALK was explicitly tapped. */
  private void startVoiceInput(){
    if(closing || busy) {
      setStatus("Finish the current AI request before starting voice input.");
      return;
    }
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION_REQUEST);
      setStatus("Android microphone permission requested. Tap TALK again after allowing.");
      return;
    }
    speech.stop();
    voiceInput.startListening();
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
    super.onRequestPermissionsResult(requestCode,permissions,grants);
    if(requestCode!=MICROPHONE_PERMISSION_REQUEST)return;
    if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)
      setStatus("Microphone permission allowed. Tap TALK to start on-device recognition.");
    else
      setStatus("Microphone permission denied. You can still type messages.");
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
      String result, completionStatus;
      try{
        // Clear, direct phone-info questions are handled locally in every AI mode.
        // No cloud prompt, telemetry, device identifier or extra permission is used.
        if(PhoneFacts.classify(question)!=PhoneFacts.Query.NONE) {
          result=PhoneFacts.answer(question,AndroidPhoneFacts.snapshot(this));
          completionStatus="PHONE • On-device answer (not sent to cloud)";
        } else {
          if(requestedMode==InferenceRouter.Mode.OFFLINE && !embedded.ready()
              && !new OfflineDiagnosticEngine().supports(question))
            throw new IllegalStateException("Import a compatible model for general questions.");
          InferenceRouter.Reply reply=router.askWithSource(question,requestedMode,approved);
          result=reply.text;
          if(reply.source==InferenceRouter.Source.OFFLINE_FALLBACK)
            completionStatus="HYBRID • Cloud unavailable • On-device fallback";
          else if(reply.source==InferenceRouter.Source.ONLINE)
            completionStatus=requestedMode+" • Cloud response complete";
          else completionStatus=requestedMode+" • On-device response complete";
        }
      }catch(Exception e){
        result="AI unavailable: "+e.getMessage();
        completionStatus=requestedMode+" • Request failed";
      }
      final String answer=result;
      final String finalStatus=completionStatus;
      runOnUiThread(()->{
        if(closing)return;
        if(generation==requestGeneration && (!approved||consent)){
          append("AI",answer);
          speech.speak(ChatMarkdownFormatter.plainSpeech(answer));
          setStatus(finalStatus);
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
  @Override protected void onPause(){
    // Always revoke live microphone capture when this screen loses focus.
    if(voiceInput!=null)voiceInput.cancelListening();
    super.onPause();
  }
  @Override protected void onStop(){
    if(speech!=null)speech.stop();
    super.onStop();
  }
  @Override protected void onDestroy(){
    closing=true;
    if(voiceInput!=null)voiceInput.close();
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
