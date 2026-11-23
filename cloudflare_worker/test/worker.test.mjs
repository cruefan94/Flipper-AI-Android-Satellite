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


test("v2 accepts 1600-character UTF-8 messages without changing legacy /ask", async () => {
  const original = globalThis.fetch;
  const longPrompt = "Company background: " + "a".repeat(1600 - "Company background: ".length);
  let captured = null;
  globalThis.fetch = async (url, options) => {
    captured = JSON.parse(options.body).contents[0].parts[0].text;
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Brand understood for this request." }] } }],
    }), { status: 200 });
  };
  try {
    const legacy = await call("/ask", env.DEVICE_TOKEN, longPrompt);
    assert.equal(legacy.status, 400, "old Android clients keep 127-character limit");
    assert.equal(captured, null, "legacy rejection must not invoke provider");
    const response = await call("/ask-v2", env.DEVICE_TOKEN, longPrompt);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { reply: "Brand understood for this request." });
    assert.equal(captured, longPrompt, "entire prompt must arrive without truncation");
    const emoji = "😀".repeat(800);
    const validUnicode = await call("/ask-v2", env.DEVICE_TOKEN, emoji);
    assert.equal(validUnicode.status, 200, "1600 UTF-16 units should fit the safe byte budget");
    assert.equal(captured, emoji);
  } finally { globalThis.fetch = original; }
});

test("v2 rejects unauthorized users and excessive prompts before upstream", async () => {
  const original = globalThis.fetch;
  let upstreamCalled = false;
  globalThis.fetch = async () => { upstreamCalled = true; throw new Error("must never fetch"); };
  try {
    let res = await call("/ask-v2", "invalid-device-token", "x".repeat(1500));
    assert.equal(res.status, 401);
    res = await call("/ask-v2", env.DEVICE_TOKEN, "x".repeat(1601));
    assert.equal(res.status, 400);
    res = await call("/ask-v2", env.DEVICE_TOKEN, "😀".repeat(2000));
    assert.equal(res.status, 400);
    res = await call("/ask-v2", env.DEVICE_TOKEN, "line\ninvalid");
    assert.equal(res.status, 400);
    res = await worker.fetch(new Request(base + "/ask-v2", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Device-Token": env.DEVICE_TOKEN },
      body: "{" + "a".repeat(9000) + "}",
    }), env);
    assert.equal(res.status, 400);
    assert.equal(upstreamCalled, false);
  } finally { globalThis.fetch = original; }
});

test("v2 maintains explicit method and content-type boundaries", async () => {
  const method = await worker.fetch(new Request(base + "/ask-v2", {
    method: "GET",
  }), env);
  assert.equal(method.status, 405);
  const query = await worker.fetch(new Request(base + "/ask-v2?token=bad", {
    method: "POST",
  }), env);
  assert.equal(query.status, 404);
  const invalidType = await worker.fetch(new Request(base + "/ask-v2", {
    method: "POST",
    headers: { "Content-Type": "text/plain", "X-Device-Token": env.DEVICE_TOKEN },
    body: "hello",
  }), env);
  assert.equal(invalidType.status, 415);
});
