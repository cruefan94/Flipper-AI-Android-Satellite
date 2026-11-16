# Phase 2 Android build consolidation — candidate, NOT production

Coordination: https://github.com/cruefan94/Flipper-AI-Integration-Testing/issues/48
Main project hub: https://github.com/cruefan94/Flipper-AI-Momentum-/issues/5

This PR is based on the BLE navigation retention PR's source branch. It must
not be merged independently into an older branch missing the side-by-side and
navigation test Gradle flags.

## Changed CI ownership

- On Android source PRs: one Phase 2 workflow runs source/host regressions
  **once**, compiles ordinary, side-by-side, and BLE-navigation APKs sequentially,
  and independently verifies each package ID, app label and APK signature.
- The existing three workflows retain their **manual** dispatch capability.
  Regular APK workflow retains its trusted develop-branch push trigger.
  The legacy side-by-side workflow retains its historical test-branch push trigger.
- The Phase 2 PR workflow has only read-only repository permission. It does not
  receive deployment/production signing secrets. Every artifact is marked
  UNTESTED and the checkout and PR source SHAs are recorded.
- Fresh clean compilation between variant builds avoids silently uploading a
  binary with the wrong Android applicationId. Caching from the Gradle setup
  action remains available but is not a substitute for the clean build boundary.
- No APK can automatically install, replace user apps, or deploy cloud code.

## Artifact evidence

Each successful run uploads a single Actions artifact with three APKs,
SHA256SUMS.txt and BUILD_EVIDENCE.json. The source SHA is the actual checked-out
commit built by Actions (normally a merge commit for pull requests), plus the
pull request's head SHA. A passing build is a **CI compilation and package
verification**, not proof of working physical BLE reconnection, cloud runtime,
AI models, or Android lifecycle on the owner's phone.

For a real update of an already-installed app, the versionCode must increase,
and both applicationId and signing certificate must match. These PR debug
builds are **not** guaranteed in-place updates. The isolated test APKs are
deliberately separate from the original app.

## Acceptance and rollback

Review YAML, compare package IDs and three artifact hashes, confirm regression
tests and all three compilation steps pass, then deliberately select a future
integration PR. If this workflow is blocked, the older per-variant pipelines
remain manually available; do not claim a failed or skipped job is a successful
build. No release, stable signing, or production merge is approved here.
