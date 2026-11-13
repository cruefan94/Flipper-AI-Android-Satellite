import com.flipperai.satellite.InferenceRouter;

/** An offline readiness probe may cancel even when it returns false. */
public final class OfflineReadinessCancellationTest {
  public static void main(String[] args) throws Exception {
    for (boolean available : new boolean[]{false, true}) {
      final int[] calls = new int[2];
      InferenceRouter.OfflineEngine local = new InferenceRouter.OfflineEngine() {
        public boolean ready() {
          Thread.currentThread().interrupt();
          return available;
        }
        public String answer(String prompt) { calls[0]++; return "LOCAL"; }
      };
      InferenceRouter.OnlineEngine cloud = new InferenceRouter.OnlineEngine() {
        public boolean ready() { calls[1]++; return true; }
        public String answer(String prompt) { calls[1]++; return "CLOUD"; }
      };
      boolean cancelled = false;
      try {
        new InferenceRouter(local, cloud).ask("offline", InferenceRouter.Mode.OFFLINE, true);
      } catch (InterruptedException expected) {
        cancelled = true;
      } finally {
        Thread.interrupted();
      }
      if (!cancelled || calls[0] != 0 || calls[1] != 0)
        throw new AssertionError("offline readiness cancellation was not honored");
    }
    System.out.println("Offline readiness cancellation tests passed");
  }
}
