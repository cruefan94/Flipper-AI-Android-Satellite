// Offline-only Worker contract regressions. No network, deployment or credentials.
import test from "node:test";
import assert from "node:assert/strict";
import worker from "../cloudflare_worker/src/index.mjs";

const base = "https://example.invalid";
const env = {
  DEVICE_TOKEN: "synthetic-test-token-123456789",
  GEMINI_API_KEY: "synthetic-test-key",
  GEMINI_MODEL: "gemini-3.5-flash-lite",
};
function ask(token, message = "test") {
  return worker.fetch(new Request(base + "/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Device-Token": token },
    body: JSON.stringify({ message }),
  }), env);
}
test("GET /ask and query-bearing /ask reject before provider access", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw Error("upstream must not run"); };
  try {
    const get = await worker.fetch(new Request(base + "/ask"), env);
    assert.equal(get.status, 405);
    assert.equal(get.headers.get("Allow"), "POST");
    const query = await worker.fetch(new Request(base + "/ask?token=leak"), env);
    assert.equal(query.status, 404);
  } finally { globalThis.fetch = original; }
});
test("invalid token returns 401 without upstream call or token echo", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw Error("upstream must not run"); };
  try {
    const response = await ask("wrong-token");
    assert.equal(response.status, 401);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: "unauthorized" });
    assert.ok(!body.includes("wrong-token"));
    assert.equal(response.headers.get("Cache-Control"), "no-store");
  } finally { globalThis.fetch = original; }
});
test("provider 502 is sanitized and does not echo provider body", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response("SENSITIVE UPSTREAM BODY", { status: 502 });
  try {
    const response = await ask(env.DEVICE_TOKEN);
    assert.equal(response.status, 502);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: "ai_provider_error" });
    assert.ok(!body.includes("SENSITIVE"));
  } finally { globalThis.fetch = original; }
});
test("provider transport failure is classified as 504 without leaking exception", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => { throw Error("SENSITIVE PROVIDER EXCEPTION"); };
  try {
    const response = await ask(env.DEVICE_TOKEN);
    assert.equal(response.status, 504);
    assert.deepEqual(await response.json(), { error: "ai_provider_unreachable" });
  } finally { globalThis.fetch = original; }
});
