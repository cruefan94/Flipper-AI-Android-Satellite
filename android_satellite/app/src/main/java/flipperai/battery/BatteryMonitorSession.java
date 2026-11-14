package flipperai.battery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only future BLE adapter state machine. Does not initiate Bluetooth, send
 * messages, change Flipper settings, or contact a cloud service.
 *
 * All timestamps use the Android monotonic clock domain. The host supplies
 * trusted-link/capability status from the existing BLE permission gates.
 */
public final class BatteryMonitorSession {
    public static final long RESPONSE_TIMEOUT_MS = 30000L;
    public static final long NORMAL_INTERVAL_MS = 180000L;
    public static final long CONSERVE_INTERVAL_MS = 300000L;
    public static final int MAX_HISTORY = 64;

    private long connectionEpoch = -1L;
    // Survives reset/disconnect: an old trusted generation must never reopen.
    private long highestAcceptedEpoch = -1L;
    private boolean trustedAndCapable;
    private boolean pending;
    private long pendingRequestId;
    private long lastIssuedRequestId;
    private long pendingAtMs;
    private long nextPollAtMs;
    private int consecutiveFailures;
    private final ArrayList<BatteryOptimizer.Sample> samples =
        new ArrayList<BatteryOptimizer.Sample>();

    /**
     * Host-verified link status. Neither successful pairing nor a visible GATT
     * characteristic by itself counts as trusted/capable.
     * Any disconnect/capability loss or new session clears prior samples.
     */
    public void updateLink(long epoch, boolean trusted, boolean capabilityAdvertised,
                           long nowMs) {
        if(epoch < 0 || nowMs < 0) throw new IllegalArgumentException("clock/epoch");
        if(!trusted || !capabilityAdvertised) {
            reset();
            return;
        }
        if(!trustedAndCapable || epoch != connectionEpoch) {
            // After a disconnect, revocation, or capability loss, require the
            // host to supply a genuinely NEW trusted transport generation.
            if(epoch <= highestAcceptedEpoch) {
                reset();
                return;
            }
            reset();
            trustedAndCapable = true;
            connectionEpoch = epoch;
            highestAcceptedEpoch = epoch;
            nextPollAtMs = nowMs;
        }
    }

    public void reset() {
        connectionEpoch = -1L;
        trustedAndCapable = false;
        pending = false;
        pendingRequestId = 0;
        lastIssuedRequestId = 0;
        pendingAtMs = 0;
        nextPollAtMs = 0;
        consecutiveFailures = 0;
        samples.clear();
    }

    /**
     * Host may send exactly ONE read-only snapshot request when this returns true.
     * A caller must never use this as authorization for setting changes.
     */
    public boolean beginPoll(long requestId, long nowMs, boolean userEnabled,
                             boolean appForeground, boolean hardwareBusy) {
        if(nowMs < 0 || requestId < 1 || requestId > 0xffffffffL ||
           requestId <= lastIssuedRequestId)
            return false;
        if(!trustedAndCapable || !userEnabled || !appForeground ||
           hardwareBusy || pending || nowMs < nextPollAtMs)
            return false;
        pendingRequestId = requestId;
        lastIssuedRequestId = requestId; // never reuse ID in this trusted connection epoch
        pendingAtMs = nowMs;
        pending = true;
        return true;
    }

    /**
     * Accept only the expected reply on the current trusted transport epoch.
     * Returns false for unsolicited, malformed, late, or replayed frames.
     */
    public boolean acceptFrame(String frame, long receivedAtMs, long epoch) {
        if(!trustedAndCapable || !pending || epoch != connectionEpoch ||
           receivedAtMs < pendingAtMs) return false;
        if(receivedAtMs - pendingAtMs > RESPONSE_TIMEOUT_MS) {
            fail(receivedAtMs);
            return false;
        }
        final BatteryOptimizer.Sample sample;
        try {
            sample = BatteryTelemetryCodec.parseFrame(frame, receivedAtMs);
            String[] fields = frame.substring(0, frame.length() - 1).split("\\|", -1);
            if(Long.parseLong(fields[2]) != pendingRequestId) return false;
        } catch(IllegalArgumentException invalid) {
            fail(receivedAtMs);
            return false;
        }
        if(!samples.isEmpty() && receivedAtMs <= samples.get(samples.size()-1).timestampMs) {
            fail(receivedAtMs);
            return false;
        }
        samples.add(sample);
        if(samples.size() > MAX_HISTORY) samples.remove(0);
        pending = false;
        pendingRequestId = 0;
        consecutiveFailures = 0;
        nextPollAtMs = saturatingAdd(receivedAtMs,
            (sample.charging || sample.activeHardwareTask || sample.levelPct <= 40)
            ? CONSERVE_INTERVAL_MS : NORMAL_INTERVAL_MS);
        return true;
    }

    /** A missing reply increases backoff; it never automatically reconnects. */
    public void expireRequest(long nowMs) {
        if(nowMs >= 0 && pending && nowMs >= pendingAtMs &&
           nowMs - pendingAtMs >= RESPONSE_TIMEOUT_MS) fail(nowMs);
    }

    /**
     * Fail a pending request after the transport explicitly rejects a write.
     * Avoid waiting thirty seconds when the BLE queue never accepted a frame.
     */
    public void transportWriteRejected(long nowMs) {
        if(pending && nowMs >= pendingAtMs) fail(nowMs);
    }

    /**
     * A screen/background transition aborts a pending request without treating
     * a user-navigation event as a BLE error. Late replies are then ignored.
     */
    public void cancelPending() {
        pending = false;
        pendingRequestId = 0;
        pendingAtMs = 0;
    }

    private void fail(long atMs) {
        pending = false;
        pendingRequestId = 0;
        consecutiveFailures = Math.min(5, consecutiveFailures + 1);
        long backoffMs = Math.min(600000L, 60000L << (consecutiveFailures - 1));
        nextPollAtMs = saturatingAdd(atMs, backoffMs);
    }

    private static long saturatingAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    public boolean hasPendingRequest() { return pending; }
    public boolean isTrustedAndCapable() { return trustedAndCapable; }
    public long nextPollAtMs() { return nextPollAtMs; }
    public int consecutiveFailures() { return consecutiveFailures; }
    public List<BatteryOptimizer.Sample> history() {
        return Collections.unmodifiableList(new ArrayList<BatteryOptimizer.Sample>(samples));
    }
    public BatteryOptimizer.Advice advice(long nowMs) {
        BatteryOptimizer.Sample last = samples.isEmpty() ? null : samples.get(samples.size()-1);
        return BatteryOptimizer.evaluate(last, nowMs);
    }
    public double estimatedHoursRemaining() {
        return BatteryOptimizer.estimatedHoursRemaining(samples);
    }
}
