import com.flipperai.satellite.BleSessionJobs;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** JVM regression for Android-to-Flipper BLE request cancellation. */
public final class BleSessionJobsTest {
  static void check(boolean ok,String detail){
    if(!ok)throw new AssertionError(detail);
  }
  static void await(CountDownLatch latch) {
    try{latch.await();}
    catch(InterruptedException cancelled){Thread.currentThread().interrupt();}
  }
  public static void main(String[] args) throws Exception {
    ExecutorService executor=Executors.newSingleThreadExecutor();
    try {
      BleSessionJobs jobs=new BleSessionJobs(executor);
      CountDownLatch entered=new CountDownLatch(1);
      CountDownLatch interrupted=new CountDownLatch(1);
      check(jobs.submit(()->{
        entered.countDown();
        try{new CountDownLatch(1).await();}
        catch(InterruptedException expected){interrupted.countDown();Thread.currentThread().interrupt();}
      }),"submit active BLE request");
      check(entered.await(2,TimeUnit.SECONDS),"running BLE job entered");
      jobs.cancelAll();
      check(interrupted.await(2,TimeUnit.SECONDS),"disconnect interrupts running BLE inference");

      // Queued BLE calls must not start after link loss.
      CountDownLatch occupying=new CountDownLatch(1),release=new CountDownLatch(1);
      Future<?> hold=executor.submit(()->{
        occupying.countDown();
        await(release);
      });
      check(occupying.await(2,TimeUnit.SECONDS),"unrelated Android task started");
      AtomicInteger executed=new AtomicInteger();
      check(jobs.submit(executed::incrementAndGet),"BLE request can queue");
      jobs.cancelAll();
      release.countDown();
      hold.get(2,TimeUnit.SECONDS);
      executor.submit(()->{}).get(2,TimeUnit.SECONDS);
      check(executed.get()==0,"queued BLE request must not execute after disconnect");

      // Reconnection must allow fresh requests on the same inference executor.
      CountDownLatch again=new CountDownLatch(1);
      check(jobs.submit(again::countDown),"new connection accepts BLE work");
      check(again.await(2,TimeUnit.SECONDS),"reconnected BLE operation runs");
      jobs.close();
      check(!jobs.submit(executed::incrementAndGet),"Activity teardown rejects BLE requests");
      check(executed.get()==0,"closed gate must not execute requests");
    }finally{
      executor.shutdownNow();
      check(executor.awaitTermination(3,TimeUnit.SECONDS),"test worker terminated");
    }
    System.out.println("BleSessionJobsTest PASS");
  }
}
