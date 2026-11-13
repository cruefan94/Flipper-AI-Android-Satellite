package com.flipperai.satellite;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Search-only public Radio Browser directory. Entries are unverified, not guaranteed playable. */
public final class RadioStationDirectory {
  public static final class Entry {
    public final String name,url,country,tags,codec;
    Entry(String name,String url,String country,String tags,String codec){
      this.name=name;this.url=url;this.country=country;this.tags=tags;this.codec=codec;
    }
    @Override public String toString(){return name+" — "+country+" ("+codec+")";}
  }
  public interface Callback {void complete(List<Entry> results,String error);}
  private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
  private static final String BASE="https://de1.api.radio-browser.info/json/stations/search";
  private RadioStationDirectory(){}
  public static void search(String term,String country,String tag,Callback callback){
    EXEC.execute(()->{
      ArrayList<Entry> results=new ArrayList<>();
      String error=null;
      HttpURLConnection connection=null;
      try{
        StringBuilder q=new StringBuilder(BASE).append("?limit=40&hidebroken=true&order=clickcount&reverse=true");
        if(term!=null&&!term.trim().isEmpty())q.append("&name=").append(encode(term.trim()));
        if(country!=null&&!country.trim().isEmpty())q.append("&country=").append(encode(country.trim()));
        if(tag!=null&&!tag.trim().isEmpty())q.append("&tag=").append(encode(tag.trim()));
        URL endpoint=new URL(q.toString());
        connection=(HttpURLConnection)endpoint.openConnection();
        connection.setConnectTimeout(9000);connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(false); // Only use this fixed radio directory HTTPS endpoint.
        connection.setRequestProperty("User-Agent","FlipperAISatellite/0.10 (Android radio browser)");
        connection.setRequestProperty("Accept","application/json");
        int code=connection.getResponseCode();
        if(code!=200)throw new IOException("Directory HTTP "+code);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=connection.getInputStream()){
          byte[] buf=new byte[4096];int n;
          while((n=input.read(buf))!=-1){if(bytes.size()+n>250000)throw new IOException("Directory response too large");bytes.write(buf,0,n);}
        }
        JSONArray arr=new JSONArray(bytes.toString("UTF-8"));
        for(int i=0;i<arr.length();i++){
          JSONObject o=arr.optJSONObject(i);if(o==null)continue;
          String url=o.optString("url_resolved",o.optString("url","")).trim();
          String name=o.optString("name","").trim();
          if(name.isEmpty()||!isSafeStream(url))continue;
          results.add(new Entry(name,url,o.optString("country",""),o.optString("tags",""),o.optString("codec","")));
        }
      }catch(Exception e){error="Station directory unavailable: "+e.getMessage();}
      finally{if(connection!=null)connection.disconnect();}
      callback.complete(results,error);
    });
  }
  private static String encode(String s)throws UnsupportedEncodingException{return URLEncoder.encode(s,"UTF-8");}
  public static boolean isSafeStream(String s){
    return RadioStreamPolicy.isAllowed(s);
  }
}
