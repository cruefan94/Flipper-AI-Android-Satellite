import com.flipperai.satellite.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real Java protocol/worker tests with fake BLE + fake inference: no phone,
 * firmware, cloud connection, test credentials, or Bluetooth radio involved.
 */
public final class BleAiRequestProcessorTest {
  private static final class Link implements BleAiRequestProcessor.Link {
    volatile boolean ready=true;
    final BlockingQueue<String> replies=new LinkedBlockingQueue<>();
    @Override public boolean isReady(){return ready;}
    @Override public int maxFrameBytes(){return 180;}
    @Override public boolean sendFrame(String frame){return ready && replies.offer(frame);}
    String take()throws Exception{
      String answer=replies.poll(3,TimeUnit.SECONDS);
      if(answer==null)throw new AssertionError("Timed out waiting for AI1 response");
      return answer;
    }
    boolean empty()throws Exception{return replies.poll(150,TimeUnit.MILLISECONDS)==null;}
  }
  private static final class Fixture {
    final Link link=new Link();
    final AtomicInteger cloudCount=new AtomicInteger();
    final AtomicInteger revoked=new AtomicInteger();
    final AtomicReference<CountDownLatch> slowStarted=new AtomicReference<>();
    final AtomicReference<CountDownLatch> slowRelease=new AtomicReference<>();
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final InferenceRouter.OfflineEngine offline=new InferenceRouter.OfflineEngine(){
      @Override public boolean ready(){return true;}
      @Override public String answer(String prompt)throws Exception{
        if(prompt.equals("slow")){
          CountDownLatch entered=slowStarted.get(),release=slowRelease.get();
          if(entered!=null)entered.countDown();
          if(release!=null)release.await(3,TimeUnit.SECONDS);
        }
        return "LOCAL "+prompt;
      }
    };
    final InferenceRouter.OnlineEngine online=new InferenceRouter.OnlineEngine(){
      @Override public boolean ready(){return true;}
      @Override public String answer(String prompt){
        cloudCount.incrementAndGet();
        return "CLOUD "+prompt;
      }
    };
    final BleAiRequestProcessor processor=new BleAiRequestProcessor(
      worker,new SatelliteRequestHandler(new InferenceRouter(offline,online)),link,()->revoked.incrementAndGet());
    void finish(){
      processor.close();
      worker.shutdownNow();
    }
  }
  static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);}
  static String frame(String type,int id,String payload){
    return SatelliteProtocol.encode(type,id,payload);
  }
  static void testBackgroundHelloAndAsk()throws Exception{
    Fixture f=new Fixture();
    try{
      f.processor.resetLink();
      require(f.processor.receive(frame("HELLO",1,"flipper-v1")),"HELLO submitted");
      require(f.link.take().equals(frame("HELLO_ACK",1,"android-v1")),"HELLO acknowledged");
      require(f.processor.receive(frame("ASK",2,"battery")),"ASK submitted");
      require(f.link.take().equals(frame("ANSWER",2,"LOCAL battery")),"offline answer");
      require(f.cloudCount.get()==0,"no background cloud");
      f.processor.receive(frame("ASK",2,"battery"));
      require(f.link.take().equals(frame("ERROR",2,"REPLAY_ID")),"replay rejected");
    }finally{f.finish();}
  }
  static void testDisconnectedDropsSlowReply()throws Exception{
    Fixture f=new Fixture();
    try{
      f.processor.resetLink();
      f.processor.receive(frame("HELLO",1,"flipper-v1"));f.link.take();
      f.slowStarted.set(new CountDownLatch(1));
      f.slowRelease.set(new CountDownLatch(1));
      f.processor.receive(frame("ASK",2,"slow"));
      require(f.slowStarted.get().await(3,TimeUnit.SECONDS),"local inference started");
      f.processor.disconnected();
      f.link.ready=false;
      f.slowRelease.get().countDown();
      require(f.link.empty(),"old ASK cannot reply after disconnect");
      f.link.ready=true;
      f.processor.resetLink();
      f.processor.receive(frame("HELLO",1,"flipper-v1"));
      require(f.link.take().equals(frame("HELLO_ACK",1,"android-v1")),"new connection needs HELLO");
      require(f.revoked.get()>0,"abort callback issued");
    }finally{f.finish();}
  }
  static void testCloudNeedsFreshExplicitConsent()throws Exception{
    Fixture f=new Fixture();
    try{
      f.processor.resetLink();
      f.processor.receive(frame("HELLO",1,"flipper-v1"));f.link.take();
      f.processor.setPolicy(InferenceRouter.Mode.ONLINE,true);
      f.processor.receive(frame("ASK",2,"hello"));
      require(f.link.take().equals(frame("ANSWER",2,"CLOUD hello")),"explicit cloud");
      require(f.cloudCount.get()==1,"cloud invoked once");
      f.processor.disconnected();
      f.link.ready=false;
      f.link.ready=true;
      f.processor.resetLink();
      f.processor.receive(frame("HELLO",1,"flipper-v1"));f.link.take();
      f.processor.receive(frame("ASK",2,"hello"));
      require(f.link.take().equals(frame("ANSWER",2,"LOCAL hello")),"cloud revoked on reconnect");
      require(f.cloudCount.get()==1,"no additional cloud inference");
      f.processor.setPolicy(InferenceRouter.Mode.OFFLINE,true);
      f.processor.receive(frame("ASK",3,"next"));
      require(f.link.take().equals(frame("ANSWER",3,"LOCAL next")),"offline forbids cloud even if checkbox true");
    }finally{f.finish();}
  }
  static void testNoRequestsWithoutAuthorizedLink()throws Exception{
    Fixture f=new Fixture();
    try{
      f.link.ready=false;
      require(!f.processor.receive(frame("HELLO",1,"flipper-v1")),"GATT readiness gate");
      require(f.link.empty(),"not ready sends nothing");
      f.processor.close();
      f.link.ready=true;
      require(!f.processor.receive(frame("HELLO",1,"flipper-v1")),"closed service rejects frames");
    }finally{f.finish();}
  }
  static void testPolicyRevocationCancelsInflightReply()throws Exception{
    Fixture f=new Fixture();
    try{
      f.processor.resetLink();
      require(f.processor.receive(frame("HELLO",1,"flipper-v1")),"HELLO submitted");
      require(f.link.take().equals(frame("HELLO_ACK",1,"android-v1")),"HELLO acknowledged");
      f.slowStarted.set(new CountDownLatch(1));
      f.slowRelease.set(new CountDownLatch(1));
      require(f.processor.receive(frame("ASK",2,"slow")),"slow ASK submitted");
      require(f.slowStarted.get().await(3,TimeUnit.SECONDS),"inference entered");
      // A no-op OFFLINE,false -> OFFLINE,false is not a policy revocation.
      // Change mode while inference is in flight, without authorizing cloud.
      f.processor.setPolicy(InferenceRouter.Mode.HYBRID,false);
      f.slowRelease.get().countDown();
      require(f.link.empty(),"revoked policy cannot send stale inference reply");
      f.processor.setPolicy(InferenceRouter.Mode.OFFLINE,false);
      require(f.processor.receive(frame("ASK",3,"fresh")),"fresh offline ASK submitted");
      require(f.link.take().equals(frame("ANSWER",3,"LOCAL fresh")),"new offline request succeeds");
      require(f.cloudCount.get()==0,"no cloud request on policy revocation");
    }finally{f.finish();}
  }
  static void testHybridWithoutConsentStaysLocal()throws Exception{
    Fixture f=new Fixture();
    try{
      f.processor.resetLink();
      require(f.processor.receive(frame("HELLO",1,"flipper-v1")),"HELLO submitted");
      require(f.link.take().equals(frame("HELLO_ACK",1,"android-v1")),"HELLO acknowledged");
      // Selecting HYBRID is not permission to transmit prompts to cloud.
      f.processor.setPolicy(InferenceRouter.Mode.HYBRID,false);
      require(f.processor.receive(frame("ASK",2,"private prompt")),"hybrid ASK submitted");
      require(f.link.take().equals(frame("ANSWER",2,"LOCAL private prompt")),"hybrid without consent remains offline");
      require(f.cloudCount.get()==0,"hybrid without consent never invokes cloud");
      f.processor.setPolicy(InferenceRouter.Mode.OFFLINE,false);
      require(f.processor.receive(frame("ASK",3,"next")),"offline ASK submitted");
      require(f.link.take().equals(frame("ANSWER",3,"LOCAL next")),"offline remains usable after policy change");
      require(f.cloudCount.get()==0,"offline policy never invokes cloud");
    }finally{f.finish();}
  }
  static void testOnlineConsentToggleFailClosed()throws Exception{
    Fixture f=new Fixture();
    try{
      f.processor.resetLink();
      require(f.processor.receive(frame("HELLO",1,"flipper-v1")),"HELLO submitted");
      require(f.link.take().equals(frame("HELLO_ACK",1,"android-v1")),"HELLO acknowledged");
      f.processor.setPolicy(InferenceRouter.Mode.ONLINE,false);
      require(f.processor.receive(frame("ASK",2,"private")),"online without consent ASK");
      require(f.link.take().equals(frame("ERROR",2,"ENGINE_UNAVAILABLE")),"ONLINE without consent fails closed");
      require(f.cloudCount.get()==0,"no cloud call without explicit consent");
      f.processor.setPolicy(InferenceRouter.Mode.ONLINE,true);
      require(f.processor.receive(frame("ASK",3,"allowed")),"consented ASK");
      require(f.link.take().equals(frame("ANSWER",3,"CLOUD allowed")),"consented online request");
      require(f.cloudCount.get()==1,"exactly one cloud call with consent");
      f.processor.setPolicy(InferenceRouter.Mode.ONLINE,false);
      require(f.processor.receive(frame("ASK",4,"revoked")),"revoked ASK");
      require(f.link.take().equals(frame("ERROR",4,"ENGINE_UNAVAILABLE")),"revocation blocks ONLINE requests");
      require(f.cloudCount.get()==1,"revocation prevents additional cloud calls");
    }finally{f.finish();}
  }
  public static void main(String[] args)throws Exception{
    testBackgroundHelloAndAsk();
    testDisconnectedDropsSlowReply();
    testCloudNeedsFreshExplicitConsent();
    testHybridWithoutConsentStaysLocal();
    testOnlineConsentToggleFailClosed();
    testPolicyRevocationCancelsInflightReply();
    testNoRequestsWithoutAuthorizedLink();
    System.out.println("BleAiRequestProcessorTest PASS");
  }
}
