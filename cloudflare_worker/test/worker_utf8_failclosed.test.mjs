import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Regression for malformed bytes and authentication order. Never contact Gemini.
const base = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "testonly-1234567890-1234567890",
  GEMINI_API_KEY: "example-api-key-not-real",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};

function rawRequest(bytes, token = env.DEVICE_TOKEN) {
  return new Request(base, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Device-Token": token,
    },
    body: new Uint8Array(bytes),
  });
}

test("malformed UTF-8 JSON fails closed without contacting Gemini", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    throw new Error("Gemini must not receive malformed bytes");
  };
  try {
    // JSON syntax around a deliberately invalid two-byte UTF-8 sequence.
    const prefix = new TextEncoder().encode('{"message":"');
    const suffix = new TextEncoder().encode('"}');
    for (const malformed of [
      [0xc3, 0x28], // invalid continuation
      [0xed, 0xa0, 0x80], // UTF-8 encoding of a surrogate
      [0xf4, 0x90, 0x80, 0x80], // above Unicode maximum
    ]) {
      const bytes = [...prefix, ...malformed, ...suffix];
      const response = await worker.fetch(rawRequest(bytes), env);
      assert.equal(response.status, 400);
      assert.deepEqual(await response.json(), { error: "invalid_json_or_oversized_body" });
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = original;
  }
});

test("invalid token is rejected before malformed request body is parsed", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("unexpected upstream"); };
  try {
    const response = await worker.fetch(rawRequest([0xff, 0xfe], "incorrect-token"), env);
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { error: "unauthorized" });
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = original;
  }
});
