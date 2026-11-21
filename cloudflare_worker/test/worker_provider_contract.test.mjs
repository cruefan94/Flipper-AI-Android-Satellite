import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Public-safe contract tests: all credentials and provider responses are synthetic.
const env = {
  DEVICE_TOKEN: "synthetic-device-token-1234567890",
  GEMINI_API_KEY: "synthetic-provider-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
const ask = () => new Request("https://flipper-ai-satellite.example.workers.dev/ask", {
  method: "POST",
  headers: { "Content-Type": "application/json", "X-Device-Token": env.DEVICE_TOKEN },
  body: JSON.stringify({ message: "status" }),
});

test("provider errors map to stable 502 classifications without leaking upstream body", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  try {
    for (const [providerStatus, expected] of [
      [400, "ai_provider_bad_request"],
      [401, "ai_provider_key_invalid"],
      [403, "ai_provider_access_denied"],
      [404, "ai_provider_model_unavailable"],
      [429, "ai_provider_quota_or_rate_limit"],
      [503, "ai_provider_overloaded"],
      [500, "ai_provider_error"],
    ]) {
      globalThis.fetch = async (url, init) => {
        calls++;
        assert.match(String(url), /^https:\/\/generativelanguage\.googleapis\.com\/v1beta\/models\/gemini-/);
        assert.equal(init.method, "POST");
        return new Response("synthetic upstream secret diagnostic", { status: providerStatus });
      };
      const response = await worker.fetch(ask(), env);
      assert.equal(response.status, 502, String(providerStatus));
      assert.equal(response.headers.get("Cache-Control"), "no-store");
      assert.deepEqual(await response.json(), { error: expected });
    }
    assert.equal(calls, 7);
  } finally {
    globalThis.fetch = original;
  }
});

test("successful provider response returns bounded reply and no upstream credentials", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "  offline-ready " }, { text: " status  " }] } }],
    }), { status: 200, headers: { "Content-Type": "application/json" } });
    const response = await worker.fetch(ask(), env);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { reply: "offline-ready  status" });
    assert.ok(!body.includes(env.GEMINI_API_KEY));
    assert.ok(!body.includes(env.DEVICE_TOKEN));
  } finally {
    globalThis.fetch = original;
  }
});
