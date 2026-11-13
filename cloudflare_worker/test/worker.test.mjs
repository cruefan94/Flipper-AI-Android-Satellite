import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const base = "https://flipper-ai-satellite.example.workers.dev";
const env = {
  DEVICE_TOKEN: "testonly-1234567890-1234567890",
  GEMINI_API_KEY: "example-api-key-not-real",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
const call = (path, token, message) => worker.fetch(new Request(base + path, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    ...(token ? { "X-Device-Token": token } : {}),
  },
  body: JSON.stringify({ message }),
}), env);

test("public health check does not expose secrets", async () => {
  const response = await worker.fetch(new Request(base + "/health"), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: "flipper-ai-satellite" });
});

test("rejects unauthorized requests before any provider call", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("provider must not be called"); };
  try {
    const response = await call("/ask", "incorrect-token", "hello");
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { error: "unauthorized" });
  } finally { globalThis.fetch = original; }
});

test("rejects invalid message and oversized body", async () => {
  let response = await call("/ask", env.DEVICE_TOKEN, "x".repeat(128));
  assert.equal(response.status, 400);
  response = await call("/ask", env.DEVICE_TOKEN, "x".repeat(1100));
  assert.equal(response.status, 400);
});

test("rejects missing server secrets without calling provider", async () => {
  const response = await worker.fetch(new Request(base + "/ask", { method: "POST" }), {
    DEVICE_TOKEN: "", GEMINI_API_KEY: "",
  });
  assert.equal(response.status, 503);
});

test("forwards authorized requests using Gemini API and returns Android-compatible reply", async () => {
  const original = globalThis.fetch;
  let called = false;
  globalThis.fetch = async (url, options) => {
    called = true;
    assert.match(url, /generativelanguage.googleapis.com\/v1beta\/models\/gemini-3\.5-flash-lite:generateContent/);
    assert.equal(options.method, "POST");
    assert.equal(options.headers["x-goog-api-key"], env.GEMINI_API_KEY);
    assert.equal(JSON.parse(options.body).contents[0].parts[0].text, "Hello Flipper");
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "AI online reply" }] } }],
    }), { status: 200 });
  };
  try {
    const response = await call("/ask", env.DEVICE_TOKEN, "Hello Flipper");
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { reply: "AI online reply" });
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    assert.equal(called, true);
  } finally { globalThis.fetch = original; }
});

test("provider errors are sanitized and output stays beneath Android 4096-byte limit", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response("provider internal error", { status: 429 });
    let response = await call("/ask", env.DEVICE_TOKEN, "test");
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "ai_provider_quota_or_rate_limit" });

    globalThis.fetch = async () => new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "😀".repeat(2300) }] } }],
    }), { status: 200 });
    response = await call("/ask", env.DEVICE_TOKEN, "test");
    assert.equal(response.status, 200);
    const bytes = Buffer.byteLength(await response.text(), "utf8");
    assert.ok(bytes < 4096, `response was ${bytes} bytes`);
  } finally { globalThis.fetch = original; }
});

test("model access and credential errors are classified without leaking provider bodies", async () => {
  const original = globalThis.fetch;
  try {
    for (const [status, error] of [
      [400, "ai_provider_bad_request"],
      [401, "ai_provider_key_invalid"],
      [403, "ai_provider_access_denied"],
      [404, "ai_provider_model_unavailable"],
      [503, "ai_provider_overloaded"],
    ]) {
      globalThis.fetch = async () => new Response("SENSITIVE provider error text", { status });
      const response = await call("/ask", env.DEVICE_TOKEN, "test");
      assert.equal(response.status, 502);
      assert.deepEqual(await response.json(), { error });
    }
  } finally { globalThis.fetch = original; }
});
