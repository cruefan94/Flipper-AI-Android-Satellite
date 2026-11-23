import com.flipperai.satellite.NorthernCircuitAiProfile;
import com.flipperai.satellite.PromptLimits;

/** Pure-JVM contract tests; actual model responses need Android device testing. */
public final class NorthernCircuitAiProfileTest {
  private static void check(boolean condition,String why) {
    if(!condition)throw new AssertionError(why);
  }

  public static void main(String[] args) {
    String input="Help me draft a friendly birthday message.";
    String first=NorthernCircuitAiProfile.firstOfflineTurn(input);
    check(first.contains("Northern Circuit AI"),"own assistant identity is included");
    check(first.contains("general-purpose assistant"),"not limited to Flipper");
    check(first.endsWith(input),"user request preserved exactly");
    check(first.length()<=PromptLimits.MAX_CHARACTERS,"bounded first turn");
    check(NorthernCircuitAiProfile.SYSTEM_INSTRUCTIONS.contains("Never claim to control hardware"),
      "do not overstate device access");

    String large="x".repeat(PromptLimits.MAX_CHARACTERS);
    check(NorthernCircuitAiProfile.firstOfflineTurn(large).equals(large),
      "a valid long request must not be silently truncated");
    for(String invalid:new String[]{"","   ","\uD800"}) {
      boolean rejected=false;
      try { NorthernCircuitAiProfile.firstOfflineTurn(invalid); }
      catch(IllegalArgumentException expected) { rejected=true; }
      check(rejected,"invalid prompt must remain rejected");
    }
    System.out.println("Northern Circuit AI offline profile tests passed");
  }
}
