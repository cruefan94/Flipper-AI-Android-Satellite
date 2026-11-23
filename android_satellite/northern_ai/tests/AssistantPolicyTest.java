import com.northerncircuitstudios.ai.AssistantPolicy;
import java.util.ArrayList;
import java.util.List;

/** Host-only boundaries: model inference itself must be validated on a phone. */
public final class AssistantPolicyTest {
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        expect(AssistantPolicy.LOCAL_ENDPOINT.equals(
            "http://127.0.0.1:9931/v1/chat/completions"), "No remote inference");
        expect(AssistantPolicy.SYSTEM_PROMPT.contains("general-purpose"),
            "Assistant must not be Flipper-only");
        expect(AssistantPolicy.SYSTEM_PROMPT.contains("no live tools"),
            "No fake autonomous actions");
        AssistantPolicy.validateInput("Explain electrical resistance");
        for (String message : new String[]{"", "   ", "\uD800", "x".repeat(1601)}) {
            boolean rejected = false;
            try { AssistantPolicy.validateInput(message); }
            catch (IllegalArgumentException expected) { rejected = true; }
            expect(rejected, "Bad prompt must be rejected");
        }
        List<AssistantPolicy.Entry> turns = new ArrayList<>();
        for (int i = 0; i < 23; i++)
            turns.add(new AssistantPolicy.Entry(i % 2 == 0 ? "user" : "assistant",
                "A turn " + i + " " + "x".repeat(400)));
        List<AssistantPolicy.Entry> selected = AssistantPolicy.recentContext(turns);
        expect(selected.size() <= 8, "Do not send unlimited history");
        expect(selected.get(selected.size() - 1).role.equals("user"),
            "Newest prompt remains user");
        int count = selected.stream().mapToInt(e -> e.text.length()).sum();
        expect(count <= AssistantPolicy.MAX_CONTEXT_CHARS,
            "LLM prompt budget must be bounded");
        expect(AssistantPolicy.boundedHistory(turns).size() <=
            AssistantPolicy.MAX_STORED_ENTRIES, "Saved history capped");
        System.out.println("Northern Circuit AI standalone policy tests passed");
    }
}
