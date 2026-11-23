import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic-only: no real cloud requests, credentials, or user prompts.
const env = { DEVICE_TOKEN: "synthetic-device-token-123456789", GEMINI_API_KEY: "synthetic-provider-key" };
const paths = ["/ask", "/ask-v2"];
function request(path) {
  return new Request("https://example.invalid" + path, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Device-Token": env.DEVICE_TOKEN },
    body: JSON.stringify({ message: "synthetic prompt" }),
  });
}
async function assertSafe(path, fetchMock, status, error) {
  const original = globalThis.fetch;
  let calls = 0;
  try {
    globalThis.fetch = async (...args) => {
      calls++;
      return fetchMock(...args);
    };
    const response = await worker.fetch(request(path), env);
    assert.equal(response.status, status, path);
    assert.deepEqual(await response.json(), { error });
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    assert.equal(response.headers.get("Content-Type"), "application/json; charset=utf-8");
    assert.equal(response.headers.get("X-Device-Token"), null);
    assert.equal(calls, 1, "only one provider request, no implicit retry");
  } finally {
    globalThis.fetch = original;
  }
}
test("both endpoints fail safely on upstream network errors without retries", async () => {
  for (const path of paths) {
    await assertSafe(path, async () => { throw new TypeError("PRIVATE_NETWORK_DIAGNOSTIC"); }, 504, "ai_provider_unreachable");
  }
});
test("both endpoints reject malformed successful provider responses", async () => {
  for (const path of paths) {
    await assertSafe(path, async () => new Response("PRIVATE_INVALID_PROVIDER_BODY", { status: 200 }), 502, "invalid_ai_provider_response");
    await assertSafe(path, async () => Response.json({ candidates: [] }), 502, "empty_ai_reply");
    await assertSafe(path, async () => Response.json({ candidates: [{ content: { parts: [{ text: "   " }] } }] }), 502, "empty_ai_reply");
  }
});
