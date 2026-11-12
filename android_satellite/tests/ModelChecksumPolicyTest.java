import com.flipperai.satellite.ModelChecksumPolicy;

public final class ModelChecksumPolicyTest {
    private static void yes(boolean b) { if(!b)throw new AssertionError("SHA-256 gate"); }
    private static void denied(Runnable task) {
        boolean rejected=false;
        try { task.run(); } catch(IllegalArgumentException | SecurityException expected) { rejected=true; }
        yes(rejected);
    }
    public static void main(String[] args) {
        String sha="a".repeat(64);
        yes(sha.equals(ModelChecksumPolicy.normalizeExpected(" "+sha.toUpperCase()+" ")));
        yes("".equals(ModelChecksumPolicy.normalizeExpected("  ")));
        ModelChecksumPolicy.verify(sha,sha);
        ModelChecksumPolicy.verify("",null);
        denied(()->ModelChecksumPolicy.normalizeExpected("abc"));
        denied(()->ModelChecksumPolicy.normalizeExpected("g".repeat(64)));
        denied(()->ModelChecksumPolicy.verify(sha,"b".repeat(64)));
        denied(()->ModelChecksumPolicy.verify(sha,"invalid"));
        System.out.println("Model checksum policy tests passed");
    }
}
