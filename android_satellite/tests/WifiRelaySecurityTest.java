import com.flipperai.satellite.SatelliteProtocol;
import com.flipperai.satellite.WifiRelayEndpointPolicy;
import com.flipperai.satellite.WifiRelayEnvelope;

public final class WifiRelaySecurityTest {
    private static void check(boolean ok,String description){
        if(!ok)throw new AssertionError(description);
    }
    private static void rejects(Runnable action,String description){
        try{action.run();throw new AssertionError("Accepted "+description);}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        String token="0123456789abcdefghijklmnopqrstuvwxyz_-ABCD";
        check(WifiRelayEndpointPolicy.validate("https://satellite.home.arpa:8443/ai1/relay")
            .equals("https://satellite.home.arpa:8443/ai1/relay"),"local HTTPS accepted");
        rejects(()->WifiRelayEndpointPolicy.validate("http://satellite.home.arpa/ai1/relay"),"plain HTTP");
        rejects(()->WifiRelayEndpointPolicy.validate("https://1.2.3.4/ai1/relay"),"IP literal");
        rejects(()->WifiRelayEndpointPolicy.validate("https://example.com/ai1/relay"),"public domain");
        rejects(()->WifiRelayEndpointPolicy.validate("https://user@satellite.local/ai1/relay"),"user info");
        rejects(()->WifiRelayEndpointPolicy.validate("https://satellite.local/ai1/relay?next=1"),"query");
        rejects(()->WifiRelayEndpointPolicy.validateToken("short"),"short bearer token");
        check(WifiRelayEndpointPolicy.validateToken(token).equals(token),"independent token");

        String nonce=WifiRelayEnvelope.randomNonce();
        check(nonce.matches("[0-9a-f]{32}"),"random session nonce");
        String frame=SatelliteProtocol.encode("PING",42,"");
        String knownNonce="0123456789abcdef0123456789abcdef";
        String knownFrame=SatelliteProtocol.encode("PING",42,"");
        String knownSigned=WifiRelayEnvelope.encode('P',knownNonce,1,knownFrame,token);
        check(knownSigned.equals(
            "WR1|P|0123456789abcdef0123456789abcdef|1|QUkxfFBJTkd8NDJ8Cg|"+
            "9ad7e7a574d8fa3cc3402c4f288d7a3c34080561ef88f2e7174104afd54d00b8"),
            "matches independent Python and ESP32 C HMAC vector");
        String packet=WifiRelayEnvelope.encode('P',nonce,1,frame,token);
        check(WifiRelayEnvelope.verify(packet,'P',nonce,1,token).equals(frame),"valid signed frame");
        check(WifiRelayEnvelope.counter(packet)==1,"counter extracted");
        rejects(()->WifiRelayEnvelope.verify(packet,'B',nonce,1,token),"reflection/direction");
        rejects(()->WifiRelayEnvelope.verify(packet,'P',nonce,2,token),"replay");
        rejects(()->WifiRelayEnvelope.verify(packet,'P',WifiRelayEnvelope.randomNonce(),1,token),"old session");
        rejects(()->WifiRelayEnvelope.verify(packet,'P',nonce,1,token+"X"),"wrong key");
        String tampered=packet.substring(0,packet.length()-1)+
            (packet.endsWith("0")?"1":"0");
        rejects(()->WifiRelayEnvelope.verify(tampered,'P',nonce,1,token),"forged MAC");
        rejects(()->WifiRelayEnvelope.encode('P',nonce,0,frame,token),"zero counter");
        rejects(()->WifiRelayEnvelope.encode('P',nonce,4294967296L,frame,token),"counter overflow");
        rejects(()->WifiRelayEnvelope.encode('P',nonce,1,"AI1|PING|1|bad\npayload\n",token),"invalid AI1 frame");

        String maximum=WifiRelayEnvelope.encode('B',nonce,4294967295L,frame,token);
        check(WifiRelayEnvelope.verify(maximum,'B',nonce,4294967295L,token).equals(frame),
            "uint32 counter boundary");
        System.out.println("WifiRelaySecurity tests passed");
    }
}
