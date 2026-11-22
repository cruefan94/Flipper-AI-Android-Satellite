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
      require(f.link.take().equals(frame("ANSWER",2,"OFFLINE: LOCAL battery")),"offline answer");
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
      require(f.link.take().equals(frame("ANSWER",2,"CLOUD: CLOUD hello")),"explicit cloud");
      require(f.cloudCount.get()==1,"cloud invoked once");
      f.processor.disconnected();
      f.link.ready=false;
      f.link.ready=true;
      f.processor.resetLink();
      f.processor.receive(frame("HELLO",1,"flipper-v1"));f.link.take();
      f.processor.receive(frame("ASK",2,"hello"));
      require(f.link.take().equals(frame("ANSWER",2,"OFFLINE: LOCAL hello")),"cloud revoked on reconnect");
      require(f.cloudCount.get()==1,"no additional cloud inference");
      f.processor.setPolicy(InferenceRouter.Mode.OFFLINE,true);
      f.processor.receive(frame("ASK",3,"next"));
      require(f.link.take().equals(frame("ANSWER",3,"OFFLINE: LOCAL next")),"offline forbids cloud even if checkbox true");
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
  public static void main(String[] args)throws Exception{
    testBackgroundHelloAndAsk();
    testDisconnectedDropsSlowReply();
    testCloudNeedsFreshExplicitConsent();
    testNoRequestsWithoutAuthorizedLink();
    System.out.println("BleAiRequestProcessorTest PASS");
  }
}
