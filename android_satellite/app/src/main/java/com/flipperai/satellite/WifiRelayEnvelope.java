package com.flipperai.satellite;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** One-frame, authenticated AI1 relay envelope for a future Wi-Fi adapter.
 *
 * Wire format: WR1|direction|session_nonce_hex|counter|ai1_base64url|hmac_hex
 * A new random 128-bit nonce is required per established relay session.
 * Direction binds phone->board (P) separately from board->phone (B).
 * Requires HTTPS, independently provisioned secret and a matching ESP32
 * verification implementation before any network adapter may be enabled.
 */
public final class WifiRelayEnvelope {
    private static final String VERSION="WR1";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER=Base64.getUrlDecoder();

    private WifiRelayEnvelope() {}

    public static String randomNonce() {
        byte[] bytes=new byte[16];
        RANDOM.nextBytes(bytes);
        return hex(bytes);
    }

    public static String encode(char direction,String nonce,long counter,String frame,String token) {
        if(!validDirection(direction) || !validNonce(nonce) ||
           counter<1 || counter>0xFFFFFFFFL || frame==null ||
           frame.getBytes(StandardCharsets.UTF_8).length>SatelliteProtocol.MAX_BYTES ||
           !frame.endsWith("\n"))
            throw new IllegalArgumentException("Invalid Wi-Fi relay frame");
        SatelliteProtocol.decode(frame);
        String key=WifiRelayEndpointPolicy.validateToken(token);
        String body=VERSION+"|"+direction+"|"+nonce+"|"+counter+"|"+
            ENCODER.encodeToString(frame.getBytes(StandardCharsets.UTF_8));
        return body+"|"+hex(mac(key,body));
    }

    /** Verify first; callers enforce counters/nonce for replay prevention. */
    public static String verify(String envelope,char direction,String nonce,
                                long minimumCounter,String token) {
        if(envelope==null || envelope.length()>700 ||
           !validDirection(direction) || !validNonce(nonce) ||
           minimumCounter<1 || minimumCounter>0xFFFFFFFFL)
            throw new IllegalArgumentException("Invalid relay envelope");
        String[] parts=envelope.split("\\|",-1);
        if(parts.length!=6 || !VERSION.equals(parts[0]) ||
           parts[1].length()!=1 || parts[1].charAt(0)!=direction ||
           !nonce.equals(parts[2]) || !parts[3].matches("[1-9][0-9]{0,9}") ||
           !parts[5].matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Bad relay envelope header");
        long counter;
        try{counter=Long.parseLong(parts[3]);}
        catch(NumberFormatException bad){throw new IllegalArgumentException("Bad relay counter");}
        if(counter<minimumCounter || counter>0xFFFFFFFFL)
            throw new IllegalArgumentException("Replayed or overflowing relay counter");
        String body=String.join("|",parts[0],parts[1],parts[2],parts[3],parts[4]);
        byte[] expected=mac(WifiRelayEndpointPolicy.validateToken(token),body);
        if(!MessageDigest.isEqual(expected,unhex(parts[5])))
            throw new IllegalArgumentException("Invalid relay authentication");
        final byte[] raw;
        try{raw=DECODER.decode(parts[4]);}
        catch(IllegalArgumentException bad){throw new IllegalArgumentException("Invalid relay encoding");}
        if(raw.length==0 || raw.length>SatelliteProtocol.MAX_BYTES)
            throw new IllegalArgumentException("Invalid relay AI1 size");
        String frame=FrameCodec.decode(raw);
        SatelliteProtocol.decode(frame);
        return frame;
    }

    public static long counter(String envelope) {
        if(envelope==null)throw new IllegalArgumentException("Envelope required");
        String[] parts=envelope.split("\\|",-1);
        if(parts.length!=6 || !parts[3].matches("[1-9][0-9]{0,9}"))
            throw new IllegalArgumentException("Bad counter");
        long counter=Long.parseLong(parts[3]);
        if(counter>0xFFFFFFFFL)throw new IllegalArgumentException("Bad counter");
        return counter;
    }

    private static boolean validDirection(char direction){return direction=='P' || direction=='B';}
    private static boolean validNonce(String nonce){return nonce!=null && nonce.matches("[0-9a-f]{32}");}
    private static byte[] mac(String token,String message) {
        try {
            Mac m=Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(token.getBytes(StandardCharsets.US_ASCII),"HmacSHA256"));
            return m.doFinal(message.getBytes(StandardCharsets.US_ASCII));
        }catch(GeneralSecurityException error){throw new IllegalStateException("HMAC unavailable",error);}
    }
    private static String hex(byte[] bytes){
        char[] chars="0123456789abcdef".toCharArray();
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes){out.append(chars[(b>>>4)&15]).append(chars[b&15]);}
        return out.toString();
    }
    private static byte[] unhex(String str) {
        byte[] result=new byte[str.length()/2];
        for(int i=0;i<result.length;i++){
            int hi=Character.digit(str.charAt(i*2),16);
            int lo=Character.digit(str.charAt(i*2+1),16);
            if(hi<0 || lo<0)throw new IllegalArgumentException("Bad signature");
            result[i]=(byte)((hi<<4)|lo);
        }
        return result;
    }
}
