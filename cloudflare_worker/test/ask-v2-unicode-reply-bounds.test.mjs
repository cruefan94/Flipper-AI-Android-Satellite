import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Synthetic replies only. No real provider calls, credentials, or personal content.
const env = { DEVICE_TOKEN: "synthetic-device-token-123456789", GEMINI_API_KEY: "synthetic-provider-key" };
for (const path of ["/ask", "/ask-v2"]) {
  test(path + " bounds successful Unicode replies without breaking surrogate pairs", async () => {
    const original = globalThis.fetch;
    let calls = 0;
    try {
      globalThis.fetch = async () => {
        calls++;
        return Response.json({ candidates: [{ content: { parts: [{ text: "😀".repeat(3000) }] } }] });
      };
      const response = await worker.fetch(new Request("https://example.invalid" + path, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Device-Token": env.DEVICE_TOKEN },
        body: JSON.stringify({ message: "synthetic" }),
      }), env);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get("Cache-Control"), "no-store");
      assert.equal(response.headers.get("Content-Type"), "application/json; charset=utf-8");
      const bytes = new Uint8Array(await response.arrayBuffer());
      assert.ok(bytes.byteLength <= 3600, "JSON response exceeds contract byte cap");
      const decoded = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
      const payload = JSON.parse(decoded);
      assert.ok(payload.reply.length > 0);
      assert.ok(Array.from(payload.reply).length <= 2800);
      assert.match(payload.reply, /^(😀)+$/u);
      assert.equal(calls, 1, "provider must be called once");
    } finally {
      globalThis.fetch = original;
    }
  });
}
