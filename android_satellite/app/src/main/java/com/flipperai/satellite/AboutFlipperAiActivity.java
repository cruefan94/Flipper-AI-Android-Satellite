package com.flipperai.satellite;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate read-only diagnostics screen. Live state is sampled on resume and Refresh. */
public final class AboutFlipperAiActivity extends Activity {
  private TextView details;
  private LinearLayout page;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    page=SatelliteUi.scrollPage(this,"ABOUT MY FLIPPER AI",
      "Verified app facts, model setup and Bluetooth readiness — never a model guess.");
    SatelliteUi.backHome(this,page);
    SatelliteUi.sectionTitle(this,page,"APPLICATION & HARDWARE STATUS");
    details=SatelliteUi.text(this,"Loading current status...",15,SatelliteUi.WHITE,false);
    details.setTextIsSelectable(true);
    details.setPadding(SatelliteUi.dp(this,12),SatelliteUi.dp(this,14),
      SatelliteUi.dp(this,12),SatelliteUi.dp(this,14));
    details.setBackground(SatelliteUi.panel(this,false));
    page.addView(details,new LinearLayout.LayoutParams(-1,-2));
    SatelliteUi.sectionTitle(this,page,"TOOLS");
    SatelliteUi.action(this,page,"↻  REFRESH DIAGNOSTICS",this::refresh);
    SatelliteUi.action(this,page,"▤  COPY REDACTED REPORT",()->{
      ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if(clipboard!=null)clipboard.setPrimaryClip(
        ClipData.newPlainText("Flipper AI safe diagnostics",details.getText()));
    });
    SatelliteUi.action(this,page,"✧  OPEN AI CHAT",()->SatelliteUi.open(this,AiChatActivity.class));
  }
  @Override protected void onResume(){super.onResume();refresh();}
  private void refresh(){
    details.setText(AppAwareness.report(this,null,false));
  }
}
