# AI provider integration — v37

## Approved target
- Offline: Gemma 3 1B, **only in a verified compatible quantized format** for the Android runtime. Existing MediaPipe `.task` import remains supported. A GGUF file is **not** runnable by the MediaPipe backend without a compatible native runtime.
- Online: Gemini Flash via a user-authorized HTTPS proxy/Worker. Never embed a provider key in the APK. A provider subscription, quotas, terms, and endpoint deployment are separate prerequisites.
- Hybrid: prefer local inference; send prompts online **only** after explicit opt-in. Keep a visible offline-only mode and revoke active cloud requests when consent is disabled.
- Future: expose the custom Flipper AI as an additional pluggable engine after implementation and compatibility testing.

## Verified baseline
Based on `feature/ai-hybrid-regression-v36` (`b25f3b67`). Existing sources include `EmbeddedModelEngine`, `WorkerCloudEngine`, `InferenceRouter`, and hybrid privacy regression tests. This plan does **not** assert a downloaded model, live provider, successful inference, BLE hardware validation, or a built APK.

## Implementation gates
1. Select a publisher-hosted, license-compatible Gemma 3 1B model artifact **confirmed to work with the chosen runtime**, verify size and SHA-256, and provide a user-friendly import/download path with progress, cancellation, storage checks, and truthful errors.
2. Deploy or configure an authenticated HTTPS Worker that invokes a supported Gemini Flash API; verify endpoint contract, secret storage, quotas, and explicit user consent. No hardcoded cloud keys.
3. Expose Offline / Online / Hybrid settings on the AI chat screen, persist mode and consent separately, default to Offline, and make AI readiness status reflect actual loaded engines.
4. Add deterministic regression tests for privacy, cancellation, failed model loads, offline-first fallback, and provider errors.
5. Build in public Android CI; confirm exact commit SHA, then test real offline and online replies on Galaxy S25 and verify Flipper BLE transport separately.

## Privacy and integration
Do not publish private Momentum firmware sources in public Android or integration-testing repositories. Do not auto-upload Flipper diagnostics to the cloud. Custom AI remains deferred until working and compatible.
