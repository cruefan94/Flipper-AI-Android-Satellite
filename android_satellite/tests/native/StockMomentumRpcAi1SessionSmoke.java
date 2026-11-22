package com.flipperai.satellite;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Pure Java Phase 2 interop test: no Android SDK or hardware needed. */
public final class StockMomentumRpcAi1SessionSmoke {
  private StockMomentumRpcAi1SessionSmoke(){}
  private static int checks;
  private static void ok(boolean condition,String message) {
    checks++;
    if(!condition)throw new AssertionError(message);
  }
  private static void reject(Runnable task) {
    checks++;
    try {task.run();throw new AssertionError("Expected rejection");}
    catch(IllegalStateException | IllegalArgumentException expected){}
  }
  private static void varint(ByteArrayOutputStream out,long value) {
    while(true) {
      int b=(int)(value & 127);value>>>=7;
      if(value==0){out.write(b);return;}
      out.write(b|128);
    }
  }
  private static byte[] field(int tag,byte[] value) {
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    varint(out,((long)tag<<3)|2);varint(out,value.length);
    out.write(value,0,value.length);
    return out.toByteArray();
  }
  private static byte[] packet(int tag,long id,int status,byte[] sub) {
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    out.write(8);varint(out,id);
    if(status!=0){out.write(16);varint(out,status);}
    byte[] body=field(tag,sub);out.write(body,0,body.length);
    byte[] raw=out.toByteArray();
    ByteArrayOutputStream frame=new ByteArrayOutputStream();
    varint(frame,raw.length);frame.write(raw,0,raw.length);
    return frame.toByteArray();
  }
  private static byte[] startState() {return packet(58,0,0,new byte[]{8,1});}
  private static byte[] stopState() {return packet(58,0,0,new byte[]{8,0});}
  private static byte[] ack(long id,int status) {return packet(4,id,status,new byte[0]);}
  private static byte[] ai1(String value) {
    return packet(65,0,0,field(1,value.getBytes(StandardCharsets.UTF_8)));
  }
  private static final class Monitor implements StockMomentumRpcAi1Session.Events {
    int ready,closed,frames;
    String lastFrame="",reason="";
    public void ready(){ready++;}
    public void closed(String why){closed++;reason=why;}
    public void received(String value){frames++;lastFrame=value;}
  }
  public static void main(String[] args) {
    final StockMomentumRpcAi1Session session=new StockMomentumRpcAi1Session();
    Monitor events=new Monitor();
    ok(session.state()==StockMomentumRpcAi1Session.State.IDLE,"starts idle");
    ok(!session.ai1HelloAckConfirmed(),"no protocol handshake before launch");
    reject(()->session.sendAiResponse("AI1|PONG|1|ok\n"));
    byte[] start=session.begin();
    ok(start.length>30,"app start packet generated");
    ok(new String(start,StandardCharsets.ISO_8859_1)
       .contains(StockMomentumRpcCodec.FAP_PATH),"exact FAP path");
    reject(session::begin);
    // Flipper may send APP_STARTED and the unsolicited AI1 HELLO before
    // the response to the outbound AppStart command arrives on Android.
    byte[] state=startState();
    for(byte b:state)session.feed(new byte[]{b},events);
    ok(events.ready==0,"app state alone is NOT authenticated RPC readiness");
    session.feed(ai1("AI1|HELLO|1|flipper-v1\n"),events);
    ok(events.frames==0,"HELLO buffered until AppStart ACK");
    session.feed(ack(2,0),events);
    ok(session.isReady() && events.ready==1,"requires both ACK and app state");
    ok(events.frames==1 && events.lastFrame.equals("AI1|HELLO|1|flipper-v1\n"),
       "preserved Flipper HELLO");
    byte[] answer=session.sendAiResponse("AI1|HELLO_ACK|1|android-v1\n");
    ok(answer.length>20 && session.pendingResponseId()==3,"HELLO_ACK queued as AppData");
    ok(!session.ai1HelloAckConfirmed(),"HELLO_ACK GATT write alone is not confirmed");
    reject(()->session.sendAiResponse("AI1|PONG|2|ok\n"));
    session.feed(ack(100,0),events);
    ok(session.pendingResponseId()==3,"stray ACK cannot confirm response");
    ok(!session.ai1HelloAckConfirmed(),"stray ACK cannot confirm HELLO_ACK");
    session.feed(ack(3,0),events);
    ok(session.responseAcknowledged(),"matching response ACK");
    ok(session.ai1HelloAckConfirmed(),"matching HELLO_ACK RPC receipt sets handshake milestone");
    session.feed(ai1("AI1|PING|2|status\n"),events);
    ok(events.lastFrame.equals("AI1|PING|2|status\n"),"PING dispatched");
    session.sendAiResponse("AI1|PONG|2|ok\n");
    session.feed(ack(4,0),events);
    ok(session.responseAcknowledged(),"second response ACK");
    session.feed(ai1("AI1|ASK|3|Can you hear me?\n"),events);
    ok(events.frames==3,"ASK dispatched without a cloud route");
    session.feed(stopState(),events);
    ok(!session.isReady() && events.closed==1,"Flipper stop kills session");
    reject(()->session.sendAiResponse("AI1|ANSWER|3|yes\n"));
    session.reset();
    ok(!session.ai1HelloAckConfirmed(),"disconnect clears protocol milestone");
    events=new Monitor();
    byte[] next=session.begin();
    ok(Arrays.equals(start,next),"new connection regenerates app start");
    session.feed(ack(2,0),events);
    ok(events.ready==0,"ACK without started event not ready");
    session.feed(startState(),events);
    ok(events.ready==1 && session.isReady(),"ACK-before-state works");
    session.feed(ai1("AI1|HELLO|1|flipper-v1\n"),events);
    ok(events.frames==1,"live HELLO passed through");
    ok(!session.ai1HelloAckConfirmed(),"reconnect does not inherit old handshake");
    byte[] invalid=packet(65,0,0,field(1,"AI1|HELLO|1|bad\nX".getBytes(StandardCharsets.UTF_8)));
    session.feed(invalid,events);
    ok(!session.isReady() && events.closed==1,"malformed frame fails closed");
    session.reset();
    events=new Monitor();
    session.begin();
    session.feed(ack(2,6),events);
    ok(!session.isReady() && events.closed==1,"rejected start not considered AI ready");
    session.reset();
    events=new Monitor();
    session.begin();session.feed(startState(),events);session.feed(ack(2,0),events);
    session.sendAiResponse("AI1|HELLO_ACK|1|android-v1\n");
    session.feed(ack(3,4),events);
    ok(!session.isReady() && events.closed==1,"remote rejected response fails closed");
    // Reconnect safety: never promote early non-HELLO data to an AI session.
    session.reset();
    events=new Monitor();
    session.begin();
    session.feed(ai1("AI1|PING|9|status\n"),events);
    ok(!session.isReady() && events.closed==1,
       "early PING rejected before verified launch");

    // An unsolicited HELLO may be buffered once, but duplicate HELLO is invalid.
    session.reset();
    events=new Monitor();
    session.begin();
    session.feed(ai1("AI1|HELLO|1|flipper-v1\n"),events);
    ok(events.frames==0 && events.closed==0,"one early HELLO buffered");
    session.feed(ai1("AI1|HELLO|1|flipper-v1\n"),events);
    ok(!session.isReady() && events.closed==1,"duplicate early HELLO fails closed");

    // A disconnect must discard buffered messages and old AppStart ACKs.
    session.reset();
    events=new Monitor();
    session.begin();
    session.feed(ai1("AI1|HELLO|1|flipper-v1\n"),events);
    session.reset();
    events=new Monitor();
    session.begin();
    session.feed(ack(3,0),events);
    session.feed(startState(),events);
    ok(!session.isReady() && events.frames==0,
       "stale response ACK cannot validate new launch or replay HELLO");
    session.feed(ack(2,0),events);
    ok(session.isReady() && events.frames==0,
       "new launch needs fresh HELLO after disconnect");

    System.out.println("Phase 2 stock Momentum AI1 session: "+checks+" checks passed");
  }
}
