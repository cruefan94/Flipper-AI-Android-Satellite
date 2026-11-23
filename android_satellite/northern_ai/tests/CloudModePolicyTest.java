import com.northerncircuitstudios.ai.CloudModePolicy;

/** Host-only consent, token and Worker contract tests. */
public final class CloudModePolicyTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static void invalidToken(String token) {
        try {
            CloudModePolicy.validateToken(token);
            throw new AssertionError("Bad token accepted");
        } catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        String good = "test-DEVICE-TOKEN-1234567890";
        check(CloudModePolicy.WORKER_ORIGIN.equals(
            "https://flipper-ai-satellite.guichet-chris94.workers.dev"),
            "Do not allow arbitrary remote hosts");
        check(CloudModePolicy.Mode.OFFLINE ==
            CloudModePolicy.parseMode("unknown"), "Corrupt config must fall back to offline");
        check(!CloudModePolicy.maySendToCloud(CloudModePolicy.Mode.OFFLINE, good),
            "Offline must never transmit");
        check(!CloudModePolicy.maySendToCloud(CloudModePolicy.Mode.CLOUD, ""),
            "Cloud needs credential");
        check(CloudModePolicy.maySendToCloud(CloudModePolicy.Mode.CLOUD, good),
            "Cloud opt-in requires token");
        check(CloudModePolicy.maySendToCloud(CloudModePolicy.Mode.HYBRID, good),
            "Hybrid opt-in requires token");
        for (String bad : new String[]{"", "short", "word with spaces here",
                "bad\ncontrolcharacters", "a".repeat(257)})
            invalidToken(bad);
        CloudModePolicy.validateToken(good);
        CloudModePolicy.validateCloudPrompt("Hello cloud");
        try {
            CloudModePolicy.validateCloudPrompt("x".repeat(128));
            throw new AssertionError("Worker must reject long prompts preflight");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Northern Circuit AI cloud consent tests passed");
    }
}
