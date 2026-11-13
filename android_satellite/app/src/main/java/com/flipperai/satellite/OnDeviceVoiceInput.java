package com.flipperai.satellite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Explicit phone push-to-talk using ONLY Android's on-device speech recognizer.
 *
 * Never starts the microphone automatically, never falls back to an online
 * recognizer, and never sends raw voice or transcribed text to an AI provider.
 * The hosting chat screen lets the user review/edit the transcript before SEND.
 * All methods and Android recognition callbacks must run on the main thread.
 */
public final class OnDeviceVoiceInput {
  public interface Events {
    void status(String message);
    void transcript(String text);
  }

  private final Activity activity;
  private final Events events;
  private SpeechRecognizer recognizer;
  private boolean active=false, finishing=false, closed=false;
  private int generation=0;

  public OnDeviceVoiceInput(Activity activity, Events events) {
    if(activity==null || events==null)throw new IllegalArgumentException("Voice activity and callbacks required");
    this.activity=activity;
    this.events=events;
  }

  public boolean isActive(){return active;}

  private static void requireMainThread() {
    if(Looper.myLooper()!=Looper.getMainLooper())
      throw new IllegalStateException("Voice recognition must run on Android main thread");
  }

  /** Called only after a user's explicit TALK tap and permission grant. */
  public boolean startListening() {
    requireMainThread();
    if(closed || active)return false;
    if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
      events.status("Microphone permission required. Tap TALK and allow permission.");
      return false;
    }
    if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
      events.status("On-device speech recognition is unavailable. Install an offline Android speech language pack; typed chat still works.");
      return false;
    }

    int currentGeneration=++generation;
    try {
      final SpeechRecognizer instance=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
      recognizer=instance;
      active=true;
      finishing=false;
      instance.setRecognitionListener(new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
          if(currentGeneration==generation)events.status("MIC ON • Speak now; tap FINISH when done");
        }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {
          if(currentGeneration==generation)events.status("Processing speech on this phone...");
        }
        @Override public void onError(int error) {
          if(currentGeneration!=generation)return;
          releaseRecognizer();
          if(error==SpeechRecognizer.ERROR_NO_MATCH || error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            events.status("No speech recognized. Tap TALK to try again.");
          else if(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                  error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            events.status("This language is unavailable for offline speech. Install its on-device language pack.");
          else if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            events.status("Microphone permission denied. Enable it in Android settings.");
          else events.status("On-device speech recognition failed ("+error+"). Text chat remains available.");
        }
        @Override public void onResults(Bundle results) {
          if(currentGeneration!=generation)return;
          ArrayList<String> matches=results==null?null:
              results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          String text=(matches==null || matches.isEmpty() || matches.get(0)==null)
              ? "" : matches.get(0).trim();
          releaseRecognizer();
          if(text.isEmpty())events.status("No speech recognized. Tap TALK to retry.");
          else events.transcript(text);
        }
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType,Bundle params) {}
      });

      Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      request.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());
      request.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
      request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
      instance.startListening(request);
      events.status("Starting on-device microphone...");
      return true;
    } catch(RuntimeException unavailable) {
      releaseRecognizer();
      events.status("Could not start on-device speech recognition. Check Android speech settings.");
      return false;
    }
  }

  /** End capture and wait for the final transcript; FINISH does not send an AI message. */
  public void finishListening() {
    requireMainThread();
    if(!active || recognizer==null) {
      if(!closed)events.status("Tap TALK to start microphone input.");
      return;
    }
    if(finishing)return;
    finishing=true;
    try {
      recognizer.stopListening();
      events.status("Finishing microphone input; waiting for transcript...");
    } catch(RuntimeException error) {
      releaseRecognizer();
      events.status("Could not finish speech recognition. Try typing instead.");
    }
  }

  /** Abandon all pending transcription, e.g. on background, cancel or activity destroy. */
  public void cancelListening() {
    requireMainThread();
    boolean wasActive=active;
    SpeechRecognizer old=recognizer;
    generation++;
    recognizer=null;
    active=false;
    finishing=false;
    if(old!=null) {
      try {old.cancel();}catch(RuntimeException ignored){}
      try {old.destroy();}catch(RuntimeException ignored){}
    }
    if(wasActive && !closed)events.status("Microphone cancelled. No voice text sent.");
  }

  public void close() {
    requireMainThread();
    closed=true;
    cancelListening();
  }

  private void releaseRecognizer() {
    generation++;
    SpeechRecognizer old=recognizer;
    recognizer=null;
    active=false;
    finishing=false;
    if(old!=null)try{old.destroy();}catch(RuntimeException ignored){}
  }
}
