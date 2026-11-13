package com.flipperai.satellite;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** HTTPS-only client for the existing Worker /ask contract.
 * Credentials remain in memory and must never be logged or persisted.
 * Run answer() off Android's main thread.
 */
public final class WorkerCloudEngine implements InferenceRouter.OnlineEngine {
  private volatile String endpoint="", token="";
  private HttpsURLConnection active;
  private long generation;
  public synchronized void configure(String url,String deviceToken) {
    String checked=WorkerEndpointPolicy.validate(url,deviceToken);
    revokeActive();
    endpoint=checked;
    token=deviceToken;
  }
  /** Revocation aborts the active connection where possible; bytes already sent cannot be recalled. */
  public synchronized void revokeActive() {
    generation++;
    HttpsURLConnection previous=active;
    active=null;
    if(previous!=null)previous.disconnect();
  }
  public synchronized void clear() {revokeActive();endpoint="";token="";}
  @Override public boolean ready() {return !endpoint.isEmpty() && !token.isEmpty();}
  @Override public String answer(String prompt) throws Exception {
    // Apply shared Unicode and blank-input checks before any network dispatch.
    PromptLimits.validate(prompt);
    if(prompt.length()>127)
      throw new IllegalArgumentException("Cloud Worker supports prompts of 1–127 characters");
    final String url,auth;
    final long requestGeneration;
    synchronized(this) {
      url=endpoint;auth=token;requestGeneration=generation;
    }
    if(url.isEmpty() || auth.isEmpty())throw new IllegalStateException("Cloud Worker not configured");
    HttpsURLConnection conn=(HttpsURLConnection)new URI(url).toURL().openConnection(Proxy.NO_PROXY);
    synchronized(this) {
      if(generation!=requestGeneration) {conn.disconnect();throw new IllegalStateException("Cloud authorization revoked");}
      // One in-flight request per engine: otherwise a second request can
      // overwrite active and make the first uncancellable on consent loss.
      if(active!=null) {conn.disconnect();throw new IllegalStateException("Cloud request already in progress");}
      active=conn;
    }
    try {
    conn.setInstanceFollowRedirects(false);
    conn.setConnectTimeout(7000);conn.setReadTimeout(42000);
    conn.setRequestMethod("POST");conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
    conn.setRequestProperty("Accept","application/json");
    conn.setRequestProperty("X-Device-Token",auth);
    conn.setUseCaches(false);
    byte[] body=new JSONObject().put("message",prompt).toString().getBytes(StandardCharsets.UTF_8);
    if(body.length>1024)throw new IllegalArgumentException("Prompt too large");
      synchronized(this) {
        if(generation!=requestGeneration)throw new IllegalStateException("Cloud authorization revoked");
      }
      conn.setFixedLengthStreamingMode(body.length);
      try(OutputStream out=conn.getOutputStream()){out.write(body);}
      int status=conn.getResponseCode();
      if(status!=200)throw new IllegalStateException("Worker returned HTTP "+status);
      ByteArrayOutputStream data=new ByteArrayOutputStream();
      try(InputStream in=conn.getInputStream()){
        byte[] chunk=new byte[512];int n;
        while((n=in.read(chunk))!=-1) {
          if(data.size()+n>4096)throw new IllegalStateException("Cloud response exceeds limit");
          data.write(chunk,0,n);
        }
      }
      JSONObject result=new JSONObject(data.toString(StandardCharsets.UTF_8.name()));
      String reply=result.optString("reply","");
      if(reply.isEmpty())throw new IllegalStateException("Cloud response has no reply");
      // Ignore returned action suggestions: network never operates hardware directly.
      synchronized(this) {
        if(generation!=requestGeneration)throw new IllegalStateException("Cloud authorization revoked");
      }
      return reply;
    } finally {
      synchronized(this) {if(active==conn)active=null;}
      conn.disconnect();
    }
  }
}
