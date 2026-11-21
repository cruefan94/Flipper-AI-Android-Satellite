import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const endpoint = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "testonly-1234567890-1234567890",
  GEMINI_API_KEY: "example-api-key-not-real",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};

function ask() {
  return worker.fetch(new Request(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Device-Token": env.DEVICE_TOKEN,
    },
    body: JSON.stringify({ message: "Hello" }),
  }), env);
}

test("malformed, empty and non-text provider success bodies fail closed with bounded JSON", async () => {
  const original = globalThis.fetch;
  const secret = "SENSITIVE_PROVIDER_BODY_MUST_NOT_LEAK";
  const cases = [
    [new Response(secret + "{", { status: 200 }), "invalid_ai_provider_response"],
    [new Response("null", { status: 200 }), "empty_ai_reply"],
    [new Response("{}", { status: 200 }), "empty_ai_reply"],
    [new Response(JSON.stringify({ candidates: [] }), { status: 200 }), "empty_ai_reply"],
    [new Response(JSON.stringify({ candidates: [{ content: { parts: [{ functionCall: { name: "x" } }] } }] }), { status: 200 }), "empty_ai_reply"],
    [new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "  " }] } }] }), { status: 200 }), "empty_ai_reply"],
  ];
  try {
    for (const [upstream, expected] of cases) {
      let calls = 0;
      globalThis.fetch = async () => { calls++; return upstream; };
      const response = await ask();
      assert.equal(calls, 1);
      assert.equal(response.status, 502);
      assert.equal(response.headers.get("Cache-Control"), "no-store");
      const body = await response.text();
      assert.ok(Buffer.byteLength(body, "utf8") < 4096);
      assert.ok(!body.includes(secret));
      assert.ok(!body.includes(env.GEMINI_API_KEY));
      assert.ok(!body.includes(env.DEVICE_TOKEN));
      assert.deepEqual(JSON.parse(body), { error: expected });
    }
  } finally {
    globalThis.fetch = original;
  }
});

test("network rejection returns sanitized 504 without credentials", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => { throw new Error("SENSITIVE_PROVIDER_DIAGNOSTIC"); };
    const response = await ask();
    assert.equal(response.status, 504);
    assert.deepEqual(await response.json(), { error: "ai_provider_unreachable" });
  } finally {
    globalThis.fetch = original;
  }
});
