package com.flipperai.satellite;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict GATT frame decoder. Rejects malformed UTF-8 rather than silently replacing bytes. */
public final class FrameCodec {
    private FrameCodec() {}
    public static String decode(byte[] value) {
        if (value == null || value.length == 0 || value.length > SatelliteProtocol.MAX_BYTES)
            throw new IllegalArgumentException("invalid frame size");
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value)).toString();
            SatelliteProtocol.decode(text);
            return text;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("invalid UTF-8", e);
        }
    }
}
