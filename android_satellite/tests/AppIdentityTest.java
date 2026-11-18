import com.flipperai.satellite.AppIdentity;

/** Pure JVM identity guards; no model or Android phone required. */
public final class AppIdentityTest {
  private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
  public static void main(String[] args){
    String ctx=AppIdentity.SYSTEM_INSTRUCTION;
    require(ctx.length()<850,"System context is too large for small offline models");
    require(ctx.contains("Flipper AI Android Satellite"),"Identity missing");
    require(ctx.contains("Momentum"),"Firmware relationship missing");
    require(ctx.contains("Raspberry Pi")&&ctx.contains("Arduino")&&ctx.contains("ESP32"),"Project scope missing");
    require(ctx.contains("consent")&&ctx.contains("Never claim"),"Truth/privacy guard missing");
    require(AppIdentity.isAppQuestion("What app are you installed in, and what can you do?"),"Self query not caught");
    require(AppIdentity.isAppQuestion("About My Flipper AI"),"About shortcut not caught");
    require(!AppIdentity.isAppQuestion("How do I connect a resistor to ESP32?"),"Generic question misclassified");
    require(!AppIdentity.isAppQuestion("Is the battery charged?"),"Hardware question misclassified");
    System.out.println("AppIdentity JVM guards passed");
  }
}
