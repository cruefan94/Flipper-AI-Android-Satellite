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
      // Race: disconnect begins while a worker's execute() is still
      // submitting a BLE job. Cancellation must wait for submission and
      // then cancel the queued FutureTask before it can run.
      CountDownLatch submitting=new CountDownLatch(1);
      CountDownLatch allowSubmit=new CountDownLatch(1);
      CountDownLatch cancelled=new CountDownLatch(1);
      AtomicInteger racedRuns=new AtomicInteger();
      Executor heldExecutor=task->{
        submitting.countDown();
        await(allowSubmit);
        executor.execute(task);
      };
      BleSessionJobs racingJobs=new BleSessionJobs(heldExecutor);
      Thread submitter=new Thread(()->check(racingJobs.submit(racedRuns::incrementAndGet),
        "race submission accepted"));
      Thread disconnector=new Thread(()->{
        racingJobs.cancelAll();
        cancelled.countDown();
      });
      // Keep the executor busy until cancellation has completed.
      CountDownLatch raceOccupied=new CountDownLatch(1),raceRelease=new CountDownLatch(1);
      Future<?> blocker=executor.submit(()->{
        raceOccupied.countDown();
        await(raceRelease);
      });
      check(raceOccupied.await(2,TimeUnit.SECONDS),"race executor occupied");
      submitter.start();
      check(submitting.await(2,TimeUnit.SECONDS),"race submission entered");
      disconnector.start();
      allowSubmit.countDown();
      submitter.join(2000);
      check(!submitter.isAlive(),"race submitter completed");
      check(cancelled.await(2,TimeUnit.SECONDS),"race disconnect completed");
      raceRelease.countDown();
      blocker.get(2,TimeUnit.SECONDS);
      executor.submit(()->{}).get(2,TimeUnit.SECONDS);
      check(racedRuns.get()==0,"disconnect cancels job submitted concurrently");
      disconnector.join(2000);
      // Rejected executor submissions must not escape as uncaught failures
      // or accidentally run after the BLE session is invalidated.
      AtomicInteger rejectedRuns=new AtomicInteger();
      BleSessionJobs rejectedJobs=new BleSessionJobs(task->{
        throw new RejectedExecutionException("executor shutting down");
      });
      check(!rejectedJobs.submit(rejectedRuns::incrementAndGet),
        "executor rejection returns false");
      rejectedJobs.cancelAll();
      rejectedJobs.close();
      check(!rejectedJobs.submit(rejectedRuns::incrementAndGet),
        "closed rejected executor cannot accept BLE work");
      check(rejectedRuns.get()==0,"rejected BLE jobs never run");

      // Activity teardown cancels queued BLE work while leaving unrelated
      // tasks on the shared Android inference executor unaffected.
      CountDownLatch closeOccupied=new CountDownLatch(1),closeRelease=new CountDownLatch(1);
      Future<?> closeBlocker=executor.submit(()->{
        closeOccupied.countDown();
        await(closeRelease);
      });
      check(closeOccupied.await(2,TimeUnit.SECONDS),"close executor occupied");
      AtomicInteger closeRuns=new AtomicInteger();
      BleSessionJobs closingJobs=new BleSessionJobs(executor);
      check(closingJobs.submit(closeRuns::incrementAndGet),"queued job before close");
      closingJobs.close();
      check(!closingJobs.submit(closeRuns::incrementAndGet),"close rejects new job");
      closeRelease.countDown();
      closeBlocker.get(2,TimeUnit.SECONDS);
      executor.submit(()->{}).get(2,TimeUnit.SECONDS);
      check(closeRuns.get()==0,"close cancels queued BLE work");
      racingJobs.close();
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
