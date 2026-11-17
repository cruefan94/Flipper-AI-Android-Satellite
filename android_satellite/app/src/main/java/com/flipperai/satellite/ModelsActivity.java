package com.flipperai.satellite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated embedded AI model setup screen, separate from chat and BLE management. */
public final class ModelsActivity extends Activity {
  private static final int PICK_MODEL=712;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private EmbeddedLlmEngine model;
  private TextView status;
  private ProgressBar operationProgress;
  private boolean operationRunning=false;
  private final AtomicBoolean downloadCancelled=new AtomicBoolean(false);
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    model=new EmbeddedLlmEngine(getCacheDir().getAbsolutePath());
    LinearLayout page=SatelliteUi.scrollPage(this,"OFFLINE AI MODELS",
      "Install a model once on this Android phone. AI can then work without internet.");
    status=SatelliteUi.text(this,"Checking installed models...",16,SatelliteUi.WHITE,false);
    status.setPadding(0,SatelliteUi.dp(this,7),0,SatelliteUi.dp(this,14));
    status.setTextIsSelectable(true);
    page.addView(status);
    operationProgress=new ProgressBar(this);
    operationProgress.setIndeterminate(true);
    operationProgress.setVisibility(android.view.View.GONE);
    page.addView(operationProgress);
    SatelliteUi.sectionTitle(this,page,"QUICK START — CHOOSE & TEST");
    page.addView(SatelliteUi.text(this,
      "Choose the AI already installed on this phone, then run a live test. No repeated import is needed.",
      14,SatelliteUi.MUTED,false));
    SatelliteUi.action(this,page,"USE INSTALLED LITERT MODEL",()->{
      if(!EmbeddedModelStore.installed(this)){
        setStatus("No LiteRT model installed. Use the download or import button first.");return;
      }
      LlamaCppSettings.setEnabled(this,false);
      setStatus("LiteRT selected. Your installed model is reused without another import.");
    });
    SatelliteUi.action(this,page,"USE LLAMA.CPP (TERMUX SERVER)",()->{
      LlamaCppSettings.setEnabled(this,true);
      setStatus("llama.cpp selected. Start llama-server on this phone at "
        +LlamaCppProfile.ENDPOINT+". Model weights stay in Termux; no import here.");
    });
    SatelliteUi.action(this,page,"TEST LLAMA.CPP SERVER",()->{
      if(!beginOperation("Checking local llama.cpp server..."))return;
      worker.execute(()->{
        LocalModelEngine probe=new LocalModelEngine();
        try{
          LlamaCppSettings.configure(probe);
          String reply=probe.answer(LlamaCppProfile.PROMPT_TEST);
          setStatus("Verified localhost llama.cpp response: "+reply);
        }catch(Exception e){setStatus("Local llama.cpp unavailable: "+e.getMessage());}
        finally{probe.clear();finishOperation();}
      });
    });
    SatelliteUi.action(this,page,"3  TEST LIVE OFFLINE AI",()->{
      if(!EmbeddedModelStore.installed(this)){
        setStatus("No model imported. Choose a compatible .litertlm model or ZIP first.");return;
      }
      if(!beginOperation("Testing on-device inference. No cloud or reinstallation needed."))return;
      worker.execute(()->{
        try {
          model.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath());
          String answer=model.answer("Reply with OK.");
          setStatus("Verified LIVE embedded offline model response: "+answer);
        }catch(Exception e){setStatus("Model could not load or respond: "+e.getMessage());}
        finally{finishOperation();}
      });
    });
    SatelliteUi.gap(this,page,15);
    SatelliteUi.sectionTitle(this,page,"ONE-TAP MODEL DOWNLOAD");
    page.addView(SatelliteUi.text(this,
      "Free Qwen3-0.6B INT8 (~614 MB), Apache-2.0. Tap to download from Hugging Face "
      +"over HTTPS and verify the pinned SHA-256. Wi-Fi recommended; mobile data may be charged. "
      +"Existing model is preserved if download fails. Import is not required again after an app update.",
      14,SatelliteUi.MUTED,false));
    SatelliteUi.action(this,page,"DOWNLOAD & INSTALL QWEN3-0.6B",()->{
      if(!beginOperation("Starting verified Qwen3 download..."))return;
      downloadCancelled.set(false);
      worker.execute(()->{
        try {
          model.clear();
          final int[] lastReported={-5};
          VettedModelDownloader.installQwen3(this,(received,total)->{
            int percent=(int)(received*100/total);
            if(percent>=lastReported[0]+5 || received==total) {
              lastReported[0]=percent;
              setStatus("Downloading Qwen3-0.6B: "+percent+"% ("
                +(received/(1024*1024))+" of "+(total/(1024*1024))+" MiB)");
            }
            return !downloadCancelled.get();
          });
          if(!downloadCancelled.get()){
            LlamaCppSettings.setEnabled(this,false);
            setStatus("Verified model installed and selected. Tap TEST LIVE OFFLINE AI to verify inference.");
          }else setStatus("Download cancelled. Previous model preserved.");
        }catch(Exception e){
          setStatus("Download stopped: "+e.getMessage()+". Previous model preserved.");
        }finally{finishOperation();}
      });
    });
    SatelliteUi.action(this,page,"CANCEL CURRENT DOWNLOAD",()->{
      downloadCancelled.set(true);
      setStatus("Cancelling download; previous model remains installed.");
    });
    SatelliteUi.sectionTitle(this,page,"MORE MODEL OPTIONS");
    SatelliteUi.sectionTitle(this,page,"MODEL FILES & COMPATIBILITY");
    TextView instructions=SatelliteUi.text(this,
      "Import a .litertlm model directly or a ZIP containing exactly one .litertlm model plus small README/LICENSE files. "+
      "Import installs the model data—no APK rebuild or code compilation is needed. "+
      "Compatible example: Qwen3-0.6B.litertlm (~586 MB INT8); other models may need a different runtime. "+
      "The newer dynamic INT4 version requires a newer runtime.",
      14,SatelliteUi.MUTED,false);
    page.addView(instructions);

    SatelliteUi.gap(this,page,15);
    SatelliteUi.action(this,page,"1  OPEN COMPATIBLE MODEL FILES",()->{
      Intent browser=new Intent(Intent.ACTION_VIEW,
        Uri.parse("https://huggingface.co/litert-community/Qwen3-0.6B/tree/main"));
      startActivity(browser);
    });
    SatelliteUi.action(this,page,"2  IMPORT .LITERTLM OR MODEL ZIP",()->{
      Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      picker.addCategory(Intent.CATEGORY_OPENABLE);
      picker.setType("*/*");
      startActivityForResult(picker,PICK_MODEL);
    });
    SatelliteUi.action(this,page,"REMOVE INSTALLED MODEL",()->{
      new android.app.AlertDialog.Builder(this).setTitle("Remove offline model?")
        .setMessage("The imported weights will be deleted from app-private storage. AI diagnostic rules will still work.")
        .setNegativeButton("KEEP",null)
        .setPositiveButton("REMOVE",(dialog,which)->{
          if(!beginOperation("Removing the installed model..."))return;
          worker.execute(()->{
            try {
              model.clear();
              boolean removed=EmbeddedModelStore.delete(this);
              setStatus(removed?"Model removed. Diagnostics remain available.":"Could not delete model file.");
            }catch(Exception e){setStatus("Remove failed: "+e.getMessage());}
            finally{finishOperation();}
          });
        }).show();
    });
    SatelliteUi.backHome(this,page);
    refreshModelStatus();
  }

  private void refreshModelStatus(){
    if(LlamaCppSettings.enabled(this)) {
      setStatus("llama.cpp is selected. Start and test your Termux localhost server.");
      return;
    }
    if(EmbeddedModelStore.installed(this)){
      long mib=EmbeddedModelStore.modelFile(this).length()/(1024L*1024L);
      setStatus("Imported file found ("+mib+" MiB). Inference NOT verified on this phone; tap Test Live Offline AI.");
    }else setStatus("No embedded offline model imported. AI diagnostics remain available.");
  }
  private boolean beginOperation(String message){
    if(operationRunning){
      setStatus("Another model operation is still running. Complete that operation first.");
      return false;
    }
    operationRunning=true;
    operationProgress.setVisibility(android.view.View.VISIBLE);
    setStatus(message);
    return true;
  }
  private void finishOperation(){
    runOnUiThread(()->{
      operationRunning=false;
      if(operationProgress!=null)operationProgress.setVisibility(android.view.View.GONE);
    });
  }
  private void setStatus(String message){
    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(message);});
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=PICK_MODEL||result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri selected=data.getData();
    if(!beginOperation("Importing and validating the model locally. Previous model is kept if import fails."))return;
    worker.execute(()->{
      try{
        model.clear();
        long bytes=EmbeddedModelStore.importFromDocument(this,selected);
        LlamaCppSettings.setEnabled(this,false);
        setStatus("Model installed ("+(bytes/(1024L*1024L))+" MiB). No APK compilation needed. Tap TEST LIVE OFFLINE AI to verify compatibility.");
      }catch(Exception e){
        setStatus("Import failed: "+e.getMessage()+
          (EmbeddedModelStore.installed(this)?". Previously imported model was preserved.":""));
      }finally{finishOperation();}
    });
  }
  @Override protected void onDestroy(){
    downloadCancelled.set(true);
    if(!worker.isShutdown()){
      worker.execute(()->model.clear());
      worker.shutdown();
    }
    super.onDestroy();
  }
}
