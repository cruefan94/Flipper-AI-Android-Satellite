import com.flipperai.satellite.SessionEpoch;
public final class SessionEpochTest {
  public static void main(String[] args) throws Exception {
    SessionEpoch e=new SessionEpoch();
    int one=e.current();
    if(!e.matches(one))throw new AssertionError("initial epoch");
    Thread disconnect=new Thread(e::advance);disconnect.start();disconnect.join();
    if(e.matches(one))throw new AssertionError("stale session accepted");
    int two=e.current();if(!e.matches(two))throw new AssertionError("new epoch rejected");
    e.advance();if(e.matches(two))throw new AssertionError("stale notification accepted");
    System.out.println("SessionEpoch tests passed");
  }
}
