package com.flipperai.satellite;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** ASCII AI1 framing with opaque UTF-8 payload; network size is measured in bytes. */
public final class SatelliteProtocol {
    private SatelliteProtocol() {}
    public static final int MAX_BYTES=180;
    private static boolean validPayload(String payload) {
        if(payload==null)return false;
        for(int i=0;i<payload.length();i++) {
            char c=payload.charAt(i);
            if(c=='|' || c<0x20 || c==0x7f)return false;
        }
        return true;
    }
    public static String encode(String type,long id,String payload) {
        if(type==null || !type.matches("[A-Z_]{1,16}") || id<0 || id>0xFFFFFFFFL)
            throw new IllegalArgumentException("header");
        if(!validPayload(payload))throw new IllegalArgumentException("payload");
        String frame=String.format(Locale.ROOT,"AI1|%s|%d|%s\n",type,id,payload);
        if(frame.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("packet too large");
        return frame;
    }
    public static String[] decode(String frame) {
        if(frame==null || frame.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES ||
           !frame.endsWith("\n"))throw new IllegalArgumentException("frame");
        String[] fields=frame.substring(0,frame.length()-1).split("\\|",-1);
        if(fields.length!=4 || !fields[0].equals("AI1") ||
           !fields[1].matches("[A-Z_]{1,16}") ||
           !fields[2].matches("[0-9]{1,10}") || !validPayload(fields[3]))
            throw new IllegalArgumentException("protocol");
        return fields;
    }
}
