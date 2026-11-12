package com.flipperai.satellite;

/** Connection-scoped command gate. This is not cryptographic authentication.
 * Reject duplicate/out-of-order IDs in one BLE session.
 */
public final class ProtocolSession {
    private boolean helloSeen;
    private long lastRequestId=-1;
    public void reset() { helloSeen=false; lastRequestId=-1; }
    public boolean helloSeen() { return helloSeen; }
    public String dispatch(String frame,int maxFrameBytes,SatelliteRequestHandler handler) {
        final String[] fields;
        try {fields=SatelliteProtocol.decode(frame);}
        catch(IllegalArgumentException e){return handler.error(0,"request id overflow".equals(e.getMessage()) ? "BAD_ID" : "BAD_FRAME",maxFrameBytes);}
        final long id;
        try {id=Long.parseLong(fields[2]);
            if(id>0xFFFFFFFFL)throw new NumberFormatException("unsigned 32-bit ID overflow");}
        catch(NumberFormatException e){return handler.error(0,"BAD_ID",maxFrameBytes);}
        if(fields[1].equals("HELLO")) {
            if(helloSeen)return handler.error(id,"ALREADY_CONNECTED",maxFrameBytes);
            if(!fields[3].equals("flipper-v1"))return handler.error(id,"BAD_VERSION",maxFrameBytes);
            // Commit the handshake only after a valid HELLO_ACK can be sent.
            String acknowledgement=handler.handle(frame,maxFrameBytes);
            if(acknowledgement==null)return null;
            helloSeen=true;
            lastRequestId=id;
            return acknowledgement;
        }
        if(!helloSeen)return handler.error(id,"HELLO_REQUIRED",maxFrameBytes);
        if(id<=lastRequestId)return handler.error(id,"REPLAY_ID",maxFrameBytes);
        lastRequestId=id;
        return handler.handle(frame,maxFrameBytes);
    }
}
