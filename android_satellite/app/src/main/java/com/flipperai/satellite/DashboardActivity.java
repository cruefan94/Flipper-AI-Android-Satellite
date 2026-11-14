package com.flipperai.satellite;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/**
 * Dedicated app launcher: restore approved dark/orange dashboard navigation.
 * Every active card opens a distinct screen, no buttons secretly open the same page.
 * Device and inference states are never invented here.
 */
public final class DashboardActivity extends Activity {
  private LinearLayout root;
  private int dp(int n){return SatelliteUi.dp(this,n);}
  private LinearLayout column(){return SatelliteUi.column(this);}
  private TextView text(String s,int size,int color,boolean bold){
    return SatelliteUi.text(this,s,size,color,bold);
  }
  private void gap(LinearLayout l,int n){SatelliteUi.gap(this,l,n);}
  private void panelCard(LinearLayout parent,String icon,String title,String description,
                         String action,Class<?> screen){
    LinearLayout box=column();
    box.setPadding(dp(16),dp(14),dp(16),dp(14));
    box.setBackground(SatelliteUi.panel(this,false));
    TextView heading=text(icon+"  "+title,19,SatelliteUi.WHITE,true);
    box.addView(heading);
    gap(box,6);
    TextView detail=text(description,14,SatelliteUi.MUTED,false);
    detail.setLineSpacing(dp(2),1.0f);box.addView(detail);
    gap(box,10);
    TextView cta=text(action+"   ›",14,SatelliteUi.ORANGE,true);
    cta.setGravity(Gravity.END);box.addView(cta);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
    lp.bottomMargin=dp(12);
    parent.addView(box,lp);
    box.setClickable(true);
    box.setFocusable(true);
    box.setContentDescription(title+": "+action);
    box.setMinHeight(dp(72));
    box.setOnClickListener(v->SatelliteUi.open(this,screen));
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(SatelliteUi.BACKGROUND);
    getWindow().setNavigationBarColor(SatelliteUi.BACKGROUND);
    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(SatelliteUi.BACKGROUND);
    root=column();
    root.setPadding(dp(13),dp(13),dp(13),dp(28));
    scroll.addView(root);
    setContentView(scroll);
  }
  @Override protected void onResume(){
    super.onResume();
    render();
  }
  private void render(){
    root.removeAllViews();
    LinearLayout brand=column();
    brand.setPadding(dp(16),dp(15),dp(16),dp(15));
    brand.setBackground(SatelliteUi.panel(this,true));
    brand.addView(text("◈  FLIPPER AI SATELLITE",27,SatelliteUi.ORANGE,true));
    brand.addView(text("MOMENTUM • ANDROID AI COMPANION",13,SatelliteUi.WHITE,true));
    root.addView(brand);
    gap(root,14);

    // A single navigational route per screen, including tablets in landscape.
    // Old landscape sidebar repeated the same destinations as the cards.
    int viewportDp=Math.round(getResources().getDisplayMetrics().widthPixels
      /getResources().getDisplayMetrics().density);
    boolean wide=viewportDp>=700;
    // The content width must also fit inside the page's 13dp side padding.
    int cardWidthDp=Math.max(1,Math.min(680,viewportDp-26));
    LinearLayout content=column();
    LinearLayout.LayoutParams contentParams=new LinearLayout.LayoutParams(
      wide?dp(cardWidthDp):-1,-2);
    contentParams.gravity=Gravity.CENTER_HORIZONTAL;
    root.addView(content,contentParams);
    SatelliteUi.sectionTitle(this,content,"ESSENTIALS");
    panelCard(content,"ᛒ","FLIPPER CONNECTION",
      "Pair, connect and check Momentum BLE and the AI channel. Connection state is shown in the dedicated BLE screen.",
      "MANAGE CONNECTION",MainActivity.class);
    panelCard(content,"✧","AI CHAT",
      "Full-screen Android AI conversation. Uses your imported offline model when available; diagnostic responses otherwise.",
      "OPEN AI CHAT",AiChatActivity.class);
    panelCard(content,"↓","OFFLINE AI MODELS",
      EmbeddedModelStore.installed(this)
        ? "Model file imported on this phone. Run the live test to confirm that inference works."
        : "No imported .litertlm model detected. Import a compatible LiteRT-LM model to enable full offline responses.",
      "INSTALL / TEST",ModelsActivity.class);
    SatelliteUi.sectionTitle(this,content,"LISTEN & EXPLORE");
    panelCard(content,"♫","RADIO LISTENING",
      "Find public internet AM/FM streams, play permitted feeds, and manage favorites. Receive-only; no SDR or Flipper radio attachment yet.",
      "OPEN RADIO",RadioScannerActivity.class);
    SatelliteUi.sectionTitle(this,content,"UTILITIES");
    panelCard(content,"▥","PHONE COMPANION",
      "Check battery, network and Bluetooth device status. No hidden permissions or automatic device control.",
      "PHONE STATUS",PhoneCompanionActivity.class);
    panelCard(content,"⌘","GITHUB BUILDS",
      "Read-only public repository builds, commits and releases. Private account access is not enabled in this app.",
      "OPEN GITHUB",GitHubActivity.class);
    TextView future=text("Additional hardware radio reception and audio transcription are future features.",
      13,SatelliteUi.MUTED,false);
    future.setPadding(dp(5),dp(6),dp(5),dp(7));
    content.addView(future);
  }
}
