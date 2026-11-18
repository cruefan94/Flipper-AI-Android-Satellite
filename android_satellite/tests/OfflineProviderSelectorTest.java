import com.flipperai.satellite.InferenceRouter;
import com.flipperai.satellite.OfflineProviderSelector;

/** No native model or Flipper required: provider choice must never silently change. */
public final class OfflineProviderSelectorTest {
  static final class Stub implements InferenceRouter.OfflineEngine {
    final String name;final boolean ready;
    Stub(String name,boolean ready){this.name=name;this.ready=ready;}
    @Override public boolean ready(){return ready;}
    @Override public String answer(String prompt){return name+":"+prompt;}
  }
  static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);}
  public static void main(String[] args) throws Exception {
    Stub lite=new Stub("LiteRT",true),llama=new Stub("llama.cpp",true);
    OfflineProviderSelector selector=new OfflineProviderSelector(lite,llama,false);
    check(selector.ready(),"default LiteRT readiness");
    check("LiteRT:hi".equals(selector.answer("hi")),"default LiteRT selected");
    selector.selectLlama(true);
    check("llama.cpp:hi".equals(selector.answer("hi")),"explicit llama selection");
    selector.selectLlama(false);
    check("LiteRT:hi".equals(selector.answer("hi")),"switch back without import");
    OfflineProviderSelector unavailable=new OfflineProviderSelector(
      lite,new Stub("unavailable",false),true);
    check(!unavailable.ready(),"selected backend must not silently fall back");
    System.out.println("Offline model provider selection tests passed");
  }
}
