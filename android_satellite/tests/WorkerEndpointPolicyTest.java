import com.flipperai.satellite.WorkerEndpointPolicy;
public class WorkerEndpointPolicyTest {
  static void bad(String url,String token){
    try {WorkerEndpointPolicy.validate(url,token);throw new AssertionError("accepted "+url);}
    catch(IllegalArgumentException expected){}
  }
  public static void main(String[] args){
    String token="0123456789abcdef0123456789abcdef";
    if(!WorkerEndpointPolicy.validate("https://example.workers.dev/ask",token).equals("https://example.workers.dev/ask"))throw new AssertionError();
    bad("http://example.workers.dev/ask",token);
    bad("https://user@example.workers.dev/ask",token);
    bad("https://example.workers.dev/ask?x=1",token);
    bad("https://example.workers.dev:8443/ask",token);
    bad("https://example.workers.dev/health",token);
    bad("https://example.workers.dev/ask","short");
    bad("https://example.workers.dev/ask",token+" ");
    System.out.println("WorkerEndpointPolicy tests passed");
  }
}
