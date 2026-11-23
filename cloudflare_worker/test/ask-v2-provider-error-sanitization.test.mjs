import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic-only regression: provider diagnostics must never expose response bodies.
const env = { DEVICE_TOKEN: "synthetic-device-token-123456789", GEMINI_API_KEY: "synthetic-provider-key" };
const paths = ["/ask", "/ask-v2"];
const origin = "https://example.invalid";
const request = path => new Request(origin + path, {
  method: "POST",
  headers: { "Content-Type": "application/json", "X-Device-Token": env.DEVICE_TOKEN },
  body: JSON.stringify({ message: "hello" }),
});

test("both cloud endpoints sanitize provider errors without leaking response bodies", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  try {
    for (const path of paths) {
      for (const [status, category] of [
        [400, "ai_provider_bad_request"],
        [401, "ai_provider_key_invalid"],
        [403, "ai_provider_access_denied"],
        [404, "ai_provider_model_unavailable"],
        [429, "ai_provider_quota_or_rate_limit"],
        [503, "ai_provider_overloaded"],
        [500, "ai_provider_error"],
      ]) {
        const marker = "PRIVATE_PROVIDER_BODY_MUST_NOT_LEAK";
        globalThis.fetch = async () => {
          calls++;
          return new Response(marker, { status });
        };
        const response = await worker.fetch(request(path), env);
        assert.equal(response.status, 502, path + " provider " + status);
        assert.deepEqual(await response.json(), { error: category });
        assert.equal(response.headers.get("Cache-Control"), "no-store");
        assert.equal(response.headers.get("Content-Type"), "application/json; charset=utf-8");
        assert.equal(response.headers.get("X-Device-Token"), null);
      }
    }
    assert.equal(calls, paths.length * 7);
  } finally {
    globalThis.fetch = original;
  }
});
