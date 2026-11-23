# Northern Circuit AI M5 — Model Hub, downloads and compact UI

## Scope
Build from M4 configurable voice draft PR #138. The prior M2 Cloud Test and Flipper AI Satellite remain unchanged.

### Interface
- Compact one-row CHAT navigation: **MODELS**, **AI MODE**, **VOICE**, **SETTINGS**.
- MODELS is a full separate Android screen, with back navigation; AI MODE, VOICE, SETTINGS show dialogs.
- Keeps encrypted chat bubbles, explicit CLOUD disclosure, voice playback and original company emblem.
- No claim of Android device layout testing until S25 screenshots are reviewed.

### Download Models
- Curated fixed GGUF list: Qwen3.5-4B Q4_K_M from ggml-org, Qwen3.5-2B Q4_K_M from gguf, Qwen3-0.6B Q4_0 from ggml-org.
- Source URLs are official Hugging Face repository `/resolve/main` links. User initiates **DOWNLOAD**, confirms data/storage expectations, and Android DownloadManager handles transfer.
- UI shows **queued, downloading with progress, paused, completed or failed**, on manual REFRESH. Android provides notification.
- Public storage target: `Downloads/NorthernCircuitAI/<model>.gguf`, so **Termux can read it after storage permission**. No storage permissions requested by Android app; no shell commands executed.
- **MODEL SOURCE** opens repo page; no arbitrary URL entry.
- **Download completed does NOT mean installed, SHA-verified or ready for inference**. The model must be readable and load successfully in llama.cpp; large storage consumption and unsupported architectures remain possible.
- Downloads of models already in Termux's independent Hugging Face cache are not needed. The app cannot see Termux's private cache; its download progress only tracks downloads initiated through this app.

### Using and selecting a model
1. Install and launch new APK. Open MODELS.
2. Download GGUF, or use already downloaded GGUF in a known path.
3. In Termux run `termux-setup-storage` once and grant access (if not done).
4. If another `llama-server` occupies port 9931, stop it with Ctrl+C.
5. Run the app's **COPY ROUTER START COMMAND** in Termux:
   `llama-server --models-dir "$HOME/storage/downloads/NorthernCircuitAI" --host 127.0.0.1 --port 9931 -c 2048 -t 4 --models-max 1`
6. In MODELS tap **SCAN & SELECT LOCAL MODEL**. The app queries loopback `/models`, falling back to `/v1/models`. It only lists IDs the server actually reports; user selection is persisted locally and used in local completions.
7. Set AI MODE to OFFLINE (or opt-in HYBRID), send a genuine prompt. A selected model could fail to load; hardware verification is needed.

### Limitations and gates
- Not a fully embedded inference runtime: Termux must remain running to host the selected model.
- Files previously cached by `-hf` may be outside the DownloadManager target directory; no attempt to find or move them.
- Re-downloading an existing GGUF may waste mobile data; choose carefully.
- No automatic delete, install, model training, arbitrary Internet model URLs or non-consensual downloads.
- [ ] Host static security tests and exact-head Gradle compile
- [ ] Confirm package ID `com.northerncircuitstudios.ai.modelhub`, apk signature and checksums
- [ ] S25 data/network consent, progress and completed-file accessibility in Termux
- [ ] Qwen3.5 router model list and switching with real answers
- [ ] CLOUD and voice regressions, menu/keyboard insets, storage exhaustion and cancelled network test
- [ ] SHA-256 verification of downloaded model weights and content-length consistency in a later update
- [ ] Do not merge to a production build until tested by owner.
