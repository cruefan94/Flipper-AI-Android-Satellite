# Android Satellite: batch-testing and upgrade signing

## Why repeat uninstall/install can happen
GitHub Actions builds `:app:assembleDebug`. Without a signing key supplied by repository secrets, the runner creates its own debug signing certificate. An APK signed with a *different* certificate cannot install over an existing Android app with the same package name (`com.flipperai.satellite`). Android will reject it, even when the APK itself builds successfully.

This is **not** an AI/Cloudflare issue. Changing the app's package name or removing app data is not an acceptable "fix."

## Development/testing policy
- Commit and test changes in GitHub Actions without asking the owner to reinstall every change.
- Keep the package ID stable. CI sets a larger APK `versionCode` from `github.run_number`.
- Ship a single consolidated, appropriately signed APK for phone testing after multiple priorities pass.
- Preserve imported local models and app settings. Do not instruct an uninstall just to update an internal dev build.
- CI green proves build and source/JVM tests, **not** Flipper hardware or Android runtime integration.

## Secret-backed signing (one-time setup, not yet claimed configured)
Use a privately generated, backed-up Android signing keystore. **Never** commit the keystore, private key, passwords, or base64 data to any public GitHub repository or issue.

Add these four repository **Actions secrets** on `cruefan94/Flipper-AI-Android-Satellite`:
- `SATELLITE_KEYSTORE_B64` — base64 of the complete keystore file, on one line
- `SATELLITE_KEYSTORE_PASSWORD` — keystore password
- `SATELLITE_KEY_ALIAS` — alias for the signing key
- `SATELLITE_KEY_PASSWORD` — key password

Example on a trusted computer with Java installed (choose your own private passwords and alias):

```sh
keytool -genkeypair -keystore satellite-signing.p12 -storetype PKCS12 \
  -alias satellite -keyalg RSA -keysize 3072 -validity 10000
base64 < satellite-signing.p12 | tr -d '\n'
```

Keep the keystore and passwords in a secure backup. The base64 result is **secret key material**, not just an encoding trick, and must be pasted only into GitHub Actions secrets.

The workflow decodes the keystore only in the temporary runner directory, never into source. The Gradle debug variant signs with this persistent key when all secrets are configured; otherwise it clearly warns and builds with a temporary debug signer.

**Important:** If the current installed APK was signed by a different temporary certificate, switching to the stable signer may require **one one-time migration** to the stable certificate; Android will not silently install a different-signer APK over the same package. Preserve offline model backups before any required migration. Once the same signing certificate, package ID and increasing versionCodes are used consistently, future APKs can update in place.

## Device-aware answers
Builds including `PhoneFacts` answer narrow requests about the phone's current time/date, battery level and Wi-Fi/mobile-data status on-device (without cloud or extra permissions). General prompts still use the selected OFFLINE / HYBRID / ONLINE AI mode. This feature needs one consolidated Android on-device verification after the build has passed CI.
