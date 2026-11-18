import com.flipperai.satellite.GattProfilePolicy;

/** Exhaustively check capability flags: unrelated flags cannot substitute for AI1 RX/TX. */
public class GattProfilePolicyTest {
 public static void main(String[] args) {
  for(int rx=0;rx<256;rx++) {
   for(int tx=0;tx<256;tx++) {
    for(int c=0;c<2;c++) {
     boolean expected=(rx & GattProfilePolicy.WRITE)!=0
       && (tx & GattProfilePolicy.NOTIFY)!=0 && c==1;
     boolean actual=GattProfilePolicy.valid(rx,tx,c==1);
     if(actual!=expected)throw new AssertionError(
       "GATT capability mismatch rx="+rx+" tx="+tx+" cccd="+c);
    }
   }
  }
  // Stock RPC discovery must never be inferred from read/indicate-only properties.
  if(GattProfilePolicy.valid(0x02,0x20,true))
   throw new AssertionError("read/indicate is not AI1 write/notify");
  System.out.println("GattProfilePolicyTest PASS (131072 combinations)");
 }
}
