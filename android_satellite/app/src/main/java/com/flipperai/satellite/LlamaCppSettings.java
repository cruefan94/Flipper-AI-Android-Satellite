package com.flipperai.satellite;

import android.content.Context;

/**
 * Stores only a local-provider choice; never passwords, tokens, model weights,
 * prompt history, remote addresses, or cloud permission.
 */
public final class LlamaCppSettings {
  private LlamaCppSettings() {}
  private static final String STORE="offline_provider_choices_v1";
  private static final String ENABLED="llamacpp_loopback_opt_in";

  public static boolean enabled(Context context) {
    return context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
      .getBoolean(ENABLED,false);
  }

  public static void setEnabled(Context context,boolean enabled) {
    context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
      .edit().putBoolean(ENABLED,enabled).apply();
  }

  public static void configure(LocalModelEngine engine) {
    engine.configure(LlamaCppProfile.ENDPOINT,LlamaCppProfile.MODEL_ALIAS);
  }
}
