import com.flipperai.satellite.PhoneFacts;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Plain-JVM verification of fresh local phone facts and cloud-bypass classification. */
public final class PhoneFactsTest {
  private static void check(boolean ok,String message) {
    if(!ok)throw new AssertionError(message);
  }
  private static PhoneFacts.Snapshot state(int charge,boolean charging,
      PhoneFacts.Connection connection,boolean validated) {
    return new PhoneFacts.Snapshot(
      ZonedDateTime.of(2026,11,13,17,10,0,0,ZoneId.of("America/Toronto")),
      charge,charging,connection,validated);
  }
  public static void main(String[] args) {
    PhoneFacts.Snapshot wifi=state(12,false,PhoneFacts.Connection.WIFI,true);
    String time=PhoneFacts.answer("whats the time",wifi);
    check(time!=null && time.contains("5:10 PM") && time.contains("EST"),
        "whats the time must report a real snapshot, not a guessed AI time");
    check(PhoneFacts.answer("What time is it?",wifi).contains("5:10 PM"),
        "standard current time request");
    check(PhoneFacts.answer("what's the time now?",wifi).contains("5:10 PM"),
        "contractions and question punctuation");
    check(PhoneFacts.answer("what's the current date and time?",wifi).contains("November 13, 2026"),
        "combined date/time request");
    check(PhoneFacts.answer("what day is it?",wifi).contains("Friday, November 13, 2026"),
        "current local calendar date");

    check(PhoneFacts.answer("what's my phone battery level?",wifi).contains("12%"),
        "actual phone battery percentage");
    check(PhoneFacts.answer("how much battery do i have?",wifi).contains("not charging"),
        "not charging should not be reported as charging");
    PhoneFacts.Snapshot charging=state(65,true,PhoneFacts.Connection.CELLULAR,false);
    check(PhoneFacts.answer("phone battery",charging).contains("65%"),
        "phone battery shortcut");
    check(PhoneFacts.answer("phone battery",charging).contains("is charging"),
        "charging state");

    check(PhoneFacts.answer("am I online?",wifi).contains("validated internet access"),
        "validated Wi-Fi connection");
    check(PhoneFacts.answer("am I online?",charging).contains("not verified"),
        "unvalidated mobile access must not claim live internet");
    check(PhoneFacts.answer("internet status",state(10,false,PhoneFacts.Connection.NONE,false))
         .contains("no active network connection"),"no active network");
    check(PhoneFacts.answer("network status",state(-1,false,PhoneFacts.Connection.UNKNOWN,false))
         .contains("could not determine"),"unknown connection");
    check(PhoneFacts.answer("phone battery",state(-1,false,PhoneFacts.Connection.NONE,false))
         .contains("did not provide"),"missing battery data is not fabricated");

    // These are model questions, not explicit local-device-information requests.
    check(PhoneFacts.classify("What time is it in Tokyo?")==PhoneFacts.Query.NONE,
        "do not incorrectly answer a remote timezone query using local clock");
    check(PhoneFacts.classify("What AI are you?")==PhoneFacts.Query.NONE,
        "normal model questions remain on the selected routing path");
    check(PhoneFacts.classify("battery diagnostics")==PhoneFacts.Query.NONE,
        "Flipper diagnostic requests must retain their existing route");
    check(PhoneFacts.classify("Tell me a story about time")==PhoneFacts.Query.NONE,
        "avoid broad substring interception");
    check(PhoneFacts.answer("Explain Bluetooth",wifi)==null,
        "non-fact requests must not be intercepted");
    check(PhoneFacts.classify(null)==PhoneFacts.Query.NONE,
        "null prompts do not trigger fact reads");
    System.out.println("Phone facts tests passed");
  }
}
