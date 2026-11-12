# Phone Companion + GitHub v08 integration checkpoint

Base: feature/phone-voice-ptt-v07 (preserves existing PhoneVoiceAdapter and BLE/GATT fixes).
Branch: feature/phone-companion-github-v08

## Actual source changes
- PhoneCompanionStatus.java: read-only battery, active Wi-Fi/cellular/ethernet and internet-validation status, Bluetooth enabled status.
- GithubPublicStatus.java: unauthenticated, read-only HTTPS GitHub public latest-release query with timeouts, response-size limit, input validation, no token storage.
- MainActivity.java: Phone Companion readout and GitHub public release button; existing voice and BLE controls retained.
- AndroidManifest.xml: ACCESS_NETWORK_STATE permission.

## Security and limitations
- No GitHub credentials, access tokens, OAuth callback, device-flow authorization or account linking implemented.
- GitHub private repositories, Actions logs, branch/commit browser, notifications and writes are not implemented.
- Phone notifications require a separately user-enabled NotificationListenerService; none is installed.
- No background companion service or boot-time automatic reconnection is claimed.
- Flipper push-to-talk and cross-device phone status require a verified AI GATT channel.
- Public release results are displayed only, never auto-installed.
- No Gradle build or physical Android test has been run for this checkpoint.

## Next work
1. Design and implement Android-hosted GitHub App authentication (verify GitHub-supported flow; use PKCE only if supported), browser consent and Android Keystore-backed credential handling, minimum read-only repository/Actions permissions, explicit opt-in writes.
2. Implement authenticated read-only repository, branches, commits, workflow runs/jobs/logs and releases with pagination, redaction, token revocation and rate-limit handling.
3. Add permission-gated notification selection, explicit background service where allowed, companion reconnect state machine.
4. Add transport-authenticated Flipper phone/GitHub status protocol and PTT commands, test GATT compatibility.
5. Compile APK and firmware in CI, inspect logs and validate on hardware before release.
