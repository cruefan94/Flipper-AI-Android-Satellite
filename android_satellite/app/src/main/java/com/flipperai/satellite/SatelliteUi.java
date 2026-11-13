package com.flipperai.satellite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Shared dark/orange Flipper AI navigation styling for all native screens. */
public final class SatelliteUi {
  public static final int BACKGROUND=0xff080b10;
  public static final int PANEL=0xff161c25;
  public static final int ORANGE=0xffff9614;
  public static final int WHITE=0xfff4f6f9;
  public static final int MUTED=0xffbac3ce;
  public static final int BORDER=0xff536276;
  private SatelliteUi(){}
  public static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
  public static GradientDrawable panel(Activity a,boolean active){
    GradientDrawable shape=new GradientDrawable();
    shape.setColor(active?0xff302014:PANEL);
    shape.setCornerRadius(dp(a,12));
    shape.setStroke(dp(a,1),active?ORANGE:BORDER);
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
    v.setBackground(panel(a,false));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
    lp.bottomMargin=dp(a,10);
    parent.addView(v,lp);
    v.setOnClickListener(x->callback.run());
    return v;
  }
  public static void open(Activity from,Class<?> destination){
    if(!from.getClass().equals(destination))
      from.startActivity(new Intent(from,destination));
  }
  public static void backHome(Activity from,LinearLayout page){
    action(from,page,"‹  DASHBOARD",()->{
      Intent i=new Intent(from,DashboardActivity.class);
      i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
      from.startActivity(i);
      from.finish();
    });
  }
}
