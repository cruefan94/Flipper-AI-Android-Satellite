import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic-only tests: no real Worker/Gemini requests or credentials.
const base = "https://example.invalid";
const env = Object.freeze({
  DEVICE_TOKEN: "synthetic-device-token-123456789",
  GEMINI_API_KEY: "synthetic-provider-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
});

function request(path, token, message) {
  return new Request(base + path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { "X-Device-Token": token } : {}),
    },
    body: JSON.stringify({ message }),
  });
}

test("legacy and v2 reject absent/wrong tokens without provider access", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("unexpected provider call"); };
  try {
    for (const path of ["/ask", "/ask-v2"]) {
      for (const token of [undefined, "wrong-token"]) {
        const result = await worker.fetch(request(path, token, "test"), env);
        assert.equal(result.status, 401);
        assert.deepEqual(await result.json(), { error: "unauthorized" });
        assert.equal(result.headers.get("Cache-Control"), "no-store");
      }
    }
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = original;
  }
});

test("legacy and v2 sanitize provider failures and never expose response bodies", async () => {
  const original = globalThis.fetch;
  const marker = "SYNTHETIC_UPSTREAM_BODY_MUST_NOT_APPEAR";
  try {
    for (const path of ["/ask", "/ask-v2"]) {
      globalThis.fetch = async () => new Response(marker, { status: 503 });
      let result = await worker.fetch(request(path, env.DEVICE_TOKEN, "test"), env);
      assert.equal(result.status, 502);
      let body = await result.text();
      assert.deepEqual(JSON.parse(body), { error: "ai_provider_overloaded" });
      assert.equal(body.includes(marker), false);

      globalThis.fetch = async () => { throw new Error(marker); };
      result = await worker.fetch(request(path, env.DEVICE_TOKEN, "test"), env);
      assert.equal(result.status, 504);
      body = await result.text();
      assert.deepEqual(JSON.parse(body), { error: "ai_provider_unreachable" });
      assert.equal(body.includes(marker), false);
    }
  } finally {
    globalThis.fetch = original;
  }
});

test("v2 preserves legacy 127-character boundary and never forwards rejected payloads", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("unexpected provider call"); };
  try {
    for (const [path, message, expected] of [
      ["/ask", "x".repeat(128), 400],
      ["/ask-v2", "x".repeat(1601), 400],
      ["/ask-v2", "\u0000", 400],
    ]) {
      const result = await worker.fetch(request(path, env.DEVICE_TOKEN, message), env);
      assert.equal(result.status, expected);
    }
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = original;
  }
});
