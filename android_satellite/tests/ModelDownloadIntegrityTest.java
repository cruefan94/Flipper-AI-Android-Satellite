import com.flipperai.satellite.ModelDownloadIntegrity;
import com.flipperai.satellite.VettedModelCatalog;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Pure Java synthetic tests; does not contact Hugging Face or install models. */
public final class ModelDownloadIntegrityTest {
  static void check(boolean condition,String message) {
    if(!condition)throw new AssertionError(message);
  }
  static void fails(String name,byte[] input,long size,String hash,
      ModelDownloadIntegrity.Progress progress) throws Exception {
    try {
      ModelDownloadIntegrity.copyChecked(
        new ByteArrayInputStream(input),new ByteArrayOutputStream(),
        size,hash,progress);
    }catch(IOException expected){return;}
    throw new AssertionError(name+" incorrectly accepted");
  }
  public static void main(String[] args) throws Exception {
    byte[] hello="hello".getBytes(StandardCharsets.UTF_8);
    String sha="2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    ByteArrayOutputStream valid=new ByteArrayOutputStream();
    final long[] received={-1};
    ModelDownloadIntegrity.copyChecked(new ByteArrayInputStream(hello),valid,
      hello.length,sha,(done,total)->{received[0]=done;return true;});
    check("hello".equals(valid.toString("UTF-8")),"verified payload bytes");
    check(received[0]==5,"complete progress callback");
    fails("tampered SHA",hello,5,
      "0000000000000000000000000000000000000000000000000000000000000000",
      (done,total)->true);
    fails("truncation",hello,6,sha,(done,total)->true);
    fails("oversize",hello,4,sha,(done,total)->true);
    fails("cancellation",hello,5,sha,(done,total)->false);
    check(VettedModelCatalog.QWEN3_0_6B.bytes==613722264L,"pinned byte count");
    check(VettedModelCatalog.QWEN3_0_6B.sha256.matches("[0-9a-f]{64}"),"pinned SHA-256");
    check(VettedModelCatalog.QWEN3_0_6B.url.startsWith("https://huggingface.co/"),
      "pinned HTTPS origin");
    System.out.println("Verified model download stream tests passed");
  }
}
