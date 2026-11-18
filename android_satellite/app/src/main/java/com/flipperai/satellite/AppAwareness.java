package com.flipperai.satellite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Truthful, read-only local application report, not model-generated telemetry.
 * The UI can show this without an installed model or permission to use cloud AI.
 * No Bluetooth address, serial, token, account ID or full filesystem path is exposed.
 */
public final class AppAwareness {
  private AppAwareness() {}

  private static String version(Context context) {
    try {
      PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
      long code=Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;
      return p.versionName+" (build "+code+")";
    }catch(Exception unavailable){return "Unavailable from Android package manager";}
  }

  public static String report(Context context,InferenceRouter.Mode mode,boolean chatCloudConsent){
    String provider=LlamaCppSettings.enabled(context)
      ?"Termux llama.cpp (selected; server connection not independently verified)"
      :"LiteRT-LM embedded (selected)";
    String model=EmbeddedModelStore.installed(context)
      ?"Installed .litertlm model file present; live inference not verified by this report"
      :"No imported LiteRT model detected";
    boolean configured=false;
    try {configured=CloudCredentialStore.load(context)!=null;}
    catch(Exception unavailable) {configured=false;}
    String cloud=configured?"Credentials stored securely":"Not configured";
    String link=BleConnectionService.readOnlyLinkReport();
    return AppIdentity.NAME+"\n"
      +"Version: "+version(context)+"\n"
      +"Role: Android offline AI companion to custom Flipper Zero Momentum firmware.\n"
      +"Inference: "+(mode==null?"Unknown (no chat session)":mode.name())+"; "+provider+"\n"
      +"Offline model: "+model+"\n"
      +"Cloud: "+cloud+"; authorization in this chat: "+(chatCloudConsent?"YES":"NO/NOT SHARED")+". No automatic upload.\n"
      +"Bluetooth/Momentum: "+link+"\n"
      +"ESP32: optional project component; live connection not verified by this app report.\n"
      +"Arduino: optional project component; live connection not verified.\n"
      +"Raspberry Pi: optional companion; live connection not verified by this report.\n"
      +"Capabilities: local LiteRT or opt-in localhost inference, explicit-consent Worker cloud, "
      +"bounded AI1 BLE messaging when firmware/device confirms service, stock Momentum RPC "
      +"diagnostics, radio stream screen, model management, voice controls and phone information.\n"
      +"Limitations: firmware changes, background BLE stability and end-to-end Flipper AI1 "
      +"communication require real hardware tests; no unrestricted hardware commands.\n"
      +"Troubleshooting: open FLIPPER CONNECTION for channel state; test your model in "
      +"OFFLINE AI MODELS; keep cloud disabled until you explicitly authorize it.";
  }

  public static String answer(Context context,InferenceRouter.Mode mode,boolean consent){
    return report(context,mode,consent);
  }
}
