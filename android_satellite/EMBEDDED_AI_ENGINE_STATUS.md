# Embedded offline AI engine — development status

This work is isolated on `feature/ai-engine-public-ci-v07`, based on the existing Android voice branch. It does not modify the private Momentum firmware, its Bluetooth RPC, ESP32-S2 firmware, themes or animations.

## What was added
- A Java MediaPipe `tasks-genai:0.10.35` in-process inference adapter (`EmbeddedModelEngine`).
- A user-driven Android document picker for a compatible MediaPipe `.task` model. The file is copied to application-private `files/ai_models/` with a SHA-256-derived filename, a 3 GiB limit and cleanup of failed imports. An optional **trusted published SHA-256** is checked before native loading; leaving it empty explicitly marks the model unverified.
- Explicit embedded-vs-localhost provider selection, without changing the separate cloud consent boundary.
- Model inference and import run on the existing single background inference worker. The native model is unloaded on activity shutdown.
- The saved-model library lists imported `.task` files, rechecks file checksum against content-addressed filenames before reloading, and supports user-confirmed deletion while preventing deletion of the active model.
- Existing read-only hardware diagnostics remain available without any model.

## User workflow once an APK build passes
1. Obtain a **MediaPipe-compatible `.task` LLM** from a trusted provider and check its license and published checksum. Generic `.gguf` and `.litertlm` files **are not supported by this adapter**.
2. In AI Settings, choose **Import and load .task model**, select the file in the Android document picker and allow loading to complete.
3. To verify provenance, paste the trusted publisher's full 64-character SHA-256 before import. Otherwise the import is *unverified*. Set **Embedded model (default)** as provider and press **Select offline model provider**.
4. Press **Test local model connection**. Inference does not require internet or a localhost server.
5. Leave cloud consent unchecked for an offline-only session.

The importer checks a published trusted expected SHA-256 **if supplied**. If left blank, a digest is only a local integrity identifier, **not a claim of authenticity**. Model weights are not bundled or downloaded automatically. Model import may fail because the device lacks RAM, free disk space, required model runtime compatibility or supported accelerator. On next app launch the copied file remains private; choose it from **Imported models** and tap **Load saved model**. Model weights are not automatically loaded on startup.

## Open blockers
- A complete public CI APK build must pass, then Android-device testing must verify MediaPipe loading and real text inference. A compiled library with no compatible weights is **not** proof of a working AI assistant.
- This MediaPipe API is maintenance-only; migrate to Google's supported LiteRT-LM API when feasible, without losing the localhost provider or read-only diagnostic fallback.
- Provide verified, licensed model recommendations, trusted checksum validation, model catalog/list and deletion, resource monitoring and performance tuning.
- Existing Flipper-side dedicated encrypted AI GATT profile is still absent. Merely pairing to standard Momentum RPC is not an AI session.
- No code is merged into `main`; review and port successful Android work into the private source tree at the integration milestone.

References:
- https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/tasks/genai/llminference/LlmInference
- https://developers.google.com/edge/litert-lm/android
