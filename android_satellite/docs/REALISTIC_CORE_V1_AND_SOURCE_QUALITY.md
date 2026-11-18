# Flipper AI — realistic scope and verified open-source policy
_Last decision: 2026-11-16. Owner-approved scope reduction. This document is planning policy, **not a feature-completion or release claim**._

## Engineering objective
Build a reliable, portable assistant using **Android as the AI compute engine**, **Flipper Zero running Momentum as the portable controller**, and optional **Raspberry Pi 4B / ESP32-S2 / ESP-WROOM-32 / Arduino** for incremental expansion. Prefer active, license-compatible open-source implementations, pinned and checked against exact hardware/software versions. Preserve installed user data and keep the separate Custom AI research project independent.

## Core release acceptance — must demonstrably work
1. **Firmware:** a genuinely NEW F7 Momentum updater compiled from pinned reviewed sources, correct target and structure, independently checked embedded DFU CRC, hashes/provenance, recovery path and first hardware boot; preserve all stock Momentum apps/themes. Previously failed qFlipper DFU CRC 9-99 image is permanently quarantined. CRC alone never authorizes a flash.
2. **Android offline AI:** successful **live** inference on a compatible, installed LiteRT model, explicit safe offline diagnostics, model selection remembered and reused across normal same-package, same-signing-key updates, fail-closed verified one-tap downloads, no unwanted reinstall.
3. **Cloud:** encrypted reuse of HTTPS Worker URL/device token; cloud inference is opt-in **per session**, no automatic prompt upload or automatic reconsent; offline and hybrid responses explicitly labeled.
4. **BLE:** real authenticated Flipper↔Android AI1 protocol, bounded frames and request correlation, reconnect/timeout/backoff, previous **verified bonded** device for one-tap reconnect, single GATT owner not destroyed on Android screen change; explicit disconnect. Aim for **much more reliable connection**, not an impossible zero-disconnection guarantee. Stock Momentum RPC alone is not evidence of AI1.
5. **Verification:** exact-head source/JVM/static tests, successful isolated Android APK build, native F7 build and validated updater, physical on-device paired/AI1/AI prompts, screen navigation, app background, out-of-range return, Flipper reboot, Android Bluetooth restart, manual disconnect, token revocation, recoverability and regression tests. Test results must identify source/CI/device separately.

## Remove outright from product promises
- Full general-purpose language model hosted directly on the Flipper Zero MCU (64 MHz Cortex-M4, 256 KB shared SRAM).
- An always-connected, never-dropping Bluetooth link; replace with visible connection health, session invalidation and reliable recovery.
- Unrestricted AI-initiated hardware control, auto-flashing or sending arbitrary radio commands without permission.
- Direct AM/FM, CB or emergency-radio reception by an ordinary phone with no compatible receiver; phone internet streams/USB SDR are different capabilities.
- Automatic BLE/Wi-Fi/USB switchover before actual authenticated adapters/physical transports exist.
- Arbitrary universal Arduino/ESP32 components or Flipper hardware programmer with no voltage, bootloader, firmware and target verification.
- Flipper pretending to be a supported Android Auto phone or unlimited free cloud AI.

## Retain but defer / keep in separate repositories
- Raspberry Pi independent local AI node, Pi diagnostics and expanded model orchestration until they directly assist core release.
- ESP32-S2 Wi-Fi/ESP-WROOM-32 sensor expansion; use a small safe verified pin/function subset first.
- Lawful receive-only online AM/FM / public safety feeds, SDR, voice/PTT and future optional hardware modules.
- Battery optimizer, animations/mascots, vehicle Android Auto companion (must stay separate), private off-grid/no-SIM messaging research requiring actual network/radios, training independent custom AI model from scratch.
- Advanced smart transports and larger model families only after profiling, authentication and battery measurements.

## Verified upstream adoption (mandatory review)
1. Prefer official current API, Android, device and firmware documentation; inspect repository license/SPDX, model-weight license, trademark/redistribution restrictions and source provenance. *Publicly posted code is not automatically reusable.*
2. Verify active project maintenance, relevant releases and dependencies (pin exact versions/hashes), known security issues, target devices, ABI/Android API compatibility and RAM/storage/battery budget. Avoid obsolete abandoned forks and secrets in demos.
3. Prefer narrow, auditable integration behind interface boundaries. Do not overwrite the existing model runtime or installed Momentum firmware; do not use a generated answer as proof of correctness.
4. Validate source/static tests, native/Gradle build and physical hardware independently. No private firmware code/keys/tokens in public test repositories.
5. Comply with applicable Canadian/Ontario laws; no unauthorized radio, vehicle or security control.

## Source-of-truth and delivery rules
- This is **scope policy only**. Existing experimental source and repositories are not deleted; describe unsupported features as UNSUPPORTED, DEFERRED or EXPERIMENTAL, never already implemented.
- Keep each subsystem on a dedicated reviewed branch/PR. Do not auto-merge to main, flash, deploy, install or uninstall.
- Confirm the newest branches and CI before changes; retain user app models on safe same-signature upgrades.
- Independent GitHub Copilot reviews are for evidence-backed critical F7, BLE AI1 and security problems while Free allowance is limited.
- Primary coordination: https://github.com/cruefan94/Flipper-AI-Momentum-/issues/5
- Public-safe testing: https://github.com/cruefan94/Flipper-AI-Integration-Testing
- Android BLE guidance: https://developer.android.com/develop/connectivity/bluetooth/ble/background
- Android connectedDevice FGS: https://developer.android.com/develop/background-work/services/fgs/service-types
- Hardware facts: https://docs.flipper.net/zero/development/hardware/tech-specs

## Work priority until core accepted
**P0:** F7 safety/new verified native release + independent Android/Flipper AI1 handshake.
**P1:** stable service-owned BLE/one-tap previous reconnect with bounded recovery; easy verified local models and encrypted cloud reconnection.
**P2:** whole-system physical regression tests and version-safe, reversible releases.
**P3:** staged Pi / ESP32 / voice / receive-only radio as capacity allows.
**P4:** independent research, broader hardware, Civic, battery, custom-model training.

## Repository-specific note
Android default stable model, encrypted Worker, foreground BLE acceptance gates. No app code change on this policy branch.
