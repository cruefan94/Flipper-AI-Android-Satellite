package com.flipperai.satellite;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Connection-scoped AI1 request owner independent of Android Activities.
 * Link implementations must prove an authorized, subscribed AI session.
 * Generic Momentum RPC ping/pairing is NOT an AI channel; Phase 2 stock RPC
 * is eligible only after an explicit FAP AppStart ACK + APP_STARTED and AI1
 * HELLO handshake, gated by the separate StockMomentumRpcAi1Session.
 *
 * The same serialized worker owns ProtocolSession and the offline inference
 * engine. Disconnect cancels outstanding tasks and prevents late replies
 * crossing into a different BLE connection. This class stores no prompts.
 */
public final class BleAiRequestProcessor {
  public interface Link {
    boolean isReady();
    int maxFrameBytes();
    boolean sendFrame(String reply);
  }

  private final ExecutorService worker;
  private final BleSessionJobs jobs;
  private final SatelliteRequestHandler requests;
  private final ProtocolSession session=new ProtocolSession();
  private final SessionEpoch linkEpoch=new SessionEpoch();
  private final ConsentEpoch consentEpoch=new ConsentEpoch();
  private final Link link;
  private final Runnable revokeActive;
  private volatile InferenceRouter.Mode mode=InferenceRouter.Mode.OFFLINE;
  private volatile boolean cloudConsent=false;
  private volatile boolean closed=false;

  public BleAiRequestProcessor(
      ExecutorService worker, SatelliteRequestHandler requests, Link link,
      Runnable revokeActive) {
    if(worker==null || requests==null || link==null || revokeActive==null)
      throw new IllegalArgumentException("AI1 dependencies are required");
    this.worker=worker;
    this.jobs=new BleSessionJobs(worker);
    this.requests=requests;
    this.link=link;
    this.revokeActive=revokeActive;
  }

  /** Only a visible user action may enable cloud consent. */
  public synchronized void setPolicy(InferenceRouter.Mode next,boolean consent) {
    if(next==null)throw new IllegalArgumentException("AI1 mode");
    if(closed)return;
    boolean approved=consent && next!=InferenceRouter.Mode.OFFLINE;
    if(mode==next && cloudConsent==approved)return;
    consentEpoch.changed();
    jobs.cancelAll();
    revokeActive.run();
    mode=next;
    cloudConsent=approved;
  }

  /** A new connection must begin with HELLO; no queued frame may survive. */
  public synchronized void resetLink() {
    if(closed)return;
    linkEpoch.advance();
    jobs.cancelAll();
    revokeActive.run();
    try {worker.execute(session::reset);}
    catch(RejectedExecutionException ignored) { /* Shutdown won the race. */ }
  }

  /** Disconnection revokes cloud consent as well as the AI1 handshake. */
  public synchronized void disconnected() {
    if(closed)return;
    setPolicy(InferenceRouter.Mode.OFFLINE,false);
    resetLink();
  }

  public boolean receive(String frame) {
    if(frame==null || closed || !link.isReady())return false;
    final int epoch=linkEpoch.current();
    final int permission=consentEpoch.snapshot();
    final InferenceRouter.Mode selected=mode;
    final boolean consent=cloudConsent;
    final int limit=link.maxFrameBytes();
    return jobs.submit(()->{
      if(closed || !linkEpoch.matches(epoch) || !link.isReady())return;
      if(consent && (!cloudConsent || !consentEpoch.current(permission)))return;
      requests.configure(selected,consent);
      String answer=session.dispatch(frame,limit,requests);
      if(answer==null || Thread.currentThread().isInterrupted())return;
      if(closed || !linkEpoch.matches(epoch) || !link.isReady())return;
      if(consent && (!cloudConsent || !consentEpoch.current(permission)))return;
      // BluetoothGatt.writeCharacteristic is serialized by BleSatellite.
      // A completed AI inference is NOT proof that the GATT write succeeded.
      link.sendFrame(answer);
    });
  }

  /** Serializes model reconfiguration behind active inference. */
  public boolean submitModelRefresh(Runnable refresh) {
    if(refresh==null || closed)return false;
    try{worker.execute(refresh);return true;}
    catch(RejectedExecutionException denied){return false;}
  }

  /** No Activity closing or Bluetooth disconnect can revive old work. */
  public synchronized void close() {
    if(closed)return;
    closed=true;
    linkEpoch.advance();
    consentEpoch.changed();
    cloudConsent=false;
    mode=InferenceRouter.Mode.OFFLINE;
    jobs.close();
    revokeActive.run();
  }
}
