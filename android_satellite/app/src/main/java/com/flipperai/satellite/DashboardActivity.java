package com.flipperai.satellite;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/**
 * Dedicated dashboard using the new midnight/teal palette and concise navigation.
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
    box.setPadding(dp(17),dp(15),dp(17),dp(13));
    box.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x3343dfc4),
      SatelliteUi.panel(this,false),null));
    TextView heading=text(icon+"  "+title,18,SatelliteUi.WHITE,true);
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
    SatelliteUi.animateEntrance(this,box,parent.getChildCount());
    box.setClickable(true);
    box.setFocusable(true);
    box.setContentDescription(title+": "+action);
    box.setMinimumHeight(dp(72));
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
  @Override public void onBackPressed(){
    // Exiting the app task, unlike switching screens, releases the BLE owner.
    finishAffinity();
  }
  private void render(){
    root.removeAllViews();
    LinearLayout brand=column();
    brand.setPadding(dp(16),dp(15),dp(16),dp(15));
    brand.setBackground(SatelliteUi.panel(this,true));
    brand.addView(text("◈  FLIPPER AI SATELLITE",24,SatelliteUi.ACCENT,true));
    brand.addView(text("MOMENTUM • ANDROID AI COMPANION",12,SatelliteUi.AMBER,true));
    root.addView(brand);
    gap(root,12);

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
    SatelliteUi.sectionTitle(this,content,"START HERE");
    panelCard(content,"ᛒ","FLIPPER CONNECTION",
      "Connect or reconnect your Flipper. See whether Bluetooth and the AI channel are ready.",
      "MANAGE CONNECTION",MainActivity.class);
    panelCard(content,"✧","AI CHAT",
      "Ask your offline AI, or use cloud AI when you choose to enable it.",
      "OPEN AI CHAT",AiChatActivity.class);
    panelCard(content,"↓","OFFLINE AI MODELS",
      EmbeddedModelStore.installed(this)
        ? "Model installed on this phone. Test it or change your selected AI."
        : "Install an offline AI once, then choose or test it here.",
      "INSTALL / TEST",ModelsActivity.class);
    SatelliteUi.sectionTitle(this,content,"LISTEN & EXPLORE");
    panelCard(content,"♫","RADIO LISTENING",
      "Listen to permitted public radio streams and manage your favorites.",
      "OPEN RADIO",RadioScannerActivity.class);
    SatelliteUi.sectionTitle(this,content,"UTILITIES");
    panelCard(content,"▥","PHONE COMPANION",
      "View phone battery, network and Bluetooth status.",
      "PHONE STATUS",PhoneCompanionActivity.class);
    panelCard(content,"◈","ABOUT MY FLIPPER AI",
      "Read real app version, model, BLE/AI1 and cloud status; no made-up device data.",
      "VIEW SELF-DIAGNOSTICS",AboutFlipperAiActivity.class);
    panelCard(content,"⌘","GITHUB BUILDS",
      "Review available public GitHub builds, commits and releases.",
      "OPEN GITHUB",GitHubActivity.class);
    TextView future=text("More device features will appear here as they are verified.",
      13,SatelliteUi.MUTED,false);
    future.setPadding(dp(5),dp(6),dp(5),dp(7));
    content.addView(future);
  }
}
