package com.northerncircuitstudios.ai;

import android.content.Context;
import android.content.SharedPreferences;

/** App-private preferences for Android on-device speech output (no account or remote keys). */
public final class VoiceSettings {
    private static final String PREFS = "northern_ai_speech_v1";
    public final boolean autoRead;
    public final String voiceName;
    public final float rate;
    public final float pitch;

    public VoiceSettings(boolean autoRead, String voiceName, float rate, float pitch) {
        this.autoRead = autoRead;
        this.voiceName = voiceName == null ? "" : voiceName;
        this.rate = bounded(rate);
        this.pitch = bounded(pitch);
    }

    public static float bounded(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(0.7f, Math.min(1.5f, value));
    }

    public static VoiceSettings load(Context context) {
        SharedPreferences p = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String voice = p.getString("voice", "");
        if (voice == null || voice.length() > 256) voice = "";
        return new VoiceSettings(p.getBoolean("auto", false), voice,
            p.getFloat("rate", 1.0f), p.getFloat("pitch", 1.0f));
    }

    public void save(Context context) {
        if (voiceName.length() > 256) throw new IllegalArgumentException("Invalid voice ID");
        SharedPreferences p = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.edit().putBoolean("auto", autoRead).putString("voice", voiceName)
                .putFloat("rate", rate).putFloat("pitch", pitch).commit()) {
            throw new IllegalStateException("Could not save voice settings");
        }
    }
}
