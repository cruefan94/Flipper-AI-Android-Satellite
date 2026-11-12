# Radio Scanner v09 — phone-first integration

Base: feature/voice-ble-integration-v08, including BLE/GATT repairs, stock Momentum RPC, Android phone voice and inference routing. Not merged to main.

Implemented: dedicated Radio Scanner activity; user-supplied HTTPS direct audio playback via Android MediaPlayer; foreground media service and media notification controls; buffering, playback and error status; saved favorites and recent names; saved-station search; eight categories; online/USB SDR/Flipper attachment selector with unavailable hardware status. No verified station catalog is bundled. User must supply an accessible, permitted, compatible direct HTTPS stream URL.

Receive-only: no radio transmission, decryption, signal decoding, microphone capture, recording or audio upload. AI radio transcription and summaries are not implemented; UI explicitly says so. RadioReceiver is an interface for future backends; USB SDR and Flipper attachment are not implemented. Flipper radio controls require future authenticated BLE protocol and are not operational.

Existing MainActivity only adds navigation. Existing BLE and voice source remains unchanged. Added foreground-service permissions. Radio service is not exported.

CI compilation and JVM/source tests are separate from Android hardware verification. Real stream playback, notification controls, background audio, Android 15 restrictions, BLE hardware and voice hardware still require on-device tests. Provider availability and permissions must be checked for every feed. No location data is collected.
