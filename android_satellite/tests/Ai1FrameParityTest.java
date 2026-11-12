import com.flipperai.satellite.SatelliteProtocol;

public final class Ai1FrameParityTest {
  private static void reject(String packet) {
    try { SatelliteProtocol.decode(packet); throw new AssertionError("accepted invalid frame"); }
    catch(IllegalArgumentException expected) {}
  }
  private static void rejectEncode(String payload) {
    try { SatelliteProtocol.encode("ASK",1,payload); throw new AssertionError("encoded invalid payload"); }
    catch(IllegalArgumentException expected) {}
  }
  public static void main(String[] args) {
    String valid=SatelliteProtocol.encode("ASK",4294967295L,"Hello UTF-8: café");
    if(!"4294967295".equals(SatelliteProtocol.decode(valid)[2]))throw new AssertionError("uint32");
    reject("AI1|ASK|4294967296|hello\n");
    reject("AI1|ASK|1|hello\u0000world\n");
    reject("AI1|ASK|1|hello\u001fworld\n");
    reject("AI1|ASK|1|hello\u007fworld\n");
    reject("AI1|ASK|1|hello|world\n");
    rejectEncode("hello\u0000world");
    rejectEncode("hello\u007fworld");
    rejectEncode("hello|world");
    System.out.println("AI1 frame parity tests passed");
  }
}
