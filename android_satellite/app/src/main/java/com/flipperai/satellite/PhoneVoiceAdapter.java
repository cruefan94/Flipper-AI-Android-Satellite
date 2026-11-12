package com.flipperai.satellite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Locale;

/** Phone-only speech adapter. No automatic microphone activation or Flipper hardware audio dependency. */
public final class PhoneVoiceAdapter {
  public interface Events {
    void state(String message);
    void transcript(String text);
  }
  private final Activity activity;
  private final Events events;
  private SpeechRecognizer recognizer;
  private TextToSpeech tts;
  private boolean listening;
  private boolean closed;
  private boolean ttsReady;
  private boolean localVoiceReady;

  public PhoneVoiceAdapter(Activity activity, Events events) {
    this.activity=activity; this.events=events;
    tts=new TextToSpeech(activity,status -> {
      if(closed)return;
      if(status!=TextToSpeech.SUCCESS){events.state("Speech output unavailable on this phone");return;}
      ttsReady=true;
      if(tts.setLanguage(Locale.getDefault())==TextToSpeech.LANG_MISSING_DATA){
        events.state("Install an offline text-to-speech voice for your language");return;
      }
      localVoiceReady=false;
      if(Build.VERSION.SDK_INT>=21) {
        try {
          java.util.Set<Voice> voices=tts.getVoices();
          if(voices!=null)for(Voice voice:voices){
            if(!voice.isNetworkConnectionRequired() &&
                voice.getLocale()!=null &&
                voice.getLocale().getLanguage().equals(Locale.getDefault().getLanguage())){
              if(tts.setVoice(voice)==TextToSpeech.SUCCESS){localVoiceReady=true;break;}
            }
          }
        }catch(Exception ignored){}
      }
      events.state(localVoiceReady?"Offline speech output ready":"No verified local TTS voice; speech output disabled");
    });
  }

  /** Returns false when the mic permission or a guaranteed on-device recognizer is absent. */
  public boolean startListening() {
    if(closed || listening)return false;
    if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44);
      events.state("Microphone permission required. Tap Talk again after allowing.");
      return false;
    }
    // Never silently send raw audio to a vendor/cloud speech engine.
    if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)){
      events.state("On-device speech recognition unavailable; install/enable a supported Android speech service");
      return false;
    }
    try{
      recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
      recognizer.setRecognitionListener(new RecognitionListener(){
        @Override public void onReadyForSpeech(Bundle params){events.state("Listening on phone...");}
        @Override public void onBeginningOfSpeech(){}
        @Override public void onRmsChanged(float rmsdB){}
        @Override public void onBufferReceived(byte[] buffer){}
        @Override public void onEndOfSpeech(){events.state("Processing speech...");}
        @Override public void onError(int error){finishListening();events.state("Speech recognition failed (code "+error+"). Retry.");}
        @Override public void onResults(Bundle results){
          ArrayList<String> matches=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          finishListening();
          if(matches!=null && !matches.isEmpty() && !matches.get(0).trim().isEmpty())
            events.transcript(matches.get(0).trim());
          else events.state("No speech recognized");
        }
        @Override public void onPartialResults(Bundle partialResults){}
        @Override public void onEvent(int eventType,Bundle params){}
      });
      Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());
      listening=true;
      recognizer.startListening(intent);
      events.state("Starting on-device microphone...");
      return true;
    }catch(Exception e){finishListening();events.state("Cannot start voice input: "+e.getMessage());return false;}
  }
  public void stopListening(){
    if(recognizer!=null)try{recognizer.cancel();}catch(Exception ignored){}
    finishListening();events.state("Microphone stopped");
  }
  private void finishListening(){
    listening=false;
    if(recognizer!=null){recognizer.destroy();recognizer=null;}
  }
  public void speak(String text){
    if(closed || !ttsReady || !localVoiceReady){events.state("Local speech output unavailable; answer remains on screen");return;}
    if(text==null || text.trim().isEmpty())return;
    String safe=text.length()>3000?text.substring(0,3000):text;
    tts.speak(safe,TextToSpeech.QUEUE_FLUSH,null,"satellite-reply");
  }
  public void stopSpeaking(){if(tts!=null)tts.stop();}
  public void close(){
    closed=true;finishListening();
    if(tts!=null){tts.stop();tts.shutdown();tts=null;}
  }
}
