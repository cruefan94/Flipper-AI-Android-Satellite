// HTTPS-only Gemini proxy for the Flipper AI Android Satellite.
// Contract: POST /ask, X-Device-Token header, {"message":"..."}, {"reply":"..."}.
// No API credentials, prompts, or device tokens are logged or stored.

const HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function respond(status, data, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...HEADERS, ...extraHeaders },
  });
}

function equalToken(provided, expected) {
  if (typeof provided !== "string" || typeof expected !== "string") return false;
  // Compare every character, including when lengths differ.
  let difference = provided.length ^ expected.length;
  for (let i = 0; i < Math.max(provided.length, expected.length); i++) {
    difference |= (provided.charCodeAt(i) || 0) ^ (expected.charCodeAt(i) || 0);
  }
  return difference === 0;
}

async function readLimitedBody(request, limitBytes) {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limitBytes) {
      await reader.cancel();
      throw new Error("request too large");
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}

function boundedReply(reply) {
  const points = Array.from(reply.trim()).slice(0, 2800);
  while (points.length &&
         new TextEncoder().encode(JSON.stringify({ reply: points.join("") })).byteLength > 3600) {
    points.pop();
  }
  return points.join("");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health" && request.method === "GET") {
      return respond(200, { ok: true, service: "flipper-ai-satellite" });
    }
    if (url.pathname !== "/ask" || url.search) {
      return respond(404, { error: "not_found" });
    }
    if (request.method !== "POST") {
      return respond(405, { error: "method_not_allowed" }, { Allow: "POST" });
    }

    if (
      typeof env.DEVICE_TOKEN !== "string" ||
      env.DEVICE_TOKEN.length < 16 ||
      env.DEVICE_TOKEN.length > 256 ||
      !/^[\x21-\x7E]+$/.test(env.DEVICE_TOKEN) ||
      typeof env.GEMINI_API_KEY !== "string" ||
      env.GEMINI_API_KEY.length === 0
    ) {
      return respond(503, { error: "server_not_configured" });
    }

    if (!equalToken(request.headers.get("X-Device-Token") || "", env.DEVICE_TOKEN)) {
      return respond(401, { error: "unauthorized" });
    }
    if ((request.headers.get("Content-Type") || "").split(";")[0].trim().toLowerCase() !== "application/json") {
      return respond(415, { error: "json_required" });
    }

    let payload;
    try {
      payload = JSON.parse(await readLimitedBody(request, 1024));
    } catch {
      return respond(400, { error: "invalid_json_or_oversized_body" });
    }
    const message = payload && payload.message;
    if (
      typeof message !== "string" ||
      message.length < 1 ||
      message.length > 127 ||
      !message.trim() ||
      /[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(message)
    ) {
      return respond(400, { error: "invalid_message" });
    }

    const model = typeof env.GEMINI_MODEL === "string" ? env.GEMINI_MODEL : "gemini-3.5-flash-lite";
    if (!/^gemini-[a-z0-9.-]{1,64}$/.test(model)) {
      return respond(503, { error: "invalid_model_configuration" });
    }

    let upstream;
    try {
      upstream = await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": env.GEMINI_API_KEY,
          },
          body: JSON.stringify({
            contents: [{ role: "user", parts: [{ text: message }] }],
            generationConfig: { maxOutputTokens: 1024 },
          }),
          signal: AbortSignal.timeout(30000),
        }
      );
    } catch {
      return respond(504, { error: "ai_provider_unreachable" });
    }

    if (!upstream.ok) {
      // Never forward provider response bodies, which may contain sensitive data.
      // Return only a fixed classification so callers can troubleshoot safely.
      const category = ({
        400: "ai_provider_bad_request",
        401: "ai_provider_key_invalid",
        403: "ai_provider_access_denied",
        404: "ai_provider_model_unavailable",
        429: "ai_provider_quota_or_rate_limit",
        503: "ai_provider_overloaded",
      })[upstream.status] || "ai_provider_error";
      return respond(502, { error: category });
    }

    try {
      const data = await upstream.json();
      const parts = data?.candidates?.[0]?.content?.parts;
      const reply = Array.isArray(parts)
        ? parts.map(part => typeof part.text === "string" ? part.text : "").join("").trim()
        : "";
      if (!reply) return respond(502, { error: "empty_ai_reply" });
      const trimmed = boundedReply(reply);
      if (!trimmed) return respond(502, { error: "empty_ai_reply" });
      return respond(200, { reply: trimmed });
    } catch {
      return respond(502, { error: "invalid_ai_provider_response" });
    }
  },
};
