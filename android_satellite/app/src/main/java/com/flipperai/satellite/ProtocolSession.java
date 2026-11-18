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
        catch(IllegalArgumentException e){return handler.error(0,"BAD_FRAME",maxFrameBytes);}
        final long id;
        try {id=Long.parseLong(fields[2]);
            if(id>0xFFFFFFFFL)throw new NumberFormatException("unsigned 32-bit ID overflow");}
        catch(NumberFormatException e){return handler.error(0,"BAD_ID",maxFrameBytes);}
        if(fields[1].equals("HELLO")) {
            if(helloSeen)return handler.error(id,"ALREADY_CONNECTED",maxFrameBytes);
            if(!fields[3].equals("flipper-v1"))return handler.error(id,"BAD_VERSION",maxFrameBytes);
            String acknowledgement=handler.handle(frame,maxFrameBytes);
            // Commit handshake state only after the ACK can actually be sent.
            if(acknowledgement==null)return null;
            helloSeen=true;
            lastRequestId=id;
            return acknowledgement;
        }
        if(!helloSeen)return handler.error(id,"HELLO_REQUIRED",maxFrameBytes);
        if(id<=lastRequestId)return handler.error(id,"REPLAY_ID",maxFrameBytes);
        String reply=handler.handle(frame,maxFrameBytes);
        // Failed delivery must not consume the ID; allow a capacity retry.
        if(reply!=null)lastRequestId=id;
        return reply;
    }
}
