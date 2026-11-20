import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const url = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "test-only-token-1234567890123456",
  GEMINI_API_KEY: "test-only-provider-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function request(token) {
  return new Request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token === null ? {} : { "X-Device-Token": token }) },
    body: JSON.stringify({ message: "safe test prompt" }),
  });
}
test("missing or invalid device token returns 401 without provider request", async () => {
  const original = globalThis.fetch;
  let upstreamCalls = 0;
  globalThis.fetch = async () => { upstreamCalls++; throw new Error("provider must not be called"); };
  try {
    for (const token of [null, "", "wrong-test-token", env.DEVICE_TOKEN + "-suffix"]) {
      const response = await worker.fetch(request(token), env);
      assert.equal(response.status, 401);
      assert.deepEqual(await response.json(), { error: "unauthorized" });
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
    assert.equal(upstreamCalls, 0);
  } finally {
    globalThis.fetch = original;
  }
});
test("provider 401 maps to sanitized 502 without exposing provider details", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response("private upstream credential diagnostic", { status: 401 });
  try {
    const response = await worker.fetch(request(env.DEVICE_TOKEN), env);
    assert.equal(response.status, 502);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: "ai_provider_key_invalid" });
    assert.equal(body.includes("private upstream credential diagnostic"), false);
    assert.equal(body.includes(env.GEMINI_API_KEY), false);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
  } finally {
    globalThis.fetch = original;
  }
});
