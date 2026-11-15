import com.flipperai.satellite.TransportState;

/** Duplicate Android success callbacks must never invalidate a live AI GATT session. */
public final class TransportStateDuplicateConnectedTest {
  private static void check(boolean ok,String message) {
    if(!ok) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    TransportState state=new TransportState();
    check(!state.alreadyConnected(),"Disconnected is not an accepted connection");
    state.begin();
    check(!state.alreadyConnected(),"Connecting has not accepted success");
    state.connected();
    check(state.alreadyConnected(),"Discovery must ignore redundant success");
    state.subscribing();
    check(state.alreadyConnected(),"Subscription must ignore redundant success");
    state.mtu(185);
    state.ready();
    check(state.alreadyConnected(),"Ready session must ignore redundant success");
    check(state.phase()==TransportState.Phase.READY,"Ignored callback preserves readiness");
    check(state.payloadLimit()==180,"Ignored callback preserves negotiated MTU");
    state.disconnected();
    check(!state.alreadyConnected(),"New connection must re-validate success");
    state.begin();
    check(!state.alreadyConnected(),"Fresh connection is not duplicate");
    state.connected();
    check(state.alreadyConnected(),"Fresh first success is accepted");
    System.out.println("TransportStateDuplicateConnected tests passed");
  }
}
