import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic request-gate tests. No provider access, user data, or credentials.
const env = { DEVICE_TOKEN: "synthetic-device-token-123456789", GEMINI_API_KEY: "synthetic-provider-key" };
const cases = [
  { label: "missing JSON content type", headers: {}, body: '{"message":"test"}', status: 415, error: "json_required" },
  { label: "malformed JSON", headers: { "Content-Type": "application/json" }, body: "{", status: 400, error: "invalid_json_or_oversized_body" },
  { label: "blank message", headers: { "Content-Type": "application/json" }, body: '{"message":"  "}', status: 400, error: "invalid_message" },
  { label: "non-string message", headers: { "Content-Type": "application/json" }, body: '{"message":42}', status: 400, error: "invalid_message" },
  { label: "control character", headers: { "Content-Type": "application/json" }, body: '{"message":"\\u0001"}', status: 400, error: "invalid_message" },
  { label: "oversized request bytes", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ message: "x".repeat(9000) }), status: 400, error: "invalid_json_or_oversized_body" },
];
for (const path of ["/ask", "/ask-v2"]) {
  for (const item of cases) {
    test(path + " rejects " + item.label + " without contacting Gemini", async () => {
      const original = globalThis.fetch;
      let calls = 0;
      try {
        globalThis.fetch = async () => { calls++; throw new Error("unexpected upstream request"); };
        const response = await worker.fetch(new Request("https://example.invalid" + path, {
          method: "POST",
          headers: { "X-Device-Token": env.DEVICE_TOKEN, ...item.headers },
          body: item.body,
        }), env);
        assert.equal(response.status, item.status);
        assert.equal(response.headers.get("Cache-Control"), "no-store");
        assert.equal(response.headers.get("Content-Type"), "application/json; charset=utf-8");
        assert.deepEqual(await response.json(), { error: item.error });
        assert.equal(calls, 0);
      } finally {
        globalThis.fetch = original;
      }
    });
  }
}
