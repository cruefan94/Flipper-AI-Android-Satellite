import com.flipperai.satellite.BlePickerRows;
import java.util.LinkedHashMap;
import java.util.List;

/** Plain JVM regression for Bluetooth picker identity, duplicates and refresh. */
public final class BlePickerRowsTest {
  private static void check(boolean ok,String message) {
    if(!ok)throw new AssertionError(message);
  }
  public static void main(String[] args) {
    BlePickerRows empty=BlePickerRows.empty();
    check(empty.size()==0 && empty.labels().size()==1,"placeholder row for no devices");
    check(empty.idAt(0)==null && empty.idAt(1)==null,"no phantom Bluetooth selections");
    check(empty.positionFor("missing")==0,"missing device does not resolve");

    LinkedHashMap<String,String> pairs=new LinkedHashMap<>();
    pairs.put("INTERNAL-A","Flipper Zero");
    pairs.put("INTERNAL-B","Flipper Zero");
    pairs.put("INTERNAL-C","Momentum");
    BlePickerRows rows=BlePickerRows.from(pairs);
    check(rows.size()==3,"all scanned devices listed");
    check(rows.idAt(1).equals("INTERNAL-A"),"first same-named device identity");
    check(rows.idAt(2).equals("INTERNAL-B"),"second same-named device identity");
    check(rows.idAt(3).equals("INTERNAL-C"),"third device identity");
    check(rows.labels().get(1).equals("Flipper Zero • 1/2"),"duplicate names distinguished 1");
    check(rows.labels().get(2).equals("Flipper Zero • 2/2"),"duplicate names distinguished 2");
    check(rows.labels().get(3).equals("Momentum"),"unique name unaffected");
    check(!rows.labels().toString().contains("INTERNAL-"),"internal addresses not displayed");

    LinkedHashMap<String,String> reordered=new LinkedHashMap<>();
    reordered.put("INTERNAL-C","Momentum");
    reordered.put("INTERNAL-B","Flipper Zero");
    reordered.put("INTERNAL-D","New");
    BlePickerRows after=BlePickerRows.from(reordered);
    check(after.positionFor("INTERNAL-B")==2,"refresh preserves identity across reorder");
    check(after.idAt(after.positionFor("INTERNAL-B")).equals("INTERNAL-B"),"restored selection is same device");
    check(after.positionFor("INTERNAL-A")==0,"removed device selection cleared");
    check(after.positionFor(null)==0,"null selection clears");
    check(after.idAt(-1)==null && after.idAt(100)==null,"invalid row positions rejected");

    LinkedHashMap<String,String> unnamed=new LinkedHashMap<>();
    unnamed.put("ONE",null);
    unnamed.put("TWO","");
    unnamed.put("THREE","\r\nB\t");
    BlePickerRows anonymous=BlePickerRows.from(unnamed);
    check(anonymous.labels().get(1).contains("Unknown Bluetooth device"),"null name safe");
    check(anonymous.labels().get(2).contains("Unknown Bluetooth device"),"empty name safe");
    check(anonymous.labels().get(3).equals("B"),"control characters sanitized");

    String longName="L".repeat(400);
    LinkedHashMap<String,String> bounded=new LinkedHashMap<>();
    bounded.put("X",longName);
    check(BlePickerRows.from(bounded).labels().get(1).length()==90,"untrusted labels bounded");
    boolean immutable=false;
    try{rows.labels().add("untrusted");}catch(UnsupportedOperationException expected){immutable=true;}
    check(immutable,"snapshot rows must not be mutable by UI consumers");
    System.out.println("BLE picker identity and stable selection tests passed");
  }
}
