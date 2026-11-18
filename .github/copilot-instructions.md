# GitHub Copilot instructions — Flipper AI Android Satellite (public)

## Project and privacy
- This public Android repository is part of the unified Flipper Zero Momentum + Android AI Satellite project. Private firmware lives separately in `cruefan94/Flipper-AI-Momentum-`; public-safe integration tests live in `cruefan94/Flipper-AI-Integration-Testing`. **Never import private firmware sources, device identifiers, decryption passphrases, user prompts, API keys, model weights, signing keys, or logs containing secrets into this public repository.**
- Inspect the selected branch and newest open PRs before modifying code. The default-branch README may not reflect active features. Do not claim physical Flipper interoperability or production readiness from mocks/CI alone.

## Required architecture
- Bluetooth first: keep a user-selected, bonded Flipper connection alive across screen changes with an explicit Android foreground service, cautious background reconnect/backoff, manual disconnect, and recent-device reconnect. Never reconnect to an arbitrary scanned device.
- Keep **custom AI1 GATT** and stock Momentum serial RPC distinct: stock RPC discovery is not proof of a working AI channel. AI1 service UUID is `c8c7ab10-6355-4ea8-a76a-0a4a64de0001`, request RX `...0002`, notify TX `...0003`. Verify full 128-bit values against private firmware before changing the contract.
- AI1 is a single bounded ASCII `AI1|TYPE|request_id|payload\n` frame, max 180 bytes; match MTU-minus-3 and request IDs across both ends. Queue GATT descriptor/characteristic operations sequentially and await callbacks. Handle timeouts, old callbacks, reconnect epochs, stale responses, invalid UTF-8, oversized prompts, and cloud-consent revocation.
- Run existing reliable, installed offline models locally first. Online requests are opt-in per session and must fall back safely. Never make network calls merely because BLE reconnects; never silently download/reinstall the user's already installed model. Retain local model files/preferences and distinguish genuine inference from diagnostics.
- Keep navigation simple, one combined app, and avoid upgrading an installed APK if debug signing differs: uninstalling may destroy private model data. New Android APK artifacts need unique date/run/attempt/source-hash names.

## Review and verification workflow
- Begin with `android_satellite/app/src/main/java/com/flipperai/satellite/BleSatellite.java`, `BleConnectionService.java`, `BleAiRequestProcessor.java`, `SatelliteProtocol.java`, `ProtocolSession.java`, `TransportState.java`, model/consent classes, and `android_satellite/app/src/main/AndroidManifest.xml`.
- When available on the current branch, run `bash android_satellite/tests/run_core_tests.sh`, `python3 android_satellite/tools/preflight.py`, `python3 -m unittest discover -s android_satellite/tests -p 'test_*.py' -v`, and the relevant Gradle Android debug build. Avoid using Gradle/SDK if unavailable; report unrun gates rather than saying tests passed.
- Review BLE callback ordering, Android version/permissions/background restrictions, bounded worker queues, thread cancellation, data races, logs, permissions, storage, signing/provenance, and consent state. Provide file/line evidence and small tests for each finding.
- Maintain source-only and CI/runtime/hardware status separately. Keep PRs reviewable and unmerged until actual test evidence supports them. Never install an APK on the owner's phone or change credentials automatically.

## Working with the owner
- The owner uses a Samsung S25 and may also test with an A52; give simple copy-paste steps and clear rollback. Do not require programming knowledge.
- Prioritize reliable offline/opt-in online AI and BLE; Raspberry Pi/ESP32 integration follows, and car integration/custom model development is deferred.
