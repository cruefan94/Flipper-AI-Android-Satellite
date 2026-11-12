package com.flipperai.satellite;

import java.net.URI;
import java.net.URISyntaxException;

/** Fail-closed policy for a future authenticated local Wi-Fi AI1 relay.
 * Requires a CA-verifiable HTTPS hostname; does not permit insecure
 * provisioning AP URLs, IP literals, credentials, redirects, or cloud URLs.
 * A validated URL alone does NOT authenticate the Flipper or open a session.
 */
public final class WifiRelayEndpointPolicy {
    private WifiRelayEndpointPolicy() {}

    public static String validate(String raw) {
        if(raw==null)throw new IllegalArgumentException("Enter relay URL");
        try {
            URI uri=new URI(raw.trim());
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme()) ||
                host==null || uri.getRawUserInfo()!=null ||
                uri.getRawQuery()!=null || uri.getRawFragment()!=null ||
                uri.getPort()!=-1 && (uri.getPort()<1024 || uri.getPort()>65535) ||
                !"/ai1/relay".equals(uri.getRawPath()) ||
                uri.getRawAuthority()==null || uri.getRawAuthority().contains("@"))
                throw new IllegalArgumentException("Relay requires HTTPS hostname /ai1/relay");
            String h=host.toLowerCase(java.util.Locale.ROOT);
            // Permit locally assigned DNS names only. IP literals, WAN hostnames
            // and public cloud relays belong to separately approved adapters.
            if(!(h.endsWith(".home.arpa") || h.endsWith(".local")) ||
                h.equals(".home.arpa") || h.equals(".local") ||
                h.contains(":") || h.startsWith("[") ||
                !h.matches("[a-z0-9-]+(\\.[a-z0-9-]+)*"))
                throw new IllegalArgumentException("Use a trusted local DNS hostname");
            return uri.toASCIIString();
        } catch(URISyntaxException error) {
            throw new IllegalArgumentException("Invalid Wi-Fi relay URL",error);
        }
    }

    public static String validateToken(String token) {
        // Independent random per-device secret, not the Wi-Fi password or
        // the cloud Worker bearer token. Store transiently until keystore
        // integration is complete; never log or persist by default.
        if(token==null || token.length()<32 || token.length()>128 ||
           !token.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Use a distinct 32–128 character relay token");
        return token;
    }
}
