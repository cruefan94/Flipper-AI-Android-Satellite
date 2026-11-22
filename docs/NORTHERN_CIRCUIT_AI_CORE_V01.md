# Northern Circuit AI — general assistant identity v0.1 (development only)

## Purpose
Begin a reusable, general-purpose Northern Circuit Studios AI by extending the
**already integrated** open-source local inference backends, without introducing
another redundant runtime or altering the working Flipper Android cloud protocol.

## Open-source foundations used
- Google AI Edge [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
  (Apache-2.0): the embedded Android offline runtime is already installed as
  `com.google.ai.edge.litertlm:litertlm-android:0.18.0` in the source.
- [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT): an optional,
  separately launched, on-device Termux GGUF engine at the existing strict
  `127.0.0.1` loopback endpoint. This patch does not bundle or download GGUF.
- The Qwen3 weights are a **separate, user-chosen model**, subject to the model
  licence and the selected runtime's compatible file format.

## What this candidate changes
- Defines one Northern Circuit AI general-purpose assistant profile with
  explicit honesty, privacy and hardware-action safeguards.
- In the embedded LiteRT-LM engine, the profile is included **only on the first
  successful conversation turn** after loading the model. Later turns keep the
  existing backend conversation context without repeating instructions.
- In optional localhost llama.cpp mode, the profile is placed in a separate
  OpenAI-compatible `system` message; the user message remains unchanged.
- Long inputs retain the existing 8,192-character policy, with no silent
  truncation. No identity prompt is added to diagnostics, phone-facts queries,
  cloud Worker calls or Bluetooth protocol frames.
- Adds pure Java regression tests for identity, prompt limits and invalid
  Unicode; no credentials, persistent memory or personal data are committed.

## Deliberately unchanged
- The installed Android APK and Flipper FAP; no automatic installation.
- BLE Momentum RPC/AI1 framing, FlipperHTTP restrictions and cloud consent.
- The existing Cloudflare Worker endpoint and 127-character /ask contract.
- The app's interface and package ID, signing and model download mechanisms.

## Test and release gate
Run `sh android_satellite/tests/run_core_tests.sh` and the repository's
Android APK workflow. Native inference, actual assistant behavior,
cloud consent, BLE pairing and end-to-end Flipper tests still require
a real Android phone and hardware test. This source PR is not a deployed or
validated AI release.

## Next extensions, separate reviewed PRs
1. Optional user-controlled private chat history/memory, with clear/delete and
   app-private secure storage.
2. A general AI Core API shared by Android apps and a Raspberry Pi gateway.
3. Tool permissions with read-only defaults and owner confirmation for writes.
4. Evaluate larger models using available RAM/storage/performance tests.
