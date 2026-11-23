import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic configuration and authorization regression tests. No live network.
const origin = "https://example.invalid";
const good = Object.freeze({
  DEVICE_TOKEN: "synthetic-device-token-123456789",
  GEMINI_API_KEY: "synthetic-provider-key",
});
const paths = ["/ask", "/ask-v2"];

async function forbidProvider(fn) {
  const previous = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("unexpected upstream request"); };
  try {
    await fn();
    assert.equal(calls, 0, "rejected request reached provider");
  } finally {
    globalThis.fetch = previous;
  }
}

function request(path, token = good.DEVICE_TOKEN) {
  return new Request(origin + path, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Device-Token": token },
    body: JSON.stringify({ message: "hello" }),
  });
}

test("both endpoints fail closed on missing, weak, or invalid server configuration", async () => {
  await forbidProvider(async () => {
    for (const path of paths) {
      const cases = [
        { ...good, DEVICE_TOKEN: "" },
        { ...good, DEVICE_TOKEN: "short" },
        { ...good, DEVICE_TOKEN: "bad token with spaces" },
        { ...good, GEMINI_API_KEY: "" },
      ];
      for (const env of cases) {
        const response = await worker.fetch(request(path), env);
        assert.equal(response.status, 503, path);
        assert.deepEqual(await response.json(), { error: "server_not_configured" });
        assert.equal(response.headers.get("Cache-Control"), "no-store");
      }
      const invalidModel = await worker.fetch(request(path), { ...good, GEMINI_MODEL: "../unsafe" });
      assert.equal(invalidModel.status, 503);
      assert.deepEqual(await invalidModel.json(), { error: "invalid_model_configuration" });
    }
  });
});

test("both endpoints reject absent and modified tokens before provider access", async () => {
  await forbidProvider(async () => {
    for (const path of paths) {
      for (const token of ["", good.DEVICE_TOKEN + "x", good.DEVICE_TOKEN.slice(0, -1)]) {
        const response = await worker.fetch(request(path, token), good);
        assert.equal(response.status, 401, path);
        assert.deepEqual(await response.json(), { error: "unauthorized" });
        assert.equal(response.headers.get("Cache-Control"), "no-store");
      }
    }
  });
});
