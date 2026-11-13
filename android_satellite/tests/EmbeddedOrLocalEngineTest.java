import com.flipperai.satellite.EmbeddedOrLocalEngine;
import com.flipperai.satellite.InferenceRouter;

/** JVM-only offline backend selection tests; actual native inference needs a phone. */
public final class EmbeddedOrLocalEngineTest {
  private static void check(boolean condition,String why){
    if(!condition)throw new AssertionError(why);
  }
  public static void main(String[] args) throws Exception {
    final boolean[] installed={false};
    final int[] embeddedCalls={0},loopbackCalls={0};
    InferenceRouter.OfflineEngine embedded=new InferenceRouter.OfflineEngine(){
      public boolean ready(){return installed[0];}
      public String answer(String prompt){embeddedCalls[0]++;return "EMBEDDED"; }
    };
    InferenceRouter.OfflineEngine loopback=new InferenceRouter.OfflineEngine(){
      public boolean ready(){return true;}
      public String answer(String prompt){loopbackCalls[0]++;return "LOOPBACK";}
    };
    EmbeddedOrLocalEngine preferred=new EmbeddedOrLocalEngine(embedded,loopback);
    check(preferred.ready(),"localhost available before model import");
    check(preferred.answer("battery").equals("LOOPBACK"),"localhost backend without model");
    check(embeddedCalls[0]==0 && loopbackCalls[0]==1,"only localhost used");
    installed[0]=true;
    check(preferred.answer("battery").equals("EMBEDDED"),"embedded takes priority");
    check(embeddedCalls[0]==1 && loopbackCalls[0]==1,"no duplicate local model requests");
    installed[0]=false;
    check(preferred.answerForHybrid("battery").equals("LOOPBACK"),"hybrid offline fallback");
    check(embeddedCalls[0]==1 && loopbackCalls[0]==2,"no cloud dispatch");
    System.out.println("Embedded offline backend selection tests passed");
  }
}
