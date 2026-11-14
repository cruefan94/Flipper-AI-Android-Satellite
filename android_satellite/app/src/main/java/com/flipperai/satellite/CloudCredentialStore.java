package com.flipperai.satellite;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/**
 * Stores only the HTTPS Worker /ask endpoint and its device token.
 * The token (and endpoint) are sealed with a non-exportable Android Keystore
 * AES-GCM key before being written to app-private preferences. No Gemini API
 * key belongs in the APK; it must remain a Cloudflare Worker server secret.
 *
 * These are reusable connection details, NOT consent to transmit prompts.
 * Each Activity session must independently require an explicit opt-in.
 */
public final class CloudCredentialStore {
  private static final String PREFS="flipper_cloud_credentials";
  private static final String RECORD="sealed_worker_v1";
  private static final String ALIAS="flipper_ai_worker_token_aes_v1";
  private CloudCredentialStore() {}

  public static final class Credentials {
    public final String endpoint;
    public final String token;
    private Credentials(String endpoint,String token){
      this.endpoint=endpoint;this.token=token;
    }
  }

  private static SharedPreferences preferences(Context context){
    return context.getApplicationContext()
      .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
  }

  private static SecretKey key() throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    KeyStore.Entry existing=store.getEntry(ALIAS,null);
    if(existing instanceof KeyStore.SecretKeyEntry)
      return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS,
      KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true)
      .setKeySize(256).build());
    return generator.generateKey();
  }

  public static synchronized void save(Context context,String endpoint,String token) throws Exception {
    String validated=WorkerEndpointPolicy.validate(endpoint,token);
    byte[] plain=new JSONObject().put("endpoint",validated).put("token",token)
      .toString().getBytes(StandardCharsets.UTF_8);
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE,key());
    byte[] ciphertext=cipher.doFinal(plain);
    JSONObject sealed=new JSONObject()
      .put("v",1)
      .put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
      .put("data",Base64.encodeToString(ciphertext,Base64.NO_WRAP));
    if(!preferences(context).edit().putString(RECORD,sealed.toString()).commit())
      throw new IllegalStateException("Could not save encrypted cloud settings");
  }

  public static synchronized Credentials load(Context context) {
    String sealed=preferences(context).getString(RECORD,null);
    if(sealed==null)return null;
    try {
      JSONObject record=new JSONObject(sealed);
      if(record.getInt("v")!=1)throw new IllegalStateException("Unknown cloud settings version");
      byte[] iv=Base64.decode(record.getString("iv"),Base64.DEFAULT);
      byte[] data=Base64.decode(record.getString("data"),Base64.DEFAULT);
      if(iv.length!=12 || data.length<16)
        throw new IllegalArgumentException("Invalid encrypted settings");
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
      JSONObject plain=new JSONObject(new String(cipher.doFinal(data),StandardCharsets.UTF_8));
      String endpoint=plain.getString("endpoint");
      String token=plain.getString("token");
      return new Credentials(WorkerEndpointPolicy.validate(endpoint,token),token);
    } catch(Exception invalidOrUnrecoverable){
      // Keystore invalidation and corrupted prefs must fail closed; do not log
      // the token, restore old consent, or trigger automatic network requests.
      clear(context);
      return null;
    }
  }

  public static synchronized void clear(Context context){
    preferences(context).edit().remove(RECORD).apply();
  }
}
