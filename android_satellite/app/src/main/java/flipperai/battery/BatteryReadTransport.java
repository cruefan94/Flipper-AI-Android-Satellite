package flipperai.battery;

import java.util.List;

/**
 * Transport-neutral Android-side bridge, intentionally NOT an Android BLE client.
 * Host passes trusted/authenticated/capability status from existing GATT service,
 * and must not transmit through any other route.
 */
public final class BatteryReadTransport {
    public interface FrameSender {
        boolean send(String frame);
    }

    private final BatteryMonitorSession session = new BatteryMonitorSession();
    private boolean optedIn;

    public void setUserOptIn(boolean enabled) {
        if(!enabled) {
            optedIn = false;
            session.reset(); // delete local telemetry when permission is revoked
        } else {
            optedIn = true;
        }
    }

    public boolean isOptedIn() {
        return optedIn;
    }

    public void updateTrustedLink(long epoch, boolean authenticated,
                                  boolean advertisedCapability, long nowMs) {
        session.updateLink(epoch, optedIn && authenticated,
            optedIn && advertisedCapability, nowMs);
    }

    /**
     * Returns true ONLY if sender accepts a canonical frame. This is not proof
     * of BLE delivery; a matching response/timeout remains mandatory.
     */
    public boolean pollIfDue(long requestId, long nowMs, boolean foreground,
                             boolean hardwareBusy, FrameSender sender) {
        if(sender == null || !optedIn ||
           !session.beginPoll(requestId, nowMs, true, foreground, hardwareBusy))
            return false;
        try {
            if(sender.send(BatteryRequestCodec.encodeGet(requestId))) return true;
        } catch(RuntimeException ignored) {
            // Failed queue submit may be a disconnected Bluetooth client.
        }
        session.transportWriteRejected(nowMs);
        return false;
    }

    public boolean receiveFrame(String frame, long timeMs, long epoch) {
        return optedIn && session.acceptFrame(frame, timeMs, epoch);
    }

    public void onBackground() {
        session.cancelPending();
    }

    public void onDisconnect() {
        session.reset();
    }

    public void expire(long nowMs) {
        session.expireRequest(nowMs);
    }

    public boolean hasPendingRequest() {
        return session.hasPendingRequest();
    }

    public int failures() {
        return session.consecutiveFailures();
    }

    public long nextPollAtMs() {
        return session.nextPollAtMs();
    }

    public List<BatteryOptimizer.Sample> history() {
        return session.history();
    }

    public BatteryOptimizer.Advice advice(long nowMs) {
        return optedIn ? session.advice(nowMs)
            : BatteryOptimizer.evaluate(null, nowMs);
    }

    public double estimatedHoursRemaining() {
        return optedIn ? session.estimatedHoursRemaining() : Double.NaN;
    }
}
