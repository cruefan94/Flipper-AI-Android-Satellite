import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic credentials only. Never contact the deployed Worker or Gemini.
const base = "https://example.invalid";
const env = {
  DEVICE_TOKEN: "synthetic-device-token-123456",
  GEMINI_API_KEY: "synthetic-provider-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function request(path, { method = "POST", token = env.DEVICE_TOKEN, contentType = "application/json", body = '{"message":"hello"}' } = {}) {
  return new Request(base + path, {
    method,
    headers: {
      "Content-Type": contentType,
      ...(token === null ? {} : { "X-Device-Token": token }),
    },
    ...(method === "GET" || method === "HEAD" ? {} : { body }),
  });
}
async function withoutProvider(fn) {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("unexpected provider invocation"); };
  try { await fn(); } finally { globalThis.fetch = original; }
}
test("unauthorized and absent tokens fail closed without invoking provider", async () => {
  await withoutProvider(async () => {
    for (const token of [null, "", "wrong-token"]) {
      const response = await worker.fetch(request("/ask", { token }), env);
      assert.equal(response.status, 401);
      assert.deepEqual(await response.json(), { error: "unauthorized" });
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
  });
});
test("unsupported method, query string and media type cannot reach provider", async () => {
  await withoutProvider(async () => {
    for (const [req, status] of [
      [request("/ask", { method: "GET" }), 405],
      [request("/ask?token=synthetic", {}), 404],
      [request("/ask", { contentType: "text/plain" }), 415],
    ]) {
      const response = await worker.fetch(req, env);
      assert.equal(response.status, status);
    }
  });
});
test("malformed and oversized JSON are rejected before provider invocation", async () => {
  await withoutProvider(async () => {
    for (const body of ["{", JSON.stringify({ message: "a".repeat(1025) }), JSON.stringify({ message: "\u0000" })]) {
      const response = await worker.fetch(request("/ask", { body }), env);
      assert.equal(response.status, 400);
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
  });
});
test("provider network errors and invalid JSON return sanitized errors", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => { throw new Error("SECRET upstream network diagnostics"); };
    let response = await worker.fetch(request("/ask"), env);
    assert.equal(response.status, 504);
    assert.deepEqual(await response.json(), { error: "ai_provider_unreachable" });
    globalThis.fetch = async () => new Response("SECRET upstream malformed JSON", { status: 200 });
    response = await worker.fetch(request("/ask"), env);
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "invalid_ai_provider_response" });
    assert.equal(response.headers.get("Cache-Control"), "no-store");
  } finally { globalThis.fetch = original; }
});
