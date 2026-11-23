# Northern Circuit AI — milestone 1: independent Android app (development)

## Milestone
Build a **standalone, separately installable Android APK**, rather than
renaming the Flipper AI Satellite. The package ID is
`com.northerncircuitstudios.ai`, with a distinct launcher icon/app label and
private app data. The existing Android Satellite and its Flipper communications
are left intact.

## Currently implemented source
- Android standard and round launcher icons use the actual Northern Circuit Studios circular company emblem sourced from the owner's existing logo image (resized as WebP for app packaging; no redesign).\n- General-purpose text chat interface with persistent encrypted history,
  clear-history confirmation, model connection test, and explicit setup dialog.
- On-device Qwen3.5-4B Q4 GGUF through the already supported
  `llama.cpp` OpenAI-compatible local loopback server
  `http://127.0.0.1:9931/v1/chat/completions`, plus 2B fallback.
- No cloud inference, automatic tool execution, server hosting, prompts sent
  over the Internet, personal data upload, or device permissions beyond
  Android's network permission (needed for localhost).
- A bounded conversation context of up to eight recent messages and encrypted
  local history (up to 30 messages) using Android Keystore AES-GCM. Erase is an
  explicit action; Android Auto Backup is disabled.
- Source-only unit/security guards and an isolated GitHub Action
  to compile and verify the independent debug APK.

## Android S25 usage
1. Confirm the owner-selected initial **8 GB RAM Plus** value; do not
   automatically modify it. RAM Plus is storage-backed swap, not physical RAM.
2. Install the independently built `NorthernCircuitAI-*.apk` GitHub artifact
   from this branch's workflow. **Do not uninstall Flipper AI Satellite.**
3. Install current Termux and its `llama-cpp` package in the phone, as
   documented in `docs/NORTHERN_CIRCUIT_AI_S25_QWEN35.md`.
4. Start Qwen3.5-4B Q4 GGUF with:
   ```sh
   llama-server -hf ggml-org/Qwen3.5-4B-GGUF:Q4_K_M --alias flipper-local --host 127.0.0.1 --port 9931 -c 2048 -t 4
   ```
5. In **Northern Circuit AI**, press **CHECK AI** (only verifies the loopback
   server responds). Send a general question and confirm the actual model answer.
6. Exit and reopen to verify chat history remains, then test **CLEAR CHAT**.
   Compare response times and heat over a few requests. No hardware testing
   has been claimed yet.

## What this milestone does NOT mean
- The GGUF weights are **not bundled inside the APK**. Termux must run the
  actual local model before the chat works.
- The assistant does not yet do research, voice, images, GitHub operations,
  device control, memory extraction, model training or cloud inference.
- This is a first **genuine, independent chat client** powered by an
  open-weight model if the server is actually configured on the S25.
- GitHub build success proves only code/packaging, **not phone compatibility,
  answer quality, model speed or a production launch**.
- Debug APKs may be signed with changing keys across GitHub CI runs;
  preserve your current test build and chat data rather than uninstalling it
  without exporting or accepting data loss.

## Acceptance checks
- [ ] Pure Java and static source tests pass
- [ ] Gradle compiles the standalone Android APK
- [ ] APK signature and distinct package ID are verified
- [ ] User installs APK alongside existing Satellite app
- [ ] S25 Qwen3.5-4B responds to actual questions offline
- [ ] Encrypted chat persists/reopens/erases on S25
- [ ] CPU load, RAM use and thermals observed on 8 GB RAM Plus baseline

## Next steps after accepted
A compatible integrated on-device native runtime would remove the manual
Termux process. Add private user-controlled memory preferences, model
switching, external tools with permissions, and an optional cloud AI gateway
in separate audited releases.
