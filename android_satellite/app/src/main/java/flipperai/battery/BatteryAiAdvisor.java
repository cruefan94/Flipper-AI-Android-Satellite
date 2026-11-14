package flipperai.battery;

import java.util.Locale;

/**
 * Phone-first battery advice adapter for an EXISTING Android AI engine.
 *
 * The policy and hardware safety checks remain deterministic. An AI engine may
 * explain the recommendation, but cannot choose power settings or issue device
 * commands through this class.
 *
 * No network access/model weights are packaged. The host must supply its own
 * offline or opt-in online AI adapter through TextEngine.
 */
public final class BatteryAiAdvisor {
    private BatteryAiAdvisor() {}

    public enum Execution { OFFLINE, ONLINE }
    public enum Outcome { EXPLAINED, DETERMINISTIC_FALLBACK, NOT_AUTHORIZED }

    public interface TextEngine {
        String explain(String prompt) throws Exception;
    }

    public static final class Result {
        public final Outcome outcome;
        public final BatteryOptimizer.Advice safeAdvice;
        public final String explanation;

        private Result(Outcome outcome, BatteryOptimizer.Advice advice, String explanation) {
            this.outcome = outcome;
            this.safeAdvice = advice;
            this.explanation = explanation;
        }
    }

    private static String prompt(BatteryOptimizer.Sample sample, BatteryOptimizer.Advice advice) {
        String temp = sample.temperatureC == null ? "unknown"
            : String.format(Locale.ROOT, "%.1f C", sample.temperatureC);
        String health = sample.healthPct == null ? "unknown" : sample.healthPct + "%";
        String profile = advice.suggestedProfile == null ? "none"
            : advice.suggestedProfile.name();
        return "Explain a battery observation in clear language without requesting device actions. " +
            "Do NOT invent battery savings, change hardware settings, give control commands, " +
            "or claim real measurements beyond these provided fields. " +
            "Battery=" + sample.levelPct + "%; health=" + health +
            "; temperature=" + temp +
            "; charging=" + sample.charging +
            "; hardware_task_active=" + sample.activeHardwareTask +
            "; advisory_state=" + advice.state.name() +
            "; suggested_profile=" + profile +
            ". These are local observations; explanations only.";
    }

    /**
     * AI is optional. Online use requires a second explicit cloud consent, and
     * even approved online calls transmit only these local power fields. The
     * host owns actual network/cloud transport and any user-facing disclosure.
     * Cloud consent is NOT implied by enabling local telemetry.
     */
    public static Result explain(BatteryOptimizer.Sample sample, long nowMs,
                                 Execution mode, boolean cloudConsent, TextEngine engine) {
        BatteryOptimizer.Advice advice = BatteryOptimizer.evaluate(sample, nowMs);
        if(mode == Execution.ONLINE && !cloudConsent)
            return new Result(Outcome.NOT_AUTHORIZED, advice,
                "Online AI needs separate consent. " + advice.explanation);
        if(sample == null || engine == null || mode == null ||
           advice.state == BatteryOptimizer.State.INVALID_DATA ||
           advice.state == BatteryOptimizer.State.STALE_DATA)
            return new Result(Outcome.DETERMINISTIC_FALLBACK, advice, advice.explanation);
        try {
            String value = engine.explain(prompt(sample, advice));
            if(value != null) {
                // Do not present unsolicited instructions/commands as trusted advice.
                // The UI must mark AI text as a non-authoritative explanation only.
                value = value.replace('\n', ' ').replace('\r', ' ').trim();
                if(!value.isEmpty() && value.length() <= 500)
                    return new Result(Outcome.EXPLAINED, advice, value);
            }
        } catch(Exception ignored) {
            // A model outage must never stop deterministic battery advice.
        }
        return new Result(Outcome.DETERMINISTIC_FALLBACK, advice, advice.explanation);
    }
}
