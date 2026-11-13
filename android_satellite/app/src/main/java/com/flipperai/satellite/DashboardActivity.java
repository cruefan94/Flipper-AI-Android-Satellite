package com.flipperai.satellite;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.graphics.Insets;
import android.widget.*;

/** Unified dashboard shell. Existing BLE, voice and radio implementations remain in their activities. */
public final class DashboardActivity extends Activity {
  private static final int BG=Color.rgb(8,17,23), PANEL=Color.rgb(19,37,45), CYAN=Color.rgb(50,231,218), MUTED=Color.rgb(166,190,195);
  private LinearLayout content;
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackgroundColor(BG);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(20),dp(18),dp(18));
    scroll.addView(content);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setBackgroundColor(PANEL);
    navButton(nav,"HOME",DashboardActivity.class);navButton(nav,"AI",AiChatActivity.class);navButton(nav,"FLIPPER",MainActivity.class);navButton(nav,"RADIO",RadioScannerActivity.class);
    frame.addView(nav);setContentView(frame);
    // Keep app navigation above gesture/three-button system navigation on edge-to-edge Android.
    frame.setOnApplyWindowInsetsListener((view,insets)->{
      android.view.WindowInsets.Type navigation=android.view.WindowInsets.Type.navigationBars();
      Insets bottom=insets.getInsets(navigation);
      nav.setPadding(0,0,0,bottom.bottom);
      return insets;
    });
    frame.requestApplyInsets();render();
  }
  private void render(){
    content.removeAllViews();
    TextView brand=label("◈  FLIPPER AI SATELLITE",24,CYAN,true);content.addView(brand);
    content.addView(label("Android AI Companion  •  Unified Dashboard",13,MUTED,false));
    space(20);
    section("SYSTEM OVERVIEW");
    card("FLIPPER ZERO", "Open BLE connection, paired-device discovery, RPC diagnostics and manual reconnect.",MainActivity.class);
    card("PHONE COMPANION", "Phone battery, charging, Bluetooth, Wi-Fi and internet connectivity.",PhoneCompanionActivity.class);
    card("AI ASSISTANT", "Dedicated private on-phone text chat with optional local model.",AiChatActivity.class);
    card("VOICE ASSISTANT", "Working phone microphone and spoken AI responses. Cloud mode requires consent.",MainActivity.class);
    card("RADIO SCANNER", "Find online stations, favorites, recent streams and media playback.",RadioScannerActivity.class);
    card("GITHUB", "Public repositories, branches, commits, Actions and releases. Account linking not yet available.",GitHubActivity.class);
    space(12);
    section("CONNECTION & SECURITY");
    content.addView(label("Bluetooth: connection details are shown inside Flipper Connection. This dashboard does not claim a live AI session.",14,MUTED,false));
    content.addView(label("GitHub OAuth, private repositories, Flipper PTT, notification sharing and USB SDR are not operational yet.",14,MUTED,false));
    space(12);
    section("QUICK ACCESS");
    tile("OPEN AI CHAT",AiChatActivity.class);
    tile("FLIPPER CONNECTION / BLE",MainActivity.class);
    tile("RADIO STATION BROWSER",RadioScannerActivity.class);
    tile("PHONE STATUS",PhoneCompanionActivity.class);
    tile("GITHUB BUILDS",GitHubActivity.class);
  }
  private void navButton(LinearLayout parent,String title,Class<?> screen){
    TextView v=label(title,11,screen==DashboardActivity.class?CYAN:Color.WHITE,true);
    v.setGravity(Gravity.CENTER);v.setPadding(0,dp(16),0,dp(16));
    parent.addView(v,new LinearLayout.LayoutParams(0,dp(55),1));
    v.setOnClickListener(w->open(screen));
  }
  private void section(String title){TextView t=label(title,15,CYAN,true);t.setPadding(0,dp(5),0,dp(10));content.addView(t);}
  private void card(String title,String subtitle,Class<?> screen){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(15),dp(16),dp(15));
    GradientDrawable shape=new GradientDrawable();shape.setColor(PANEL);shape.setCornerRadius(dp(13));shape.setStroke(dp(1),Color.rgb(38,92,99));panel.setBackground(shape);
    panel.addView(label(title+"  ›",17,Color.WHITE,true));panel.addView(label(subtitle,13,MUTED,false));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);content.addView(panel,lp);panel.setOnClickListener(v->open(screen));
  }
  private void tile(String title,Class<?> screen){
    TextView t=label(title+"    ›",14,Color.WHITE,true);t.setPadding(dp(14),dp(15),dp(14),dp(15));t.setBackgroundColor(PANEL);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);content.addView(t,lp);t.setOnClickListener(v->open(screen));
  }
  private TextView label(String text,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
  private void space(int dp){Space space=new Space(this);content.addView(space,new LinearLayout.LayoutParams(1,dp(dp)));}
  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
  private void open(Class<?> screen){if(screen!=DashboardActivity.class)startActivity(new Intent(this,screen));}
}
