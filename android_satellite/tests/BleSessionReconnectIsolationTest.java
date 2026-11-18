import com.flipperai.satellite.BleSessionJobs;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** JVM-only repeated disconnect/reconnect regression on the shared Android worker. */
public final class BleSessionReconnectIsolationTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      BleSessionJobs session = new BleSessionJobs(worker);
      AtomicInteger stale = new AtomicInteger();
      AtomicInteger fresh = new AtomicInteger();
      AtomicInteger unrelated = new AtomicInteger();

      for (int cycle = 0; cycle < 12; cycle++) {
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> blocker = worker.submit(() -> {
          occupied.countDown();
          try { release.await(); }
          catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        check(occupied.await(2, TimeUnit.SECONDS), "worker occupied at cycle " + cycle);
        check(session.submit(stale::incrementAndGet), "queued stale BLE job " + cycle);
        session.cancelAll();
        session.cancelAll(); // duplicate disconnect callbacks must be harmless
        release.countDown();
        blocker.get(2, TimeUnit.SECONDS);
        worker.submit(unrelated::incrementAndGet).get(2, TimeUnit.SECONDS);
        check(stale.get() == 0, "disconnected BLE job leaked at cycle " + cycle);
        CountDownLatch completed = new CountDownLatch(1);
        check(session.submit(() -> { fresh.incrementAndGet(); completed.countDown(); }),
            "fresh BLE job accepted at cycle " + cycle);
        check(completed.await(2, TimeUnit.SECONDS), "fresh BLE job ran at cycle " + cycle);
      }

      check(fresh.get() == 12, "each reconnection executes one fresh BLE job");
      check(unrelated.get() == 12, "disconnect never shuts down unrelated Android work");
      session.close();
      session.close(); // Activity teardown may be repeated
      check(!session.submit(stale::incrementAndGet), "closed session rejects new BLE jobs");
      worker.submit(unrelated::incrementAndGet).get(2, TimeUnit.SECONDS);
      check(unrelated.get() == 13, "shared inference executor survives BLE close");
      check(stale.get() == 0, "no cancelled BLE job executes");
    } finally {
      worker.shutdownNow();
      check(worker.awaitTermination(3, TimeUnit.SECONDS), "worker stopped");
    }
    System.out.println("BleSessionReconnectIsolationTest PASS");
  }
}
