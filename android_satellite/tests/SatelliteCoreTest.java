import com.flipperai.satellite.*;
public final class SatelliteCoreTest {
  private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
  public static void main(String[] args) throws Exception {
    String encoded=SatelliteProtocol.encode("HELLO",1,"phone");
    check(SatelliteProtocol.decode(encoded)[3].equals("phone"),"round-trip");
    String emojiPayload="radio \uD83D\uDD0A";
    check(SatelliteProtocol.decode(SatelliteProtocol.encode("ASK",2,emojiPayload))[3]
      .equals(emojiPayload),"valid supplementary Unicode round-trip");
    for(String malformed : new String[]{"bad\uD83D", "bad\uDD0A", "\uDD0A\uD83D"}) {
      boolean rejected=false;
      try { SatelliteProtocol.encode("ASK",2,malformed); }
      catch(IllegalArgumentException expected) { rejected=true; }
      check(rejected,"malformed UTF-16 must not be silently replaced in AI1");
    }

    boolean negativeIdRejected=false;
    try { SatelliteProtocol.encode("ASK",-1L,"battery"); }
    catch(IllegalArgumentException expected) { negativeIdRejected=true; }
    check(negativeIdRejected,"negative request IDs must be rejected by encoder");
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
    boolean rejectedMissingOffline=false;
    try { new InferenceRouter(null,cloud); }
    catch (IllegalArgumentException expected) { rejectedMissingOffline=true; }
    check(rejectedMissingOffline,"missing offline engine must fail at configuration");
    boolean rejectedMissingCloud=false;
    try { new InferenceRouter(local,null); }
    catch (IllegalArgumentException expected) { rejectedMissingCloud=true; }
    check(rejectedMissingCloud,"missing online engine must fail at configuration");
    InferenceRouter router=new InferenceRouter(local,cloud);
    final int[] unexpectedCloudCalls={0};
    InferenceRouter.OnlineEngine countingCloud=new InferenceRouter.OnlineEngine(){
      public boolean ready(){return true;}
      public String answer(String p){unexpectedCloudCalls[0]++;return "CLOUD";}
    };
    InferenceRouter nullModeRouter=new InferenceRouter(local,countingCloud);
    SatelliteRequestHandler capacityHandler=new SatelliteRequestHandler(
      new InferenceRouter(local,countingCloud));
    capacityHandler.configure(InferenceRouter.Mode.ONLINE,true);
    String undeliverable=capacityHandler.handle(
      SatelliteProtocol.encode("ASK",4294967295L,"private prompt"),16);
    check(undeliverable==null && unexpectedCloudCalls[0]==0,
      "undersized BLE payload must not trigger consented cloud inference");

    for (InferenceRouter.Mode testMode : InferenceRouter.Mode.values()) {
      boolean oversizedRejected=false;
      try { nullModeRouter.ask("x".repeat(PromptLimits.MAX_CHARACTERS+1),testMode,true); }
      catch (IllegalArgumentException expected) { oversizedRejected=true; }
      check(oversizedRejected && unexpectedCloudCalls[0]==0,
        "oversized prompts must be rejected before cloud dispatch in "+testMode);
    }
    check(nullModeRouter.ask("x".repeat(PromptLimits.MAX_CHARACTERS),
      InferenceRouter.Mode.ONLINE,true).equals("CLOUD"),
      "maximum permitted prompt remains accepted");
    unexpectedCloudCalls[0]=0;
    for (String malformedPrompt : new String[]{"bad\uD83D", "bad\uDD0A", "\uDD0A\uD83D"}) {
      for (InferenceRouter.Mode testMode : InferenceRouter.Mode.values()) {
        boolean malformedRejected=false;
        try { nullModeRouter.ask(malformedPrompt,testMode,true); }
        catch (IllegalArgumentException expected) { malformedRejected=true; }
        check(malformedRejected && unexpectedCloudCalls[0]==0,
          "malformed prompt Unicode must not reach cloud in "+testMode);
      }
    }
    check(nullModeRouter.ask("valid \uD83D\uDD0A",InferenceRouter.Mode.ONLINE,true).equals("CLOUD"),
      "valid supplementary Unicode prompt accepted");
    unexpectedCloudCalls[0]=0;
    boolean missingModeRejected=false;
    try{nullModeRouter.ask("private prompt",null,true);}
    catch(IllegalArgumentException expected){missingModeRejected=true;}
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
    boolean missingHandlerRouterRejected=false;
    try { new SatelliteRequestHandler(null); }
    catch(IllegalArgumentException expected) { missingHandlerRouterRejected=true; }
    check(missingHandlerRouterRejected,"missing request router rejected during setup");
    SatelliteRequestHandler handler=new SatelliteRequestHandler(router);
    String ping=handler.handle(SatelliteProtocol.encode("PING",2,""),180);
    check(SatelliteProtocol.decode(ping)[1].equals("PONG"),"ping");
    ProtocolSession mtuSession=new ProtocolSession();
    String tooSmallAck=mtuSession.dispatch(
      SatelliteProtocol.encode("HELLO",10,"flipper-v1"),16,handler);
    check(tooSmallAck==null && !mtuSession.helloSeen(),
      "failed HELLO_ACK must not establish a session");
    String retriedAck=mtuSession.dispatch(
      SatelliteProtocol.encode("HELLO",10,"flipper-v1"),180,handler);
    check(retriedAck!=null && mtuSession.helloSeen() &&
      SatelliteProtocol.decode(retriedAck)[1].equals("HELLO_ACK"),
      "HELLO must succeed after ATT capacity becomes sufficient");
    ProtocolSession s=new ProtocolSession();
    String rejected=s.dispatch(SatelliteProtocol.encode("ASK",3,"battery"),180,handler);
    check(SatelliteProtocol.decode(rejected)[3].equals("HELLO_REQUIRED"),"handshake required");
    String hello=s.dispatch(SatelliteProtocol.encode("HELLO",4,"flipper-v1"),180,handler);
    check(SatelliteProtocol.decode(hello)[1].equals("HELLO_ACK"),"hello");
    String tooSmallAnswer=s.dispatch(
      SatelliteProtocol.encode("ASK",5,"battery"),16,handler);
    check(tooSmallAnswer==null,"undersized ATT payload cannot carry response");
    String reply=s.dispatch(SatelliteProtocol.encode("ASK",5,"battery"),180,handler);
    check(SatelliteProtocol.decode(reply)[1].equals("ANSWER"),"answer");
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
    check(SatelliteProtocol.decode(overflowReply)[3].equals("BAD_ID"),"decode rejects uint32 overflow");

    // An interrupted BLE inference must not emit a misleading ENGINE_UNAVAILABLE
    // response or permit hybrid escalation to the online engine.
    final int[] cloudCallsAfterCancellation={0};
    InferenceRouter.OfflineEngine interruptedLocal=new InferenceRouter.OfflineEngine(){
      public boolean ready(){return true;}
      public String answer(String prompt) throws Exception {throw new InterruptedException("cancelled");}
      public String answerForHybrid(String prompt) throws Exception {throw new InterruptedException("cancelled");}
    };
    InferenceRouter.OnlineEngine countingCloud=new InferenceRouter.OnlineEngine(){
      public boolean ready(){return true;}
      public String answer(String prompt){cloudCallsAfterCancellation[0]++;return "CLOUD";}
    };
    SatelliteRequestHandler cancelledHandler=new SatelliteRequestHandler(
      new InferenceRouter(interruptedLocal,countingCloud));
    cancelledHandler.configure(InferenceRouter.Mode.HYBRID,true);
    String cancelledFrame=cancelledHandler.handle(
      SatelliteProtocol.encode("ASK",19,"battery"),180);
    check(cancelledFrame==null,"cancelled BLE request must not produce an answer");
    check(Thread.currentThread().isInterrupted(),"BLE cancellation preserves interrupt flag");
    check(cloudCallsAfterCancellation[0]==0,"cancelled BLE request must not reach cloud");
    Thread.interrupted(); // clear test-thread flag before remaining tests

    System.out.println("Satellite core tests passed");
  }
}
