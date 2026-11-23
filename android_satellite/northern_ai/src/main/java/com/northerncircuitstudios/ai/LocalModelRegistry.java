package com.northerncircuitstudios.ai;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Read-only model discovery on this device's loopback llama.cpp server.
 * /models exposes the multi-model router inventory; /v1/models is a fallback
 * for a traditional single-model server. Never reads Termux private files.
 */
public final class LocalModelRegistry {
    private static final String BASE = "http://127.0.0.1:9931";
    private static final int MAX_ITEMS = 60;
    private static final int MAX_RESPONSE = 262144;
    private volatile HttpURLConnection active;

    public static final class Item {
        public final String id;
        public final String status;
        Item(String id, String status) {
            this.id = id;
            this.status = status;
        }
    }

    public static final class Inventory {
        public final boolean router;
        public final List<Item> items;
        Inventory(boolean router, List<Item> items) {
            this.router = router;
            this.items = Collections.unmodifiableList(items);
        }
    }

    public static String validateModelId(String id) {
        if (id == null || id.isEmpty() || id.length() > 256)
            throw new IllegalArgumentException("Invalid model identifier");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < 33 || c > 126)
                throw new IllegalArgumentException("Model identifier has invalid characters");
        }
        return id;
    }

    public void cancel() {
        HttpURLConnection connection = active;
        if (connection != null) connection.disconnect();
    }

    private JSONObject get(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(BASE + path)
            .toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(6000);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        active = connection;
        try {
            if (connection.getResponseCode() != 200)
                throw new IllegalStateException("Model inventory is not available.");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (n == 0) continue;
                    if (buffer.size() + n > MAX_RESPONSE)
                        throw new IllegalStateException("Model list response too large");
                    buffer.write(chunk, 0, n);
                }
            }
            return new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
        } finally {
            active = null;
            connection.disconnect();
        }
    }

    public Inventory discover() throws Exception {
        JSONObject response;
        boolean router = true;
        try { response = get("/models"); }
        catch (Exception routerError) {
            router = false;
            response = get("/v1/models");
        }
        JSONArray array = response.optJSONArray("data");
        if (array == null) throw new IllegalStateException("Local AI returned no model list.");
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < array.length() && list.size() < MAX_ITEMS; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            try { validateModelId(id); }
            catch (IllegalArgumentException error) { continue; }
            JSONObject statusInfo = item.optJSONObject("status");
            String status = statusInfo == null ? "available" :
                statusInfo.optString("value", "available");
            if (!"loaded".equals(status) && !"unloaded".equals(status) &&
                !"loading".equals(status)) status = "available";
            list.add(new Item(id, status));
        }
        return new Inventory(router, list);
    }
}
