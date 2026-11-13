package com.flipperai.satellite;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Import model weights from Android's document picker without buffering them in RAM.
 * No remote network access and no shared-storage permission are needed.
 */
public final class EmbeddedModelStore {
  private static final long MAX_BYTES=6L*1024L*1024L*1024L;
  private EmbeddedModelStore(){}
  public static File modelFile(Context context){
    return new File(new File(context.getFilesDir(),"offline_model"),"selected.litertlm");
  }
  public static boolean installed(Context context){
    File file=modelFile(context);
    return file.isFile() && file.length()>0L;
  }
  /** Call only on the dedicated inference worker. Preserves the previous model if import fails. */
  public static long importFromDocument(Context context,Uri uri) throws Exception {
    if(uri==null || !"content".equals(uri.getScheme()))
      throw new IllegalArgumentException("Choose a model through Android Files");
    String name=null;
    try(Cursor cursor=context.getContentResolver().query(
        uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(cursor!=null && cursor.moveToFirst())name=cursor.getString(0);
    }
    boolean direct=name!=null && name.toLowerCase(Locale.ROOT).endsWith(".litertlm");
    boolean packaged=name!=null && ModelPackageExtractor.supportsZipName(name);
    if(!direct && !packaged)
      throw new IllegalArgumentException("Choose a .litertlm model or a ZIP containing one .litertlm model");
    // The new external-weights Qwen3 INT4 export is documented to crash LiteRT-LM 0.18.x.
    if("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm".equalsIgnoreCase(name))
      throw new IllegalArgumentException("This model needs LiteRT-LM 0.19+. Choose Qwen3-0.6B.litertlm for this app.");
    File target=modelFile(context);
    File directory=target.getParentFile();
    if(!directory.isDirectory() && !directory.mkdirs())
      throw new IllegalStateException("Could not create private model storage");
    File staging=new File(directory,"import.part");
    long copied=0L;
    try {
      if(staging.exists() && !staging.delete())
        throw new IllegalStateException("Could not reset interrupted import");
      try(InputStream input=context.getContentResolver().openInputStream(uri);
          FileOutputStream output=new FileOutputStream(staging)){
        if(input==null)throw new IllegalStateException("Could not read selected model file");
        if(packaged) {
          // Extract only the model data. Never load arbitrary package code.
          copied=ModelPackageExtractor.extract(input,output);
        } else {
          byte[] buffer=new byte[65536];int count;
          while((count=input.read(buffer))!=-1){
            if(copied>MAX_BYTES-count)throw new IllegalArgumentException("Model file exceeds 6 GiB limit");
            output.write(buffer,0,count);
            copied+=count;
          }
        }
        output.getFD().sync();
      }
      if(copied==0)throw new IllegalArgumentException("Model file is empty");
      try {
        Files.move(staging.toPath(),target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      }catch(AtomicMoveNotSupportedException unsupported){
        Files.move(staging.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
      }
      return copied;
    }finally{
      if(staging.exists())staging.delete();
    }
  }
  public static boolean delete(Context context){
    File target=modelFile(context);
    return !target.exists() || target.delete();
  }
}
