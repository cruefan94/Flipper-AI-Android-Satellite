import com.flipperai.satellite.CloudConnectionDiagnosis;

/** Safe UI diagnostics without exposing credentials or arbitrary exceptions. */
public final class CloudConnectionDiagnosisTest {
  private static int checks;
  private static void check(String source,String contains){
    checks++;
    String text=CloudConnectionDiagnosis.explain(new IllegalStateException(source));
    if(!text.contains(contains))
      throw new AssertionError("Cloud diagnostic expected sanitized category missing (case "+checks+")");
    if(text.contains("sensitive-secret") || text.contains("https://private.example.com"))
      throw new AssertionError("Cloud diagnostic included forbidden private data (case "+checks+")");
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
    // Adversarial provider diagnostics must never reach the UI, even for known HTTP statuses.
    check("Cloud request already in progress","another cloud request is still running");
    check("Cloud response exceeds limit","too large");
    check("Worker returned HTTP 418: sensitive-secret https://private.example.com","HTTP error");
    check("Worker HTTP 401: sensitive-secret https://private.example.com","token was rejected");
    check("Worker HTTP 502: sensitive-secret https://private.example.com","model or gateway");
    check("Worker HTTP 403: sensitive-secret https://private.example.com","denied access");
    check("Worker HTTP 404: sensitive-secret https://private.example.com","/ask route");
    check("Worker HTTP 429: sensitive-secret https://private.example.com","request limit");
    check("Worker HTTP 503: sensitive-secret https://private.example.com","AI binding");
    check("Worker HTTP 504: sensitive-secret https://private.example.com","AI binding");
    check("Worker HTTP 599: sensitive-secret https://private.example.com","HTTP error");
    check("Worker HTTP 401: sensitive-secret\nAuthorization: Bearer hidden","token was rejected");
    check("Cloud network or TLS request failed. sensitive-secret https://private.example.com","Cannot reach");
    checks++;
    String missing=CloudConnectionDiagnosis.explain(null);
    if(missing.contains("sensitive-secret") || !missing.contains("Online AI failed"))
      throw new AssertionError("Null diagnostic must be sanitized");
    checks++;
    String noMessage=CloudConnectionDiagnosis.explain(new IllegalStateException());
    if(noMessage.contains("sensitive-secret") || !noMessage.contains("Online AI failed"))
      throw new AssertionError("Missing exception message must be sanitized");
    System.out.println("CloudConnectionDiagnosisTest: "+checks+" checks passed");
  }
}
