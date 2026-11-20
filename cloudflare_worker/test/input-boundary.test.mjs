import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const url = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "test-only-token-1234567890123456",
  GEMINI_API_KEY: "test-only-provider-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function ask(body, contentType = "application/json") {
  return new Request(url, {
    method: "POST",
    headers: { "X-Device-Token": env.DEVICE_TOKEN, "Content-Type": contentType },
    body,
  });
}

test("invalid JSON, oversized body, and invalid message fail before provider call", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw Error("unexpected upstream call"); };
  try {
    const cases = [
      [ask("{"), 400, "invalid_json_or_oversized_body"],
      [ask(JSON.stringify({ message: "x".repeat(1025) })), 400, "invalid_json_or_oversized_body"],
      [ask(JSON.stringify({ message: "" })), 400, "invalid_message"],
      [ask(JSON.stringify({ message: " ".repeat(3) })), 400, "invalid_message"],
      [ask(JSON.stringify({ message: "x".repeat(128) })), 400, "invalid_message"],
      [ask(JSON.stringify({ message: "bad\\u0000value" })), 400, "invalid_message"],
      [ask(JSON.stringify({ message: "valid" }), "text/plain"), 415, "json_required"],
    ];
    for (const [request, status, error] of cases) {
      const response = await worker.fetch(request, env);
      assert.equal(response.status, status);
      assert.deepEqual(await response.json(), { error });
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = original;
  }
});

test("invalid worker secrets or model configuration fail closed without upstream call", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw Error("unexpected upstream call"); };
  try {
    const cases = [
      [{ ...env, DEVICE_TOKEN: "short" }, 503, "server_not_configured"],
      [{ ...env, GEMINI_API_KEY: "" }, 503, "server_not_configured"],
      [{ ...env, GEMINI_MODEL: "not-a-gemini-model" }, 503, "invalid_model_configuration"],
    ];
    for (const [config, status, error] of cases) {
      const response = await worker.fetch(ask(JSON.stringify({ message: "hello" })), config);
      assert.equal(response.status, status);
      assert.deepEqual(await response.json(), { error });
      assert.equal(response.headers.get("Cache-Control"), "no-store");
    }
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = original;
  }
});
