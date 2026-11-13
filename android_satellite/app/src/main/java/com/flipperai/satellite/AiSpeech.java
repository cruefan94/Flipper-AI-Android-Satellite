package com.flipperai.satellite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phone-side text-to-speech for AI responses. Never records audio or sends
 * prompts to a voice provider from this class. Only installed OFFLINE voices
 * are selectable, so voice synthesis does not introduce cloud fallback.
 *
 * Android's TTS Voice API does NOT specify gender. Prefer locally installed
 * voices commonly named as feminine, but always let the user preview and
 * choose the voice they actually hear.
 */
public final class AiSpeech {
  public interface Events { void status(String message); }
  private final SharedPreferences prefs;
  private final Events events;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private TextToSpeech tts;
  private boolean ready=false,closed=false,enabled,offlineVoiceReady=false;
  private String selectedName;
  private static final String PREF_ENABLED="speak_answers";
  private static final String PREF_VOICE="voice_name";

  public AiSpeech(Context context,Events events) {
    this.events=events;
    prefs=context.getSharedPreferences("flipper_ai_speech",Context.MODE_PRIVATE);
    enabled=prefs.getBoolean(PREF_ENABLED,true);
    selectedName=prefs.getString(PREF_VOICE,"");
    tts=new TextToSpeech(context.getApplicationContext(),status->
      ui.post(()->{
        if(closed)return;
        ready=status==TextToSpeech.SUCCESS;
        if(ready)configureVoice();
        else events.status("Android speech engine unavailable. Text replies still work.");
      }));
  }

  public boolean enabled(){return enabled;}
  public void setEnabled(boolean value) {
    enabled=value;
    prefs.edit().putBoolean(PREF_ENABLED,value).apply();
    if(!value)stop();
  }
  public void stop() {
    if(tts!=null && ready && !closed)tts.stop();
  }

  private List<Voice> englishLocalVoices() {
    ArrayList<Voice> result=new ArrayList<>();
    if(tts==null || !ready)return result;
    Set<Voice> available=tts.getVoices();
    if(available==null)return result;
    for(Voice voice:available) {
      if(voice!=null && voice.getLocale()!=null
          && "en".equalsIgnoreCase(voice.getLocale().getLanguage())
          && !voice.isNetworkConnectionRequired())result.add(voice);
    }
    result.sort(Comparator.comparing(Voice::getName));
    return result;
  }

  private static int preferenceScore(Voice voice) {
    String name=voice.getName().toLowerCase(Locale.ROOT);
    // Voice names are vendor-specific hints, not reliable gender metadata.
    int score=name.contains("female")?100:0;
    if(name.contains("sfg")||name.contains("tpf"))score+=50;
    if(name.contains("en-us"))score+=10;
    if(name.contains("en-ca"))score+=9;
    return score;
  }

  private void configureVoice() {
    if(tts==null || !ready || closed)return;
    List<Voice> voices=englishLocalVoices();
    offlineVoiceReady=false;
    Voice choice=null;
    for(Voice voice:voices) {
      if(voice.getName().equals(selectedName)){choice=voice;break;}
    }
    if(choice==null) {
      for(Voice voice:voices)
        if(choice==null || preferenceScore(voice)>preferenceScore(choice))
          choice=voice;
    }
    if(choice!=null) {
      offlineVoiceReady=tts.setVoice(choice)==TextToSpeech.SUCCESS;
      if(!offlineVoiceReady)
        events.status("Selected speech voice unavailable; check Android text-to-speech settings.");
    } else {
      events.status("No installed offline English TTS voice found. Check Android speech settings.");
    }
    tts.setSpeechRate(0.98f);
    tts.setPitch(1.04f);
  }

  public void speak(String text) {
    if(!enabled || !ready || !offlineVoiceReady || closed || tts==null || text==null || text.isEmpty())return;
    // The TTS engine has a bounded input size; spoken text is capped, not the
    // original visible AI reply. No audio capture or online API is used.
    int limit=Math.min(3000,TextToSpeech.getMaxSpeechInputLength()-1);
    if(limit<1)return;
    String spoken=text.length()>limit?text.substring(0,limit):text;
    tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"flipper-ai-reply");
  }

  public void pickVoice(Activity activity) {
    if(!ready || tts==null) {
      events.status("Voice engine is initializing. Retry when Android speech is ready.");
      return;
    }
    List<Voice> voices=englishLocalVoices();
    if(voices.isEmpty()) {
      events.status("No installed offline English voice. Install a voice in Android Text-to-speech settings.");
      return;
    }
    String[] labels=new String[voices.size()];
    int selected=0;
    Voice current=tts.getVoice();
    for(int i=0;i<voices.size();i++) {
      Voice voice=voices.get(i);
      labels[i]=voice.getLocale().toLanguageTag()+"  •  "+voice.getName();
      if(current!=null && current.getName().equals(voice.getName()))selected=i;
    }
    new AlertDialog.Builder(activity)
      .setTitle("AI voice • choose and preview")
      .setSingleChoiceItems(labels,selected,(dialog,index)->{
        Voice voice=voices.get(index);
        if(tts.setVoice(voice)==TextToSpeech.SUCCESS) {
          offlineVoiceReady=true;
          selectedName=voice.getName();
          prefs.edit().putString(PREF_VOICE,selectedName).apply();
          tts.speak("Hello! I'm your Flipper AI assistant.",
            TextToSpeech.QUEUE_FLUSH,null,"flipper-ai-voice-preview");
          events.status("Voice selected: "+voice.getName());
        } else events.status("This Android voice could not be selected.");
        dialog.dismiss();
      })
      .setNegativeButton("CANCEL",null).show();
  }

  public void close() {
    if(closed)return;
    closed=true;ready=false;offlineVoiceReady=false;
    if(tts!=null){tts.stop();tts.shutdown();tts=null;}
  }
}
