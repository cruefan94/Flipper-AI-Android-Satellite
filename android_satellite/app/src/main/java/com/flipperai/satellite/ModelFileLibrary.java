package com.flipperai.satellite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.nio.file.Files;

/** App-private model inventory: no URLs, remote downloads or shared-storage writes. */
public final class ModelFileLibrary {
    private ModelFileLibrary() {}
    private static File directory(File filesRoot) throws IOException {
        if(filesRoot==null)throw new IllegalArgumentException("files root");
        return new File(filesRoot,"ai_models").getCanonicalFile();
    }
    private static File checked(File filesRoot,File candidate) throws IOException {
        if(candidate==null || !candidate.getName().matches("[0-9a-f]{64}\\.task"))
            throw new IllegalArgumentException("Not an imported model");
        File base=directory(filesRoot);
        if(Files.isSymbolicLink(candidate.toPath()) || !candidate.getCanonicalFile().getParentFile().equals(base))
            throw new SecurityException("Model must be a direct private file");
        if(!candidate.isFile())throw new IllegalArgumentException("Missing model");
        return candidate.getCanonicalFile();
    }
    public static File[] list(File filesRoot) throws IOException {
        File base=directory(filesRoot);
        if(!base.isDirectory())return new File[0];
        File[] all=base.listFiles();
        if(all==null)throw new IOException("Cannot list model storage");
        File[] out=new File[all.length];
        int n=0;
        for(File candidate:all) {
            try { out[n]=checked(filesRoot,candidate); n++; }
            catch(IllegalArgumentException | SecurityException ignored) { /* not an owned model file */ }
        }
        File[] result=Arrays.copyOf(out,n);
        Arrays.sort(result,(a,b)->a.getName().compareTo(b.getName()));
        return result;
    }
    /** Integrity check against the checksum that defines the filename.
     * This does not establish provenance unless the user compares a trusted published digest.
     */
    public static String verify(File filesRoot,File candidate) throws Exception {
        File model=checked(filesRoot,candidate);
        if(model.length()<1024*1024 || model.length()>ImportedModelStore.MAX_BYTES)
            throw new IllegalArgumentException("Unexpected stored model size");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(model)) {
            byte[] chunk=new byte[65536];
            int count;
            while((count=in.read(chunk))!=-1)if(count>0)digest.update(chunk,0,count);
        }
        StringBuilder actual=new StringBuilder(64);
        for(byte b:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        String expected=model.getName().substring(0,64);
        ModelChecksumPolicy.verify(expected,actual.toString());
        return actual.toString();
    }
    /** Caller must unload the active runtime before requesting deletion. */
    public static void delete(File filesRoot,File candidate,String activeModelName) throws Exception {
        File model=checked(filesRoot,candidate);
        if(model.getName().equals(activeModelName))
            throw new IllegalStateException("Unload the active model before deletion");
        if(!model.delete())throw new IOException("Could not delete selected model");
    }
}
