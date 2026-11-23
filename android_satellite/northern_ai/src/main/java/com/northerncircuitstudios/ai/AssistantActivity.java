package com.northerncircuitstudios.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowInsets;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent local-first Northern Circuit AI interface.
 *
 * Requires the user to run a compatible llama.cpp model server in Termux.
 * No cloud access, no subscriptions, no Bluetooth/FAP modification.
 */
public final class AssistantActivity extends Activity {
    private static final int BG = Color.rgb(12, 18, 32);
    private static final int PANEL = Color.rgb(25, 35, 53);
    private static final int TEXT = Color.rgb(242, 246, 253);
    private static final int MUTED = Color.rgb(160, 176, 195);
    private static final int ACCENT = Color.rgb(65, 199, 186);
    private static final String SETUP_COMMAND =
        "llama-server -hf ggml-org/Qwen3.5-4B-GGUF:Q4_K_M " +
        "--alias flipper-local --host 127.0.0.1 --port 9931 -c 2048 -t 4";
    private static final String GUIDE_URL =
        "https://github.com/cruefan94/Flipper-AI-Android-Satellite/blob/" +
        "feature/northern-circuit-ai-profile-v01-20261122/" +
        "docs/NORTHERN_CIRCUIT_AI_S25_QWEN35.md";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LocalAiClient client = new LocalAiClient();
    private final ArrayList<AssistantPolicy.Entry> history = new ArrayList<>();
    private TextView status;
    private TextView transcript;
    private EditText input;
    private Button send;
    private ScrollView conversationScroll;
    private boolean busy = true;
    private boolean closing = false;
    private int generation = 0;

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private Button button(String title, int color) {
        Button result = new Button(this);
        result.setText(title);
        result.setAllCaps(false);
        result.setTextColor(TEXT);
        result.setTextSize(13);
        result.setMinHeight(dp(48));
        result.setPadding(dp(8), dp(6), dp(8), dp(6));
        result.setBackground(background(color, 12));
        return result;
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(14), dp(16), dp(14), dp(12));
        screen.setBackgroundColor(BG);
        setContentView(screen);
        // Android 15+ enforces edge-to-edge at targetSdk 35. Own the system
        // bars and keyboard insets to keep chat and SEND visible on the S25.
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                int top = dp(16) + Math.max(0, bars.top);
                int bottom = dp(12) + Math.max(bars.bottom, keyboard.bottom);
                if (view.getPaddingTop() != top || view.getPaddingBottom() != bottom)
                    view.setPadding(dp(14), top, dp(14), bottom);
                return insets;
            });
            screen.requestApplyInsets();
        }

        screen.addView(label("NORTHERN CIRCUIT AI", 24, TEXT, true));
        TextView tagline = label("YOUR PRIVATE, LOCAL AI  •  GALAXY S25", 11, ACCENT, true);
        tagline.setPadding(0, dp(4), 0, dp(9));
        screen.addView(tagline);
        status = label("Loading encrypted conversation…", 12, MUTED, false);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(background(PANEL, 9));
        screen.addView(status);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(9), 0, dp(7));
        screen.addView(tools);
        Button check = button("CHECK AI", PANEL);
        Button setup = button("SETUP", PANEL);
        Button clear = button("CLEAR CHAT", PANEL);
        for (Button control : new Button[]{check, setup, clear}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            p.setMargins(dp(2), 0, dp(2), 0);
            tools.addView(control, p);
        }
        check.setOnClickListener(v -> checkServer());
        setup.setOnClickListener(v -> showSetup());
        clear.setOnClickListener(v -> confirmClear());

        conversationScroll = new ScrollView(this);
        conversationScroll.setFillViewport(true);
        conversationScroll.setBackground(background(PANEL, 14));
        transcript = label("Your conversations stay on this device.\n\n" +
            "Start the local model in Termux, then ask me anything.", 15, TEXT, false);
        transcript.setPadding(dp(15), dp(16), dp(15), dp(16));
        transcript.setLineSpacing(dp(4), 1.02f);
        transcript.setTextIsSelectable(true);
        conversationScroll.addView(transcript);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(-1, 0, 1);
        logParams.setMargins(0, 0, 0, dp(10));
        screen.addView(conversationScroll, logParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        screen.addView(composer);
        input = new EditText(this);
        input.setHint("Ask Northern Circuit AI…");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setFilters(new InputFilter[]{
            new InputFilter.LengthFilter(AssistantPolicy.MAX_INPUT_CHARS)
        });
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(background(PANEL, 11));
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        send = button("SEND", ACCENT);
        send.setTextColor(BG);
        send.setEnabled(false);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(80), dp(54));
        sendParams.setMargins(dp(7), 0, 0, 0);
        composer.addView(send, sendParams);
        send.setOnClickListener(v -> ask());
        worker.execute(() -> {
            try {
                List<AssistantPolicy.Entry> previous = EncryptedChatStore.load(this);
                runOnUiThread(() -> {
                    if (closing) return;
                    history.addAll(previous);
                    renderHistory();
                    setStatus("OFFLINE • Local llama.cpp model required • Cloud OFF");
                    busy = false;
                    send.setEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (closing) return;
                    busy = false;
                    send.setEnabled(false);
                    setStatus("Encrypted history cannot be opened. Use CLEAR CHAT to reset.");
                });
            }
        });
    }

    private void setStatus(String message) {
        if (!closing && status != null) status.setText(message);
    }

    private void renderHistory() {
        if (history.isEmpty()) {
            transcript.setText("Your conversations stay on this device.\n\n" +
                "Start the local model in Termux, then ask me anything.");
            return;
        }
        StringBuilder out = new StringBuilder();
        for (AssistantPolicy.Entry item : history) {
            out.append("user".equals(item.role) ? "YOU" : "NORTHERN CIRCUIT AI");
            out.append("\n").append(item.text).append("\n\n");
        }
        transcript.setText(out.toString());
        conversationScroll.post(() -> conversationScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void checkServer() {
        if (busy || closing) return;
        busy = true;
        send.setEnabled(false);
        setStatus("Checking model server on this phone only…");
        worker.execute(() -> {
            boolean reachable = false;
            try { reachable = client.serverReachable(); }
            catch (Exception ignored) { /* Never print private networking diagnostics. */ }
            final boolean available = reachable;
            runOnUiThread(() -> {
                if (closing) return;
                setStatus(available
                    ? "Local server reachable. Send a question to verify model answers."
                    : "Local server unavailable. Tap SETUP to start Qwen3.5 in Termux.");
                busy = false;
                send.setEnabled(true);
            });
        });
    }

    private void ask() {
        if (busy || closing) return;
        String question = input.getText().toString().trim();
        try { AssistantPolicy.validateInput(question); }
        catch (Exception error) {
            setStatus(error.getMessage());
            return;
        }
        input.setText("");
        history.add(new AssistantPolicy.Entry("user", question));
        renderHistory();
        final ArrayList<AssistantPolicy.Entry> snapshot =
            new ArrayList<>(AssistantPolicy.boundedHistory(history));
        final int request = ++generation;
        busy = true;
        send.setEnabled(false);
        setStatus("Thinking locally • No cloud transmission…");
        worker.execute(() -> {
            String reply = null;
            String errorMessage = null;
            String storageError = null;
            try {
                EncryptedChatStore.save(this, snapshot);
                reply = client.reply(snapshot);
                snapshot.add(new AssistantPolicy.Entry("assistant", reply));
                EncryptedChatStore.save(this, snapshot);
            } catch (Exception ex) {
                errorMessage = ex instanceof java.net.ConnectException
                    ? "Local AI not running. Open Termux and start the model."
                    : ex instanceof java.net.SocketTimeoutException
                        ? "Local model took too long. Try the 2B fallback."
                        : ex instanceof IllegalStateException
                            ? ex.getMessage()
                            : "Local AI could not answer. Check that the model is running.";
            }
            final String answer = reply;
            final String problem = errorMessage;
            runOnUiThread(() -> {
                if (closing || request != generation) return;
                if (answer != null) {
                    history.add(new AssistantPolicy.Entry("assistant", answer));
                    setStatus("Verified model response • OFFLINE • Stored on device");
                } else {
                    setStatus(problem == null ? "Local AI could not answer." : problem);
                }
                renderHistory();
                busy = false;
                send.setEnabled(true);
            });
        });
    }

    private void confirmClear() {
        if (busy || closing) return;
        new AlertDialog.Builder(this)
            .setTitle("Clear your encrypted conversation?")
            .setMessage("This erases the saved messages from Northern Circuit AI on this device.")
            .setNegativeButton("KEEP", null)
            .setPositiveButton("CLEAR", (dialog, which) -> {
                ++generation;
                busy = true;
                send.setEnabled(false);
                worker.execute(() -> {
                    try {
                        EncryptedChatStore.clear(this);
                        runOnUiThread(() -> {
                            if (closing) return;
                            history.clear();
                            renderHistory();
                            setStatus("Encrypted chat cleared • OFFLINE");
                            busy = false;
                            send.setEnabled(true);
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> {
                            if (closing) return;
                            setStatus("Unable to clear encrypted chat");
                            busy = false;
                            send.setEnabled(false);
                        });
                    }
                });
            }).show();
    }

    private void showSetup() {
        new AlertDialog.Builder(this)
            .setTitle("Start your offline AI")
            .setMessage("1. Install official Termux and its llama.cpp package.\n" +
                "2. Copy the start command and paste it into Termux.\n" +
                "3. Keep Termux running while chatting.\n" +
                "4. Return here and tap CHECK AI.\n\n" +
                "Default: Qwen3.5-4B Q4; RAM Plus testing starts at 8 GB.\n\n" +
                SETUP_COMMAND)
            .setNegativeButton("CLOSE", null)
            .setNeutralButton("VIEW GUIDE", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL));
                startActivity(intent);
            })
            .setPositiveButton("COPY COMMAND", (dialog, which) -> {
                ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (manager != null)
                    manager.setPrimaryClip(ClipData.newPlainText("Qwen3.5 local launch", SETUP_COMMAND));
                setStatus("Launch command copied. Paste it into Termux.");
            }).show();
    }

    @Override protected void onDestroy() {
        closing = true;
        ++generation;
        client.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }
}
