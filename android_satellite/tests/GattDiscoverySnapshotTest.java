import com.flipperai.satellite.GattDiscoverySnapshot;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class GattDiscoverySnapshotTest {
  private static final UUID AI=UUID.fromString("c8c7ab10-6355-4ea8-a76a-0a4a64de0001");
  private static final UUID RPC=UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000");
  private static final UUID OTHER=UUID.fromString("12345678-1234-1234-1234-123456789012");
  private static void check(boolean condition,String label) {
    if(!condition)throw new AssertionError(label);
  }
  public static void main(String[] args) {
    GattDiscoverySnapshot unknown=GattDiscoverySnapshot.notChecked();
    check(unknown.kind==GattDiscoverySnapshot.Kind.NOT_CHECKED,"unknown phase");
    check(unknown.report().contains("NOT CHECKED"),"not yet discovered");
    GattDiscoverySnapshot failed=GattDiscoverySnapshot.failed();
    check(failed.kind==GattDiscoverySnapshot.Kind.DISCOVERY_FAILED,"failed phase");

    GattDiscoverySnapshot stock=GattDiscoverySnapshot.observed(
      Arrays.asList(OTHER,RPC),AI,RPC);
    check(stock.kind==GattDiscoverySnapshot.Kind.MOMENTUM_RPC_ONLY,"rpc only");
    check(!stock.aiPresent && stock.momentumRpcPresent,"service flags");
    check(stock.serviceCount==2,"service count");
    check(stock.report().contains("AI service ABSENT"),"AI absent disclosure");
    check(stock.report().contains("GATT cache"),"uncertainty warning");
    check(!stock.report().contains(OTHER.toString()),"redact unrelated UUID");

    GattDiscoverySnapshot ai=GattDiscoverySnapshot.observed(
      Arrays.asList(AI,RPC),AI,RPC);
    check(ai.kind==GattDiscoverySnapshot.Kind.AI_PRESENT,"both services");
    check(ai.report().contains("handshake UNVERIFIED"),"no false HELLO claims");

    GattDiscoverySnapshot missing=GattDiscoverySnapshot.observed(
      Collections.singleton(OTHER),AI,RPC);
    check(missing.kind==GattDiscoverySnapshot.Kind.NO_EXPECTED_SERVICE,"wrong device");
    check(missing.report().contains("Momentum RPC ABSENT"),"both absent");

    GattDiscoverySnapshot empty=GattDiscoverySnapshot.observed(
      Collections.emptyList(),AI,RPC);
    check(empty.serviceCount==0 && empty.kind==GattDiscoverySnapshot.Kind.NO_EXPECTED_SERVICE,
      "empty inventory must not imply link");
    try {
      GattDiscoverySnapshot.observed(null,AI,RPC);
      throw new AssertionError("null input accepted");
    } catch(IllegalArgumentException expected) {}
    System.out.println("GattDiscoverySnapshotTest PASS");
  }
}
