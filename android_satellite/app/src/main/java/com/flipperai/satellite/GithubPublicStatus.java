package com.flipperai.satellite;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Public GitHub read-only status. No login, token, write, or Actions execution. */
public final class GithubPublicStatus {
  private GithubPublicStatus(){}
  public static String fetchLatest(String owner,String repository) throws Exception {
    if(!owner.matches("[A-Za-z0-9-]{1,39}") || !repository.matches("[A-Za-z0-9._-]{1,100}"))
      throw new IllegalArgumentException("Invalid repository");
    URL url=new URL("https://api.github.com/repos/"+owner+"/"+repository+"/releases/latest");
    HttpURLConnection c=(HttpURLConnection)url.openConnection();
    try {
      c.setRequestMethod("GET");
      c.setRequestProperty("Accept","application/vnd.github+json");
      c.setConnectTimeout(7000);c.setReadTimeout(7000);
      c.setInstanceFollowRedirects(false);
      int status=c.getResponseCode();
      if(status==404)return "No public latest release found (private repository or no releases).";
      if(status!=200)return "GitHub returned HTTP "+status;
      try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[2048];int n;int total=0;
        while((n=in.read(buf))!=-1){total+=n;if(total>131072)throw new IllegalStateException("Response too large");out.write(buf,0,n);}
        org.json.JSONObject json=new org.json.JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        return "Latest public release: "+json.optString("tag_name","unknown")+"\n"+
          json.optString("html_url","No release URL");
      }
    } finally {c.disconnect();}
  }
}
