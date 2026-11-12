import com.flipperai.satellite.LocalModelEndpointPolicy;

public final class LocalModelEndpointPolicyTest {
  private static void reject(String url) {
    boolean rejected=false;
    try{LocalModelEndpointPolicy.validate(url);}
    catch(IllegalArgumentException expected){rejected=true;}
    if(!rejected)throw new AssertionError("Unsafe local-model endpoint accepted: "+url);
  }
  public static void main(String[] args){
    String valid="http://127.0.0.1:8080/v1/chat/completions";
    if(!valid.equals(LocalModelEndpointPolicy.validate(valid)))
      throw new AssertionError("Loopback model endpoint not accepted");
    reject("https://api.example.com/v1/chat/completions");
    reject("http://example.com/v1/chat/completions");
    reject("http://localhost:8080/v1/chat/completions");
    reject("http://127.0.0.2:8080/v1/chat/completions");
    reject("http://127.0.0.1.example.com:8080/v1/chat/completions");
    reject("http://127.0.0.1@evil.example:8080/v1/chat/completions");
    reject("http://user:pass@127.0.0.1:8080/v1/chat/completions");
    reject("http://127.0.0.1:80/v1/chat/completions");
    reject("http://127.0.0.1:8080/other");
    reject("http://127.0.0.1:8080/v1/chat/completions?redirect=https://evil.example");
    reject("http://127.0.0.1:8080/v1/chat/completions#fragment");
    reject("file:///tmp/model");
    System.out.println("Local model endpoint security tests passed");
  }
}
