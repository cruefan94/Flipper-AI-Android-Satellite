package flipperai.battery;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Future-only read-only AI1 battery snapshot decoder.
 * This does not install a new BLE command or change the running app/firmware.
 */
public final class BatteryTelemetryCodec {
    private BatteryTelemetryCodec() {}

    /** Proposed frame: AI1|BAT_TELEM|7|v1,p=83,h=95,t=230,c=0,a=0\n */
    public static BatteryOptimizer.Sample parseFrame(String frame, long receivedAtMs) {
        if(frame == null || receivedAtMs < 0 ||
            frame.getBytes(StandardCharsets.UTF_8).length > 180 || !frame.endsWith("\n"))
            throw new IllegalArgumentException("frame length/framing");
        for(int i = 0; i < frame.length() - 1; i++) {
            char ch = frame.charAt(i);
            if(ch < 32 || ch > 126) throw new IllegalArgumentException("non-ASCII/control");
        }
        String[] fields = frame.substring(0,frame.length()-1).split("\\|",-1);
        if(fields.length != 4 || !"AI1".equals(fields[0]) ||
           !"BAT_TELEM".equals(fields[1]) || !fields[2].matches("[0-9]{1,10}"))
            throw new IllegalArgumentException("invalid header");
        long id = Long.parseLong(fields[2]);
        if(id < 1 || id > 0xFFFFFFFFL) throw new IllegalArgumentException("invalid request id");
        String[] tokens = fields[3].split(",", -1);
        if(tokens.length < 4 || tokens.length > 6 || !"v1".equals(tokens[0]))
            throw new IllegalArgumentException("invalid schema");
        Map<String,String> values = new HashMap<String,String>();
        for(int i = 1; i < tokens.length; i++) {
            String[] pair = tokens[i].split("=", -1);
            if(pair.length != 2 || !pair[0].matches("(p|h|t|c|a)") ||
               !pair[1].matches("-?[0-9]{1,5}|-") || values.put(pair[0],pair[1]) != null)
                throw new IllegalArgumentException("invalid or repeated battery field");
        }
        if(!values.containsKey("p") || !values.containsKey("c") || !values.containsKey("a"))
            throw new IllegalArgumentException("missing required field");
        int level = bounded(values.get("p"),0,100);
        Integer health = optionalBounded(values.get("h"),0,100);
        Integer tempTenths = optionalBounded(values.get("t"),-400,1000);
        boolean charging = bit(values.get("c"));
        boolean active = bit(values.get("a"));
        return new BatteryOptimizer.Sample(receivedAtMs, level, health,
            tempTenths == null ? null : tempTenths / 10.0, charging, active);
    }

    private static boolean bit(String value) {
        if("0".equals(value)) return false;
        if("1".equals(value)) return true;
        throw new IllegalArgumentException("expected bit");
    }

    private static int bounded(String text,int min,int max) {
        if(!text.matches("[0-9]{1,5}|-[0-9]{1,5}"))
            throw new IllegalArgumentException("invalid number");
        int number = Integer.parseInt(text);
        if(number < min || number > max) throw new IllegalArgumentException("out of range");
        return number;
    }

    private static Integer optionalBounded(String text,int min,int max) {
        return text == null || "-".equals(text) ? null : bounded(text,min,max);
    }
}
