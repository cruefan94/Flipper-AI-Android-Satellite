import com.flipperai.satellite.GattProfilePolicy;
public class GattProfilePolicyTest {
 public static void main(String[] args) {
  if(!GattProfilePolicy.valid(8,16,true))throw new AssertionError("valid");
  if(GattProfilePolicy.valid(4,16,true))throw new AssertionError("no write");
  if(GattProfilePolicy.valid(8,32,true))throw new AssertionError("no notify");
  if(GattProfilePolicy.valid(8,16,false))throw new AssertionError("no CCCD");
  System.out.println("GattProfilePolicyTest PASS");
 }
}
