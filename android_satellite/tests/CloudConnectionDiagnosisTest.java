import com.flipperai.satellite.CloudConnectionDiagnosis;

/** Safe UI diagnostics without exposing credentials or arbitrary exceptions. */
public final class CloudConnectionDiagnosisTest {
  private static int checks;
  private static void check(String source,String contains){
    checks++;
    String text=CloudConnectionDiagnosis.explain(new IllegalStateException(source));
    if(!text.contains(contains))
      throw new AssertionError(source+" -> "+text+" did not contain "+contains);
    if(text.contains("sensitive-secret") || text.contains("https://private.example.com"))
      throw new AssertionError("Secret leaked");
  }
  public static void main(String[] args){
    check("Cloud Worker not configured","not configured");
    check("Cloud authorization revoked","permission ended");
    check("Worker HTTP 401: rejected","token was rejected");
    check("Worker HTTP 403: forbidden","denied access");
    check("Worker HTTP 404: not found","/ask route");
    check("Worker HTTP 429: limit","request limit");
    check("Worker HTTP 502: unavailable","model or gateway");
    check("Worker HTTP 503: cloud failed","AI binding");
    check("Worker HTTP 504: timeout","AI binding");
    check("Cloud network or TLS request failed.","Cannot reach");
    check("Cloud response has no reply","no AI reply");
    check("sensitive-secret https://private.example.com","Online AI request failed");
    System.out.println("CloudConnectionDiagnosisTest: "+checks+" checks passed");
  }
}
