package com.northerncircuitstudios.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/**
 * Separate, app-private, Android Keystore-encrypted cloud consent and device token.
 * Never stores Gemini API keys; those remain only in Cloudflare Worker secrets.
 * Android Auto Backup is disabled in the application manifest.
 */
public final class CloudSettingsStore {
    private static final String STORE = "northern_ai_cloud_settings";
    private static final String RECORD = "sealed_cloud_v1";
    private static final String ALIAS = "northern_ai_cloud_key_v1";

    public static final class Settings {
        public final CloudModePolicy.Mode mode;
        public final String token;
        public final boolean shareMemoryWithCloud;
        public Settings(CloudModePolicy.Mode mode, String token) {
            this(mode, token, false);
        }
        public Settings(CloudModePolicy.Mode mode, String token, boolean shareMemoryWithCloud) {
            this.mode = mode == null ? CloudModePolicy.Mode.OFFLINE : mode;
            this.token = token == null ? "" : token;
            this.shareMemoryWithCloud = shareMemoryWithCloud;
        }
    }

    private CloudSettingsStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    public static synchronized Settings load(Context context) throws Exception {
        String sealed = prefs(context).getString(RECORD, null);
        if (sealed == null) return new Settings(CloudModePolicy.Mode.OFFLINE, "");
        JSONObject wrapper = new JSONObject(sealed);
        if (wrapper.getInt("version") != 1)
            throw new IllegalStateException("Unsupported cloud settings version");
        byte[] iv = Base64.decode(wrapper.getString("iv"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(wrapper.getString("data"), Base64.DEFAULT);
        if (iv.length != 12 || ciphertext.length > 2048)
            throw new IllegalStateException("Invalid cloud settings record");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        JSONObject data = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
        String token = data.optString("token", "");
        if (!token.isEmpty()) CloudModePolicy.validateToken(token);
        CloudModePolicy.Mode mode = CloudModePolicy.parseMode(data.optString("mode", "OFFLINE"));
        if (!CloudModePolicy.maySendToCloud(mode, token))
            mode = CloudModePolicy.Mode.OFFLINE;
        return new Settings(mode, token, data.optBoolean("shareMemoryWithCloud", false));
    }

    public static synchronized void save(Context context, Settings settings) throws Exception {
        if (settings == null) throw new IllegalArgumentException("Missing settings");
        String token = settings.token;
        if (!token.isEmpty()) CloudModePolicy.validateToken(token);
        if (settings.mode != CloudModePolicy.Mode.OFFLINE && token.isEmpty())
            throw new IllegalArgumentException("Set your Worker device token first.");
        JSONObject data = new JSONObject()
            .put("mode", settings.mode.name()).put("token", token)
            .put("shareMemoryWithCloud", settings.shareMemoryWithCloud);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new SecureRandom());
        byte[] ciphertext = cipher.doFinal(
            data.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject wrapper = new JSONObject()
            .put("version", 1)
            .put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        if (!prefs(context).edit().putString(RECORD, wrapper.toString()).commit())
            throw new IllegalStateException("Could not save cloud settings");
    }

    public static synchronized void clear(Context context) {
        if (!prefs(context).edit().remove(RECORD).commit())
            throw new IllegalStateException("Could not remove cloud credentials");
    }
}
