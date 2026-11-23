package com.northerncircuitstudios.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device model catalog, system-managed GGUF downloads and local router picker.
 * This activity neither accesses Termux private storage nor silently runs shell commands.
 */
public final class ModelsActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(12,18,32);
    private static final int PANEL = Color.rgb(25,35,53);
    private static final int FOREGROUND = Color.rgb(241,246,252);
    private static final int MUTED = Color.rgb(165,184,200);
    private static final int ACCENT = Color.rgb(65,199,186);
    private static final String DOWNLOAD_FOLDER = "NorthernCircuitAI";
    private static final String ROUTER_COMMAND =
        "llama-server --models-dir \"$HOME/storage/downloads/NorthernCircuitAI\" " +
        "--host 127.0.0.1 --port 9931 -c 2048 -t 4 --models-max 1";
    private static final String INIT_COMMAND = "termux-setup-storage";
    private static final String PREFS = "northern_ai_model_downloads_v1";

    private static final class OfferedModel {
        final String name, filename, url, displaySize, note;
        final long approximateBytes;
        OfferedModel(String name, String filename, String url, String size,
                     long approximateBytes, String note) {
            this.name=name; this.filename=filename; this.url=url;
            this.displaySize=size; this.approximateBytes=approximateBytes; this.note=note;
        }
    }

    // Explicit allowlist; these are GGUF downloads, not packages or executables.
    private static final OfferedModel[] CATALOG = {
        new OfferedModel("Qwen3.5 4B • Q4_K_M",
            "Qwen3.5-4B-Q4_K_M.gguf",
            "https://huggingface.co/ggml-org/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            "about 3 GB", 3000000000L, "Primary S25 assistant"),
        new OfferedModel("Qwen3.5 2B • Q4_K_M",
            "qwen3.5-2b-Q4_K_M.gguf",
            "https://huggingface.co/gguf/Qwen3.5-2B-GGUF/resolve/main/qwen3.5-2b-Q4_K_M.gguf",
            "about 1.5 GB", 1500000000L, "Lighter fallback"),
        new OfferedModel("Qwen3 0.6B • Q4_0",
            "Qwen3-0.6B-Q4_0.gguf",
            "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_0.gguf",
            "about 400 MB", 400000000L, "Low-resource option")
    };

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final LocalModelRegistry registry = new LocalModelRegistry();
    private boolean destroyed;
    private LinearLayout content;
    private TextView inventoryStatus;
    private TextView selectedStatus;
    private final List<TextView> downloadStatuses = new ArrayList<>();

    private int dp(float n) {
        return (int)(n*getResources().getDisplayMetrics().density+0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private TextView text(String content, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(content); t.setTextSize(sp); t.setTextColor(color);
        t.setPadding(dp(5), dp(6), dp(5), dp(6)); return t;
    }

    private Button button(String caption) {
        Button b = new Button(this); b.setText(caption); b.setAllCaps(false);
        b.setTextColor(FOREGROUND); b.setTextSize(12);
        b.setMinHeight(dp(44)); b.setBackground(shape(PANEL,10)); return b;
    }

    private void addButtonRow(LinearLayout group, Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button button : buttons) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(47), 1);
            p.setMargins(dp(2),0,dp(2),0);
            row.addView(button,p);
        }
        group.addView(row);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(18),dp(16),dp(24));
        scroll.addView(content);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((v,insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                content.setPadding(dp(16), dp(18)+bars.top, dp(16), dp(24)+bars.bottom);
                return insets;
            });
            scroll.requestApplyInsets();
        }

        TextView title = text("LOCAL AI MODELS",24,FOREGROUND);
        title.setTypeface(null,1); content.addView(title);
        content.addView(text(
            "Download GGUF models, check progress and choose which model your local " +
            "llama.cpp server should use. Cloud AI is not changed.",13,MUTED));

        Button back=button("← BACK TO CHAT");
        back.setOnClickListener(v -> finish());
        content.addView(back);
        selectedStatus=text("",14,ACCENT);
        content.addView(selectedStatus);
        updateSelectedStatus();

        TextView section=text("AVAILABLE ON LOCAL SERVER",17,FOREGROUND);
        section.setTypeface(null,1); content.addView(section);
        inventoryStatus=text(
            "Tap SCAN SERVER. Only models the server actually exposes can be selected.",
            13,MUTED);
        content.addView(inventoryStatus);
        Button scan=button("SCAN & SELECT LOCAL MODEL");
        scan.setOnClickListener(v -> scanModels());
        content.addView(scan);

        TextView dlHeading=text("DOWNLOAD MODELS",17,FOREGROUND);
        dlHeading.setTypeface(null,1);
        dlHeading.setPadding(dp(5),dp(22),dp(5),dp(7));
        content.addView(dlHeading);
        content.addView(text(
            "Tap DOWNLOAD to use Android's Download Manager. Files are saved to " +
            "Downloads/NorthernCircuitAI. Only download models you need; a Qwen model " +
            "already cached by Termux does not need to be downloaded twice.",13,MUTED));

        for (OfferedModel model : CATALOG) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(10),dp(12),dp(10));
            card.setBackground(shape(PANEL,13));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,dp(9),0,dp(5));
            content.addView(card,lp);
            TextView modelName=text(model.name,16,FOREGROUND);
            modelName.setTypeface(null,1); card.addView(modelName);
            card.addView(text(model.displaySize+" • "+model.note,13,MUTED));
            TextView stateLabel=text("Checking download status...",12,ACCENT);
            card.addView(stateLabel);
            downloadStatuses.add(stateLabel);
            Button download=button("DOWNLOAD");
            Button source=button("MODEL SOURCE");
            download.setOnClickListener(v -> confirmDownload(model));
            source.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(model.url.substring(0,model.url.indexOf("/resolve/"))))));
            addButtonRow(card,download,source);
        }

        Button refresh = button("REFRESH DOWNLOAD PROGRESS");
        refresh.setOnClickListener(v -> refreshDownloads());
        content.addView(refresh);

        TextView setupTitle=text("USE MODELS WITH TERMUX",17,FOREGROUND);
        setupTitle.setTypeface(null,1);
        setupTitle.setPadding(dp(5),dp(24),dp(5),dp(6));
        content.addView(setupTitle);
        content.addView(text(
            "1. If this is your first shared-storage setup, copy and run " +
            "'termux-setup-storage' in Termux, then grant storage access.\n" +
            "2. Finish downloading the GGUF models here.\n" +
            "3. Stop any running llama-server with Ctrl+C.\n" +
            "4. Copy the router command below and paste it into Termux. " +
            "Leave Termux running.\n" +
            "5. Return here, SCAN SERVER and select a listed model. " +
            "A downloaded file is not active until llama.cpp can load it.",
            13,MUTED));
        Button copyStorage=button("COPY STORAGE SETUP");
        copyStorage.setOnClickListener(v -> copy(INIT_COMMAND,
            "Storage setup copied. Paste it in Termux."));
        Button copyRouter=button("COPY ROUTER START COMMAND");
        copyRouter.setOnClickListener(v -> copy(ROUTER_COMMAND,
            "Router command copied. Paste it in Termux."));
        content.addView(copyStorage);
        content.addView(copyRouter);
        refreshDownloads();
    }

    private void copy(String value, String result) {
        ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(manager!=null) manager.setPrimaryClip(ClipData.newPlainText("Termux command",value));
        inventoryStatus.setText(result);
    }

    private SharedPreferences downloadPrefs() {
        return getSharedPreferences(PREFS,MODE_PRIVATE);
    }

    private long getDownloadId(OfferedModel model) {
        return downloadPrefs().getLong(model.filename,-1L);
    }

    private void confirmDownload(OfferedModel model) {
        long existing=getDownloadId(model);
        if(existing>0) {
            String state=downloadState(existing);
            if(state.startsWith("Downloading") || state.startsWith("Queued") ||
                state.startsWith("Download completed")) {
                inventoryStatus.setText("An existing download is recorded. Refresh progress.");
                refreshDownloads();
                return;
            }
        }
        long free=0;
        try {
            StatFs fs = new StatFs(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            free=fs.getAvailableBytes();
        } catch(Exception ignored) { }
        final String warning=free>0 && free<model.approximateBytes+350000000L
            ? "\n\nLow available storage detected. Free space before downloading."
            : "";
        new AlertDialog.Builder(this)
            .setTitle("Download "+model.name+"?")
            .setMessage("Approximate model size: "+model.displaySize+
                "\nDestination: Downloads/NorthernCircuitAI/"+model.filename+
                "\n\nThis large download may consume mobile data. " +
                "Use Wi-Fi if possible. Downloading does not automatically " +
                "activate the model in Termux." + warning)
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("DOWNLOAD", (dialog,which) -> queue(model))
            .show();
    }

    private void queue(OfferedModel model) {
        // Trusted static Hugging Face URLs only, no user-configurable sources.
        if(!model.url.startsWith("https://huggingface.co/")) return;
        try {
            DownloadManager downloads=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            if(downloads==null) throw new IllegalStateException("Android Download Manager unavailable");
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(model.url));
            request.setTitle("Northern Circuit AI • "+model.name);
            request.setDescription("GGUF model file");
            request.setMimeType("application/octet-stream");
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                DOWNLOAD_FOLDER+"/"+model.filename);
            long id=downloads.enqueue(request);
            downloadPrefs().edit().putLong(model.filename,id).apply();
            inventoryStatus.setText("Download queued. Open Android downloads for notifications.");
            refreshDownloads();
        } catch(Exception error) {
            inventoryStatus.setText("Download could not start. Check storage space and permissions.");
        }
    }

    private String downloadState(long id) {
        DownloadManager manager=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        if(manager==null) return "Download Manager unavailable";
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))) {
            if(cursor==null || !cursor.moveToFirst()) return "Download not found in system history";
            int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long total=cursor.getLong(cursor.getColumnIndexOrThrow(
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            long done=cursor.getLong(cursor.getColumnIndexOrThrow(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            if(status==DownloadManager.STATUS_SUCCESSFUL)
                return "Download completed • GGUF not yet verified or loaded";
            if(status==DownloadManager.STATUS_FAILED)
                return "Download failed • Check network and space, then retry";
            if(status==DownloadManager.STATUS_PAUSED)
                return "Paused • Android may resume when conditions allow";
            if(status==DownloadManager.STATUS_PENDING) return "Queued by Android Download Manager";
            if(total>0) return "Downloading • "+(100*done/total)+"%  ("+
                (done/1000000L)+" / "+(total/1000000L)+" MB)";
            return "Downloading • "+(done/1000000L)+" MB received";
        } catch(Exception error) { return "Download progress not available"; }
    }

    private void refreshDownloads() {
        for(int i=0;i<CATALOG.length;i++) {
            long id=getDownloadId(CATALOG[i]);
            downloadStatuses.get(i).setText(id<0 ? "Not downloaded by this app" :
                downloadState(id));
        }
    }

    private void updateSelectedStatus() {
        String saved=LocalModelPreferences.load(this);
        selectedStatus.setText(saved.isEmpty()
            ? "Selected local model: server default (flipper-local)"
            : "Selected local model: "+saved);
    }

    private void scanModels() {
        inventoryStatus.setText("Checking local llama.cpp models on 127.0.0.1:9931...");
        background.execute(() -> {
            LocalModelRegistry.Inventory result=null;
            try { result=registry.discover(); } catch(Exception ignored) { }
            final LocalModelRegistry.Inventory inventory=result;
            runOnUiThread(() -> {
                if(destroyed) return;
                if(inventory==null) {
                    inventoryStatus.setText("No local server inventory available. " +
                        "Start llama-server in Termux and try again.");
                    return;
                }
                if(inventory.items.isEmpty()) {
                    inventoryStatus.setText("Server reachable but reports no models.");
                    return;
                }
                final ArrayList<String> choices=new ArrayList<>();
                choices.add("Server default (flipper-local)");
                for(LocalModelRegistry.Item item : inventory.items)
                    choices.add(item.id+"  • "+item.status);
                new AlertDialog.Builder(this)
                    .setTitle(inventory.router ? "Select router model" : "Select local model")
                    .setItems(choices.toArray(new String[0]), (d,index) -> {
                        String selected=index==0 ? "" : inventory.items.get(index-1).id;
                        LocalModelPreferences.save(this,selected);
                        updateSelectedStatus();
                        inventoryStatus.setText(selected.isEmpty()
                            ? "Using server default model."
                            : "Selected "+selected+
                                " • The server must successfully load it on your next request.");
                    })
                    .setNegativeButton("CANCEL",null)
                    .show();
                inventoryStatus.setText(inventory.items.size()+" model(s) reported by server. " +
                    "Tap SCAN SERVER to change your selection.");
            });
        });
    }

    @Override protected void onDestroy() {
        destroyed=true;
        registry.cancel();
        background.shutdownNow();
        super.onDestroy();
    }
}
