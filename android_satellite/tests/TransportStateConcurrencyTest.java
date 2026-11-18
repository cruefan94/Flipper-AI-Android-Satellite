import com.flipperai.satellite.SatelliteProtocol;
import com.flipperai.satellite.TransportState;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Host-side regression for Bluetooth-callback/UI state visibility.
 * Thread scheduling is ordered by latches; failures cannot hang indefinitely.
 */
public final class TransportStateConcurrencyTest {
  private static void check(boolean condition,String message){
    if(!condition)throw new AssertionError(message);
  }
  public static void main(String[] args) throws Exception {
    TransportState state=new TransportState();
    CountDownLatch connected=new CountDownLatch(1);
    CountDownLatch read=new CountDownLatch(1);
    Thread callbacks=new Thread(()->{
      try{
        state.begin();
        state.connected();
        state.subscribing();
        state.mtu(185);
        state.ready();
        connected.countDown();
        if(!read.await(5,TimeUnit.SECONDS))throw new AssertionError("reader did not finish");
        state.disconnected();
      }catch(InterruptedException e){
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    },"fake-gatt-callback");
    callbacks.start();
    check(connected.await(5,TimeUnit.SECONDS),"setup timed out");
    check(state.phase()==TransportState.Phase.READY,"ready visible on UI thread");
    check(state.payloadLimit()==180,"MTU visible on UI thread");
    check(state.canWrite(180),"bounded frame permitted");
    read.countDown();
    callbacks.join(5000);
    check(!callbacks.isAlive(),"callback finished");
    check(state.phase()==TransportState.Phase.DISCONNECTED,"disconnect visible on UI thread");
    check(!state.canWrite(1),"disconnect revokes write access");
    check(state.payloadLimit()==20,"disconnect resets negotiated payload");
    System.out.println("TransportStateConcurrency tests passed");
  }
}
