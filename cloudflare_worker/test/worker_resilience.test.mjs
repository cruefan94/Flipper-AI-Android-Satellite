import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const base = "https://flipper-ai-satellite.example.workers.dev";
const env = {
  DEVICE_TOKEN: "testonly-1234567890-1234567890",
  GEMINI_API_KEY: "example-api-key-not-real",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function request(path, { method = "POST", token = env.DEVICE_TOKEN, body = '{"message":"status"}', contentType = "application/json" } = {}) {
  return new Request(base + path, {
    method,
    headers: { "X-Device-Token": token, "Content-Type": contentType },
    ...(method === "GET" || method === "HEAD" ? {} : { body }),
  });
}
test("rejects unsupported methods, paths, query strings and media types", async () => {
  for (const [req, status] of [
    [request("/ask", { method: "GET" }), 405],
    [request("/ask?debug=1"), 404],
    [request("/other"), 404],
    [request("/ask", { contentType: "text/plain" }), 415],
  ]) {
    const response = await worker.fetch(req, env);
    assert.equal(response.status, status);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
  }
});
test("rejects invalid JSON, missing message and control characters", async () => {
  for (const body of [
    "{", "null", "[]", "{}", '{"message":0}', '{"message":""}',
    '{"message":"   "}', '{"message":"bad\\u0000data"}',
  ]) {
    const response = await worker.fetch(request("/ask", { body }), env);
    assert.equal(response.status, 400, body);
  }
});
test("provider network failure does not expose credentials or upstream error text", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("secret provider diagnostic"); };
  try {
    const response = await worker.fetch(request("/ask"), env);
    assert.equal(response.status, 504);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: "ai_provider_unreachable" });
    assert.ok(!body.includes(env.GEMINI_API_KEY));
    assert.ok(!body.includes(env.DEVICE_TOKEN));
    assert.ok(!body.includes("secret provider diagnostic"));
  } finally { globalThis.fetch = original; }
});
test("provider empty and malformed replies are sanitized", async () => {
  const original = globalThis.fetch;
  try {
    for (const [upstream, expected] of [
      [new Response("not-json", { status: 200 }), "invalid_ai_provider_response"],
      [new Response(JSON.stringify({ candidates: [] }), { status: 200 }), "empty_ai_reply"],
      [new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "  " }] } }] }), { status: 200 }), "empty_ai_reply"],
    ]) {
      globalThis.fetch = async () => upstream;
      const response = await worker.fetch(request("/ask"), env);
      assert.equal(response.status, 502);
      assert.deepEqual(await response.json(), { error: expected });
    }
  } finally { globalThis.fetch = original; }
});
test("invalid model configuration blocks upstream call", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("must not call upstream"); };
  try {
    const response = await worker.fetch(request("/ask"), { ...env, GEMINI_MODEL: "https://bad.example/" });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: "invalid_model_configuration" });
  } finally { globalThis.fetch = original; }
});
