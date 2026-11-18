import com.flipperai.satellite.ConsentEpoch;

public final class ConsentEpochTest {
  private static void check(boolean condition,String message){
    if(!condition)throw new AssertionError(message);
  }
  public static void main(String[] args){
    ConsentEpoch consent=new ConsentEpoch();
    int queued=consent.snapshot();
    check(consent.current(queued),"fresh consent snapshot");
    consent.changed(); // revoked
    check(!consent.current(queued),"revoked consent invalidates queued work");
    consent.changed(); // re-approved
    check(!consent.current(queued),"re-approval must not revive old work");
    check(consent.current(consent.snapshot()),"new work can use fresh consent");
    System.out.println("Consent epoch tests passed");
  }
}
