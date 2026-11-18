import com.flipperai.satellite.RecentFlipperPolicy;

/** Test privacy-safe labels and exact paired identity keys without Android or hardware. */
public final class RecentFlipperPolicyTest {
  static void check(boolean value,String message) {
    if(!value)throw new AssertionError(message);
  }
  public static void main(String[] args) {
    check(RecentFlipperPolicy.validAddress("F0:23:AA:00:11:BC"),"BLE address form");
    check("F0:23:AA:00:11:BC".equals(
      RecentFlipperPolicy.canonicalAddress("f0:23:aa:00:11:bc")),"canonical case");
    check(!RecentFlipperPolicy.validAddress("F0:23:AA:00:11"),"truncated rejected");
    check(!RecentFlipperPolicy.validAddress("192.168.0.1"),"IPv4 rejected");
    check(!RecentFlipperPolicy.validAddress("XX:23:AA:00:11:BC"),"nonnumeric rejected");
    check("Paired Flipper".equals(RecentFlipperPolicy.displayName(null)),"missing name");
    check("Sarah".equals(RecentFlipperPolicy.displayName("  Sarah  ")),"trim name");
    check(!RecentFlipperPolicy.displayName("A\nB|C").contains("|"),"delimiter removed");
    check(!RecentFlipperPolicy.displayName("A\nB|C").contains("\n"),"newline removed");
    check(RecentFlipperPolicy.displayName("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX").length()<=32,
      "bounded visible label");
    check(RecentFlipperPolicy.MAX_RECENTS==3,"compact previous devices");
    System.out.println("Recent Flipper quick reconnect identity tests passed");
  }
}
