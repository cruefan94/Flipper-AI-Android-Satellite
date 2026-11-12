package com.flipperai.satellite;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Copies user-chosen local model bytes into app-private storage.
 * Never downloads models, trusts remote URLs, or modifies the source document.
 */
public final class ImportedModelStore {
    public static final long MAX_BYTES=3L*1024*1024*1024;
    private ImportedModelStore() {}
    public static final class Imported {
        public final File file;
        public final String sha256;
        public final long bytes;
        private Imported(File file,String sha256,long bytes) {
            this.file=file;this.sha256=sha256;this.bytes=bytes;
        }
    }
    /** Blocking I/O: call only on background worker. SHA-256 is for integrity
     * identification; without a trusted expected hash it does not prove authenticity.
     */
    public static Imported importTask(Context context,Uri source) throws Exception {
        if(context==null || source==null || !"content".equals(source.getScheme()))
            throw new IllegalArgumentException("Select a model using Android's document picker");
        File dir=new File(context.getFilesDir(),"ai_models");
        if(!dir.isDirectory() && !dir.mkdirs())throw new IllegalStateException("Model storage unavailable");
        if(new android.os.StatFs(dir.getAbsolutePath()).getAvailableBytes()<256L*1024*1024)throw new IllegalStateException("Insufficient storage for model import");
        File partial=File.createTempFile("model-", ".partial", dir);
        long copied=0;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try {
            try(InputStream input=context.getContentResolver().openInputStream(source);
                FileOutputStream output=new FileOutputStream(partial)) {
                if(input==null)throw new IllegalArgumentException("Cannot open selected model");
                byte[] buffer=new byte[65536];
                int n;
                while((n=input.read(buffer))!=-1) {
                    if(n==0)continue;
                    copied+=n;
                    if(copied>MAX_BYTES)throw new IllegalArgumentException("Model exceeds 3 GiB import limit");
                    if(new android.os.StatFs(dir.getAbsolutePath()).getAvailableBytes()<128L*1024*1024)throw new IllegalStateException("Storage critically low; model import stopped");
                    digest.update(buffer,0,n);
                    output.write(buffer,0,n);
                }
                output.getFD().sync();
            }
            if(copied<1024*1024)throw new IllegalArgumentException("Selected model is unexpectedly small");
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            File destination=new File(dir,hex+".task");
            if(destination.isFile()){
                if(destination.length()!=copied)throw new IllegalStateException("Existing model has mismatched size");
                return new Imported(destination,hex.toString(),copied);
            }
            if(!partial.renameTo(destination))throw new IllegalStateException("Cannot finalize model import");
            return new Imported(destination,hex.toString(),copied);
        }finally{
            if(partial.exists())partial.delete();
        }
    }
}
