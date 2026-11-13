import com.flipperai.satellite.*;
public final class SatelliteCoreTest {
  private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
  public static void main(String[] args) throws Exception {
    String encoded=SatelliteProtocol.encode("HELLO",1,"phone");
    check(SatelliteProtocol.decode(encoded)[3].equals("phone"),"round-trip");
    boolean bad=false;
    try{SatelliteProtocol.encode("ASK",1,"unsafe|payload");}
    catch(IllegalArgumentException ex){bad=true;}
    check(bad,"reject malformed payload");
    check(!BondGate.canUseAiChannel(10,true),"unbonded");
    check(!BondGate.canUseAiChannel(12,false),"no consent");
    check(BondGate.canUseAiChannel(12,true),"bonded and approved");
    TransportState state=new TransportState();
    check(!state.canWrite(5),"must be connected");
    state.begin();state.connected();state.subscribing();state.ready();
    check(state.canWrite(20) && !state.canWrite(21),"default ATT payload");
    state.mtu(185);check(state.canWrite(180),"negotiated MTU");
    state.disconnected();check(!state.canWrite(1),"disconnect");
    check(TransportState.MIN_AI_ATT_PAYLOAD==32,"matches Flipper handshake minimum");
    state.begin();state.connected();state.subscribing();
    state.mtu(34);check(!state.hasHandshakeCapacity(),"31 ATT bytes cannot start Flipper AI");
    state.mtu(35);check(state.hasHandshakeCapacity(),"32 ATT bytes permit Flipper AI");
    state.disconnected();check(!state.hasHandshakeCapacity(),"disconnect clears MTU negotiation");
    InferenceRouter.OfflineEngine local=new OfflineDiagnosticEngine();
    InferenceRouter.OnlineEngine cloud=new InferenceRouter.OnlineEngine(){
      public boolean ready(){return true;}
      public String answer(String p){return "CLOUD";}
    };
    InferenceRouter router=new InferenceRouter(local,cloud);
    final int[] unexpectedCloudCalls={0};
    InferenceRouter.OnlineEngine countingCloud=new InferenceRouter.OnlineEngine(){
      public boolean ready(){return true;}
      public String answer(String p){unexpectedCloudCalls[0]++;return "CLOUD";}
    };
    InferenceRouter nullModeRouter=new InferenceRouter(local,countingCloud);
    boolean missingModeRejected=false;
    try{nullModeRouter.ask("private prompt",null,true);}
    catch(IllegalArgumentException expected){missingModeRejected=true;}
    for (String blankPrompt : new String[]{null, "", "  ", "\t"}) {
      boolean rejectedBlank=false;
      try { nullModeRouter.ask(blankPrompt,InferenceRouter.Mode.ONLINE,true); }
      catch(IllegalArgumentException expected) { rejectedBlank=true; }
      check(rejectedBlank,"blank prompt must fail before cloud routing");
    }
    check(unexpectedCloudCalls[0]==0,"blank prompts must not reach cloud");
    check(missingModeRejected && unexpectedCloudCalls[0]==0,
      "missing inference mode must fail closed without contacting cloud");
    check(!router.ask("battery",InferenceRouter.Mode.HYBRID,false).equals("CLOUD"),"local first");
    check(router.ask("general literature question",InferenceRouter.Mode.HYBRID,true).equals("CLOUD"),
      "hybrid may use cloud for unsupported questions only with consent");
    check(!router.ask("general literature question",InferenceRouter.Mode.HYBRID,false).equals("CLOUD"),
      "hybrid must not transmit to cloud without consent");
    check(router.ask("battery",InferenceRouter.Mode.HYBRID,true).contains("Power checks"),
      "specialized offline diagnostic stays local even with cloud consent");
    InferenceRouter.OfflineEngine unavailableModel=new InferenceRouter.OfflineEngine(){
      public boolean ready(){return true;}
      public boolean supports(String p){return true;}
      public String answerForHybrid(String p) throws Exception {throw new Exception("local model stopped");}
      public String answer(String p){return "LOCAL DIAGNOSTIC"; }
    };
    InferenceRouter fallbackRouter=new InferenceRouter(unavailableModel,cloud);
    check(fallbackRouter.ask("general",InferenceRouter.Mode.HYBRID,true).equals("CLOUD"),
      "consented hybrid switches from failing local model to online");
    check(fallbackRouter.ask("general",InferenceRouter.Mode.HYBRID,false).equals("LOCAL DIAGNOSTIC"),
      "no consent means only local diagnostic fallback");
    check(fallbackRouter.ask("general",InferenceRouter.Mode.OFFLINE,true).equals("LOCAL DIAGNOSTIC"),
      "offline mode never sends to cloud regardless of consent");
    InferenceRouter.OnlineEngine unavailableCloud=new InferenceRouter.OnlineEngine(){
      public boolean ready(){return true;}
      public String answer(String p) throws Exception {throw new Exception("cloud offline");}
    };
    check(new InferenceRouter(unavailableModel,unavailableCloud)
      .ask("general",InferenceRouter.Mode.HYBRID,true).equals("LOCAL DIAGNOSTIC"),
      "cloud failure must return safely to on-device diagnostics");

    boolean denied=false;
    try{router.ask("secret",InferenceRouter.Mode.ONLINE,false);}
    catch(IllegalStateException e){denied=true;}
    check(denied,"deny online without consent");
    SatelliteRequestHandler handler=new SatelliteRequestHandler(router);
    String ping=handler.handle(SatelliteProtocol.encode("PING",2,""),180);
    check(SatelliteProtocol.decode(ping)[1].equals("PONG"),"ping");
    // Rejected handshakes must not establish a session or consume request IDs.
    ProtocolSession handshakeRecovery=new ProtocolSession();
    String badHello=handshakeRecovery.dispatch(
      SatelliteProtocol.encode("HELLO",11,"wrong-version"),180,handler);
    check(SatelliteProtocol.decode(badHello)[3].equals("BAD_VERSION"),
      "unsupported HELLO rejected");
    check(!handshakeRecovery.helloSeen(),"invalid HELLO cannot open session");
    String correctedHello=handshakeRecovery.dispatch(
      SatelliteProtocol.encode("HELLO",11,"flipper-v1"),180,handler);
    check(correctedHello!=null &&
      SatelliteProtocol.decode(correctedHello)[1].equals("HELLO_ACK"),
      "corrected HELLO accepted with same request ID");
    String duplicateHello=handshakeRecovery.dispatch(
      SatelliteProtocol.encode("HELLO",12,"flipper-v1"),180,handler);
    check(SatelliteProtocol.decode(duplicateHello)[3].equals("ALREADY_CONNECTED"),
      "duplicate HELLO rejected");
    handshakeRecovery.reset();
    check(!handshakeRecovery.helloSeen(),"disconnect clears handshake");
    String freshHello=handshakeRecovery.dispatch(
      SatelliteProtocol.encode("HELLO",1,"flipper-v1"),180,handler);
    check(freshHello!=null && SatelliteProtocol.decode(freshHello)[1].equals("HELLO_ACK"),
      "reconnect accepts fresh request ID sequence");
    ProtocolSession s=new ProtocolSession();
    String rejected=s.dispatch(SatelliteProtocol.encode("ASK",3,"battery"),180,handler);
    check(SatelliteProtocol.decode(rejected)[3].equals("HELLO_REQUIRED"),"handshake required");
    String hello=s.dispatch(SatelliteProtocol.encode("HELLO",4,"flipper-v1"),180,handler);
    check(SatelliteProtocol.decode(hello)[1].equals("HELLO_ACK"),"hello");
    String reply=s.dispatch(SatelliteProtocol.encode("ASK",5,"battery"),180,handler);
    check(SatelliteProtocol.decode(reply)[1].equals("ANSWER"),"answer");
    // A response that cannot fit the negotiated ATT limit must not consume
    // the request ID; the same request can be retried when capacity recovers.
    String tooSmall=s.dispatch(SatelliteProtocol.encode("ASK",6,"battery"),12,handler);
    check(tooSmall==null,"undersized ATT response must not be sent");
    String retried=s.dispatch(SatelliteProtocol.encode("ASK",6,"battery"),180,handler);
    check(retried!=null && SatelliteProtocol.decode(retried)[1].equals("ANSWER"),
      "retry succeeds after response-capacity recovery");
    check(SatelliteProtocol.decode(s.dispatch(SatelliteProtocol.encode("ASK",6,"battery"),180,handler))[3].equals("REPLAY_ID"),
      "successfully answered request ID cannot be replayed");
    InferenceRouter.OnlineEngine largeEngine=new InferenceRouter.OnlineEngine(){
      public boolean ready(){return true;}
      public String answer(String p){return "🔋".repeat(90)+" end";}
    };
    InferenceRouter longRouter=new InferenceRouter(local,largeEngine);
    SatelliteRequestHandler longHandler=new SatelliteRequestHandler(longRouter);
    longHandler.configure(InferenceRouter.Mode.ONLINE,true);
    String limited=longHandler.handle(SatelliteProtocol.encode("ASK",7,"battery"),80);
    check(limited!=null && limited.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=80,
      "fit response to negotiated ATT size");
    check(SatelliteProtocol.decode(limited)[3].endsWith("..."),
      "truncated response must be marked");
    check(!SatelliteProtocol.decode(limited)[3].contains("�"),
      "truncation must preserve Unicode code points");
    String tiny=longHandler.handle(SatelliteProtocol.encode("ASK",8,"battery"),16);
    check(tiny==null || tiny.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=16,
      "tiny MTU never produces oversized response");

    check(SatelliteProtocol.decode(s.dispatch(SatelliteProtocol.encode("ASK",5,"battery"),180,handler))[3].equals("REPLAY_ID"),"replay");
    s.reset();check(!s.helloSeen(),"session reset");
    check(FrameCodec.decode(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(encoded),"UTF8");
    boolean invalid=false;
    try{FrameCodec.decode(new byte[]{(byte)0xc3,(byte)0x28});}
    catch(IllegalArgumentException ex){invalid=true;}
    check(invalid,"reject invalid UTF8");
    // Flipper uses uint32_t IDs: Android must preserve the full unsigned
    // range instead of truncating/rejecting values over Integer.MAX_VALUE.
    ProtocolSession unsignedSession=new ProtocolSession();
    String unsignedHello=unsignedSession.dispatch(
      SatelliteProtocol.encode("HELLO",2147483648L,"flipper-v1"),180,handler);
    check(SatelliteProtocol.decode(unsignedHello)[2].equals("2147483648"),"unsigned HELLO ID echoed");
    String unsignedAnswer=unsignedSession.dispatch(
      SatelliteProtocol.encode("ASK",4294967295L,"battery"),180,handler);
    check(SatelliteProtocol.decode(unsignedAnswer)[1].equals("ANSWER"),"uint32 max ASK handled");
    check(SatelliteProtocol.decode(unsignedAnswer)[2].equals("4294967295"),"uint32 max ID echoed");
    String unsignedReplay=unsignedSession.dispatch(
      SatelliteProtocol.encode("ASK",2147483648L,"battery"),180,handler);
    check(SatelliteProtocol.decode(unsignedReplay)[3].equals("REPLAY_ID"),"uint32 replay rejected");
    boolean unsignedOverflow=false;
    try{SatelliteProtocol.encode("ASK",4294967296L,"battery");}
    catch(IllegalArgumentException expected){unsignedOverflow=true;}
    check(unsignedOverflow,"encode rejects request IDs above uint32 max");
    String overflowFrame="AI1|ASK|4294967296|battery\n";
    String overflowReply=unsignedSession.dispatch(overflowFrame,180,handler);
    check(overflowReply==null,"invalid uint32 overflow cannot receive an AI1 reply with zero ID");

    boolean zeroEncodeRejected=false;
    try { SatelliteProtocol.encode("ASK",0,"battery"); }
    catch(IllegalArgumentException expected) { zeroEncodeRejected=true; }
    check(zeroEncodeRejected,"zero request id cannot be encoded");
    boolean zeroDecodeRejected=false;
    try { SatelliteProtocol.decode("AI1|ASK|0|battery\\n"); }
    catch(IllegalArgumentException expected) { zeroDecodeRejected=true; }
    check(zeroDecodeRejected,"zero request id cannot be decoded");
    String zeroReply=unsignedSession.dispatch("AI1|ASK|0|battery\\n",180,handler);
    check(zeroReply==null,"zero request id cannot produce an invalid zero-ID AI1 response");
    System.out.println("Satellite core tests passed");
  }
}
