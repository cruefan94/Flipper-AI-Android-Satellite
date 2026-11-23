# Northern Circuit AI — configurable local speech output (M4 development)

## Owner's request
Give the AI an adjustable voice for BOTH **OFFLINE local Qwen** and **CLOUD Gemini** responses, also including any **HYBRID** replies. Preserve the working cloud version and separate Flipper AI Satellite installation.

## Implemented in isolated source
- **AUTO VOICE: OFF/ON** — auto-read each successful answer; default OFF.
- **CHOOSE VOICE** — select any English voice the installed Android TTS engine marks as *on-device and installed*.
- **Speed and pitch** sliders, 0.70× to 1.50×; **PREVIEW** gives a spoken sample.
- **READ LAST / STOP VOICE** — replay the most recent AI response (also when auto-read is off), or stop speech.
- Local app-private persistence of voice ID, speed, pitch, and auto-read preference.
- Multi-chunk reading for long responses and stripping common Markdown marks before speaking.
- No microphone permission or separate online speech API; engine is Android's system TextToSpeech. Voice choice is subject to Android engine availability. The app filters out voices whose metadata requires a network connection, but the underlying engine is supplied by the phone and should be checked in system settings for offline support.
- Android 11+ speech engine <queries> visibility declaration.
- OFFLINE/CLOUD/HYBRID inference, Worker token and encrypted chat storage unchanged.
- Debug build uses **com.northerncircuitstudios.ai.voicetest** and label **Northern AI Voice Test**, allowing installation beside M1, M2 Cloud Test and M3 Chat Preview without overwriting any of their credentials or chat history.

## Limitations
- Text-to-speech **speaks** the assistant's replies; it does not yet support microphone speech-to-text or real-time two-way voice conversation.
- Voice selection doesn't guarantee a female/male preset or any specific accents: list comes from installed Android English voices; the owner may download voice packs in Android Settings (requires phone consent and network at install time).
- Audio quality, playback completion, background behavior and availability of on-device voices must be tested on the Galaxy S25.
- This does not change the Flipper Android Satellite app or the Flipper Zero FAP. A shared module for Satellite can follow explicit acceptance.

## Acceptance
- [ ] Host source/security checks green
- [ ] Exact commit Android APK compiled with verified signature/package and checksum
- [ ] Install M4 alongside M2 without uninstall/data loss
- [ ] Pick at least two installed on-device voices (if available) and hear distinct previews
- [ ] Speed/pitch controls audibly change playback
- [ ] CLOUD reply read aloud and replayed without breaking working cloud authentication
- [ ] OFFLINE reply read aloud when local model is running (not yet proven)
- [ ] Auto voice OFF remains silent; app can stop an active long answer
- [ ] Confirm no network-dependent voice is used
- [ ] Check S25 UI at standard and large system font sizes

Do not merge into a working or production branch until testing establishes those results.
