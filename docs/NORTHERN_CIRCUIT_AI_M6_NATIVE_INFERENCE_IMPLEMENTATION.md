# Northern Circuit AI — M6 Native GGUF Inference (Termux-free)
Created 2026-11-22; source planning baseline: M5 commit add5891b6810e14c5d6fc221d5cc77fc7def9f78.

## Device lock: GALAXY S25 ONLY (owner approved)
- First native inference target: Samsung Galaxy S25, 64-bit ARM (arm64-v8a), Android 17 on user's current test phone.
- Native Android minimum SDK 33 (Android 13+) is acceptable for this isolated S25 experiment. Do not broaden support or spend time supporting older Android devices at this stage.
- Keep a separate M6 development branch and test application identity; do not touch the installed M4 voice/M5 model/cloud baselines or the separate Flipper AI Satellite APK.
- No Termux dependence is permitted in the final M6 OFFLINE path, but retain the known-good Termux build as a fallback during development.
- Primary model Qwen3.5-4B Q4_K_M, with smaller 0.6B smoke test before moving to 2B/4B; model weights stay separately downloaded by the owner.

## Goal
One self-contained Android application on the owner's Galaxy S25 that downloads/imports GGUF files, loads an offline model, generates and streams responses, switches models, stops/unloads inference, and optionally uses Cloud/Hybrid, without requiring Termux, root, a localhost HTTP server, or a separate inference app.

**Status:** Implementation architecture and acceptance gates approved for *development research*, NOT a native runtime implementation or tested APK. M5 and the installed M4 voice/cloud tests remain the known working baselines.

## Verified implementation basis
- Upstream official llama.cpp Android example, currently `examples/llama.android`: `https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android`
- Official Android guidance: `https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md`
- Upstream Android binding currently reads GGUF from a ContentResolver URI for metadata or app-private file and uses `AiChat` / `InferenceEngine` for generation via Kotlin `Flow`.
- Upstream library Gradle example currently uses Kotlin/Android NDK, CMake, minSdk 33, arm64-v8a, AND NDK 29; ours uses Android 8 minSdk 26, Android Gradle Plugin 8.7.3, and Java-based Activity. Make the first experimental build S25-only and do not silently raise requirements for the main app.
- License/security review: pin a reviewed llama.cpp source commit, retain upstream license/notices; check each GGUF model's license separately before redistribution. Model weights downloaded by the user, not bundled inside the APK.

## Architecture

```text
Northern Circuit AI Chat (existing)
       |
       v
AI Mode Router  -------------------------+------------------+
       |                                  |                  |
       v                                  v                  v
Native local inference engine         Gemini Worker      HYBRID policy
       |                               (opt in)          (local first)
       v
Arm64 JNI / pinned official llama.cpp Android library
       |
       v
Selected verified GGUF in app-private model store
```

Maintain separate `LocalAiClient` localhost adapter as an *explicit legacy fallback only while native inference is being tested*. After native is verified, it is no longer invoked by the OFFLINE button. Do not operate both engines simultaneously when memory is constrained.

## Work packets and exact acceptance gates

### 1. Native library integration and build
- Add isolated Android library module, Kotlin bridge where needed, pinned llama.cpp upstream revision, `arm64-v8a` shared object via NDK + CMake.
- Build in GitHub Actions on separate experimental branch; run ABI/ELF and APK signing/integrity checks. Include reproducible upstream SHA, license files, compiler versions.
- Validate supported model metadata and tokenizer; avoid promising that any arbitrary GGUF works.
- No remote executable downloads, no arbitrary build-time scripts fetched from mutable default branches, no unreviewed third-party binary.
- Preserve standard Cloud/voice/Flipper APK build gates.

### 2. App-owned model storage
- M5 Download Manager stores files under `Downloads/NorthernCircuitAI`; imported files and downloaded GGUF models must be made available as real readable files in an app-private directory (e.g. `getFilesDir()/models`), using Android `ACTION_OPEN_DOCUMENT`/`ContentResolver` or controlled same-app download staging.
- Validate model size, GGUF header, metadata, available space, hash/checksum when trustworthy expected digest exists, expected file name and safe file path.
- Use temp file + atomic rename after complete transfer and validation, never execute GGUFs as code; provide storage usage, cancellation, and explicitly confirmed deletion. Avoid duplicate 3 GB downloads when possible.
- Termux's private Hugging Face cache is in another app sandbox; never assume direct access. Provide one-time user-mediated export/import where available, or download via this app.
- No superuser/root, wide storage permissions or M5 regressions.

### 3. Inference engine adapter
- Introduce a common `InferenceBackend` surface: `loadModel(path, settings)`, `generate(messages, tokenCallback, cancellation)`, `unload`, `state`. Use a dedicated worker thread/dispatcher, never execute inference on the Android UI thread.
- Stream generated text into existing chat bubbles. Support system prompt, limited conversation context, stop/cancel, and clear errors for unsupported models, full disk, low memory, malformed GGUF.
- Set sensible generation bounds; avoid repeat of 320-token budget leaving only reasoning and no final answer. Configure correct Qwen chat template/disable reasoning for text-first baseline where supported.
- Free model/context/native allocations reliably on unload, model switch, and app termination. Device rotation portrait lock remains.
- No local HTTP port/network required by native OFFLINE inference.

### 4. Model picker + Cloud/Hybrid + voice
- M5 MODELS screen should show model file state separately from **loaded/ready** state. Provide Download, Import, Verify, Load, Switch, Unload, Cancel.
- Do not label a model 'installed' solely because DownloadManager completed.
- OFFLINE -> native inference; CLOUD -> existing Cloudflare Worker with explicit consent; HYBRID -> native first and cloud only after user opt-in. Error messages must distinguish failure to load from inference timeout.
- Keep encrypted chat history, source labels, current voice picker/Android on-device TTS, and privacy-preserving cloud storage unchanged. NO automatic upload of local chat context.
- UI clean and compact, no nine-button rows.

### 5. S25 compatibility and performance tests
- Prove actual response **in Airplane mode** with Termux fully stopped and no localhost AI server.
- Start with verified Qwen3 0.6B GGUF, then Qwen3.5 2B, then Qwen3.5 4B Q4_K_M; retain existing Termux 4B test as baseline only.
- Track token speed, load latency, RSS/memory pressure, crash reports, storage, thermal throttling, screen off/on, app process recreation, backup/restore and low battery.
- RAM Plus is storage-backed memory expansion, not extra physical RAM. Plan one model loaded at a time, CPU-first, investigate Android GPU acceleration after functional CPU baseline.
- Voice output must play audible, repeatable replies through both native OFFLINE and CLOUD.
- Model switching must not leave leaked allocations or double-load models; no cloud sends while Offline or before Hybrid consent.
- Restore prior functioning APK easily; do not merge to production before phone-side tests.

## Initial implementation strategy
Prefer integrating the **official llama.cpp Android library/binding** with a small Java-facing facade, instead of writing a new model runtime or relying on Termux. Investigate the upstream minSdk33/Kotlin/AGP/CMake compatibility before checking in actual library integration; pin a known source revision. The developer performs all compiling and produces an installable APK. The owner should only need to install and test.

## Work not complete at this checkpoint
No .so built or packaged, JNI binder integrated, model imported/verified/loaded natively, backend routing changed, native answer generated, or Termux-free device test performed. This document must never be treated as implementation proof.
