import com.flipperai.satellite.PhonePromptPolicy;
public final class PhonePromptPolicyTest {
    private static void yes(boolean value){if(!value)throw new AssertionError("phone prompt policy");}
    private static void reject(String prompt){
        boolean rejected=false;
        try{PhonePromptPolicy.requireValid(prompt);}catch(IllegalArgumentException expected){rejected=true;}
        yes(rejected);
    }
    public static void main(String[] args){
        yes("Hi there".equals(PhonePromptPolicy.requireValid("Hi there")));
        yes(PhonePromptPolicy.requireValid("A".repeat(2048)).length()==2048);
        reject(null);reject("");reject("     ");reject("A".repeat(2049));
        reject("hello"+(char)0);reject("hello"+(char)127);
        yes(PhonePromptPolicy.requireValid("What was the last radio program about? ".repeat(4)).length()>127);
        System.out.println("Phone AI prompt policy tests passed");
    }
}
