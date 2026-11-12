import com.flipperai.satellite.RadioStreamPolicy;

public final class RadioStreamPolicyTest {
    private static void ok(boolean value) { if(!value)throw new AssertionError("Radio URL validation"); }
    private static void reject(String source) {
        boolean denied=false;
        try{RadioStreamPolicy.validate(source);}catch(IllegalArgumentException expected){denied=true;}
        ok(denied);
    }
    public static void main(String[] args) {
        ok(RadioStreamPolicy.validate(" https://radio.example.org/live.mp3 ").equals("https://radio.example.org/live.mp3"));
        ok(RadioStreamPolicy.validate("https://stream.example.ca:8443/broadcast?station=one").contains("station=one"));
        reject("http://radio.example.org/live.mp3");
        reject("file:///tmp/audio.mp3");
        reject("https://user:pass@radio.example.org/live");
        reject("https://radio.example.org/live#fragment");
        reject("https://127.0.0.1/live");
        reject("https://192.168.1.1/live");
        reject("https://radio.local/live");
        reject("https://radio.example.org/live|other");
        reject("https://radio.example.org/live"+(char)10);
        reject("https://radio.example.org/live"+(char)127);
        reject("https://radio.example.org/live"+(char)92+"next");
        reject("https://");
        System.out.println("Radio stream endpoint policy tests passed");
    }
}
