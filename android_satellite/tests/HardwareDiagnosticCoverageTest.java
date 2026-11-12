import com.flipperai.satellite.OfflineDiagnosticEngine;

public final class HardwareDiagnosticCoverageTest {
    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        OfflineDiagnosticEngine engine=new OfflineDiagnosticEngine();
        String[][] cases={
            {"Sub-GHz capture","Sub-GHz checks"},
            {"NFC tag not read","NFC checks"},
            {"RFID 125 kHz","RFID checks"},
            {"infrared remote","Infrared checks"},
            {"GPIO pin","GPIO checks"},
            {"ESP32 wifi","Wi-Fi checks"},
            {"Bluetooth GATT","BLE checks"},
            {"battery low","Power checks"},
            {"SD card errors","Storage checks"}
        };
        for(String[] item:cases) {
            check(engine.supports(item[0]),"unsupported: "+item[0]);
            check(engine.answer(item[0]).startsWith(item[1]),"incorrect guidance: "+item[0]);
        }
        check(!engine.supports("tell me a joke"),"non-diagnostic prompt was classified");
        check(engine.answer("tell me a joke").contains("not language-model"),"rules must disclose limitations");
        check(engine.answer(null).contains("HELP"),"null prompt");
        System.out.println("Hardware diagnostic coverage tests passed");
    }
}
