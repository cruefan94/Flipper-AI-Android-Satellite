# Android AI Satellite — Stable, data-preserving update delivery (development gate)

## Why this is necessary

The user has a working Android AI Satellite app installed and does not want to uninstall it for every refinement. **Android in-place APK updates require all three conditions:** identical `applicationId` (package), compatible signing certificate/signing lineage, and an Android `versionCode` greater than the currently installed release. A successful debug APK build alone does **not** satisfy these conditions.

Existing GitHub Actions debug APKs may have been signed with ephemeral debug keys. An old APK cannot be transparently updated by a newly created release key with a different signing certificate. **Never tell the user to uninstall the current app or delete the installed model merely to try a new development APK.** First identify the installed APK certificate and establish an appropriate migration/backup plan.

## Implemented code

- Existing package `com.flipperai.satellite` remains unchanged.
- The Android GitHub Actions workflow assigns a monotonically increasing `FLIPPER_AI_VERSION_CODE` based on the workflow run number, with an offset high enough to avoid prior low source build codes. Source-only local builds retain version 2.
- Debug APKs are explicitly named **DEBUG-UNTESTED**. They are not promised to be update-compatible with existing installations.
- A stable-key signed **UPDATE-CANDIDATE-UNTESTED** release APK is built **only** from trusted `develop/android-apk-build` push or workflow-dispatch runs, and **only** when all four private GitHub signing secrets are configured.
- The GitHub runner decodes a secret-provided keystore into its private temporary directory with mode 0600; the signing secret values are never stored in repository files or echoed into artifacts. The release APK undergoes `apksigner verify` and versionCode comparison before upload.
- If every signing secret is absent, CI still builds the debug candidate. A partially configured secret set fails closed. The temporary keystore is cleaned on completion.
- No automatic app installation, uninstall, on-device compilation, or model modification is performed.

## Owner one-time setup (when ready)

On a trusted computer, use an Android-compatible signing tool such as `keytool` to generate and **privately back up** a long-lived Android APK signing keystore. If a signing keystore already exists for the current installed APK, prefer that **same** keystore and alias instead of creating a new identity. Do not share it in ChatGPT or a public repository.

Add these four protected GitHub Actions secrets to the Android Satellite repository, using GitHub's web UI:

| GitHub secret | Value |
|---|---|
| `FLIPPER_ANDROID_KEYSTORE_B64` | Base64-encoded binary keystore, with no additional characters |
| `FLIPPER_ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `FLIPPER_ANDROID_KEY_ALIAS` | Alias for the signing key inside keystore |
| `FLIPPER_ANDROID_KEY_PASSWORD` | Password for that key |

Set them in <https://github.com/cruefan94/Flipper-AI-Android-Satellite/settings/secrets/actions>. Never paste values into a chat, GitHub issue, PR, workflow logs or Termux issue queue. Keep an offline backup of the actual key and password: losing the key can prevent future in-place updates.

Only a **trusted main development-branch run** consumes these secrets; feature/PR branch builds cannot access them through the committed CI workflow. The output is an update *candidate*, **not** an approval to install it over the owner's current APK.

## Mandatory first migration check

1. Confirm the existing Android app `applicationId` and installed APK signing fingerprint (e.g. via a trusted package-inspection tool or Android debug bridge with the owner's consent).
2. Compare the public signing certificate SHA-256 fingerprint from `apksigner verify --print-certs` on the new release APK. If they differ, stop. Android generally refuses a direct update (unless a separately validated signing lineage rotation exists).
3. Compare installed versionCode to the new candidate. A lower/equal candidate cannot update.
4. Back up user data/model files using an appropriate, lawful Android-supported mechanism before any potentially destructive migration. The model is stored in app-private storage; ordinary APK downloads do **not** constitute a backup.
5. Only when cert, package, version and backup/recovery gates pass, test a normal install-as-update **without** uninstalling the app. Check chat, imported model availability, Cloud settings, BLE pairing and reconnect.
6. Preserve the last working APK/signing identity in a private record. Never distribute raw private keys or model weights.

## What is NOT yet verified

- Whether the owner has the original signing certificate/keystore for their installed APK.
- Whether all four GitHub secrets are configured. No secret values are accessible to this conversation.
- Live installation, app data retention, signing continuity over multiple updates, or an end-to-end AI response on a physical phone.
- The custom Flipper firmware and private AI BLE GATT service are unaffected by this Android signing workflow.

The release candidate pipeline is a necessary prerequisite for reliable updates, **not** evidence that any particular installed APK can update in place.
