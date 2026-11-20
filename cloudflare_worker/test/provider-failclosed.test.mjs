import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const url = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "test-only-token-1234567890123456",
  GEMINI_API_KEY: "test-only-provider-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function request(token = env.DEVICE_TOKEN) {
  return new Request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Device-Token": token },
    body: JSON.stringify({ message: "safe test prompt" }),
  });
}
test("provider network failure returns sanitized 504 without secrets", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("private upstream diagnostic"); };
  try {
    const response = await worker.fetch(request(), env);
    assert.equal(response.status, 504);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: "ai_provider_unreachable" });
    assert.equal(body.includes("private upstream diagnostic"), false);
    assert.equal(body.includes(env.GEMINI_API_KEY), false);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
  } finally { globalThis.fetch = original; }
});
test("malformed Gemini JSON returns sanitized 502", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response("{private malformed response", { status: 200 });
  try {
    const response = await worker.fetch(request(), env);
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "invalid_ai_provider_response" });
  } finally { globalThis.fetch = original; }
});
test("empty Gemini candidates return sanitized 502", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ candidates: [] }), { status: 200 });
  try {
    const response = await worker.fetch(request(), env);
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "empty_ai_reply" });
  } finally { globalThis.fetch = original; }
});
