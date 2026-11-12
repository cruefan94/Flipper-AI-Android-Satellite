package com.flipperai.satellite;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** ASCII wire format v1: AI1|TYPE|request_id|payload\n. */
public final class SatelliteProtocol {
    private SatelliteProtocol() {}
    public static final int MAX_BYTES = 180;
    public static String encode(String type, long id, String payload) {
        if (!type.matches("[A-Z_]{1,16}") || id < 0 || id > 0xFFFFFFFFL) throw new IllegalArgumentException("header");
        if (payload == null || !validPayload(payload)) throw new IllegalArgumentException("payload");
        String packet = String.format(Locale.ROOT, "AI1|%s|%d|%s\n", type, id, payload);
        if (packet.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("packet too large");
        return packet;
    }
    public static String[] decode(String packet) {
        if (packet == null || packet.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES || !packet.endsWith("\n")) throw new IllegalArgumentException("frame");
        String[] fields = packet.substring(0, packet.length()-1).split("\\|", -1);
        if (fields.length != 4 || !fields[0].equals("AI1") || !fields[1].matches("[A-Z_]{1,16}") || !fields[2].matches("[0-9]{1,10}")) throw new IllegalArgumentException("protocol");
        // The C AI1 parser accepts exactly one terminal LF. Reject framing
        // delimiters in received payloads as rigorously as in encoded frames.
        if(!validPayload(fields[3]))
            throw new IllegalArgumentException("embedded payload newline");
        long parsedId;
        try { parsedId=Long.parseLong(fields[2]); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("request id",e); }
        if(parsedId>0xFFFFFFFFL)throw new IllegalArgumentException("request id overflow");
        return fields;
    }
    private static boolean validPayload(String payload) {
        if(payload==null)return false;
        for(int i=0;i<payload.length();i++) {
            char c=payload.charAt(i);
            if(c=='|' || c<0x20 || c==0x7f)return false;
        }
        return true;
    }
}
