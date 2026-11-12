package com.flipperai.satellite;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** Verifies local GGUF weights before a future native inference runtime loads them. */
public final class ModelFileVerifier {
  private ModelFileVerifier(){}
  public static String sha256(File file) throws Exception {
    if(file==null||!file.isFile()||!file.canRead())throw new IllegalArgumentException("Model file not readable");
    if(file.length()<1024*1024)throw new IllegalArgumentException("Model file is unexpectedly small");
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(FileInputStream in=new FileInputStream(file)){
      byte[] chunk=new byte[65536];int n;
      while((n=in.read(chunk))!=-1)digest.update(chunk,0,n);
    }
    StringBuilder out=new StringBuilder(64);
    for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
    return out.toString();
  }
  public static void requireExpectedHash(File file,String expected) throws Exception {
    if(expected==null||!expected.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Verified SHA-256 required");
    String actual=sha256(file);
    if(!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
       expected.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      throw new SecurityException("Model checksum mismatch");
  }
}
