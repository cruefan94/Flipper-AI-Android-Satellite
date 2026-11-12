package com.flipperai.satellite;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Local model weight inventory and explicit deletion; never executes GGUF content. */
public final class VerifiedModelStore {
  private VerifiedModelStore(){}
  public static File[] list(Context context) {
    File root=new File(context.getFilesDir(),"models");
    File[] files=root.listFiles(f->f.isFile()&&f.getName().matches("verified-[a-f0-9]{64}\\.gguf"));
    if(files==null)return new File[0];
    Arrays.sort(files,Comparator.comparing(File::getName));
    return files;
  }
  public static boolean delete(Context context,String filename) throws IOException {
    if(filename==null||!filename.matches("verified-[a-f0-9]{64}\\.gguf"))
      throw new IllegalArgumentException("Invalid model identifier");
    File root=new File(context.getFilesDir(),"models").getCanonicalFile();
    File target=new File(root,filename).getCanonicalFile();
    if(!root.equals(target.getParentFile()))throw new SecurityException("Model path rejected");
    if(!target.exists())return false;
    if(!target.isFile())throw new IOException("Model is not a file");
    if(!target.delete())throw new IOException("Could not delete model");
    return true;
  }
}
