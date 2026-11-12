package com.flipperai.satellite;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Imports a user-selected, checksum-pinned GGUF file to private app storage.
 * This installs only model weights, NOT a native inference runtime.
 */
public final class VerifiedModelImporter {
  private VerifiedModelImporter(){}
  public static final class Result {
    public final File file;
    public final long bytes;
    Result(File file,long bytes){this.file=file;this.bytes=bytes;}
  }
  public static Result importFile(Context context,Uri source,String expectedSha256) throws Exception {
    if(context==null||source==null)throw new IllegalArgumentException("Model source required");
    if(expectedSha256==null||!expectedSha256.matches("[a-fA-F0-9]{64}"))
      throw new IllegalArgumentException("Trusted SHA-256 required");
    File folder=new File(context.getFilesDir(),"models");
    if(!folder.exists()&&!folder.mkdirs())throw new IOException("Cannot create private model directory");
    StatFs stat=new StatFs(folder.getAbsolutePath());
    if(stat.getAvailableBytes()<512L*1024*1024)throw new IOException("Not enough available storage");
    File temp=File.createTempFile("model-", ".partial",folder);
    boolean completed=false;
    try {
      MessageDigest sha=MessageDigest.getInstance("SHA-256");
      long total=0,limit=6L*1024*1024*1024;
      try(InputStream in=context.getContentResolver().openInputStream(source);
          FileOutputStream out=new FileOutputStream(temp)){
        if(in==null)throw new IOException("Cannot open model document");
        byte[] buffer=new byte[65536];int count;
        while((count=in.read(buffer))!=-1){
          total+=count;
          if(total>limit)throw new IOException("Model exceeds 6 GiB import limit");
          if(new StatFs(folder.getAbsolutePath()).getAvailableBytes()<128L*1024*1024)throw new IOException("Insufficient storage while importing");
          sha.update(buffer,0,count);out.write(buffer,0,count);
        }
        out.getFD().sync();
      }
      if(total<1024*1024)throw new IOException("Model is too small");
      byte[] expected=new byte[32];
      for(int i=0;i<32;i++)expected[i]=(byte)Integer.parseInt(expectedSha256.substring(i*2,i*2+2),16);
      if(!MessageDigest.isEqual(sha.digest(),expected))throw new SecurityException("Model checksum mismatch");
      try(FileInputStream header=new FileInputStream(temp)){
        byte[] magic=new byte[4];
        if(header.read(magic)!=4||magic[0]!='G'||magic[1]!='G'||magic[2]!='U'||magic[3]!='F')
          throw new IOException("Selected file is not a GGUF model");
      }
      File target=new File(folder,"verified-"+expectedSha256.toLowerCase(Locale.ROOT)+".gguf");
      if(target.exists()&&!target.delete())throw new IOException("Cannot replace existing model");
      if(!temp.renameTo(target))throw new IOException("Cannot finalize model import");
      completed=true;
      return new Result(target,total);
    }finally{if(!completed)temp.delete();}
  }
}
