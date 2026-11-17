# Phone/Flipper Bluetooth reliability — simple owner acceptance test
_Last updated 2026-11-16. Test only; not proof of a release-ready firmware or AI1._

## Before you start
- **Keep your working Flipper firmware and installed Android app unchanged.**
- Open the successful GitHub Actions run linked from the development PR #55 and, if you want to test, download the **isolated BLE-NAV-UNTESTED APK** from its three-variant artifact. Install it *beside* your normal app (different Android package ID). Do not uninstall your existing app.
- This test app has separate storage. **Do not expect your normal app's imported AI model or saved Worker settings to be present.** The ordinary debug APK is NOT a safe signed update for your installed app.
- Enable Bluetooth and Android's **Nearby devices** permission for the test app. Start with normal battery settings; note if power saving is enabled. The foreground Bluetooth service needs a visible notification/task indicator as Android permits.
- On Flipper, use the firmware you already trust. **Do not flash the quarantined qFlipper CRC 9-99 updater** or any unaccepted new candidate.

## First connection
1. Open **Flipper AI BLE Nav Test → Flipper Connection**. Choose your bonded Flipper in **Paired devices**. If needed, pair in Android settings first.
2. Tap **Connect selected**. Note whether the status says *Stock Momentum RPC* or *AI BLE ready*. **Stock RPC is not proof of our custom AI1 interface.**
3. On the same screen, tap **COPY BLUETOOTH RELIABILITY REPORT**. This is the baseline. The report has no MAC address, cloud token, or prompt.
4. Visit the dashboard and AI Chat, then return to Flipper Connection. Check whether Bluetooth stays connected. Repeat this navigation several times.
5. Press Home, lock/unlock the phone, return to the app. Observe the foreground-service notification and check the link again. Android may stop a service under power restrictions, force-stop or memory pressure; do not claim zero-drop operation.

## Recovery and recent-device shortcut
6. Put Flipper out of Bluetooth range, then return within range. If still bonded, the connection should try **bounded** automatic reconnect; it should not flood retries indefinitely.
7. Tap **COPY BLUETOOTH RELIABILITY REPORT** again. Look for **Unexpected verified-link drops**, **Automatic reconnect attempts** and **Current verified-link uptime**.
8. Tap **Disconnect** and confirm the service stops trying to reconnect. Reopen Flipper Connection; the **Previously Connected** area should show the verified bonded device and **RECONNECT** button.
9. Tap **RECONNECT** and see whether the saved Flipper connects without repeating scanning/selection. The app must re-check the current Android pairing before reconnecting.
10. Optional: remove the Android pairing, then tap the old shortcut. It should refuse and tell you to pair again.

## What to report back
Copy/paste the **Bluetooth reliability report**, and also tell us:
- Phone model and Android version
- Momentum firmware version on the Flipper
- Whether connection dropped during screen changes, phone lock, out-of-range return, or manual reconnect
- Whether it showed **AI BLE ready** or only **Stock Momentum RPC**
- Whether Android displayed a persistent Bluetooth service notification

If an exception or unexpected disconnection occurs, share the redacted diagnostic output (not your Bluetooth MAC, private tokens, or keys). That gives us evidence for the next repair.

## Acceptance rule
Passing source/JVM tests + compiling three APKs does **not** establish physical stability. This checklist also does **not** prove that Flipper AI1 HELLO/ASK/ANSWER succeeds without a correctly built, independently verified Momentum firmware. No release/merge or firmware flash is authorized by this document alone.
