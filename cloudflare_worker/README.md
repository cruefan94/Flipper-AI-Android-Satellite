# Flipper AI Satellite: Cloudflare Worker

Secure, opt-in online Gemini bridge for the Android AI Satellite. This is a **separate deployable Worker**, not an Android APK. No device token or provider API key belongs in source control.

## Android wire contract

- HTTPS `POST https://flipper-ai-satellite.guichet-chris94.workers.dev/ask`
- Header `X-Device-Token: <your secret token>`
- Header `Content-Type: application/json`
- Request: `{"message":"Hello"}` (1–127 UTF-16 code units)
- Successful response: `{"reply":"Hello!"}` (less than Android's 4096-byte response limit)
- Health check: `GET /health` (does not test the Gemini API)

This contract was checked against the existing Android `WorkerCloudEngine` and `WorkerEndpointPolicy` in `fix/offline-model-status-after-chat-v233`. Android cloud consent is separate and must be explicit.

## Cloudflare setup (account owner)

1. Merge the reviewed Worker pull request to the public Android Satellite repository's `main` branch.
2. In Cloudflare **Workers & Pages → flipper-ai-satellite → Settings → Builds**, connect GitHub repo `cruefan94/Flipper-AI-Android-Satellite`, production branch `main`. Linking a GitHub *account* alone does not necessarily link this *Worker* to the repository.
3. Set **Root directory** to `cloudflare_worker`. Leave **Build command** blank and use **Deploy command** `npx wrangler deploy`. The `wrangler.toml` name matches the existing Worker.
4. Under Worker **Settings → Variables & Secrets**, add runtime **secrets** (not plain-text variables):
   - `GEMINI_API_KEY`: a valid key created in Google AI Studio, subject to the provider's terms and quotas.
   - `DEVICE_TOKEN`: a newly generated random 32+ character printable ASCII secret; use exactly the same value in the Android app. Never paste either secret in chat, screenshots, or GitHub.
5. Trigger a deployment from the configured branch and check Cloudflare deployment logs. Do **not** confuse a GitHub CI pass with a Cloudflare deployment.
6. Verify `https://flipper-ai-satellite.guichet-chris94.workers.dev/health` returns `{"ok":true,...}`; this is a **basic routing check only**.
7. In Android Satellite AI Chat → CLOUD, enter `https://flipper-ai-satellite.guichet-chris94.workers.dev/ask` and the token. Select ONLINE or HYBRID **and** grant cloud consent, then send a short test message. Confirm a real provider reply before calling online AI functional.

## Security and limitations

The Worker requires a device token, accepts only POST /ask, limits body and message size, uses timeouts, does not log secrets/prompts, and never runs Flipper hardware commands. The Gemini API key is kept server-side in Cloudflare runtime secrets. Workers are publicly reachable endpoints even when token-protected: consider Cloudflare rate limiting, monitor quota/cost, and rotate the device token if exposed. A device token is a shared secret, not full per-device identity management.

Current offline model and Flipper BLE testing remain separate. Production functionality is **not** verified by these source files or by a mock test. Do not enable cloud requests without the Android user's explicit consent.

## Connected-build verification

After selecting GitHub `main`, enabling builds, and setting `cloudflare_worker` as the root directory in Cloudflare, a new commit under this directory can trigger a fresh Cloudflare build. Confirm an actual **GitHub-triggered** successful deployment in Cloudflare Deployments or Builds; the existing **manually deployed** Hello World version does not establish that the Worker has updated. The online AI flow remains unconfigured until runtime secrets `DEVICE_TOKEN` and `GEMINI_API_KEY` are saved in Cloudflare.

## Git-connected deployment trigger

Cloudflare Git integration starts observing new commits after the repository and production branch are connected. If an earlier commit already passed GitHub Actions before the Cloudflare connection was saved, that GitHub check alone will not confirm a Worker deployment. A documentation-only commit under `cloudflare_worker/` can trigger the newly configured deployment without changing the Worker code. Confirm the resulting build and deployed commit in Cloudflare **Workers & Pages → flipper-ai-satellite → Deployments / Builds**. The runtime secrets `DEVICE_TOKEN` and `GEMINI_API_KEY` must still be configured separately before live Gemini replies will work.
