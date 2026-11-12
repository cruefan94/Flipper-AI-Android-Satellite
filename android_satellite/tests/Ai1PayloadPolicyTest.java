import com.flipperai.satellite.SatelliteProtocol;

public final class Ai1PayloadPolicyTest {
    private static void require(boolean ok) { if(!ok)throw new AssertionError("AI1 framing"); }
    private static void rejected(Runnable action) {
        boolean denied=false;
        try { action.run(); }catch(IllegalArgumentException expected){denied=true;}
        require(denied);
    }
    public static void main(String[] args) {
        String ok=SatelliteProtocol.encode("ANSWER",4294967295L,"normal ünicode text");
        require(SatelliteProtocol.decode(ok)[3].equals("normal ünicode text"));
        require(SatelliteProtocol.decode(SatelliteProtocol.encode("PING",0,""))[3].isEmpty());
        for(String bad:new String[]{"a|b","a\tb","a\rb","a\nb","a\u0000b","a\u007fb"}) {
            rejected(()->SatelliteProtocol.encode("ASK",1,bad));
            final String packet="AI1|ASK|1|"+bad+"\n";
            rejected(()->SatelliteProtocol.decode(packet));
        }
        rejected(()->SatelliteProtocol.decode("AI1|ASK|1|body\ntrailing\n"));
        rejected(()->SatelliteProtocol.decode("AI1|ask|1|body\n"));
        rejected(()->SatelliteProtocol.encode("ASK",4294967296L,"hi"));
        System.out.println("AI1 Android payload policy tests passed");
    }
}
