import com.flipperai.satellite.WorkerHttpStatus;

public class WorkerHttpStatusTest {
  private static void contains(int status, String expected) {
    String msg = WorkerHttpStatus.describe(status);
    if(!msg.contains(expected) || !msg.contains(Integer.toString(status)))
      throw new AssertionError("Incorrect HTTP diagnostic for " + status);
    if(msg.contains("0123456789abcdef") || msg.contains("https://"))
      throw new AssertionError("Unexpected secret or URL in diagnostic");
  }
  public static void main(String[] args) {
    contains(401, "authorization rejected");
    contains(403, "access forbidden");
    contains(404, "/ask endpoint");
    contains(429, "rate limit");
    contains(502, "upstream AI");
    contains(503, "temporarily unavailable");
    contains(504, "temporarily unavailable");
    contains(418, "Worker returned HTTP 418");
    System.out.println("WorkerHttpStatus tests passed");
  }
}
