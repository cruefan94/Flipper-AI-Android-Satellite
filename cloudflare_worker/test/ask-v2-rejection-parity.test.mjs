import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Offline-only negative-path parity; no live Worker or Gemini requests.
const origin = "https://example.invalid";
const env = Object.freeze({
  DEVICE_TOKEN: "synthetic-device-token-123456789",
  GEMINI_API_KEY: "synthetic-provider-key",
});
const paths = ["/ask", "/ask-v2"];

async function withForbiddenProvider(run) {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw Error("provider must not be called"); };
  try { await run(); assert.equal(calls, 0, "rejected requests must not reach Gemini"); }
  finally { globalThis.fetch = original; }
}

function post(path, body, contentType = "application/json") {
  return new Request(origin + path, {
    method: "POST",
    headers: { "X-Device-Token": env.DEVICE_TOKEN, "Content-Type": contentType },
    body,
  });
}

test("both endpoints reject method, query and content-type violations before upstream", async () => {
  await withForbiddenProvider(async () => {
    for (const path of paths) {
      const method = await worker.fetch(new Request(origin + path, { method: "GET" }), env);
      assert.equal(method.status, 405);
      assert.equal(method.headers.get("Allow"), "POST");
      const query = await worker.fetch(post(path + "?debug=true", '{"message":"hello"}'), env);
      assert.equal(query.status, 404);
      const type = await worker.fetch(post(path, '{"message":"hello"}', "text/plain"), env);
      assert.equal(type.status, 415);
      for (const response of [method, query, type]) {
        assert.equal(response.headers.get("Cache-Control"), "no-store");
      }
    }
  });
});

test("both endpoints reject malformed JSON and reject oversized byte bodies before upstream", async () => {
  await withForbiddenProvider(async () => {
    for (const [path, cap] of [["/ask", 1024], ["/ask-v2", 8192]]) {
      const malformed = await worker.fetch(post(path, '{"message":'), env);
      assert.equal(malformed.status, 400);
      assert.deepEqual(await malformed.json(), { error: "invalid_json_or_oversized_body" });
      const oversized = await worker.fetch(post(path, JSON.stringify({
        message: "hello", ignored: "x".repeat(cap),
      })), env);
      assert.equal(oversized.status, 400);
      assert.deepEqual(await oversized.json(), { error: "invalid_json_or_oversized_body" });
    }
  });
});

test("v2 does not relax legacy validation of empty and control-character prompts", async () => {
  await withForbiddenProvider(async () => {
    for (const path of paths) {
      for (const message of ["", "   ", "a\\u0000b", "a\\u001fb"]) {
        const response = await worker.fetch(post(path, JSON.stringify({ message })), env);
        assert.equal(response.status, 400, path + " should reject invalid prompt");
        assert.deepEqual(await response.json(), { error: "invalid_message" });
      }
    }
  });
});
