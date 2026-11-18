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
    // Reject pasted whitespace rather than silently changing the configured endpoint.
    bad(" https://example.workers.dev/ask",token);
    bad("https://example.workers.dev/ask ",token);
    bad("\thttps://example.workers.dev/ask",token);
    bad("https://example.workers.dev/ask\n",token);
    bad("https://user@example.workers.dev/ask",token);
    bad("https://example.workers.dev/ask?x=1",token);
    bad("https://example.workers.dev:8443/ask",token);
    bad("https://example.workers.dev/health",token);
    bad("https://example.workers.dev/ask","short");
    bad("https://example.workers.dev/ask",token+" ");
    // Reject URLs that could redirect or smuggle configuration through URI components.
    bad("https://example.workers.dev/ask#fragment",token);
    bad("https://example.workers.dev/ask?device_token=hidden",token);
    bad("https://example.workers.dev/ask/",token);
    bad("https://example.workers.dev/%61sk",token);
    bad("https://example.workers.dev/ask/../ask",token);
    bad("https://example.workers.dev:443/ask",token);
    bad("https://example.workers.dev/ask","0123456789abcde");
    bad("https://example.workers.dev/ask",token+"\n");
    bad("https://example.workers.dev/ask",token+"\t");
    bad("https://example.workers.dev/ask",token+" ");
    // The maximum supported token length is permitted; longer tokens fail closed.
    String maxToken = "A".repeat(256);
    if(!WorkerEndpointPolicy.validate("https://example.workers.dev/ask",maxToken)
        .equals("https://example.workers.dev/ask")) throw new AssertionError("max token rejected");
    bad("https://example.workers.dev/ask",maxToken+"A");
    System.out.println("WorkerEndpointPolicy tests passed");
  }
}
