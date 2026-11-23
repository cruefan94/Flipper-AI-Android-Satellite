package com.northerncircuitstudios.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** App-private encrypted conversation, without Android Auto Backup. */
public final class EncryptedChatStore {
    private static final String STORE = "northern_ai_local_history";
    private static final String RECORD = "sealed_chat_v1";
    private static final String ALIAS = "northern_ai_local_chat_key_v1";
    private EncryptedChatStore() {}

    private static SharedPreferences preferences(Context context) {
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

    public static synchronized void save(Context context, List<AssistantPolicy.Entry> history)
            throws Exception {
        JSONArray saved = new JSONArray();
        for (AssistantPolicy.Entry entry : AssistantPolicy.boundedHistory(history))
            saved.put(new JSONObject().put("role", entry.role).put("text", entry.text));
        byte[] plaintext = saved.toString().getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > 300000)
            throw new IllegalArgumentException("History too large");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new SecureRandom());
        byte[] secret = cipher.doFinal(plaintext);
        JSONObject record = new JSONObject()
            .put("version", 1)
            .put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .put("data", Base64.encodeToString(secret, Base64.NO_WRAP));
        if (!preferences(context).edit().putString(RECORD, record.toString()).commit())
            throw new IllegalStateException("Unable to persist encrypted chat");
    }

    public static synchronized List<AssistantPolicy.Entry> load(Context context) throws Exception {
        ArrayList<AssistantPolicy.Entry> entries = new ArrayList<>();
        String sealed = preferences(context).getString(RECORD, null);
        if (sealed == null) return entries;
        JSONObject record = new JSONObject(sealed);
        if (record.getInt("version") != 1)
            throw new IllegalStateException("Unsupported saved history");
        byte[] iv = Base64.decode(record.getString("iv"), Base64.DEFAULT);
        byte[] data = Base64.decode(record.getString("data"), Base64.DEFAULT);
        if (iv.length != 12 || data.length > 300016)
            throw new IllegalStateException("Invalid encrypted history");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(data);
        JSONArray array = new JSONArray(new String(plaintext, StandardCharsets.UTF_8));
        if (array.length() > AssistantPolicy.MAX_STORED_ENTRIES)
            throw new IllegalStateException("Saved history exceeds limit");
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            entries.add(new AssistantPolicy.Entry(item.getString("role"), item.getString("text")));
        }
        return entries;
    }

    public static synchronized void clear(Context context) {
        if (!preferences(context).edit().remove(RECORD).commit())
            throw new IllegalStateException("Failed to erase conversation record");
    }
}
