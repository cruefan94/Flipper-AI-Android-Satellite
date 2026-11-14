package flipperai.battery;

import java.util.List;

/**
 * Independent, read-only battery advice core. No Android, BLE, Flipper or cloud dependencies.
 * Java 8 compatible so the module can later be embedded without rewriting its logic.
 */
public final class BatteryOptimizer {
    private BatteryOptimizer() {}

    public enum Profile { ECO, BALANCED, PERFORMANCE }
    public enum State { NO_DATA, INVALID_DATA, STALE_DATA, THERMAL_WARNING, CHARGING,
                        HARDWARE_BUSY, LOW_BATTERY, NORMAL }

    public static final class Sample {
        public final long timestampMs;
        public final int levelPct;
        public final Integer healthPct; // null when firmware does not provide a trusted reading
        public final Double temperatureC; // null when unsupported
        public final boolean charging;
        public final boolean activeHardwareTask;

        public Sample(long timestampMs, int levelPct, Integer healthPct, Double temperatureC,
                      boolean charging, boolean activeHardwareTask) {
            this.timestampMs = timestampMs;
            this.levelPct = levelPct;
            this.healthPct = healthPct;
            this.temperatureC = temperatureC;
            this.charging = charging;
            this.activeHardwareTask = activeHardwareTask;
        }
    }

    public static final class Advice {
        public final State state;
        public final Profile suggestedProfile; // null unless recommendation is actionable
        public final int suggestedDisplayTimeoutSeconds; // 0 means no change suggested
        public final int suggestedTelemetryIntervalSeconds; // 0 means no change suggested
        public final String explanation;
        public final boolean automaticApplyAllowed;

        private Advice(State state, Profile profile, int display, int telemetry, String text) {
            this.state = state;
            this.suggestedProfile = profile;
            this.suggestedDisplayTimeoutSeconds = display;
            this.suggestedTelemetryIntervalSeconds = telemetry;
            this.explanation = text;
            this.automaticApplyAllowed = false; // never send hardware commands in v0.1
        }
    }

    private static Advice noAction(State state, String text) {
        return new Advice(state, null, 0, 0, text);
    }

    /** Battery samples normally arrive every 3-5 minutes; allow one minute for retry. */
    public static final long STALE_AFTER_MS = 360000L;

    private static boolean valid(Sample s) {
        return s != null && s.timestampMs >= 0 && s.levelPct >= 0 && s.levelPct <= 100 &&
            (s.healthPct == null || (s.healthPct >= 0 && s.healthPct <= 100)) &&
            (s.temperatureC == null || (Double.isFinite(s.temperatureC) &&
                                        s.temperatureC >= -40.0 && s.temperatureC <= 100.0));
    }

    /** Evaluate an explicitly captured reading; this method never polls the device. */
    public static Advice evaluate(Sample sample, long nowMs) {
        if(sample == null) return noAction(State.NO_DATA, "No Flipper reading; connect to inspect battery.");
        if(!valid(sample) || nowMs < sample.timestampMs)
            return noAction(State.INVALID_DATA, "Invalid battery telemetry; do not adjust settings.");
        if(nowMs - sample.timestampMs > STALE_AFTER_MS)
            return noAction(State.STALE_DATA, "Battery reading is stale; request an updated snapshot.");
        if(sample.temperatureC != null &&
           (sample.temperatureC >= 50.0 || sample.temperatureC <= -10.0))
            return noAction(State.THERMAL_WARNING, "Temperature needs attention; avoid automated adjustments.");
        if(sample.charging)
            return noAction(State.CHARGING, "Device is charging; monitor without power-saving changes.");
        if(sample.activeHardwareTask)
            return noAction(State.HARDWARE_BUSY, "Hardware operation active; preserve its settings and link.");
        if(sample.levelPct <= 15)
            return new Advice(State.LOW_BATTERY, Profile.ECO, 5, 300,
                "Low battery: suggest shorter display timeout and less frequent telemetry.");
        if(sample.levelPct <= 40)
            return new Advice(State.NORMAL, Profile.ECO, 10, 300,
                "Conservation recommended; confirm any settings change manually.");
        if(sample.levelPct <= 75)
            return new Advice(State.NORMAL, Profile.BALANCED, 15, 180,
                "Balanced profile suggested; maintain normal hardware availability.");
        return new Advice(State.NORMAL, Profile.PERFORMANCE, 30, 180,
            "Battery is well charged; do not change active task settings.");
    }

    /**
     * Rough hours remaining based on discharge percentage over >=30 minutes.
     * Returns NaN when data are too sparse, charging, out of order, or implausible.
     * Sample timestamps must be monotonically increasing.
     */
    public static double estimatedHoursRemaining(List<Sample> orderedSamples) {
        if(orderedSamples == null || orderedSamples.size() < 2) return Double.NaN;
        Sample first = orderedSamples.get(0);
        Sample last = orderedSamples.get(orderedSamples.size()-1);
        if(!valid(first) || !valid(last)) return Double.NaN;
        long previous = -1L;
        int previousLevel = 101;
        for(Sample s : orderedSamples) {
            if(!valid(s) || s.charging || s.timestampMs <= previous ||
               s.levelPct > previousLevel) return Double.NaN;
            previous = s.timestampMs;
            previousLevel = s.levelPct;
        }
        long span = last.timestampMs - first.timestampMs;
        int delta = first.levelPct - last.levelPct;
        if(span < 1800000L || span > 43200000L || delta < 2 || delta > 50 ||
           last.levelPct <= 0) return Double.NaN;
        double hours = (last.levelPct * (span / 3600000.0)) / delta;
        return hours > 0 && hours <= 200 ? hours : Double.NaN;
    }
}
