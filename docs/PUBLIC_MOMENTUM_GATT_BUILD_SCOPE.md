# Public Momentum AI GATT compile lane

This branch is a **reviewed source-only public test mirror** for the experimental
AI Bluetooth GATT overlay. It does **not** contain the private Momentum source
tree, private Flipper AI application, ESP32-S2 firmware, credentials, model
weights, themes, dolphin animations, or other private project customizations.

## What the free public CI checks

Workflow: `.github/workflows/public-momentum-gatt-build.yml`

- Compiles and executes host C regression tests for AI protocol framing,
  authenticated connection policy, packet queue and replay rejection.
- Executes Python regression tests for source patching and staging.
- Clones the public Next-Flip/Momentum-Firmware source at exact commit
  `d3f89dfe2ef6b01839201598e9be1590cba80322`.
- Checks pinned source Git blob fingerprints before changing anything.
- Stages the experimental GATT/GAP/Bluetooth changes outside the source tree,
  then applies them only to the disposable CI checkout.
- Attempts `./fbt TARGET_HW=7 COMPACT=1 updater_package -j2` to check whether
  the additive service compiles together with standard Momentum serial RPC.

**Not covered:** the private Flipper AI internal application, Android-to-Flipper
encrypted BLE pairing, actual notifications/CCCD, real model inference, hardware
testing, or regression of the user's private Momentum customization build.

The workflow intentionally uploads **no firmware images**, never flashes any
hardware, and must not be treated as proof of a safe installation.

The private `cruefan94/Flipper-AI-Momentum-` repository and its `main` branch
remain the authoritative sources. Public test changes should be reviewed before
copying fixes back into the private development branch.
