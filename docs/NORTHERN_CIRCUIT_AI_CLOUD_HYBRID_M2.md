# Northern Circuit AI — cloud settings and hybrid mode (isolated development)

This branch extends the independent Android Northern Circuit AI APK from PR #134.
It does not modify or merge the existing Flipper Satellite, Momentum firmware,
Cloudflare Worker, its live configuration, or any private API secrets.

## Side-by-side safety
The isolated debug trial is packaged as
`com.northerncircuitstudios.ai.cloudtest` and labelled
**Northern AI Cloud Test**, so it can be installed alongside the existing
`com.northerncircuitstudios.ai` M1 APK even if GitHub's temporary debug
signing key differs. The two apps intentionally have separate, private
credentials and chat history. No production app overwrite or data migration
is claimed. This suffix is for the experimental debug build only.

## New app controls
- **CHECK LOCAL** — checks the local llama.cpp endpoint only.
- **LOCAL SETUP** — displays the Termux model launch command and setup guide.
- **MODE: OFFLINE/CLOUD/HYBRID** — changes the persistent inference mode.
- **CLOUD SETUP** — enter the Cloudflare Worker's existing `DEVICE_TOKEN`
  (never the Gemini API key). Token and mode are AES-GCM encrypted using Android
  Keystore and app-private SharedPreferences. Saving the token defaults to
  OFFLINE; it does not activate cloud AI.
- **TEST CLOUD** — after confirmation, sends a fixed non-personal test prompt to
  the authenticated Worker and checks for a real text reply.
- **CLEAR CHAT** — erases the local encrypted chat but retains cloud settings.
  Remove the token separately in CLOUD SETUP.

## Modes and disclosure
1. **OFFLINE (default)**: sends no prompts to the cloud; requires
   `llama-server` at `127.0.0.1:9931`.
2. **CLOUD**: after explicit consent, sends the current message via HTTPS to
   `https://flipper-ai-satellite.guichet-chris94.workers.dev/ask`, with the
   encrypted-at-rest device token supplied in the `X-Device-Token` request
   header. Model inference occurs at Gemini via the Worker.
3. **HYBRID**: tries the local model first; on a local error, transmits only the
   CURRENT prompt to the same cloud Worker. Selection requires an explicit
   consent confirmation. Previous turns are never transmitted to the Worker.

The existing deployed Worker has a **127-character message contract**, so
cloud requests longer than 127 characters are rejected in the app instead of
silently truncating or transmitting incomplete content. The Worker is
single-turn and does not receive previous conversation context. These
limitations require a separately reviewed Worker and API contract migration.
No claim is made that Gemini credentials or live backend are functioning.

## Cloudflare prerequisite
The existing Worker requires server-side secrets:
`DEVICE_TOKEN` (16–256 visible ASCII chars) and `GEMINI_API_KEY`.
The user enters only the matching **DEVICE_TOKEN** in the app. Neither
credential belongs in a source file, GitHub issue, PR comment or screenshot.
If the Worker has not been configured, even a successful APK compilation
cannot make cloud inference work.

## Verification gates
- [ ] Host Java CloudModePolicy + AssistantPolicy unit tests
- [ ] Python source/security tests
- [ ] Exact-commit independent Android APK compilation and manifest/signature checks
- [ ] Device install, encrypted settings persistence and reset
- [ ] OFFLINE verified with phone airplane mode
- [ ] TEST CLOUD real authenticated response and intentionally invalid token
- [ ] CLOUD and HYBRID real response, privacy consent and offline fallback
- [ ] Debug signing continuity, no uninstall/data loss
- [ ] UI viewport/keyboard checks and no regression to the original Satellite

Do not promote this branch until source, CI and device tests pass. The public
repo does not establish the configuration or health of deployed Worker secrets.
Future separate milestones: full-length contextual cloud chat, model manager
with downloadable weights, local integrated runtime, voice, exports, memory,
and opt-in authenticated device/tool connectors.
