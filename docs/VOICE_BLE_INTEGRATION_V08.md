# Android Voice + Bluetooth Integration v08

Integration branches: `feature/voice-ble-integration-v08` in both repositories. The Android branch is based on `feature/phone-voice-ptt-v07`, which already includes the repaired stock Momentum BLE/RPC integration from `develop/android-apk-build`. The main project branch is based on `feature/ble-ai-satellite-v06` and receives the Android voice files without changing firmware.

Implemented in source: explicit phone microphone button; Android on-device speech recognizer (Android 12+ with compatible installed recognition service); locally installed text-to-speech voice; transcript and spoken reply; OFFLINE/HYBRID/ONLINE inference through the existing router; separate opt-in cloud consent; stop/cancel controls and status feedback. No bundled offline LLM. Offline inference uses a separately installed local model endpoint or diagnostic fallback.

Not implemented: authenticated Flipper button PTT events, hardware microphone/speaker attachment, full validated stock Momentum RPC exchange, automatic speech recognizer installation, guaranteed local TTS on all phones. Stock Momentum firmware remains unchanged. A BLE GATT connection is not authorization to activate the phone microphone. Future PTT events must be authenticated, explicit and user-visible.

Verification: Android GitHub Actions PR checks compile and run JVM/source tests. A successful CI run does not prove phone microphone, TTS, Flipper button or hardware behavior. Test on Android hardware before release; keep the PR draft and unmerged until verified.
