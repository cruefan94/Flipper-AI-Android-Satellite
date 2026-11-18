import com.flipperai.satellite.ModelPackageExtractor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Standalone JVM tests for bundled model extraction. No Android device required. */
public final class ModelPackageExtractorTest {
  static void check(boolean yes,String msg) {
    if(!yes)throw new AssertionError(msg);
  }
  static byte[] archive(String[] paths,byte[][] data) throws Exception {
    ByteArrayOutputStream output=new ByteArrayOutputStream();
    try(ZipOutputStream zip=new ZipOutputStream(output)) {
      for(int i=0;i<paths.length;i++) {
        zip.putNextEntry(new ZipEntry(paths[i]));
        if(data[i]!=null)zip.write(data[i]);
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }
  static byte[] payload(String text){return text.getBytes(StandardCharsets.UTF_8);}
  static long unpack(byte[] zip,ByteArrayOutputStream target) throws Exception {
    return ModelPackageExtractor.extract(new ByteArrayInputStream(zip),target);
  }
  static void reject(String description,byte[] zip) throws Exception {
    boolean failed=false;
    try { unpack(zip,new ByteArrayOutputStream()); }
    catch(IOException expected) { failed=true; }
    check(failed,description);
  }
  public static void main(String[] args) throws Exception {
    check(ModelPackageExtractor.supportsDirectModelName("qwen3.litertlm"),"direct LiteRT model");
    check(ModelPackageExtractor.supportsDirectModelName("QWEN3.LITERTLM"),"case insensitive extension");
    check(ModelPackageExtractor.supportsZipName("AI-model.ZIP"),"case insensitive ZIP support");
    check(!ModelPackageExtractor.supportsDirectModelName("model.gguf"),"GGUF not a LiteRT model");
    check(!ModelPackageExtractor.supportsZipName("model.apk"),"APK not a model ZIP");

    ByteArrayOutputStream result=new ByteArrayOutputStream();
    byte[] valid=archive(
      new String[]{"models/","models/Qwen3-0.6B.litertlm","README.md","LICENSE"},
      new byte[][]{null,payload("testmodel"),payload("info"),payload("license")});
    long count=unpack(valid,result);
    check(count==9 && result.toString("UTF-8").equals("testmodel"),"one model extracted");
    check(!result.toString("UTF-8").contains("license"),"metadata is never installed");

    reject("reject missing model",archive(new String[]{"README.md"},new byte[][]{payload("info")}));
    reject("reject zero-byte model",archive(new String[]{"empty.litertlm"},new byte[][]{new byte[0]}));
    reject("reject multiple models",archive(new String[]{"a.litertlm","b.litertlm"},
      new byte[][]{payload("A"),payload("B")}));
    reject("reject ZIP traversal",archive(new String[]{"../evil.litertlm"},
      new byte[][]{payload("bad")}));
    reject("reject nested traversal",archive(new String[]{"safe/../../evil.litertlm"},
      new byte[][]{payload("bad")}));
    reject("reject absolute path",archive(new String[]{"/evil.litertlm"},
      new byte[][]{payload("bad")}));
    reject("reject Windows backslashes",archive(new String[]{"folder\\evil.litertlm"},
      new byte[][]{payload("bad")}));
    reject("reject executable code",archive(new String[]{"model.litertlm","payload.dex"},
      new byte[][]{payload("model"),payload("dex")}));
    reject("reject GGUF package",archive(new String[]{"model.gguf"},
      new byte[][]{payload("gguf")}));
    reject("reject known incompatible runtime model",archive(
      new String[]{"Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"},
      new byte[][]{payload("bad")}));
    reject("reject oversized metadata",archive(new String[]{"model.litertlm","README.md"},
      new byte[][]{payload("model"),new byte[2*1024*1024]}));
    String[] manyPaths=new String[65];
    byte[][] manyData=new byte[65][];
    manyPaths[0]="model.litertlm";manyData[0]=payload("model");
    for(int i=1;i<65;i++){manyPaths[i]="files"+i+"/";manyData[i]=null;}
    reject("reject excessive entry count",archive(manyPaths,manyData));
    reject("reject invalid ZIP bytes",payload("not a zip"));
    System.out.println("Model ZIP package extraction tests passed");
  }
}
