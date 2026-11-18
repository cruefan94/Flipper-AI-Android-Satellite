import com.flipperai.satellite.BleWriteGuard;

public final class BleWriteGuardTest {
  private static void check(boolean condition,String label){
    if(!condition)throw new AssertionError(label);
  }
  public static void main(String[] args){
    BleWriteGuard g=new BleWriteGuard();
    check(!g.isOutstanding(),"initially idle");
    int first=g.begin();
    check(g.isOutstanding()&&g.isPending(first),"first write active");
    boolean rejected=false;
    try{g.begin();}
    catch(IllegalStateException expected){rejected=true;}
    check(rejected,"cannot start overlapping writes");
    check(g.complete(),"completion accepted");
    check(!g.isPending(first),"completed write timer invalid");
    check(!g.complete(),"duplicate callback rejected");
    int second=g.begin();
    check(second!=first,"new write has new token");
    g.reset();
    check(!g.isPending(second)&&!g.isOutstanding(),"disconnect invalidates timer");
    int third=g.begin();
    check(third!=second && third!=first,"reconnected write is distinct");
    check(!g.isPending(second),"stale timer cannot expire new write");
    check(g.complete()&&!g.isOutstanding(),"new write completes");
    System.out.println("BleWriteGuard tests passed");
  }
}
