package com.flipperai.satellite;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

/** Responsive Flipper AI concept dashboard. Actual device states remain on their owning screens. */
public final class DashboardActivity extends Activity {
  private static final int BLACK=0xff080b10, PANEL=0xff161c25, ORANGE=0xffff9614, WHITE=0xfff4f6f9, MUTED=0xffbac3ce, EDGE=0xff647386;
  private LinearLayout root;
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private TextView text(String s,int size,int color,boolean bold){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));return t;
  }
  private GradientDrawable panel(int fill,int stroke){
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,0xff0b1018});
    d.setCornerRadius(dp(8));d.setStroke(dp(1),stroke);return d;
  }
  private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  private void gap(LinearLayout l,int n){l.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(n)));}
  private void open(Class<?> screen){if(screen!=DashboardActivity.class)startActivity(new Intent(this,screen));}
  private void radio(int index){Intent i=new Intent(this,RadioScannerActivity.class);i.putExtra("quick_category",index);startActivity(i);}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);getWindow().setStatusBarColor(BLACK);getWindow().setNavigationBarColor(BLACK);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BLACK);
    root=column();root.setPadding(dp(12),dp(12),dp(12),dp(18));scroll.addView(root);setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener((v,insets)->{
      android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
      root.setPadding(dp(12)+bars.left,dp(12)+bars.top,dp(12)+bars.right,dp(18)+bars.bottom);
      return insets;
    });render();
  }
  private void heading(LinearLayout parent,String title){
    TextView t=text(title,18,ORANGE,true);t.setPadding(dp(2),dp(6),0,dp(8));parent.addView(t);
  }
  private void menu(LinearLayout parent,String glyph,String title,Class<?> screen,boolean selected){
    TextView t=text(glyph+"   "+title,15,selected?ORANGE:WHITE,true);
    t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(13),dp(12),dp(9),dp(12));
    t.setBackground(panel(selected?0xff39240e:PANEL,selected?ORANGE:EDGE));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(7);parent.addView(t,lp);
    t.setOnClickListener(v->open(screen));
  }
  private void card(LinearLayout parent,String title,String body,String action,Class<?> screen){
    LinearLayout card=column();card.setPadding(dp(15),dp(12),dp(15),dp(12));card.setBackground(panel(PANEL,EDGE));
    card.addView(text(title,19,WHITE,true));gap(card,5);
    TextView detail=text(body,14,MUTED,false);detail.setLineSpacing(dp(2),1);card.addView(detail);
    gap(card,8);TextView cta=text(action+"   ›",14,ORANGE,true);cta.setGravity(Gravity.END);card.addView(cta);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(11);parent.addView(card,lp);
    card.setOnClickListener(v->open(screen));
  }
  private void radioGrid(LinearLayout parent){
    String[] names={"AM","FM","POLICE","FIRE / EMS","HAM","CB","AVIATION","WEATHER"};
    String[] glyphs={"◉","▥","✦","✚","⌁","▣","✈","☁"};
    for(int i=0;i<8;i+=2){
      LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
      parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
      for(int j=i;j<i+2;j++){
        final int index=j;
        LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(5),dp(10),dp(5),dp(10));
        tile.setBackground(panel(PANEL,0xff7b5c38));
        TextView icon=text(glyphs[j],27,ORANGE,true);icon.setGravity(Gravity.CENTER);tile.addView(icon);
        TextView name=text(names[j],14,WHITE,true);name.setGravity(Gravity.CENTER);tile.addView(name);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(85),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,lp);
        tile.setOnClickListener(v->radio(index));
      }
    }
  }
  private void render(){
    root.removeAllViews();
    LinearLayout brand=column();brand.setPadding(dp(10),dp(10),dp(10),dp(12));brand.setBackground(panel(0xff151920,0xff995d1b));
    brand.addView(text("◈  FLIPPER AI SATELLITE",28,ORANGE,true));
    brand.addView(text("ANDROID AI COMPANION FOR FLIPPER ZERO",13,WHITE,true));
    root.addView(brand);gap(root,12);
    boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE &&
      getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=700;
    LinearLayout workspace=new LinearLayout(this);workspace.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);root.addView(workspace);
    LinearLayout sidebar=column(),center=column(),right=column();
    if(wide){
      LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1.1f);a.rightMargin=dp(10);workspace.addView(sidebar,a);
      LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,3.1f);b.rightMargin=dp(10);workspace.addView(center,b);
      workspace.addView(right,new LinearLayout.LayoutParams(0,-2,1.75f));
    }else{
      workspace.addView(center);workspace.addView(right);workspace.addView(sidebar);
    }
    if(wide){
      menu(sidebar,"⌂","DASHBOARD",DashboardActivity.class,true);
      menu(sidebar,"◉","AI CHAT",AiChatActivity.class,false);
      menu(sidebar,"ᛒ","FLIPPER / BLE",MainActivity.class,false);
      menu(sidebar,"⌁","RADIO SCANNER",RadioScannerActivity.class,false);
      menu(sidebar,"♩","VOICE ASSISTANT",MainActivity.class,false);
      menu(sidebar,"⬡","TOOLS",PhoneCompanionActivity.class,false);
      menu(sidebar,"↓","MODELS",AiChatActivity.class,false);
      menu(sidebar,"⌘","GITHUB",GitHubActivity.class,false);
      menu(sidebar,"⚙","SETTINGS",PhoneCompanionActivity.class,false);
      gap(sidebar,15);sidebar.addView(text("◈  MOMENTUM",15,ORANGE,true));
    }
    card(center,"ᛒ   FLIPPER CONNECTION","Connection: check live BLE state in Manage. Momentum RPC does not confirm an AI channel.","MANAGE",MainActivity.class);
    card(center,"✧   AI ENGINE","Mode: Offline (local)\nModel: not confirmed installed\nStatus: install and load a compatible model to enable chat.","INSTALL / SELECT MODEL",AiChatActivity.class);
    card(center,"⌁   RADIO SCANNER","Online directory: AM / FM / Police / Fire / Ham / CB. Stream availability depends on each provider.","OPEN RADIO",RadioScannerActivity.class);
    card(center,"♩   VOICE ASSISTANT","Phone microphone and speech output. Real AI replies require an installed working model.","TALK TO AI",MainActivity.class);
    card(center,"▥   SYSTEM STATUS","Battery, storage, Wi-Fi, mobile connection and diagnostics are available in Phone Companion.","VIEW STATUS",PhoneCompanionActivity.class);
    heading(right,"⌁  QUICK RADIO");radioGrid(right);
    gap(right,10);
    card(right,"★   FAVORITES","Open saved and recently played stations. No unverified emergency feeds are presented as live.","BROWSE ALL STATIONS",RadioScannerActivity.class);
    if(!wide){
      heading(sidebar,"MORE TOOLS");
      menu(sidebar,"ᛒ","FLIPPER / BLE",MainActivity.class,false);
      menu(sidebar,"⬡","PHONE COMPANION",PhoneCompanionActivity.class,false);
      menu(sidebar,"⌘","GITHUB",GitHubActivity.class,false);
    }
  }
}
