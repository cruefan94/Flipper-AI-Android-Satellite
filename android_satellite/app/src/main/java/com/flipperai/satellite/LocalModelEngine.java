package com.flipperai.satellite;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional on-device model inference via a locally running OpenAI-compatible server.
 * The model server and model weights must be installed separately on the same phone.
 * This engine cannot send traffic off-device: endpoint policy permits only 127.0.0.1,
 * and redirects are disabled. No API key or prompt is stored.
 */
public final class LocalModelEngine implements InferenceRouter.OfflineEngine {
  private volatile String endpoint="";
  private volatile String model="local";
  private HttpURLConnection active;
  private long generation;
  public synchronized void configure(String url,String modelName) {
    String validated=LocalModelEndpointPolicy.validate(url);
    if(modelName==null || !modelName.matches("[A-Za-z0-9._/-]{1,64}"))
      throw new IllegalArgumentException("Model name must be 1–64 safe characters");
    revokeActive();
    endpoint=validated;model=modelName;
  }
  /** Abort an active localhost request when configuration is changed or disabled. */
  public synchronized void revokeActive(){
    generation++;
    HttpURLConnection previous=active;
    active=null;
    if(previous!=null)previous.disconnect();
  }
  public synchronized void clear(){revokeActive();endpoint="";model="local";}
  @Override public boolean ready(){return !endpoint.isEmpty();}
  @Override public String answer(String prompt) throws Exception {
    PromptLimits.validate(prompt);
    final String target, selectedModel;
    final long requestGeneration;
    synchronized(this){
      target=endpoint;selectedModel=model;requestGeneration=generation;
    }
    if(target.isEmpty())throw new IllegalStateException("Configure an Android localhost model first");
    /* Proxy.NO_PROXY prevents a configured system HTTP proxy from carrying
     * supposedly local-only model prompts off the phone. The endpoint is
     * also strictly validated as 127.0.0.1 before configuration. */
    HttpURLConnection conn=(HttpURLConnection)new URI(target).toURL().openConnection(Proxy.NO_PROXY);
    synchronized(this){
      if(generation!=requestGeneration){conn.disconnect();throw new IllegalStateException("Local model authorization revoked");}
      if(active!=null){conn.disconnect();throw new IllegalStateException("Local model request already in progress");}
      active=conn;
    }
    try {
    conn.setInstanceFollowRedirects(false);
    conn.setConnectTimeout(2000);conn.setReadTimeout(30000);
    conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setUseCaches(false);
    conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
    conn.setRequestProperty("Accept","application/json");
    JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content",prompt));
    byte[] body=new JSONObject().put("model",selectedModel).put("messages",messages)
      .put("stream",false).put("max_tokens",100).toString().getBytes(StandardCharsets.UTF_8);
    // JSON escaping can expand the UTF-8 request substantially beyond prompt length.
    // Keep a bounded body while allowing the shared 8192-character input policy.
    if(body.length>65536)throw new IllegalArgumentException("Local model request exceeds 64 KiB");
      synchronized(this){
        if(generation!=requestGeneration)throw new IllegalStateException("Local model authorization revoked");
      }
      conn.setFixedLengthStreamingMode(body.length);
      try(OutputStream out=conn.getOutputStream()){out.write(body);}
      int code=conn.getResponseCode();
      if(code!=200)throw new IllegalStateException("Local model server returned HTTP "+code);
      ByteArrayOutputStream data=new ByteArrayOutputStream();
      try(InputStream in=conn.getInputStream()){
        byte[] block=new byte[512];int n;
        while((n=in.read(block))!=-1){
          if(data.size()+n>65536)throw new IllegalStateException("Local model output too large");
          data.write(block,0,n);
        }
      }
      JSONObject json=new JSONObject(data.toString(StandardCharsets.UTF_8.name()));
      JSONArray choices=json.getJSONArray("choices");
      String reply=choices.getJSONObject(0).getJSONObject("message").getString("content").trim();
      if(reply.isEmpty())throw new IllegalStateException("Local model returned no text");
      // No autonomous hardware action or model-supplied command execution.
      synchronized(this){
        if(generation!=requestGeneration)throw new IllegalStateException("Local model authorization revoked");
      }
      return reply;
    }finally{
      synchronized(this){if(active==conn)active=null;}
      conn.disconnect();
    }
  }
}
