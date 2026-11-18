# Next APK visual identity and optional package integration review

## Implemented in development source
- Retain the app's existing midnight/teal/amber palette and dedicated-screen navigation.
- New scalable launcher identity (source vector; no image/font download).
- Lightweight, brief dashboard card entrance animation with Android system reduced-motion/animation-scale respect.
- Read-only `About My Flipper AI` screen reuses the existing visual component library; no privileged device control.
- No additional third-party UI dependencies or APK permissions.

## Approved assets which must remain separate from Android builds
- Momentum's existing visual pack authoring source: `cruefan94/Flipper-AI-Momentum-` `docs/MOMENTUM_VISUAL_CUSTOMIZATION.md`. Source catalog: **56 visual packs inventoried**, 48 previously built selectable variants, not 56 tested installable packs. Preserve original Momentum interface and theme selector.
- `cruefan94/Flipper-AI-Mascot-Library`: AI Dolphin metadata and individually timed clips; base PNG/GIF assets not currently published in repository, `.bm` conversion and hardware testing outstanding. AI Fox source keyframes are approved by owner, but the published `animations/catalog.json` is older and incorrectly lists fox as unapproved; reconcile metadata before treating it as packaged firmware art.
- Neither Android source nor visual bundle is evidence that custom animations display on real FZ.1 hardware; firmware flashing is on hold due to reported `furi_check failed` crash.

## Requested '52 custom GitHub packages'
The linked repositories and current project context have **not supplied 52 specific package coordinates, names, versions, provenance/licensing, or usage requirements**. GitHub Actions, GitHub Marketplace apps, GitHub Packages registry entries, Momentum asset packs and Android library dependencies are different things.
- Do not invent 52 packages, upload/install unknown binaries, grant GitHub Apps access, merge arbitrary Marketplace extensions, or add 52 libraries to an offline Android app.
- Owner can provide the list/source URL or canonical package manifest; then verify necessity, license, permissions, duplication, pricing, SDK/ABI compatibility and pinned versions before introducing only justified integrations.
- Keep GitHub Actions paid usage within the owner's existing **US$5 monthly hard budget**, with other spending budgets at zero; do not change billing or deploy anything.
- This document records an **unresolved inventory**, not completed package installation.

## Release gates
- Compile and static/JVM test the exact new source revision in one combined debug APK.
- Compare signing certificate against the installed APK before proposing in-place upgrade.
- Preserve installed app and model; no automatic uninstall or model deletion.
- Physical Android model inference, BLE AI1/Momentum and firmware stability remain separately unverified until user-led testing.
