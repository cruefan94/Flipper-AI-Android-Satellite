# First Flipper AI Momentum flash: BLE GATT discovery diagnostics

## Confirmed owner hardware observations (2026-11-14)

- The owner successfully installed the private custom Momentum AI F7 firmware on a physical Flipper Zero, confirmed normal boot and photographed the running multi-page Flipper AI menu.
- The Flipper's **AI Satellite status** screen reported **No verified AI GATT link; stock RPC is not AI**, plus trusted snapshot **No**, AI1 HELLO issued **No**, and 0-byte max payload.
- The Android Satellite companion connected to the Flipper's stock Momentum **RPC**, with indications active and RPC flow control 262144 (unsigned little-endian). **AI NOT READY** was displayed.
- This proves neither AI service registration nor failure of the firmware's source-side implementation. Physical BLE AI1 discovery and handshake remain unverified.

## Diagnostic refinement

The Android BLE client now records an **immutable, redacted record of the last completed Android GATT service discovery**. The record includes the number of service UUIDs discovered and *only* whether these two fixed, expected service UUIDs were present:

- Momentum stock RPC: `8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000`
- Custom AI GATT: `c8c7ab10-6355-4ea8-a76a-0a4a64de0001`

The snapshot has five states: not checked, discovery failed, AI service present, Momentum RPC only, or neither service. It is refreshed for a new connection and remains available after disconnect, labeled **Last GATT discovery**, to preserve failure evidence. It **does not** disclose raw UUID inventories, device names, MAC addresses, pairing keys, prompts, or tokens. AI service presence does **not** imply an authenticated session or a successful AI1 HELLO/ASK exchange.

In the Android app, open **FLIPPER / BLE → Advanced AI settings and diagnostics**. Choose **Check AI BLE channel** to see the discovery state, or **Copy BLE hardware test report** to get a redacted text report. Device-specific testing is still required.

## Recommended low-impact check before reflashing

1. Retain the successfully booted custom Flipper firmware and Android app; **do not reflash solely for this missing-link report**.
2. Close the official Flipper app temporarily to eliminate competing Bluetooth sessions.
3. With user approval, remove the old Bluetooth bond on Android and Flipper, then pair again through the Android Satellite app (this may require reconnecting the official Flipper app later). Android can cache GATT services across firmware updates. **Do not use unsupported hidden Android GATT refresh calls.**
4. Reconnect. Read the exact **Last GATT discovery** classification. If **AI service PRESENT**, continue with encrypted/authenticated pairing and AI1 handshake checks. If **Momentum RPC only** persists on fresh pairing or a second phone, investigate runtime registration and the firmware's `ble_svc_ai_satellite_start()` result, attribute allocation, and service availability in the active serial profile.
5. Only after the AI1 handshake reads **ready**, attempt a harmless **Ask Android AI (BLE)** request. Keep the Wi-Fi/cloud route opt-in and separate.

## Limitations

Source/JVM tests and a GitHub APK build validate only the code and package. A live GATT UUID inventory, bond reset, AI1 handshake, Android inference and Flipper response all require an actual device run. **No hardware Bluetooth success is claimed by this code change.**

The GitHub Actions debug APK signing key may differ between builds; users should not be instructed to uninstall their working app or lose local data to test this diagnostics update. Establish stable signing and a supported in-place update path before a production upgrade.
