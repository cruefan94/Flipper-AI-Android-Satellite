package com.northerncircuitstudios.ai;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared speech output for LOCAL, CLOUD and HYBRID replies.
 * Only voices explicitly declared by the Android engine as on-device are used.
 * No microphone permission, no separate remote text-to-speech API.
 */
public final class VoiceSpeaker {
    public interface Listener {
        void onAvailabilityChanged(boolean available);
        void onPlaybackChanged(boolean playing);
    }

    private final Activity activity;
    private final Listener listener;
    private TextToSpeech speech;
    private volatile boolean ready = false;
    private volatile boolean closed = false;
    private volatile boolean playing = false;
    private String finalUtterance = "";
    private long generation = 0;

    public VoiceSpeaker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        speech = new TextToSpeech(activity.getApplicationContext(), result ->
            activity.runOnUiThread(() -> onInitialized(result)));
    }

    private void onInitialized(int result) {
        if (closed) return;
        ready = result == TextToSpeech.SUCCESS && speech != null;
        if (ready) {
            speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { finish(id); }
                @Override public void onError(String id) { finish(id); }
            });
        }
        listener.onAvailabilityChanged(ready && !availableVoices().isEmpty());
    }

    private void finish(String id) {
        activity.runOnUiThread(() -> {
            if (!closed && id != null && id.equals(finalUtterance)) {
                playing = false;
                listener.onPlaybackChanged(false);
            }
        });
    }

    public boolean isReady() {
        return !closed && ready && !availableVoices().isEmpty();
    }

    public boolean isPlaying() { return playing; }

    public List<Voice> availableVoices() {
        ArrayList<Voice> choices = new ArrayList<>();
        if (closed || !ready || speech == null) return choices;
        Set<Voice> voices;
        try { voices = speech.getVoices(); }
        catch (Exception error) { return choices; }
        if (voices == null) return choices;
        for (Voice voice : voices) {
            if (voice == null || voice.getLocale() == null) continue;
            if (!"en".equalsIgnoreCase(voice.getLocale().getLanguage())) continue;
            if (voice.isNetworkConnectionRequired()) continue;
            if (voice.getFeatures() != null &&
                voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                continue;
            choices.add(voice);
        }
        Collections.sort(choices, Comparator.comparing(v -> v.getLocale().toLanguageTag()
            + ":" + v.getName()));
        return choices;
    }

    public String selectedVoiceName(VoiceSettings settings) {
        List<Voice> choices = availableVoices();
        if (choices.isEmpty()) return "";
        for (Voice voice : choices)
            if (voice.getName().equals(settings.voiceName)) return voice.getName();
        Voice original = speech.getVoice();
        if (original != null) for (Voice voice : choices)
            if (voice.getName().equals(original.getName())) return voice.getName();
        Locale locale = Locale.getDefault();
        for (Voice voice : choices)
            if (voice.getLocale().getLanguage().equals("en") &&
                voice.getLocale().getCountry().equals(locale.getCountry()))
                return voice.getName();
        return choices.get(0).getName();
    }

    public String speak(String rawText, VoiceSettings settings) {
        if (closed || !ready || speech == null)
            return "Speech engine is still loading. Check Android text-to-speech settings.";
        List<Voice> voices = availableVoices();
        if (voices.isEmpty())
            return "No installed on-device English voices. Install one in Android text-to-speech settings.";
        String selected = selectedVoiceName(settings);
        Voice chosen = null;
        for (Voice option : voices)
            if (option.getName().equals(selected)) { chosen = option; break; }
        if (chosen == null || speech.setVoice(chosen) != TextToSpeech.SUCCESS)
            return "The selected speech voice is unavailable.";
        speech.setSpeechRate(settings.rate);
        speech.setPitch(settings.pitch);
        String text = toSpeechText(rawText);
        if (text.isEmpty()) return "Nothing to read aloud.";
        stop();
        int limit = Math.max(200, Math.min(2600,
            TextToSpeech.getMaxSpeechInputLength() - 100));
        ArrayList<String> segments = splitSpeech(text, limit);
        if (segments.isEmpty()) return "Nothing to read aloud.";
        long sequence = ++generation;
        playing = true;
        listener.onPlaybackChanged(true);
        for (int i = 0; i < segments.size(); i++) {
            String id = "ncs-utterance-" + sequence + "-" + i;
            if (i == segments.size() - 1) finalUtterance = id;
            int result = speech.speak(segments.get(i),
                i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                new Bundle(), id);
            if (result != TextToSpeech.SUCCESS) {
                stop();
                return "Android could not start spoken playback.";
            }
        }
        return null;
    }

    public void stop() {
        finalUtterance = "";
        if (speech != null) speech.stop();
        if (playing) {
            playing = false;
            listener.onPlaybackChanged(false);
        }
    }

    static String toSpeechText(String raw) {
        if (raw == null) return "";
        // Keep plain text; strip common Markdown delimiters for intelligible output.
        return raw.replaceAll("(?m)^```[^\\n]*$", " ")
            .replace("**", "").replace("__", "")
            .replace("`", "").replace("#", "")
            .replaceAll("(?m)^\\s*[-*]\\s+", "")
            .trim();
    }

    static ArrayList<String> splitSpeech(String text, int maxChunk) {
        ArrayList<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        int at = 0;
        while (at < text.length()) {
            int end = Math.min(text.length(), at + maxChunk);
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > at + maxChunk / 3) end = space;
            }
            String item = text.substring(at, end).trim();
            if (!item.isEmpty()) result.add(item);
            at = end;
            while (at < text.length() && text.charAt(at) == ' ') at++;
        }
        return result;
    }

    public void shutdown() {
        closed = true;
        stop();
        ready = false;
        if (speech != null) {
            speech.shutdown();
            speech = null;
        }
    }
}
