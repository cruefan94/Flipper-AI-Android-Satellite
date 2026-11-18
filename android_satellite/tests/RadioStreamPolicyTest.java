import com.flipperai.satellite.RadioStreamPolicy;

/** Regression checks for permitted, receive-only HTTPS public radio URLs. */
public final class RadioStreamPolicyTest {
  private static void yes(String url) {
    if(!RadioStreamPolicy.isAllowed(url))
      throw new AssertionError("Expected public HTTPS stream to be accepted: "+url);
  }
  private static void no(String url) {
    if(RadioStreamPolicy.isAllowed(url))
      throw new AssertionError("Expected private, unsafe or non-HTTPS URL rejected: "+url);
  }
  private static void check(boolean yes,String message) {
    if(!yes)throw new AssertionError(message);
  }
  public static void main(String[] args) {
    yes("https://stream.example.org:8443/live.mp3");
    yes("https://radio.example.ca/aac");
    yes("https://subdomain.radio-browser.info:8000/stream?id=1");
    no("http://stream.example.org/live");
    no("file:///sdcard/music.mp3");
    no("content://com.android.local/secret");
    no("ftp://stream.example.org/live");
    no("https://localhost/stream");
    no("https://127.0.0.1/stream");
    no("https://192.168.1.1/stream");
    no("https://[::1]/stream");
    no("https://radio.local/live");
    no("https://foo.internal/live");
    no("https://radio.invalid/live");
    no("https://radio.test/live");
    no("https://radio.example/live");
    no("https://user:pass@radio.example.net/stream");
    no("https://radio.example.net/stream#fragment");
    no("https://radio.example.net:0/stream");
    no("https://radio.example.net:65536/stream");
    no("https://radiodomain/stream");
    no("   ");
    no(null);
    check(RadioStreamPolicy.safeLabel("Station\n  A\r") .equals("Station   A"),
      "Station label must remove CR/LF before notification");
    check(RadioStreamPolicy.safeLabel("").equals("Radio stream"),"empty radio name");
    check(RadioStreamPolicy.safeLabel(null).equals("Radio stream"),"null radio name");
    check(RadioStreamPolicy.safeLabel("x".repeat(300)).length()==100,"bounded notification label");
    System.out.println("Receive-only radio URL policy tests passed");
  }
}
