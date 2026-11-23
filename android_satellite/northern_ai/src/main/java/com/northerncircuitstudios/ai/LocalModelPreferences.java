package com.northerncircuitstudios.ai;

import android.content.Context;
import android.content.SharedPreferences;

/** Saves only the user's selected model ID, never downloads or model weights. */
public final class LocalModelPreferences {
    private static final String FILE = "northern_ai_local_model_v1";
    private LocalModelPreferences() {}

    public static String load(Context context) {
        String id = context.getApplicationContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("id", "");
        if (id == null || id.isEmpty()) return "";
        try { return LocalModelRegistry.validateModelId(id); }
        catch (IllegalArgumentException error) { return ""; }
    }

    public static void save(Context context, String id) {
        if (id == null) throw new IllegalArgumentException("Model ID is required.");
        if (!id.isEmpty()) LocalModelRegistry.validateModelId(id);
        SharedPreferences preferences = context.getApplicationContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        preferences.edit().putString("id", id).apply();
    }
}
