package com.northerncircuitstudios.ai;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local-only OpenAI-compatible llama.cpp adapter.
 * NO remote addresses, redirects, proxies, cloud token or hardware commands.
 */
public final class LocalAiClient {
    private volatile HttpURLConnection active;

    public void cancel() {
        HttpURLConnection previous = active;
        if (previous != null) previous.disconnect();
    }

    public boolean serverReachable() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(AssistantPolicy.LOCAL_MODELS_ENDPOINT)
                .toURL().openConnection(Proxy.NO_PROXY);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(4000);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            return status == 200;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public String reply(List<AssistantPolicy.Entry> conversation) throws Exception {
        List<AssistantPolicy.Entry> context = AssistantPolicy.recentContext(conversation);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system")
            .put("content", AssistantPolicy.SYSTEM_PROMPT));
        for (AssistantPolicy.Entry entry : context)
            messages.put(new JSONObject().put("role", entry.role).put("content", entry.text));
        JSONObject request = new JSONObject()
            .put("model", AssistantPolicy.MODEL_ALIAS)
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", 320)
            .put("temperature", 0.65);
        byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > 24000) throw new IllegalArgumentException("Conversation context too large");

        HttpURLConnection connection = (HttpURLConnection) new URI(AssistantPolicy.LOCAL_ENDPOINT)
            .toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(150000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setFixedLengthStreamingMode(payload.length);
        active = connection;
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            if (status != 200)
                throw new IllegalStateException("Local AI server returned HTTP " + status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[4096];
                for (int n; (n = input.read(chunk)) != -1;) {
                    if (n == 0) continue;
                    if (bytes.size() + n > 131072)
                        throw new IllegalStateException("Local AI reply too long");
                    bytes.write(chunk, 0, n);
                }
            }
            JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0)
                throw new IllegalStateException("Local AI returned no choices");
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            String answer = message == null ? "" : message.optString("content", "").trim();
            if (answer.isEmpty())
                throw new IllegalStateException("Local AI returned an empty text answer");
            if (answer.length() > 12000)
                throw new IllegalStateException("Local AI answer exceeds history limit");
            return answer;
        } finally {
            active = null;
            connection.disconnect();
        }
    }
}
