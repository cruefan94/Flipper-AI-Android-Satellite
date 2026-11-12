import com.flipperai.satellite.SatelliteTransport;
import com.flipperai.satellite.SatelliteTransportCoordinator;

public final class SatelliteTransportCoordinatorTest {
    private static void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
    }
    private static final class FakeTransport implements SatelliteTransport {
        private final Kind kind;
        boolean ready;
        int sends,closes;
        String lastFrame;
        FakeTransport(Kind kind,boolean ready){this.kind=kind;this.ready=ready;}
        public Kind kind(){return kind;}
        public boolean isReady(){return ready;}
        public boolean sendFrame(String frame){sends++;lastFrame=frame;return true;}
        public void close(){closes++;ready=false;}
    }
    public static void main(String[] args) {
        SatelliteTransportCoordinator coordinator=new SatelliteTransportCoordinator();
        FakeTransport ble=new FakeTransport(SatelliteTransport.Kind.BLE,true);
        coordinator.register(ble);
        check(coordinator.selected()==SatelliteTransport.Kind.BLE,"BLE must be default");
        check(coordinator.isReady(),"registered BLE active");
        check(!coordinator.select(SatelliteTransport.Kind.WIFI),"unregistered Wi-Fi unavailable");
        check(!coordinator.select(SatelliteTransport.Kind.USB),"unregistered USB unavailable");
        check(coordinator.selected()==SatelliteTransport.Kind.BLE,"unsupported choice cannot switch");
        check(!coordinator.sendFrame(null),"null frame rejected");
        check(!coordinator.sendFrame(""),"empty frame rejected");

        long first=coordinator.generation();
        check(coordinator.sendFrameIfCurrent("AI1|PING|1|\\n",first),"current BLE frame sent");
        check(ble.sends==1,"exactly one BLE write");
        coordinator.invalidateSession();
        check(!coordinator.sendFrameIfCurrent("AI1|ASK|2|data\\n",first),"old session rejected");
        check(ble.sends==1,"no stale transmission");

        FakeTransport wifi=new FakeTransport(SatelliteTransport.Kind.WIFI,true);
        coordinator.register(wifi);
        check(coordinator.selected()==SatelliteTransport.Kind.BLE,"registration must not silently switch");
        check(coordinator.select(SatelliteTransport.Kind.WIFI),"explicit switch supported");
        check(ble.closes==1,"previous transport closed on switch");
        check(!coordinator.sendFrameIfCurrent("stale",first),"old BLE token cannot use Wi-Fi");
        check(coordinator.sendFrameIfCurrent("wifi",coordinator.generation()),"explicit Wi-Fi transport");
        check(wifi.sends==1,"sent only over chosen Wi-Fi");
        check(coordinator.select(SatelliteTransport.Kind.WIFI),"reselect is safe");
        check(wifi.closes==0,"same selection is not closed");
        coordinator.close();
        check(wifi.closes==1,"active adapter closes");
        check(!coordinator.isReady(),"coordinator closes fail closed");
        check(!coordinator.sendFrame("no link"),"no send after close");
        System.out.println("SatelliteTransportCoordinator tests passed");
    }
}
