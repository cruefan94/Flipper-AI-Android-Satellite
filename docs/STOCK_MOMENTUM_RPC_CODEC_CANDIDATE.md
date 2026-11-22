# Android Satellite: Stock Momentum RPC codec (source-only candidate)

This codec addresses the missing transport envelope between the existing
Bluetooth RPC diagnostic ping in the current Android APK and Flipper AI v0.7's
standalone App Data Exchange interface. It is NOT a working phone↔Flipper link.

Authoritative pinned schema: Next-Flip/flipperzero-protobuf, submodule SHA
ea4f185f5eaa265955c520eae2832887ee6aa5e4, flipper.proto and
application.proto. PB.Main: command_id = field 1, command_status = field 2,
app_start_request = field 16, app_state_response = field 58,
app_data_exchange_request = field 65, empty response = field 4.
PB_App.StartRequest.name = 1, args = 2; PB_App.DataExchangeRequest.data = 1.
Messages use nanopb LENGTH-DELIMITED varint32 framing, often split across BLE
indications. AppStart args must be the literal "RPC". Flipper's app path is
/ext/apps/GPIO/flipper_ai.fap; installed Momentum version must be compatible.

## Implemented
- Pure Java (no Android dependencies) AppStart and AppData protobuf encoding.
- Strictly bounded 512-byte PB.Main envelope, 180-byte AI1 payload, UTF-8 fail
  closed and state response parsing. Streaming decoder handles fragmented and
  concatenated BLE indications, and reset on disconnection.
- Java 17 native offline test covering start, HELLO, data, status, app started
  and exited, fragmentation, duplicate chunk and invalid UTF-8/length.
- Public GitHub CI contains only newly authored public Java protocol code.
  NO private Flipper source, firmware, secrets, or artifacts are copied there.

## Deliberately NOT yet implemented
- Calling GATT write to send AppStart/AppData, BLE MTU-aware chunked transport,
  replenished flow credits, command ACK correlation, timeout and generation
  fencing, and backpressure/paired-device authorization.
- Routing a decoded AI1 frame through the existing Android ProtocolSession,
  phone-owned inference worker and offline/cloud policy, then relaying a safe
  ANSWER back to the Flipper's FAP via AppData RPC.
- Any change to the user's installed APK or current Flipper .fap.

## Priority integration gates
1. Keep the user's current installed Android APK stable while the Flipper-side
   v0.7 standalone FAP compiles and passes a basic menu smoke test.
2. Integrate new codec with RPC-only Bluetooth transport behind explicit UI
   selection; enforce verified pairing, GATT app launch, ATT size, flow-credit
   accounting, on-write callbacks, and fail-closed reconnect.
3. Reuse Android foreground Service's inference worker, not MainActivity;
   avoid cloud permission escalation or automatic online fallback.
4. Test with a real Flipper and phone before declaring end-to-end success.

This is a verified interoperability source candidate, not a bundled or
flashable firmware image.
