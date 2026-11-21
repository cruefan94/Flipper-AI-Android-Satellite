import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

// Contract-only test. No network calls, live tokens or production deployment.
const endpoint = "https://flipper-ai-satellite.example.workers.dev/ask";
const env = {
  DEVICE_TOKEN: "test-only-device-token-0123456789",
  GEMINI_API_KEY: "test-only-provider-key-0123456789",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};

test("authenticated /ask isolates device credential from Gemini and provider key from Android", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  const prompt = "offline-first test prompt";
  globalThis.fetch = async (url, options) => {
    calls++;
    assert.equal(
      url,
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",
    );
    assert.equal(options.method, "POST");
    assert.equal(options.headers["x-goog-api-key"], env.GEMINI_API_KEY);
    assert.equal(options.headers["Content-Type"], "application/json");
    assert.ok(!JSON.stringify(options.headers).includes(env.DEVICE_TOKEN));
    assert.ok(!options.body.includes(env.DEVICE_TOKEN));
    assert.ok(!options.body.includes(env.GEMINI_API_KEY));
    assert.deepEqual(JSON.parse(options.body), {
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig: { maxOutputTokens: 1024 },
    });
    assert.ok(options.signal);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "test reply" }] } }],
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const response = await worker.fetch(new Request(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Device-Token": env.DEVICE_TOKEN,
      },
      body: JSON.stringify({ message: prompt }),
    }), env);
    assert.equal(calls, 1);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    assert.deepEqual(await response.json(), { reply: "test reply" });
    assert.ok(!JSON.stringify([...response.headers]).includes(env.GEMINI_API_KEY));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("invalid credentials cannot reach upstream even with valid JSON", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    throw new Error("must not reach upstream");
  };
  try {
    for (const token of ["", "wrong", env.DEVICE_TOKEN + "x"]) {
      const response = await worker.fetch(new Request(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Device-Token": token,
        },
        body: JSON.stringify({ message: "status" }),
      }), env);
      assert.equal(response.status, 401);
      assert.deepEqual(await response.json(), { error: "unauthorized" });
    }
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
