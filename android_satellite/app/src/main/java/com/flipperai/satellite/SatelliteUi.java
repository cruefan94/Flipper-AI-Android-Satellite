package com.flipperai.satellite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.provider.Settings;
import android.widget.*;

/** Shared midnight/teal Flipper AI styling for every native screen. */
public final class SatelliteUi {
  // Dark navy surfaces, high-contrast teal actions, restrained warm highlights.
  public static final int BACKGROUND=0xff0b1422;
  public static final int PANEL=0xff172637;
  public static final int ACCENT=0xff43dfc4;
  public static final int AMBER=0xffffc06a;
  // Backwards-compatible semantic alias until all screen-specific code migrates.
  public static final int ORANGE=ACCENT;
  public static final int WHITE=0xfff1f7ff;
  public static final int MUTED=0xffb5c8da;
  public static final int BORDER=0xff405b73;
  private SatelliteUi(){}
  /** Small nonblocking motion; system-disabled animator scales are honored. */
  public static void animateEntrance(Activity a,View view,int index){
    float scale;
    try{scale=Settings.Global.getFloat(a.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE,1f);}
    catch(SecurityException unavailable){scale=0f;}
    if(scale<=0f)return;
    view.setAlpha(0f);
    view.setTranslationY(dp(a,8));
    view.animate().alpha(1f).translationY(0f).setDuration(150)
      .setStartDelay(Math.min(Math.max(0,index),8)*25L).start();
  }
  public static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
  public static GradientDrawable panel(Activity a,boolean active){
    GradientDrawable shape=new GradientDrawable();
    shape.setColor(active?0xff1a3544:PANEL);
    shape.setCornerRadius(dp(a,16));
    shape.setStroke(dp(a,1),active?ACCENT:BORDER);
    return shape;
  }
  public static TextView text(Activity a,String content,int size,int color,boolean strong){
    TextView v=new TextView(a);v.setText(content);v.setTextSize(size);v.setTextColor(color);
    if(strong)v.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
    return v;
  }
  public static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
  public static void gap(Activity a,LinearLayout l,int dp){l.addView(new Space(a),new LinearLayout.LayoutParams(1,dp(a,dp)));}
  public static LinearLayout scrollPage(Activity a,String heading,String subtitle){
    a.getWindow().setStatusBarColor(BACKGROUND);
    a.getWindow().setNavigationBarColor(BACKGROUND);
    ScrollView scroll=new ScrollView(a);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(BACKGROUND);
    LinearLayout page=column(a);
    page.setPadding(dp(a,16),dp(a,20),dp(a,16),dp(a,28));
    scroll.addView(page);
    a.setContentView(scroll);
    TextView title=text(a,heading,25,ORANGE,true);
    page.addView(title);
    TextView sub=text(a,subtitle,14,MUTED,false);
    LinearLayout.LayoutParams subtitleLp=new LinearLayout.LayoutParams(-1,-2);
    subtitleLp.topMargin=dp(a,5);subtitleLp.bottomMargin=dp(a,15);
    page.addView(sub,subtitleLp);
    return page;
  }
  public static TextView action(Activity a,LinearLayout parent,String title,Runnable callback){
    TextView v=text(a,title,16,WHITE,true);
    v.setGravity(Gravity.CENTER_VERTICAL);
    v.setPadding(dp(a,15),dp(a,14),dp(a,15),dp(a,14));
    v.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x3343dfc4),
      panel(a,false),null));
    // One consistently sized, accessible touch surface across app screens.
    v.setMinHeight(dp(a,52));
    v.setFocusable(true);
    v.setClickable(true);
    v.setContentDescription(title);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
    lp.bottomMargin=dp(a,10);
    parent.addView(v,lp);
    v.setOnClickListener(x->callback.run());
    return v;
  }
  /** Highlight the one recommended action in a group without changing its behavior. */
  public static TextView primaryAction(Activity a,LinearLayout parent,String title,Runnable callback){
    TextView action=action(a,parent,title,callback);
    GradientDrawable fill=new GradientDrawable();
    fill.setColor(ACCENT);
    fill.setCornerRadius(dp(a,16));
    action.setTextColor(BACKGROUND);
    action.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x330b1422),fill,null));
    return action;
  }
  public static TextView sectionTitle(Activity a,LinearLayout parent,String heading){
    TextView label=text(a,heading,15,ORANGE,true);
    label.setPadding(dp(a,4),dp(a,14),dp(a,4),dp(a,9));
    parent.addView(label);
    return label;
  }
  public static void open(Activity from,Class<?> destination){
    if(from.getClass().equals(destination))return;
    Intent i=new Intent(from,destination);
    // Reuse the living BLE Activity instead of creating another GATT owner.
    if(destination==MainActivity.class)
      i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    from.startActivity(i);
  }
  public static void goHome(Activity from){
    Intent i=new Intent(from,DashboardActivity.class);
    // CLEAR_TOP destroys the BLE Activity on its way to the dashboard.
    // Reorder the existing dashboard instead, keeping the BLE link alive.
    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    from.startActivity(i);
    // Other screens still release their resources after navigation.
    // Explicit Disconnect and app task exit remain the BLE teardown paths.
    if(!(from instanceof MainActivity))from.finish();
  }
  public static void backHome(Activity from,LinearLayout page){
    action(from,page,"‹  DASHBOARD",()->goHome(from));
  }
}
