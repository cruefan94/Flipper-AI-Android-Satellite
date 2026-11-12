import com.flipperai.satellite.*;

public final class PttProtocolTest {
    private static void check(boolean good) { if(!good)throw new AssertionError("PTT guard"); }
    private static String payload(String response) { return SatelliteProtocol.decode(response)[3]; }
    public static void main(String[] args) throws Exception {
        final int[] cloudCalls={0},pttCalls={0};
        InferenceRouter.OnlineEngine cloud=new InferenceRouter.OnlineEngine(){
            public boolean ready(){return true;}
            public String answer(String prompt){cloudCalls[0]++;return "CLOUD";}
        };
        SatelliteRequestHandler handler=new SatelliteRequestHandler(
            new InferenceRouter(new OfflineDiagnosticEngine(),cloud));
        ProtocolSession session=new ProtocolSession();
        check(payload(session.dispatch(SatelliteProtocol.encode("PTT",1,"start"),180,handler))
              .equals("HELLO_REQUIRED"));
        check(SatelliteProtocol.decode(session.dispatch(
            SatelliteProtocol.encode("HELLO",2,"flipper-v1"),180,handler))[1].equals("HELLO_ACK"));
        check(payload(session.dispatch(SatelliteProtocol.encode("PTT",3,"start"),180,handler))
              .equals("PTT_DENIED"));
        check(payload(session.dispatch(SatelliteProtocol.encode("PTT",4,"stop"),180,handler))
              .equals("BAD_PTT"));
        handler.setPhoneControl(()->{pttCalls[0]++;return true;});
        String accepted=session.dispatch(SatelliteProtocol.encode("PTT",5,"start"),180,handler);
        check(SatelliteProtocol.decode(accepted)[1].equals("PTT_ACK"));
        check(payload(accepted).equals("accepted"));
        check(payload(session.dispatch(SatelliteProtocol.encode("PTT",5,"start"),180,handler))
              .equals("REPLAY_ID"));
        check(pttCalls[0]==1 && cloudCalls[0]==0);
        handler.setPhoneControl(()->false);
        check(payload(session.dispatch(SatelliteProtocol.encode("PTT",6,"start"),180,handler))
              .equals("PTT_DENIED"));
        check(pttCalls[0]==1 && cloudCalls[0]==0);
        System.out.println("PTT protocol and deny-by-default tests passed");
    }
}
