import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Regression gate for Android's bounded /ask JSON contract. Synthetic only:
// no live Gemini call, secrets, Android device or model execution.
const base = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "testonly-1234567890-1234567890",
  GEMINI_API_KEY: "example-api-key-not-real",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function request() {
  return new Request(base, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Device-Token": env.DEVICE_TOKEN },
    body: JSON.stringify({ message: "status" }),
  });
}

test("successful Unicode /ask replies remain valid bounded UTF-8 JSON across multiple parts", async () => {
  const original = globalThis.fetch;
  const cases = [
    ["😀".repeat(3000)],
    ["漢字".repeat(3000)],
    ['\\\"'.repeat(3000)],
    ["prefix ", "🛰️".repeat(2000), " suffix"],
    ["e\u0301".repeat(3000)],
  ];
  try {
    for (const parts of cases) {
      let calls = 0;
      globalThis.fetch = async () => {
        calls++;
        return new Response(JSON.stringify({
          candidates: [{ content: { parts: parts.map(text => ({ text })) } }],
        }), { status: 200 });
      };
      const response = await worker.fetch(request(), env);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get("Cache-Control"), "no-store");
      const body = await response.text();
      const decoded = JSON.parse(body);
      assert.equal(typeof decoded.reply, "string");
      assert.ok(decoded.reply.length > 0);
      assert.ok(Buffer.byteLength(body, "utf8") <= 3600, "bounded response JSON bytes");
      assert.ok(Array.from(decoded.reply).length <= 2800, "bounded Unicode code points");
      assert.ok(parts.join("").trim().startsWith(decoded.reply), "no altered or reordered text");
      assert.equal(calls, 1, "one authorized provider request");
      assert.ok(!body.includes(env.GEMINI_API_KEY));
      assert.ok(!body.includes(env.DEVICE_TOKEN));
    }
  } finally {
    globalThis.fetch = original;
  }
});
