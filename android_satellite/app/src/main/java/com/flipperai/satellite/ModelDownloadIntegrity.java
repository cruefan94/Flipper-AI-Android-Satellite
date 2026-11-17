package com.flipperai.satellite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Pure Java stream verification. A failed download must never replace a model. */
public final class ModelDownloadIntegrity {
  private ModelDownloadIntegrity() {}
  public interface Progress {
    /** Return false to cancel. Called on the download worker, not the UI thread. */
    boolean onProgress(long received,long total);
  }
  public static void copyChecked(InputStream input,OutputStream output,
      long expectedBytes,String expectedSha256,Progress progress) throws IOException {
    if(input==null || output==null || progress==null
        || expectedBytes<=0 || expectedBytes>6L*1024*1024*1024
        || expectedSha256==null || !expectedSha256.matches("[0-9a-fA-F]{64}"))
      throw new IllegalArgumentException("Invalid verified model specification");
    final MessageDigest digest;
    try { digest=MessageDigest.getInstance("SHA-256"); }
    catch(NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    byte[] buf=new byte[65536];
    long total=0,lastReport=0;
    if(!progress.onProgress(0,expectedBytes))throw new IOException("Model download cancelled");
    for(int n;(n=input.read(buf))!=-1;) {
      if(n==0)continue;
      if(Thread.currentThread().isInterrupted())
        throw new IOException("Model download cancelled");
      if(total>expectedBytes-n)
        throw new IOException("Downloaded model exceeds verified size");
      output.write(buf,0,n);
      digest.update(buf,0,n);
      total+=n;
      if(total-lastReport>=1024*1024 || total==expectedBytes) {
        if(!progress.onProgress(total,expectedBytes))
          throw new IOException("Model download cancelled");
        lastReport=total;
      }
    }
    if(total!=expectedBytes)throw new IOException("Incomplete model download");
    byte[] expected=new byte[32];
    for(int i=0;i<32;i++)
      expected[i]=(byte)Integer.parseInt(expectedSha256.substring(i*2,i*2+2),16);
    if(!MessageDigest.isEqual(expected,digest.digest()))
      throw new IOException("Model checksum mismatch; previous model preserved");
  }
}
