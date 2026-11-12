# Flipper-to-phone push-to-talk — experimental AI1 extension

**Source status:** Android implementation and regression tests only.
**Hardware status:** Unverified. A compatible Flipper firmware button handler is
not implemented/validated by this Android change.

## Transport requirement

The Flipper remains the peripheral; Android remains the BLE central. A PTT
request must arrive on the dedicated authenticated Momentum AI GATT notification
channel after the connection's normal `AI1|HELLO|id|flipper-v1\\n` handshake.
Stock Momentum Bluetooth RPC is **not** an AI GATT session.

## Frames

- Flipper notification: `AI1|PTT|<monotonic_uint32_id>|start\\n`
- Accepted response: `AI1|PTT_ACK|<same_id>|accepted\\n`
- No opt-in or microphone permission: `AI1|ERROR|<same_id>|PTT_DENIED\\n`
- Invalid payload: `AI1|ERROR|<same_id>|BAD_PTT\\n`
- Duplicate/out-of-order request: `AI1|ERROR|<same_id>|REPLAY_ID\\n`

The usual 180-byte AI1 frame limit and negotiated BLE ATT payload restrictions
apply. PTT_ACK acknowledges that the request was queued for UI-thread review;
it does not guarantee speech-recognition availability or a completed result.

## User permissions and privacy

The Android checkbox is **off by default** and does not persist across app
restarts. The user must enable it for the current authenticated AI BLE
session and grant Android microphone permission separately.
On disconnect/reconnect its authorization is revoked.
The UI thread re-checks the link session epoch, readiness and opt-in before
starting the phone microphone. On-device Android recognition is required.
A Flipper PTT request never authorizes online/cloud AI processing; the existing
separate OFFLINE/HYBRID/ONLINE routing and cloud opt-in policies still apply.

## Remaining work

1. Implement user-configurable PTT button behavior in the Flipper AI internal
   application, without replacing stock Momentum controls or animations.
2. Verify GATT session identity, notification subscription, request/response
   timing and PTT_ACK on actual paired Flipper and Android hardware.
3. Validate Android 17 on-device speech recognition/TTS and permission revocation.
4. Consider explicit PTT_STOP semantics only after a reviewed user-experience
   and security design; no persistent/automatic microphone recording is enabled.

Do not claim PTT works on physical hardware based on a passing Java regression
suite or APK compilation alone.
