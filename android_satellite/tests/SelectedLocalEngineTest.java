import com.flipperai.satellite.InferenceRouter;
import com.flipperai.satellite.SelectedLocalEngine;

public final class SelectedLocalEngineTest {
    private static void check(boolean value) { if(!value)throw new AssertionError("provider selection"); }
    private static InferenceRouter.OfflineEngine fake(String name,boolean available) {
        return new InferenceRouter.OfflineEngine() {
            @Override public boolean ready(){return available;}
            @Override public String answer(String prompt){return name+":"+prompt;}
        };
    }
    public static void main(String[] args) throws Exception {
        SelectedLocalEngine selected=new SelectedLocalEngine(fake("EMBEDDED",false),fake("LOOPBACK",true));
        check(selected.selected()==SelectedLocalEngine.Provider.EMBEDDED);
        check(!selected.ready());
        selected.select(SelectedLocalEngine.Provider.LOCALHOST);
        check(selected.ready());
        check("LOOPBACK:hello".equals(selected.answer("hello")));
        boolean denied=false;
        try { selected.select(null); }catch(IllegalArgumentException expected){denied=true;}
        check(denied);
        selected.select(SelectedLocalEngine.Provider.EMBEDDED);
        check(!selected.ready());
        System.out.println("Selected local model provider tests passed");
    }
}
