package com.flipperai.satellite;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import flipperai.battery.BatteryOptimizer;

/** Battery Assistant preview. No simulated or phone-derived values masquerade as Flipper telemetry. */
public final class BatteryAssistantActivity extends Activity {
  private int dp(int n) { return SatelliteUi.dp(this,n); }
  private void line(LinearLayout parent, String value, int size, int color) {
    TextView label=SatelliteUi.text(this,value,size,color,false);
    label.setPadding(dp(4),dp(8),dp(4),dp(8));
    parent.addView(label);
  }
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(SatelliteUi.BACKGROUND);
    getWindow().setNavigationBarColor(SatelliteUi.BACKGROUND);
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(SatelliteUi.BACKGROUND);
    LinearLayout root=SatelliteUi.column(this);
    root.setPadding(dp(18),dp(20),dp(18),dp(24));
    scroll.addView(root);
    TextView title=SatelliteUi.text(this,"BATTERY ASSISTANT",24,SatelliteUi.ORANGE,true);
    root.addView(title);
    line(root,"Read-only • Recommendation only",14,SatelliteUi.MUTED);
    line(root,"Flipper battery: — (no verified telemetry)",19,SatelliteUi.WHITE);
    line(root,"Charging: Unknown",16,SatelliteUi.WHITE);
    line(root,"Battery condition: Unavailable",16,SatelliteUi.WHITE);
    line(root,"Estimated runtime: Insufficient discharge history",16,SatelliteUi.WHITE);
    line(root,"Eco / Balanced / Performance",18,SatelliteUi.ORANGE);
    BatteryOptimizer.Advice advice=BatteryOptimizer.evaluate(null,android.os.SystemClock.elapsedRealtime());
    line(root,advice.explanation,15,SatelliteUi.MUTED);
    line(root,"Battery readings will appear only after an authorized, capability-negotiated AI1 BAT_GET / BAT_TELEM connection is implemented and verified on both devices.",14,SatelliteUi.MUTED);
    line(root,"No device settings are changed. AI explanations and online processing remain disabled on this preview screen.",14,SatelliteUi.MUTED);
    setContentView(scroll);
  }
}
