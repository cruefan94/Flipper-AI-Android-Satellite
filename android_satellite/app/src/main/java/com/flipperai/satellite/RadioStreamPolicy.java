package com.flipperai.satellite;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Receive-only internet radio endpoint policy.
 * Does not connect to radio RF hardware, transmit, record, or discover feeds.
 * Streams are chosen by the user and must be hosted via HTTPS.
 */
public final class RadioStreamPolicy {
    private RadioStreamPolicy() {}

    public static String validate(String userInput) {
        if(userInput == null) throw new IllegalArgumentException("Enter an HTTPS audio stream URL");
        String input = userInput.trim();
        if(input.length() < 12 || input.length() > 2048)
            throw new IllegalArgumentException("Invalid radio stream URL length");
        for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(c <= 0x20 || c == 0x7f || c == '\\' || c == '"' || c == '<' || c == '>')
                throw new IllegalArgumentException("Invalid character in stream URL");
        }
        URI uri;
        try { uri = new URI(input); }
        catch(URISyntaxException e) { throw new IllegalArgumentException("Invalid stream URL"); }
        if(!"https".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("Radio streams must use HTTPS");
        if(uri.getRawUserInfo()!=null || uri.getRawFragment()!=null)
            throw new IllegalArgumentException("Radio stream URL cannot contain credentials or fragments");
        String host=uri.getHost();
        if(host==null || host.isEmpty() || !host.contains(".") || host.endsWith(".") ||
           host.equalsIgnoreCase("localhost") || host.toLowerCase(Locale.ROOT).endsWith(".local"))
            throw new IllegalArgumentException("Use a public HTTPS radio stream hostname");
        if(uri.getPort() < -1 || uri.getPort() > 65535 || uri.getPort() == 0)
            throw new IllegalArgumentException("Invalid radio stream port");
        if(host.matches("(?i)^(?:127|10|0)\\..*") || host.matches("(?i)^192\\.168\\..*") ||
           host.matches("(?i)^169\\.254\\..*") || host.matches("(?i)^172\\.(?:1[6-9]|2[0-9]|3[01])\\..*"))
            throw new IllegalArgumentException("Local-network addresses are not internet radio feeds");
        return uri.toASCIIString();
    }
}
