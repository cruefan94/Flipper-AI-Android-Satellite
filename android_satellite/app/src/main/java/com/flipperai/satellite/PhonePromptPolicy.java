package com.flipperai.satellite;

/** Bounded phone-native offline inference prompt policy.
 * BLE AI1 frames retain their separate 180-byte wire limit.
 */
public final class PhonePromptPolicy {
    public static final int MAX_CHARS=2048;
    private PhonePromptPolicy() {}
    public static String requireValid(String prompt) {
        if(prompt==null || prompt.trim().isEmpty() || prompt.length()>MAX_CHARS)
            throw new IllegalArgumentException("Phone AI prompt must contain 1–2048 characters");
        for(int i=0;i<prompt.length();i++) {
            char c=prompt.charAt(i);
            if(c==0 || c==0x7f)
                throw new IllegalArgumentException("Phone AI prompt contains invalid controls");
        }
        return prompt;
    }
}
