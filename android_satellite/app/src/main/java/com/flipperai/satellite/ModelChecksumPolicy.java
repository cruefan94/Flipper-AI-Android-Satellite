package com.flipperai.satellite;

import java.util.Locale;

/** Optional trusted model checksum gate. Blank means unverified (not trusted).
 * Never accepts a truncated checksum, prefixes, or non-hex characters.
 */
public final class ModelChecksumPolicy {
    private ModelChecksumPolicy() {}
    public static String normalizeExpected(String value) {
        if(value == null || value.trim().isEmpty()) return "";
        String normalized=value.trim().toLowerCase(Locale.ROOT);
        if(!normalized.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Expected SHA-256 must contain exactly 64 hex characters");
        return normalized;
    }
    public static void verify(String expected, String actual) {
        String trusted=normalizeExpected(expected);
        if(trusted.isEmpty()) return;
        if(actual == null || !actual.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Invalid computed model checksum");
        if(!java.security.MessageDigest.isEqual(
             trusted.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
             actual.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new SecurityException("Model SHA-256 differs from trusted expected checksum; not loading");
    }
}
