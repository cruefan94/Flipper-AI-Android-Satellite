# Phase 2: stock Momentum Bluetooth RPC → Android AI inference

**Development stage; not a verified end-to-end Flipper AI session.**

Existing user-built standalone Flipper AI v0.8 FAP is left unchanged, along with installed Momentum mntm-012. This phase targets the current Android Satellite source and its embedded on-device AI worker.

## Source-grounded communication

1. Phone establishes a bonded, explicitly selected stock Momentum BLE RPC link. BLE indications and flow-control need proper GATT subscription.
2. A visible Android user action issues protobuf AppStart with name /ext/apps/GPIO/flipper_ai.fap and args RPC; Momentum passes the RPC context to the external FAP.
3. Require matching successful AppStart ACK and separate Flipper APP_STARTED event. No AI readiness is inferred from mere Bluetooth pairing or diagnostic RPC PING.
4. Flipper FAP initiates AI1 HELLO (flipper-v1); Android replies HELLO_ACK (android-v1). AI1 frame max 180 UTF-8 bytes. FAP can issue PING → PONG, and ASK → ANSWER.
5. Satellite's existing serialized foreground Service AI1 worker handles OFFLINE inference using the embedded/local model runtime; HYBRID/ONLINE are accessible only under explicit phone-side cloud consent. No Flipper-side AI model download is needed.
6. Android returns response using protobuf AppDataExchange over same stock Bluetooth RPC session; commands require matching ACK, timeout, credit and MTU-safe fragmentation.

## Implemented as Phase 2 foundation

- StockMomentumRpcCodec: strict 512-byte bounded protobuf envelope and stream decoder (PR #118).
- StockMomentumRpcAi1Session: fail-closed launch/ACK/state correlation; pre-ACK HELLO buffering, response ACK correlation, on-app-exit invalidation and fresh reconnect.
- Native offline Java host regression tests and GitHub CI. Does not claim a live Bluetooth link or hardware test.

## Release gates

- Wire the new session into existing bonded BleSatellite GATT service, subscribe to RPC RX notifications and flow-control, send AppStart and AppDataExchange via serialized MTU-safe writes.
- Route verified AI1 inbound frames to the existing BleConnectionService inference worker and back through the RPC session, keeping cloud consent scoped to the phone UI.
- Build a side-by-side Android debug APK and test the matching v0.8 FAP from the user's private Codespaces output on an actual Flipper.
- Perform HELLO, PING, offline ASK/ANSWER, disconnect/reconnect, duplicate IDs, long replies and cross-link cancellation tests before marking Phase 2 complete.

FlipperHTTP remains an optional read-only board diagnostics channel until the upstream TLS certificate validation fallback is fixed and hardware-tested.
Pi and ESP32-S3 are not required for the first direct Bluetooth AI interaction.

Do not overwrite Android stable APK or declare v0.8 end-to-end ready solely based on host tests.