package com.flipperai.satellite;

/** Side-effect-free protocol dispatcher for a prospective Momentum BLE AI profile.
 * Commands never execute hardware operations. Responses are bounded to one GATT value.
 */
public final class SatelliteRequestHandler {
    private final InferenceRouter router;
    private InferenceRouter.Mode mode=InferenceRouter.Mode.OFFLINE;
    private boolean cloudConsent=false;
    public SatelliteRequestHandler(InferenceRouter router) { this.router=router; }
    public void configure(InferenceRouter.Mode mode,boolean consent) {
        if(mode==null)throw new IllegalArgumentException("mode");
        this.mode=mode; this.cloudConsent=consent;
    }
    public String handle(String frame,int maxFrameBytes) {
        if(maxFrameBytes<16)return null;
        String[] fields;
        try {fields=SatelliteProtocol.decode(frame);}
        catch(IllegalArgumentException ex){return fit("ERROR",0,"request id overflow".equals(ex.getMessage()) ? "BAD_ID" : "BAD_FRAME",maxFrameBytes);}
        long id;
        try{id=Long.parseLong(fields[2]); if(id>0xFFFFFFFFL)throw new NumberFormatException("ID overflow");}
        catch(NumberFormatException ex){return fit("ERROR",0,"BAD_ID",maxFrameBytes);}
        try{
            switch(fields[1]){
                case "HELLO":return fit("HELLO_ACK",id,"android-v1",maxFrameBytes);
                case "PING":return fit("PONG",id,"ok",maxFrameBytes);
                case "ASK":
                    if(fields[3].trim().isEmpty())return fit("ERROR",id,"EMPTY_PROMPT",maxFrameBytes);
                    return fit("ANSWER",id,router.ask(fields[3],mode,cloudConsent),maxFrameBytes);
                default:return fit("ERROR",id,"UNKNOWN_TYPE",maxFrameBytes);
            }
        }catch(Exception ex){return fit("ERROR",id,"ENGINE_UNAVAILABLE",maxFrameBytes);}
    }
    public String error(long id,String code,int limit) { return fit("ERROR",id,code,limit); }
    private static String fit(String type,long id,String message,int limit) {
        String safe=(message==null?"":message).replace('|','/').replace('\r',' ').replace('\n',' ');
        final int allowed=Math.min(limit,SatelliteProtocol.MAX_BYTES);
        /* Keep a visible truncation indicator without splitting a surrogate
         * pair or exceeding the negotiated ATT payload. No multi-frame
         * responses are available in AI1; silent truncation is misleading. */
        final String marker="...";
        boolean truncated=false;
        while(true) {
            String payload=truncated?safe+marker:safe;
            try {
                String packet=SatelliteProtocol.encode(type,id,payload);
                if(packet.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=allowed)return packet;
            }catch(IllegalArgumentException ignored){}
            if(safe.isEmpty())return null;
            safe=safe.substring(0,safe.offsetByCodePoints(safe.length(),-1));
            truncated=true;
        }
    }
}
