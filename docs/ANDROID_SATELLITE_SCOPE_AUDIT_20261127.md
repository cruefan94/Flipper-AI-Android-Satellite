# Android AI Satellite — purpose and scope audit (2026-11-27)

**Status:** Initial source-only cleanup candidate; not built, not merged, not installed, and not validated on physical Android/Flipper hardware.

## Product purpose
An Android companion for Flipper Zero and the Northern Circuit hardware ecosystem: offline/cloud/hybrid AI, user-controlled voice, reliable BLE, and a secure path to Raspberry Pi/ESP32 and Termux commands. This is **separate development** from Tradesman Pro, JobProof, Rental Inspector and Recipe Haven. Tradesman Pro remains the leading commercial Android app; the company GitHub efficiency audit remains the infrastructure priority.

## First change — exclude internet radio
Remove the internet-radio dashboard card, radio activity, media playback foreground service, stream/directory/policy Java classes, dedicated audio permission and radio-only regression tests. Preserve essential AI, BLE, voice, online/offline models, network and developer tooling. Phone status copy no longer advertises a removed radio feature. Radio experiments remain recoverable from Git history and can become a separate project if desired.

### Source-only acceptance
- [ ] Run `python3 android_satellite/tools/preflight.py`
- [ ] Run `bash android_satellite/tests/run_core_tests.sh`
- [ ] Run `python3 -m unittest discover -s android_satellite/tests -p 'test_*.py' -v`
- [ ] Verify existing BLE/AI flows and radio-absence regression tests
- [ ] Review single-APK pipeline and avoid cost-triggering automatic builds
- [ ] Obtain owner review before any manual APK build; never overwrite working signed APK
- [ ] Test on Samsung phone before integration, deployment or release

## Keep / simplify / defer
**Keep:** Flipper BLE and reconnect reliability; AI chat and user-consented cloud/offline/hybrid paths; model management; voice PTT; diagnostics; essential device/network status. Keep existing stable user data and package identity.

**Simplify next:** dashboard navigation, redundant setup/diagnostics prompts, GitHub developer tools (move to advanced tools rather than remove without evidence); remove unused permissions/dependencies only after verifying their consumers.

**Future integration (not implemented by this cleanup):** a `Termux` command hand-off, plus Pi and ESP32 connection management. The existing prototype in `cruefan94/Flipper-AI-Integration-Testing/termux` is GitHub-Issues polling, not a verified direct Android-app bridge. Prefer a narrow, explicit Android-to-Termux action interface with allowed script IDs, per-action confirmation, bounded output and timeouts, and no arbitrary background shell. Never claim remote execution is established before device testing.

**Risk:** Branch inherits stacked draft BLE fixes from PR #133, not main. Keep this work isolated until the existing source/release chain is reconciled. No PR opened yet because pull-request workflows can trigger paid builds.

## Evidence required before claiming improvements
Compare APK size, method count/dependencies, requested permissions, startup/memory performance, navigation and release stability on the same build settings. Scope reduction is confirmed in source; performance and savings are not yet measured.
