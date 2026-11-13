package com.flipperai.satellite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dedicated embedded AI model setup screen, separate from chat and BLE management. */
public final class ModelsActivity extends Activity {
  private static final int PICK_MODEL=712;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private EmbeddedLlmEngine model;
  private TextView status;
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    model=new EmbeddedLlmEngine(getCacheDir().getAbsolutePath());
    LinearLayout page=SatelliteUi.scrollPage(this,"OFFLINE AI MODELS",
      "Install a model once on this Android phone. AI can then work without internet.");
    status=SatelliteUi.text(this,"Checking installed models...",16,SatelliteUi.WHITE,false);
    status.setPadding(0,SatelliteUi.dp(this,7),0,SatelliteUi.dp(this,14));
    page.addView(status);
    TextView instructions=SatelliteUi.text(this,
      "Supported: Qwen3-0.6B.litertlm (~586 MB INT8). Other .litertlm exports may not be compatible. "+
      "The dynamic INT4 file requires a newer runtime. Download model weights separately, then import using Android Files.",
      14,SatelliteUi.MUTED,false);
    page.addView(instructions);
    SatelliteUi.gap(this,page,15);
    SatelliteUi.action(this,page,"1  OPEN COMPATIBLE MODEL FILES",()->{
      Intent browser=new Intent(Intent.ACTION_VIEW,
        Uri.parse("https://huggingface.co/litert-community/Qwen3-0.6B/tree/main"));
      startActivity(browser);
    });
    SatelliteUi.action(this,page,"2  IMPORT .LITERTLM FILE",()->{
      Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      picker.addCategory(Intent.CATEGORY_OPENABLE);
      picker.setType("*/*");
      startActivityForResult(picker,PICK_MODEL);
    });
    SatelliteUi.action(this,page,"3  TEST LIVE OFFLINE AI",()->{
      if(!EmbeddedModelStore.installed(this)){
        setStatus("No model imported. Choose a .litertlm file first.");return;
      }
      setStatus("Testing live on-device inference... This uses no cloud service.");
      worker.execute(()->{
        try {
          model.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
          String answer=model.answer("Reply with OK.");
          setStatus("Verified LIVE embedded offline model response: "+answer);
        }catch(Exception e){setStatus("Model could not load or respond: "+e.getMessage());}
      });
    });
    SatelliteUi.action(this,page,"REMOVE INSTALLED MODEL",()->{
      new android.app.AlertDialog.Builder(this).setTitle("Remove offline model?")
        .setMessage("The imported weights will be deleted from app-private storage. AI diagnostic rules will still work.")
        .setNegativeButton("KEEP",null)
        .setPositiveButton("REMOVE",(dialog,which)->worker.execute(()->{
          try {
            model.clear();
            boolean removed=EmbeddedModelStore.delete(this);
            setStatus(removed?"Model removed. Diagnostics remain available.":"Could not delete model file.");
          }catch(Exception e){setStatus("Remove failed: "+e.getMessage());}
        })).show();
    });
    SatelliteUi.backHome(this,page);
    refreshModelStatus();
  }
  private void refreshModelStatus(){
    if(EmbeddedModelStore.installed(this)){
      long mib=EmbeddedModelStore.modelFile(this).length()/(1024L*1024L);
      setStatus("Imported file found ("+mib+" MiB). Inference NOT verified on this phone; tap Test Live Offline AI.");
    }else setStatus("No embedded offline model imported. AI diagnostics remain available.");
  }
  private void setStatus(String message){
    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(message);});
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=PICK_MODEL||result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri selected=data.getData();
    setStatus("Importing model to private storage... Keep the app open.");
    worker.execute(()->{
      try{
        model.clear();
        long bytes=EmbeddedModelStore.importFromDocument(this,selected);
        setStatus("Imported "+(bytes/(1024L*1024L))+" MiB. Tap TEST LIVE OFFLINE AI to confirm it works.");
      }catch(Exception e){
        setStatus("Import failed: "+e.getMessage()+
          (EmbeddedModelStore.installed(this)?". Previously imported model was preserved.":""));
      }
    });
  }
  @Override protected void onDestroy(){
    if(!worker.isShutdown()){
      worker.execute(()->model.clear());
      worker.shutdown();
    }
    super.onDestroy();
  }
}
