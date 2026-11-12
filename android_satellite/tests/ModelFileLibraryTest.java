import com.flipperai.satellite.ModelFileLibrary;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class ModelFileLibraryTest {
    private static void check(boolean value) { if(!value)throw new AssertionError("Model library"); }
    private static void denied(Throwing action) throws Exception {
        boolean rejected=false;
        try { action.run(); } catch(IllegalArgumentException | SecurityException | IllegalStateException e){rejected=true;}
        check(rejected);
    }
    interface Throwing { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("model-library-test").toFile();
        try {
            File dir=new File(root,"ai_models");
            check(dir.mkdirs());
            byte[] data=new byte[1024*1024+1];
            java.util.Arrays.fill(data,(byte)42);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] hashed=digest.digest(data);
            StringBuilder hex=new StringBuilder();
            for(byte b:hashed)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            File model=new File(dir,hex+".task");
            try(FileOutputStream out=new FileOutputStream(model)){out.write(data);}
            File decoy=new File(dir,"untrusted.task");
            check(decoy.createNewFile());
            check(ModelFileLibrary.list(root).length==1);
            check(ModelFileLibrary.verify(root,model).equals(hex.toString()));
            denied(()->ModelFileLibrary.delete(root,model,model.getName()));
            try(java.io.RandomAccessFile bytes=new java.io.RandomAccessFile(model,"rw")) {
                bytes.seek(0);bytes.write(7);
            }
            denied(()->ModelFileLibrary.verify(root,model));
            ModelFileLibrary.delete(root,model,"");
            check(ModelFileLibrary.list(root).length==0);
            check(decoy.exists());
            System.out.println("Model file library tests passed");
        } finally {
            File dir=new File(root,"ai_models");
            File[] all=dir.listFiles();
            if(all!=null)for(File file:all)file.delete();
            dir.delete();root.delete();
        }
    }
}
