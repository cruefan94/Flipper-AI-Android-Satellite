package com.flipperai.satellite;

import java.util.List;

/**
 * Phase 2 stock Momentum RPC AppStart/AppData session gate.
 *
 * This class owns NO Bluetooth device and runs NO inference. A separately
 * authorized and bonded Android GATT transport sends the returned byte arrays.
 * Start requires both a matching successful AppStart ACK and an APP_STARTED
 * event. A Flipper HELLO sent before those arrive is buffered once only.
 *
 * Requires the stock Momentum v0.8 external FAP staged at FAP_PATH.
 * Never falls back from Bluetooth to internet or FlipperHTTP.
 */
public final class StockMomentumRpcAi1Session {
  public enum State { IDLE, STARTING, READY, FAILED }
  public interface Events {
    void ready();
    void received(String frame);
    void closed(String cause);
  }

  private final StockMomentumRpcCodec.StreamDecoder decoder=
      new StockMomentumRpcCodec.StreamDecoder();
  private State state=State.IDLE;
  private static final long LAUNCH_ID=2L;
  private long nextCommandId=3L;
  private long pendingReplyAck=0;
  private boolean launchAck=false,appStarted=false;
  private String bufferedHello;

  public synchronized State state(){return state;}
  public synchronized boolean isReady(){return state==State.READY;}
  public synchronized void reset() {
    decoder.reset();
    state=State.IDLE;
    nextCommandId=3;
    pendingReplyAck=0;
    launchAck=false;
    appStarted=false;
    bufferedHello=null;
  }

  /** Explicit action by user after bonded Momentum RPC indications + flow are ready. */
  public synchronized byte[] begin() {
    if(state!=State.IDLE)
      throw new IllegalStateException("Flipper AI RPC session is already active");
    reset();
    state=State.STARTING;
    return StockMomentumRpcCodec.appStart(LAUNCH_ID);
  }

  private void fail(String reason,Events events) {
    state=State.FAILED;
    decoder.reset();
    pendingReplyAck=0;
    bufferedHello=null;
    events.closed(reason);
  }

  private void checkReady(Events events) {
    if(state==State.STARTING && launchAck && appStarted) {
      state=State.READY;
      events.ready();
      if(bufferedHello!=null) {
        String frame=bufferedHello;
        bufferedHello=null;
        events.received(frame);
      }
    }
  }

  /** BLE indications may be fragmented or concatenated; never parse packets by substring. */
  public synchronized void feed(byte[] notification,Events events) {
    if(notification==null || events==null)
      throw new IllegalArgumentException("RPC indication/events missing");
    if(state==State.IDLE || state==State.FAILED)return;
    final List<StockMomentumRpcCodec.Message> messages;
    try {messages=decoder.feed(notification);}
    catch(IllegalArgumentException invalid) {
      fail("Malformed stock RPC protobuf frame",events);return;
    }
    for(StockMomentumRpcCodec.Message msg:messages) {
      if(state==State.FAILED)break;
      if(msg.kind==StockMomentumRpcCodec.Kind.APP_STATE) {
        if(msg.appState==0) {
          fail("Flipper AI app stopped",events);break;
        }
        if(state==State.STARTING && msg.appState==1) {
          appStarted=true;
          checkReady(events);
        }
        continue;
      }
      if(msg.kind==StockMomentumRpcCodec.Kind.EMPTY) {
        if(state==State.STARTING && msg.commandId==LAUNCH_ID) {
          if(msg.status!=0) {fail("Flipper AI launch rejected",events);break;}
          launchAck=true;
          checkReady(events);
        } else if(state==State.READY &&
                  pendingReplyAck!=0 && msg.commandId==pendingReplyAck) {
          if(msg.status!=0) {fail("Flipper rejected AI response",events);break;}
          pendingReplyAck=0;
        }
        continue;
      }
      if(msg.kind!=StockMomentumRpcCodec.Kind.APP_DATA)continue;
      if(msg.commandId!=0 || msg.status!=0) {
        fail("Unexpected stock RPC app data envelope",events);break;
      }
      final String frame;
      try {
        frame=StockMomentumRpcCodec.strictUtf8(msg.appData);
        SatelliteProtocol.decode(frame);
      }catch(IllegalArgumentException bad) {
        fail("Invalid AI1 message from Flipper",events);break;
      }
      if(state==State.STARTING) {
        // Only the opening AI1 handshake may arrive before AppStart ACK.
        String[] fields=SatelliteProtocol.decode(frame);
        if(bufferedHello!=null || !fields[1].equals("HELLO") ||
           !fields[3].equals("flipper-v1")) {
          fail("Unexpected AI1 frame before launch verification",events);break;
        }
        bufferedHello=frame;
      } else if(state==State.READY) {
        events.received(frame);
      }
    }
  }

  /** Called by the existing Android AI1 worker; no automatic cloud authorization. */
  public synchronized byte[] sendAiResponse(String ai1) {
    if(state!=State.READY || pendingReplyAck!=0)
      throw new IllegalStateException("Flipper stock RPC is not ready to reply");
    if(nextCommandId>0xFFFFFFFFL)
      throw new IllegalStateException("RPC command counter exhausted");
    long id=nextCommandId;
    byte[] data=StockMomentumRpcCodec.appData(id,ai1);
    nextCommandId++;
    pendingReplyAck=id;
    return data;
  }

  public synchronized boolean responseAcknowledged(){return pendingReplyAck==0;}
  public synchronized long pendingResponseId(){return pendingReplyAck;}
}
