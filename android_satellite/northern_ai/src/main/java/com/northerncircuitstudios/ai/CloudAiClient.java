package com.northerncircuitstudios.ai;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * HTTPS-only client for the owner's existing Cloudflare /ask Gemini proxy.
 * Cloud mode is opt-in and only one current message is transmitted, no history.
 * Device token is provided via Android Keystore storage, never a bundled secret.
 */
public final class CloudAiClient {
    private volatile HttpsURLConnection active;

    public void cancel() {
        HttpsURLConnection current = active;
        if (current != null) current.disconnect();
    }

    private HttpsURLConnection open(String path) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URI(
            CloudModePolicy.WORKER_ORIGIN + path).toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(35000);
        connection.setUseCaches(false);
        return connection;
    }

    public boolean health() throws Exception {
        HttpsURLConnection connection = open("/health");
        active = connection;
        try {
            connection.setRequestMethod("GET");
            return connection.getResponseCode() == 200;
        } finally {
            active = null;
            connection.disconnect();
        }
    }

    public String reply(String prompt, String token) throws Exception {
        CloudModePolicy.validateCloudPrompt(prompt);
        CloudModePolicy.validateToken(token);
        JSONObject request = new JSONObject().put("message", prompt);
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > 8192) throw new IllegalArgumentException("Cloud request too large");

        final boolean extended = prompt.length() > CloudModePolicy.LEGACY_MAX_CLOUD_CHARS;
        HttpsURLConnection connection = open(extended ? "/ask-v2" : "/ask");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Device-Token", token);
        connection.setFixedLengthStreamingMode(body.length);
        active = connection;
        try {
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            if (status != 200) {
                // Return fixed classifications; do not expose provider response bodies.
                if (status == 404 && extended) throw new IllegalStateException(
                    "Long cloud messages require the updated Cloudflare Worker. " +
                    "The new /ask-v2 endpoint is not deployed yet; shorter requests still work.");
                if (status == 400 && extended) throw new IllegalStateException(
                    "Cloud Worker rejected this long message. Check /ask-v2 deployment.");
                if (status == 401) throw new IllegalStateException(
                    "Cloud unauthorized (401). Check Worker DEVICE_TOKEN.");
                if (status == 503) throw new IllegalStateException(
                    "Cloud backend not configured (503). Check Worker secrets.");
                if (status == 429) throw new IllegalStateException(
                    "Cloud rate limited (429). Try again later.");
                if (status == 502) throw new IllegalStateException(
                    "Cloud AI provider failed (502). Check Worker/Gemini diagnostics.");
                throw new IllegalStateException("Cloud request failed (HTTP " + status + ").");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] piece = new byte[2048];
                for (int n; (n = in.read(piece)) >= 0;) {
                    if (n == 0) continue;
                    if (buffer.size() + n > 8192)
                        throw new IllegalStateException("Cloud reply exceeds size limit");
                    buffer.write(piece, 0, n);
                }
            }
            String answer = new JSONObject(
                buffer.toString(StandardCharsets.UTF_8.name()))
                .optString("reply", "").trim();
            if (answer.isEmpty() || answer.length() > 12000)
                throw new IllegalStateException("Cloud returned an empty or invalid answer");
            return answer;
        } finally {
            active = null;
            connection.disconnect();
        }
    }
}
