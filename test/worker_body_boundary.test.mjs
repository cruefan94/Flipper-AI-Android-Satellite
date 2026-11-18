import test from "node:test";
import assert from "node:assert/strict";
import worker from "../cloudflare_worker/src/index.mjs";

// Offline contract tests only. Never use real credentials or network.
const endpoint = "https://example.invalid/ask";
const env = {
  DEVICE_TOKEN: "synthetic-device-token-123456",
  GEMINI_API_KEY: "synthetic-gemini-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};

function request(body, headers = {}) {
  return new Request(endpoint, {
    method: "POST",
    headers: {
      "X-Device-Token": env.DEVICE_TOKEN,
      "Content-Type": "application/json",
      ...headers,
    },
    body,
  });
}

test("malformed JSON and invalid UTF-8 fail closed without provider access", async () => {
  const oldFetch = globalThis.fetch;
  let providerCalls = 0;
  globalThis.fetch = async () => { providerCalls++; throw new Error("unexpected provider call"); };
  try {
    for (const body of ['{"message":', new Uint8Array([0xc3, 0x28])]) {
      const response = await worker.fetch(request(body), env);
      assert.equal(response.status, 400);
      assert.deepEqual(await response.json(), { error: "invalid_json_or_oversized_body" });
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
    assert.equal(providerCalls, 0);
  } finally {
    globalThis.fetch = oldFetch;
  }
});

test("UTF-8 byte limit blocks oversized multibyte payload before Gemini", async () => {
  const oldFetch = globalThis.fetch;
  let providerCalls = 0;
  globalThis.fetch = async () => { providerCalls++; throw new Error("unexpected provider call"); };
  try {
    const body = JSON.stringify({ message: "😀".repeat(300) });
    assert.ok(Buffer.byteLength(body, "utf8") > 1024);
    const response = await worker.fetch(request(body), env);
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: "invalid_json_or_oversized_body" });
    assert.equal(providerCalls, 0);
  } finally {
    globalThis.fetch = oldFetch;
  }
});

test("wrong content type fails closed before parsing or provider access", async () => {
  const oldFetch = globalThis.fetch;
  let providerCalls = 0;
  globalThis.fetch = async () => { providerCalls++; throw new Error("unexpected provider call"); };
  try {
    const response = await worker.fetch(request('{"message":"hello"}', {
      "Content-Type": "text/plain",
    }), env);
    assert.equal(response.status, 415);
    assert.deepEqual(await response.json(), { error: "json_required" });
    assert.equal(providerCalls, 0);
  } finally {
    globalThis.fetch = oldFetch;
  }
});
