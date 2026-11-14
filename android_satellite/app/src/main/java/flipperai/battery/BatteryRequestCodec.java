package flipperai.battery;

import java.nio.charset.StandardCharsets;

/**
 * Proposed BAT_GET v1 request for the future Android-to-Flipper direction.
 * Pure formatter, does not transmit bytes or assert that any link is trusted.
 * The host must call BatteryMonitorSession.beginPoll() first.
 */
public final class BatteryRequestCodec {
    private BatteryRequestCodec() {}

    public static String encodeGet(long requestId) {
        if(requestId <= 0 || requestId > 0xffffffffL)
            throw new IllegalArgumentException("invalid request ID");
        String frame = "AI1|BAT_GET|" + requestId + "|v1\n";
        if(frame.getBytes(StandardCharsets.UTF_8).length > 180)
            throw new IllegalArgumentException("frame too large");
        return frame;
    }
}
