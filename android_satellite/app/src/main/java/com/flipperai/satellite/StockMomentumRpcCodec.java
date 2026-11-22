package com.flipperai.satellite;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone codec for the pinned Momentum mntm-012 protobuf RPC envelope.
 * Based on Next-Flip/flipperzero-protobuf commit ea4f185f: flipper.proto,
 * application.proto, system.proto. This module does not access Bluetooth,
 * grant cloud consent, start Flipper apps, or execute hardware operations.
 *
 * All API calls that transmit packets MUST be made by a separately reviewed,
 * bonded/authorized and generation-bound BLE RPC transport.
 */
public final class StockMomentumRpcCodec {
    public static final int MAX_RPC_MESSAGE = 512;
    public static final int MAX_AI1_FRAME = 180;
    public static final String FAP_PATH = "/ext/apps/GPIO/flipper_ai.fap";
    public enum Kind { APP_STATE, APP_DATA, EMPTY, OTHER }
    public static final class Message {
        public final long commandId;
        public final int status;
        public final Kind kind;
        public final int appState;
        public final byte[] appData;
        private Message(long id, int s, Kind k, int st, byte[] d) {
            commandId = id; status = s; kind = k; appState = st;
            appData = d == null ? new byte[0] : d.clone();
        }
    }
    private StockMomentumRpcCodec() {}

    private static IllegalArgumentException bad(String reason) {
        return new IllegalArgumentException("Invalid Momentum RPC envelope: " + reason);
    }
    private static void fieldVarint(ByteArrayOutputStream out, int number, long value) {
        writeVarint(out, (number << 3));
        writeVarint(out, value);
    }
    private static void fieldBytes(ByteArrayOutputStream out, int number, byte[] bytes) {
        writeVarint(out, ((long)number << 3) | 2);
        writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }
    private static byte[] encode(long id, int field, byte[] body) {
        if(id < 1 || id > 0xFFFFFFFFL) throw bad("command id");
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        fieldVarint(msg, 1, id);
        fieldBytes(msg, field, body);
        byte[] payload = msg.toByteArray();
        if(payload.length > MAX_RPC_MESSAGE) throw bad("frame size");
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        writeVarint(frame, payload.length); // Nanopb PB_ENCODE_DELIMITED
        frame.write(payload, 0, payload.length);
        return frame.toByteArray();
    }
    public static byte[] appStart(long id) {
        ByteArrayOutputStream start = new ByteArrayOutputStream();
        fieldBytes(start, 1, FAP_PATH.getBytes(StandardCharsets.UTF_8));
        fieldBytes(start, 2, "RPC".getBytes(StandardCharsets.UTF_8));
        return encode(id, 16, start.toByteArray());
    }
    public static byte[] appData(long id, String ai1) {
        if(ai1 == null) throw bad("null AI1 message");
        byte[] data = ai1.getBytes(StandardCharsets.UTF_8);
        if(data.length < 8 || data.length > MAX_AI1_FRAME ||
           data[data.length - 1] != '\n' || !ai1.startsWith("AI1|"))
            throw bad("AI1 message boundary");
        for(int i = 0; i < data.length - 1; i++) {
            int c = data[i] & 255;
            if(c == 0 || c == '\n' || c == '\r') throw bad("AI1 contains control framing");
        }
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        fieldBytes(request, 1, data);
        return encode(id, 65, request.toByteArray());
    }
    private static void writeVarint(ByteArrayOutputStream out, long v) {
        if(v < 0) throw bad("negative varint");
        do {
            int b = (int)(v & 127);
            v >>>= 7;
            out.write(v == 0 ? b : (b | 128));
        } while(v != 0);
    }
    private static final class Cursor {
        final byte[] src;
        int pos;
        final int end;
        Cursor(byte[] src, int lo, int hi) { this.src=src;pos=lo;end=hi; }
        long varint() {
            long v=0;
            for(int shift=0;shift<=63;shift+=7) {
                if(pos>=end) throw bad("incomplete varint");
                int b=src[pos++]&255;
                if(shift==63 && (b&254)!=0) throw bad("varint overflow");
                v|=((long)(b&127)<<shift);
                if((b&128)==0) return v;
            }
            throw bad("varint too long");
        }
        byte[] bytes() {
            long n=varint();
            if(n<0 || n>end-pos || n>MAX_RPC_MESSAGE) throw bad("field length");
            byte[] out=Arrays.copyOfRange(src,pos,pos+(int)n);
            pos+=(int)n;
            return out;
        }
        void skip(int wireType) {
            if(wireType==0) {varint();return;}
            if(wireType==2) {bytes();return;}
            if(wireType==1 && end-pos>=8) {pos+=8;return;}
            if(wireType==5 && end-pos>=4) {pos+=4;return;}
            throw bad("field wire type");
        }
    }
    public static Message decodePayload(byte[] payload) {
        if(payload == null || payload.length > MAX_RPC_MESSAGE || payload.length < 2)
            throw bad("payload length");
        Cursor c=new Cursor(payload,0,payload.length);
        long id=0; int status=0, state=-1;
        byte[] appData=null; Kind kind=Kind.OTHER;
        boolean sawId=false,sawStatus=false,sawKind=false;
        while(c.pos<c.end) {
            long key=c.varint(); int wire=(int)(key&7); long tag=key>>>3;
            if(tag==0 || tag>1000) throw bad("tag");
            if(tag==1) {
                if(sawId || wire!=0) throw bad("duplicate/wrong command id");
                id=c.varint(); sawId=true;
                if(id>0xFFFFFFFFL) throw bad("command id overflow");
            } else if(tag==2) {
                if(sawStatus || wire!=0) throw bad("duplicate/wrong status");
                long v=c.varint();if(v>255)throw bad("status overflow");
                status=(int)v;sawStatus=true;
            } else if(tag==4 || tag==58 || tag==65) {
                if(sawKind || wire!=2) throw bad("duplicate content");
                sawKind=true;
                byte[] nested=c.bytes();
                if(tag==4){if(nested.length!=0)throw bad("empty response body");kind=Kind.EMPTY;}
                if(tag==58){kind=Kind.APP_STATE;state=parseState(nested);}
                if(tag==65){kind=Kind.APP_DATA;appData=parseAppData(nested);}
            } else {
                c.skip(wire);
            }
        }
        if(!sawKind) throw bad("missing content");
        return new Message(id,status,kind,state,appData);
    }
    private static int parseState(byte[] nested) {
        Cursor c=new Cursor(nested,0,nested.length);
        int state=0;boolean saw=false;
        while(c.pos<c.end) {
            long key=c.varint();
            if((key>>>3)==1 && (key&7)==0) {
                if(saw) throw bad("duplicate state");
                long s=c.varint();if(s>1)throw bad("unsupported state");
                state=(int)s;saw=true;
            } else c.skip((int)(key&7));
        }
        return state;
    }
    private static byte[] parseAppData(byte[] nested) {
        Cursor c=new Cursor(nested,0,nested.length);
        byte[] data=null;
        while(c.pos<c.end) {
            long key=c.varint();
            if((key>>>3)==1 && (key&7)==2) {
                if(data!=null)throw bad("duplicate app data");
                data=c.bytes();
            } else c.skip((int)(key&7));
        }
        if(data==null || data.length<8 || data.length>MAX_AI1_FRAME)throw bad("AI1 data size");
        if(data[data.length-1]!='\n')throw bad("AI1 terminal LF");
        String str=strictUtf8(data);
        if(!str.startsWith("AI1|") || str.indexOf('\n')!=str.length()-1 || str.indexOf('\r')>=0 || str.indexOf('\0')>=0)
            throw bad("AI1 data format");
        return data;
    }
    public static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch(CharacterCodingException bad) { throw bad("invalid UTF-8"); }
    }
    /**
     * Assembles notification fragments; each PB_Main is length-varint-prefixed.
     * Drop/reset on any malformed length, disconnect, or new BLE generation.
     */
    public static final class StreamDecoder {
        private final byte[] ring = new byte[MAX_RPC_MESSAGE+5];
        private int used=0;
        public void reset(){used=0;}
        public List<Message> feed(byte[] chunk) {
            if(chunk==null)throw bad("null chunk");
            List<Message> messages=new ArrayList<>();
            for(byte b:chunk) {
                if(used==ring.length){used=0;throw bad("stream overflow");}
                ring[used++]=b;
                int header=0;long size=0;boolean complete=false;
                for(;header<used && header<5;header++) {
                    int v=ring[header]&255;
                    size|=(long)(v&127)<<(7*header);
                    if((v&128)==0){header++;complete=true;break;}
                }
                if(!complete) {
                    if(used>=5){used=0;throw bad("oversized size varint");}
                    continue;
                }
                if(size<2 || size>MAX_RPC_MESSAGE){used=0;throw bad("invalid frame length");}
                int need=header+(int)size;
                if(used==need) {
                    byte[] payload=Arrays.copyOfRange(ring,header,need);
                    used=0;
                    messages.add(decodePayload(payload));
                }
            }
            return messages;
        }
    }
}
