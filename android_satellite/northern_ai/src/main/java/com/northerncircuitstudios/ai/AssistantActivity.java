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
 * Local llama.cpp by default; optional, user-authorized Cloudflare/Gemini mode.
 * No subscription, Bluetooth/FAP modification or autonomous hardware control.
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
    private final CloudAiClient cloudClient = new CloudAiClient();
    private CloudSettingsStore.Settings cloudSettings =
        new CloudSettingsStore.Settings(CloudModePolicy.Mode.OFFLINE, "");
    private Button modeButton;
    private final ArrayList<AssistantPolicy.Entry> history = new ArrayList<>();
    private TextView status;
    private LinearLayout chatMessages;
    private TextView tagline;
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
        tagline = label("PRIVATE ON-DEVICE AI  •  GALAXY S25", 11, ACCENT, true);
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
        Button check = button("CHECK LOCAL", PANEL);
        Button setup = button("LOCAL SETUP", PANEL);
        Button clear = button("CLEAR CHAT", PANEL);
        for (Button control : new Button[]{check, setup, clear}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            p.setMargins(dp(2), 0, dp(2), 0);
            tools.addView(control, p);
        }
        check.setOnClickListener(v -> checkServer());
        setup.setOnClickListener(v -> showSetup());
        clear.setOnClickListener(v -> confirmClear());

        // Cloud controls are always visible, even when the token is not configured.
        LinearLayout cloudTools = new LinearLayout(this);
        cloudTools.setOrientation(LinearLayout.HORIZONTAL);
        cloudTools.setPadding(0, 0, 0, dp(9));
        screen.addView(cloudTools);
        modeButton = button("MODE: OFFLINE", PANEL);
        Button cloudSetup = button("CLOUD SETUP", PANEL);
        Button cloudTest = button("TEST CLOUD", PANEL);
        for (Button control : new Button[]{modeButton, cloudSetup, cloudTest}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            p.setMargins(dp(2), 0, dp(2), 0);
            cloudTools.addView(control, p);
        }
        modeButton.setOnClickListener(v -> chooseMode());
        cloudSetup.setOnClickListener(v -> showCloudSetup());
        cloudTest.setOnClickListener(v -> testCloud());

        conversationScroll = new ScrollView(this);
        conversationScroll.setFillViewport(true);
        conversationScroll.setBackground(background(PANEL, 14));
        chatMessages = new LinearLayout(this);
        chatMessages.setOrientation(LinearLayout.VERTICAL);
        chatMessages.setPadding(dp(10), dp(10), dp(10), dp(10));
        conversationScroll.addView(chatMessages);
        renderHistory();
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
                CloudSettingsStore.Settings loaded;
                boolean settingsCorrupted = false;
                try { loaded = CloudSettingsStore.load(this); }
                catch (Exception error) {
                    loaded = new CloudSettingsStore.Settings(CloudModePolicy.Mode.OFFLINE, "");
                    settingsCorrupted = true;
                }
                final CloudSettingsStore.Settings storedSettings = loaded;
                final boolean needsCloudReset = settingsCorrupted;
                runOnUiThread(() -> {
                    if (closing) return;
                    cloudSettings = storedSettings;
                    updateModeButton();
                    history.addAll(previous);
                    renderHistory();
                    setStatus(needsCloudReset
                        ? "Cloud settings unreadable. Tap CLOUD SETUP to remove and reset."
                        : "Ready • " + cloudSettings.mode.name() +
                          " • " + (cloudSettings.mode == CloudModePolicy.Mode.OFFLINE
                              ? "Private local default" : "Cloud transmission enabled"));
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
        if (chatMessages == null) return;
        chatMessages.removeAllViews();
        if (history.isEmpty()) {
            TextView placeholder = label(
                "Your encrypted conversations are stored on this phone.\n\n" +
                "Choose OFFLINE for a local model or CLOUD for your Gemini Worker.",
                15, TEXT, false);
            placeholder.setPadding(dp(8), dp(10), dp(8), dp(10));
            chatMessages.addView(placeholder);
            return;
        }
        for (AssistantPolicy.Entry entry : history) {
            boolean isUser = "user".equals(entry.role);
            LinearLayout messageGroup = new LinearLayout(this);
            messageGroup.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams groupLayout =
                new LinearLayout.LayoutParams(-1, -2);
            groupLayout.setMargins(dp(3), dp(5), dp(3), dp(9));
            chatMessages.addView(messageGroup, groupLayout);

            TextView role = label(isUser ? "YOU" : "NORTHERN CIRCUIT AI",
                11, isUser ? ACCENT : MUTED, true);
            role.setPadding(dp(8), 0, 0, dp(5));
            messageGroup.addView(role);

            TextView message = label("", 15, TEXT, false);
            message.setText(ChatMarkdown.render(entry.text));
            message.setLineSpacing(dp(4), 1.06f);
            message.setPadding(dp(13), dp(13), dp(13), dp(13));
            message.setBackground(background(
                isUser ? Color.rgb(32, 57, 73) : Color.rgb(32, 43, 63), 11));
            message.setTextIsSelectable(true);
            messageGroup.addView(message, new LinearLayout.LayoutParams(-1, -2));
        }
        conversationScroll.post(() -> {
            if (!closing) conversationScroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    private void checkServer() {
        if (busy || closing) return;
        busy = true;
        send.setEnabled(false);
        setStatus("Checking local model server on this phone only…");
        worker.execute(() -> {
            boolean reachable = false;
            try { reachable = client.serverReachable(); }
            catch (Exception ignored) { /* Never print private networking diagnostics. */ }
            final boolean available = reachable;
            runOnUiThread(() -> {
                if (closing) return;
                setStatus(available
                    ? "Local server reachable. Send a question to verify model answers."
                    : "Local server unavailable. Tap LOCAL SETUP to start Qwen3.5 in Termux.");
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
        final CloudModePolicy.Mode mode = cloudSettings.mode;
        final String token = cloudSettings.token;
        if (mode != CloudModePolicy.Mode.OFFLINE &&
            !CloudModePolicy.maySendToCloud(mode, token)) {
            setStatus("Cloud token missing. Tap CLOUD SETUP or switch to OFFLINE.");
            return;
        }
        if (mode == CloudModePolicy.Mode.CLOUD) {
            try { CloudModePolicy.validateCloudPrompt(question); }
            catch (IllegalArgumentException error) { setStatus(error.getMessage()); return; }
        }

        input.setText("");
        history.add(new AssistantPolicy.Entry("user", question));
        renderHistory();
        final ArrayList<AssistantPolicy.Entry> snapshot =
            new ArrayList<>(AssistantPolicy.boundedHistory(history));
        final int request = ++generation;
        busy = true;
        send.setEnabled(false);
        setStatus(mode == CloudModePolicy.Mode.OFFLINE
            ? "Thinking locally • No cloud transmission…"
            : mode == CloudModePolicy.Mode.CLOUD
                ? "Cloud request • Sending current message only…"
                : "Hybrid • Trying local model; cloud fallback enabled…");
        worker.execute(() -> {
            String reply = null;
            String errorMessage = null;
            String source = "";
            try {
                // Storage failure must never trigger a cloud request.
                EncryptedChatStore.save(this, snapshot);
                if (mode == CloudModePolicy.Mode.CLOUD) {
                    reply = cloudClient.reply(question, token);
                    source = "CLOUD";
                } else if (mode == CloudModePolicy.Mode.HYBRID) {
                    try {
                        reply = client.reply(snapshot);
                        source = "LOCAL";
                    } catch (Exception localFailure) {
                        // Selected HYBRID explicitly authorizes the current prompt
                        // (never previous turns) to be transmitted if local fails.
                        reply = cloudClient.reply(question, token);
                        source = "CLOUD FALLBACK";
                    }
                } else {
                    reply = client.reply(snapshot);
                    source = "OFFLINE";
                }
                snapshot.add(new AssistantPolicy.Entry("assistant", reply));
                EncryptedChatStore.save(this, snapshot);
            } catch (Exception ex) {
                errorMessage = ex instanceof java.net.ConnectException
                    ? (mode == CloudModePolicy.Mode.OFFLINE
                        ? "Local AI not running. Open Termux and start the model."
                        : "Network connection failed. Check the selected AI backend.")
                    : ex instanceof java.net.SocketTimeoutException
                        ? "AI response timed out. Check the model or network."
                        : ex instanceof IllegalArgumentException ||
                          ex instanceof IllegalStateException
                            ? ex.getMessage()
                            : "AI could not answer. Check model and cloud setup.";
            }
            final String answer = reply;
            final String problem = errorMessage;
            final String finalSource = source;
            runOnUiThread(() -> {
                if (closing || request != generation) return;
                if (answer != null) {
                    history.add(new AssistantPolicy.Entry("assistant", answer));
                    setStatus("AI replied via " + finalSource + " • Encrypted local history");
                } else {
                    setStatus(problem == null ? "AI could not answer." : problem);
                }
                renderHistory();
                busy = false;
                send.setEnabled(true);
            });
        });
    }

    private void updateModeButton() {
        if (modeButton != null)
            modeButton.setText("MODE: " + cloudSettings.mode.name());
        if (tagline != null) {
            String message = cloudSettings.mode == CloudModePolicy.Mode.OFFLINE
                ? "PRIVATE ON-DEVICE AI  •  GALAXY S25"
                : cloudSettings.mode == CloudModePolicy.Mode.CLOUD
                    ? "GEMINI CLOUD AI  •  REQUESTS SENT ONLINE"
                    : "LOCAL FIRST  •  CLOUD FALLBACK ENABLED";
            tagline.setText(message);
        }
    }

    private void saveMode(CloudModePolicy.Mode next) {
        if (busy || closing) return;
        final CloudSettingsStore.Settings updated =
            new CloudSettingsStore.Settings(next, cloudSettings.token);
        busy = true;
        send.setEnabled(false);
        worker.execute(() -> {
            String problem = null;
            try { CloudSettingsStore.save(this, updated); }
            catch (Exception error) { problem = "Could not save AI mode."; }
            final String finalProblem = problem;
            runOnUiThread(() -> {
                if (closing) return;
                if (finalProblem == null) {
                    cloudSettings = updated;
                    updateModeButton();
                    setStatus("AI mode: " + next.name() +
                        (next == CloudModePolicy.Mode.OFFLINE
                          ? " • Cloud transmission disabled"
                          : " • Cloud transmission authorized"));
                } else setStatus(finalProblem);
                busy = false;
                send.setEnabled(true);
            });
        });
    }

    private void chooseMode() {
        if (busy || closing) return;
        String[] choices = {
            "OFFLINE — only local model (default)",
            "CLOUD — send each prompt to Gemini Worker",
            "HYBRID — local first; send prompt to cloud if local fails"
        };
        int selected = cloudSettings.mode.ordinal();
        new AlertDialog.Builder(this)
            .setTitle("Choose AI mode")
            .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                dialog.dismiss();
                CloudModePolicy.Mode chosen = CloudModePolicy.Mode.values()[which];
                if (chosen == CloudModePolicy.Mode.OFFLINE) { saveMode(chosen); return; }
                if (!CloudModePolicy.maySendToCloud(chosen, cloudSettings.token)) {
                    setStatus("Set the Worker device token in CLOUD SETUP first.");
                    showCloudSetup();
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Enable " + chosen.name() + " cloud requests?")
                    .setMessage(chosen == CloudModePolicy.Mode.CLOUD
                        ? "Every question will be sent over HTTPS to your Cloudflare Worker " +
                          "and Gemini. Previous chat messages are not transmitted. " +
                          "Cloud service policies and charges may apply."
                        : "When the local model fails, the CURRENT question is " +
                          "automatically sent over HTTPS to your Cloudflare Worker " +
                          "and Gemini. Previous chat messages are not transmitted. " +
                          "Cloud service policies and charges may apply.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("I AGREE", (confirm, decision) -> saveMode(chosen))
                    .show();
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void showCloudSetup() {
        if (busy || closing) return;
        final EditText tokenField = new EditText(this);
        tokenField.setSingleLine(true);
        tokenField.setHint("Worker DEVICE_TOKEN (not Gemini API key)");
        tokenField.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        tokenField.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(8), dp(15), 0);
        TextView note = label("Worker: " + CloudModePolicy.WORKER_ORIGIN +
            "\n\nEnter the DEVICE_TOKEN configured in Cloudflare Worker secrets. " +
            "Never enter your Gemini API key here. The token is encrypted on this phone. " +
            "Saving a token alone does NOT enable cloud requests. " +
            "\n\n" + (cloudSettings.token.isEmpty()
                ? "No device token saved." : "A device token is saved (not displayed)."),
            12, MUTED, false);
        content.addView(note);
        content.addView(tokenField);
        new AlertDialog.Builder(this)
            .setTitle("Cloud AI setup")
            .setView(content)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("REMOVE TOKEN", (dialog, which) -> removeCloudToken())
            .setPositiveButton("SAVE TOKEN", (dialog, which) -> {
                String token = tokenField.getText().toString().trim();
                try { CloudModePolicy.validateToken(token); }
                catch (Exception error) { setStatus(error.getMessage()); return; }
                if (busy || closing) return;
                busy = true;
                send.setEnabled(false);
                CloudSettingsStore.Settings updated =
                    new CloudSettingsStore.Settings(CloudModePolicy.Mode.OFFLINE, token);
                worker.execute(() -> {
                    String problem = null;
                    try { CloudSettingsStore.save(this, updated); }
                    catch (Exception error) { problem = "Failed to save cloud token."; }
                    final String finalProblem = problem;
                    runOnUiThread(() -> {
                        if (closing) return;
                        if (finalProblem == null) {
                            cloudSettings = updated;
                            updateModeButton();
                            setStatus("Cloud token encrypted. Tap TEST CLOUD, then choose MODE.");
                        } else setStatus(finalProblem);
                        busy = false;
                        send.setEnabled(true);
                    });
                });
            }).show();
    }

    private void removeCloudToken() {
        if (busy || closing) return;
        // Disabling cloud in memory is immediate, even if storage clearing fails.
        cloudSettings = new CloudSettingsStore.Settings(CloudModePolicy.Mode.OFFLINE, "");
        updateModeButton();
        busy = true;
        send.setEnabled(false);
        worker.execute(() -> {
            String problem = null;
            try { CloudSettingsStore.clear(this); }
            catch (Exception error) { problem = "Could not erase cloud token from storage."; }
            final String finalProblem = problem;
            runOnUiThread(() -> {
                if (closing) return;
                setStatus(finalProblem == null
                    ? "Cloud token removed • OFFLINE mode enabled" : finalProblem);
                busy = false;
                send.setEnabled(true);
            });
        });
    }

    private void testCloud() {
        if (busy || closing) return;
        String token = cloudSettings.token;
        if (!CloudModePolicy.maySendToCloud(CloudModePolicy.Mode.CLOUD, token)) {
            setStatus("Tap CLOUD SETUP to save your Worker device token first.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Send cloud test?")
            .setMessage("This sends only a harmless fixed diagnostic question " +
                "to Cloudflare/Gemini. It does not transmit your chat history.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TEST", (dialog, which) -> {
                busy = true;
                send.setEnabled(false);
                setStatus("Testing authenticated Gemini response…");
                worker.execute(() -> {
                    String error = null;
                    try {
                        String result = cloudClient.reply(
                            "Reply OK to confirm that cloud AI is working.", token);
                        if (result.isEmpty()) throw new IllegalStateException("Empty test result");
                    } catch (Exception failure) {
                        error = failure instanceof IllegalStateException
                            ? failure.getMessage()
                            : "Cloud test failed. Check connectivity and Worker setup.";
                    }
                    final String finalError = error;
                    runOnUiThread(() -> {
                        if (closing) return;
                        setStatus(finalError == null
                            ? "Cloud test: Gemini generated a response via Worker."
                            : "Cloud test: " + finalError);
                        busy = false;
                        send.setEnabled(true);
                    });
                });
            }).show();
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
                            setStatus("Encrypted chat cleared • " + cloudSettings.mode.name());
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
        cloudClient.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }
}
