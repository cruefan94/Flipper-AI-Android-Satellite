import com.flipperai.satellite.SessionEpoch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** JVM-only reconnect/callback invalidation; not proof of a physical AI1 link. */
public final class SessionEpochTest {
  private static void check(boolean ok, String reason) {
    if(!ok) throw new AssertionError(reason);
  }
  public static void main(String[] args) throws Exception {
    SessionEpoch e = new SessionEpoch();
    int initial = e.current();
    check(e.matches(initial), "initial epoch");
    Thread disconnect = new Thread(e::advance);
    disconnect.start();
    disconnect.join();
    check(!e.matches(initial), "stale session accepted");
    int fresh = e.current();
    check(e.matches(fresh), "new epoch rejected");
    e.advance();
    check(!e.matches(fresh), "stale notification accepted");

    // Every reconnect invalidates all previously captured callback epochs.
    List<Integer> oldCallbacks = new ArrayList<>();
    for(int cycle = 0; cycle < 256; cycle++) {
      int active = e.current();
      check(e.matches(active), "current callback rejected at cycle " + cycle);
      oldCallbacks.add(active);
      int next = e.advance();
      check(next != active, "reconnect did not advance epoch");
      for(int stale : oldCallbacks) {
        check(!e.matches(stale), "stale callback accepted at cycle " + cycle);
      }
      check(e.matches(next), "fresh callback rejected at cycle " + cycle);
    }

    // Simulate a delayed callback from an old GATT session after a new link.
    int oldLink = e.current();
    CountDownLatch releaseCallback = new CountDownLatch(1);
    AtomicBoolean staleWasAccepted = new AtomicBoolean(false);
    Thread delayedCallback = new Thread(() -> {
      try { releaseCallback.await(); } catch(InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
      staleWasAccepted.set(e.matches(oldLink));
    });
    delayedCallback.start();
    e.advance();
    releaseCallback.countDown();
    delayedCallback.join();
    check(!staleWasAccepted.get(), "late old-link callback accepted");
    System.out.println("SessionEpoch tests passed");
  }
}
