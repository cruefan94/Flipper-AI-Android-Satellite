package com.flipperai.satellite;

import android.os.StatFs;
import java.io.File;

/** Preflight checks for a future native model loader; never claims inference is installed. */
public final class LocalModelReadiness {
  private LocalModelReadiness(){}
  public static String inspect(File modelDirectory,long minimumFreeBytes){
    if(modelDirectory==null||!modelDirectory.exists())return "Model storage unavailable";
    if(minimumFreeBytes<0)return "Invalid storage requirement";
    try {
      StatFs stat=new StatFs(modelDirectory.getAbsolutePath());
      long available=stat.getAvailableBytes();
      if(available<minimumFreeBytes)return "Not enough free space for model and working memory";
      return "Storage check passed; model runtime installation still required";
    }catch(RuntimeException e){return "Unable to inspect model storage";}
  }
}
