package com.flipperai.satellite;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * User-initiated, HTTPS-only model download into app-private temporary storage.
 * Redirects are manually checked. Model replacement is atomic where supported.
 * No model weights, cloud tokens or prompts enter app source/CI artifacts.
 */
public final class VettedModelDownloader {
  private VettedModelDownloader(){}
  private static final long FREE_SPACE_MARGIN=64L*1024L*1024L;
  private static final int MAX_REDIRECTS=8;

  public static void installQwen3(Context context,ModelDownloadIntegrity.Progress progress)
      throws Exception {
    VettedModelCatalog.Entry entry=VettedModelCatalog.QWEN3_0_6B;
    File target=EmbeddedModelStore.modelFile(context);
    File dir=target.getParentFile();
    if(!dir.isDirectory() && !dir.mkdirs())
      throw new IOException("Cannot create private model directory");
    if(dir.getUsableSpace()<entry.bytes+FREE_SPACE_MARGIN)
      throw new IOException("Not enough phone storage; keep at least 700 MB free");

    // Keep the existing model untouched until the entire new file passes SHA-256.
    File stage=new File(dir,"verified-download.part");
    HttpURLConnection connection=null;
    try {
      if(stage.exists() && !stage.delete())
        throw new IOException("Could not reset interrupted model download");
      URL url=new URL(entry.url);
      for(int redirects=0;;redirects++) {
        if(!"https".equalsIgnoreCase(url.getProtocol())
            || url.toURI().getRawUserInfo()!=null || url.getHost()==null || url.getHost().isEmpty()
            || (url.getPort()!=-1 && url.getPort()!=443))
          throw new IOException("Model downloads require HTTPS");
        connection=(HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setRequestProperty("Accept-Encoding","identity");
        connection.setRequestProperty("User-Agent","FlipperAISatellite/1.0");
        int status=connection.getResponseCode();
        if(status==301 || status==302 || status==303 || status==307 || status==308) {
          if(redirects>=MAX_REDIRECTS)
            throw new IOException("Model download redirected too many times");
          String location=connection.getHeaderField("Location");
          if(location==null || location.isEmpty())
            throw new IOException("Model download redirect has no location");
          URL redirect=new URL(url,location);
          connection.disconnect();
          connection=null;
          url=redirect;
          continue;
        }
        if(status!=200)throw new IOException("Model source returned HTTP "+status);
        long advertised=connection.getContentLengthLong();
        if(advertised>entry.bytes)
          throw new IOException("Model source exceeded verified size");
        try(InputStream in=connection.getInputStream();
            FileOutputStream out=new FileOutputStream(stage)) {
          ModelDownloadIntegrity.copyChecked(in,out,entry.bytes,entry.sha256,progress);
          out.getFD().sync();
        }
        break;
      }
      try {
        Files.move(stage.toPath(),target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } catch(AtomicMoveNotSupportedException unsupported) {
        Files.move(stage.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
      }
    }finally {
      if(connection!=null)connection.disconnect();
      if(stage.exists())stage.delete();
    }
  }
}
