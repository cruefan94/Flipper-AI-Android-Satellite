package com.flipperai.satellite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Treats an offline model ZIP as DATA, not executable code. A package can
 * contain exactly one self-contained LiteRT-LM .litertlm model plus small
 * optional documentation files. No executable payload is installed or run.
 *
 * Pure Java so the same extraction checks run on GitHub Actions without Android.
 * The caller stages the output and only replaces the previously installed
 * model after this method has validated every archive entry.
 */
public final class ModelPackageExtractor {
  public static final long MAX_MODEL_BYTES=6L*1024L*1024L*1024L;
  private static final long MAX_METADATA_BYTES=1024L*1024L;
  private static final int MAX_ENTRIES=64;
  private ModelPackageExtractor() {}

  public static boolean supportsDirectModelName(String name) {
    if(name==null)return false;
    String lower=name.toLowerCase(Locale.ROOT);
    return lower.endsWith(".litertlm") && !lower.endsWith("/.litertlm");
  }

  public static boolean supportsZipName(String name) {
    return name!=null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
  }

  private static void checkPath(String path) throws IOException {
    if(path==null || path.isEmpty() || path.length()>240 || path.indexOf('\u0000')>=0
        || path.startsWith("/") || path.indexOf('\\')>=0
        || path.indexOf(':')>=0 || path.contains("//"))
      throw new IOException("Model package contains an unsafe file path");
    for(String part:path.split("/")) {
      if(part.equals("..") || part.equals(".") || part.isEmpty())
        throw new IOException("Model package contains a traversal path");
    }
  }

  private static boolean allowedDocumentation(String path) {
    String file=path.substring(path.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
    return file.equals("readme") || file.startsWith("readme.")
        || file.equals("license") || file.startsWith("license.")
        || file.equals("notice") || file.startsWith("notice.");
  }

  public static long extract(InputStream input,OutputStream target) throws IOException {
    if(input==null || target==null)throw new IllegalArgumentException("ZIP input and model output are required");
    long modelBytes=0L, metadataBytes=0L;
    boolean seenModel=false;
    int count=0;
    byte[] buffer=new byte[65536];
    try(ZipInputStream zip=new ZipInputStream(input)) {
      ZipEntry entry;
      while((entry=zip.getNextEntry())!=null) {
        if(++count>MAX_ENTRIES)throw new IOException("Model ZIP contains too many entries");
        String name=entry.getName();
        checkPath(name);
        if(!entry.isDirectory()) {
          boolean model=supportsDirectModelName(name);
          if(model) {
            if(seenModel)throw new IOException("Model package must contain exactly one .litertlm file");
            String basename=name.substring(name.lastIndexOf('/')+1);
            if("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm".equalsIgnoreCase(basename))
              throw new IOException("This model requires a newer LiteRT-LM runtime");
            seenModel=true;
          } else if(!allowedDocumentation(name)) {
            throw new IOException("Model ZIP includes an unsupported file: "+name);
          }
          int bytesRead;
          while((bytesRead=zip.read(buffer))!=-1) {
            if(model) {
              if(modelBytes>MAX_MODEL_BYTES-bytesRead)
                throw new IOException("Model ZIP exceeds 6 GiB uncompressed model limit");
              target.write(buffer,0,bytesRead);
              modelBytes+=bytesRead;
            } else {
              if(metadataBytes>MAX_METADATA_BYTES-bytesRead)
                throw new IOException("Model ZIP includes too much documentation");
              metadataBytes+=bytesRead;
            }
          }
        }
        zip.closeEntry();
      }
    }
    if(!seenModel || modelBytes==0L)
      throw new IOException("ZIP must contain one non-empty .litertlm model");
    return modelBytes;
  }
}
