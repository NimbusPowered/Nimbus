package dev.nimbuspowered.nimbus.punishments.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Thin REST client for the controller's punishments API.
 *
 * Blocking I/O deliberately — callers must wrap in CompletableFuture.supplyAsync
 * since commands and chat listeners run on the main thread.
 *
 * Includes a short-lived per-uuid mute cache so chat events aren't blocked on I/O.
 */
public class PunishmentsApiClient {

    private final JavaPlugin plugin;
    private final String apiUrl;
    private final String token;
    private final Gson gson = new Gson();

    // uuid -> (mute record JSON or null) + cached-at millis
    private final ConcurrentHashMap<UUID, MuteCacheEntry> muteCache = new ConcurrentHashMap<>();
    private static final long MUTE_CACHE_TTL_MS = 5_000;

    public PunishmentsApiClient(JavaPlugin plugin, String apiUrl, String token) {
        this.plugin = plugin;
        this.apiUrl = apiUrl;
        this.token = token;
    }

    /** Synchronous issue — pass all identifying fields directly. */
    public ApiResponse issue(String type, String targetUuid, String targetName,
                              String targetIp, String duration, String reason,
                              String issuer, String issuerName) {
        JsonObject body = new JsonObject();
        body.addProperty("type", type);
        if (targetUuid != null) body.addProperty("targetUuid", targetUuid);
        body.addProperty("targetName", targetName);
        if (targetIp != null) body.addProperty("targetIp", targetIp);
        if (duration != null) body.addProperty("duration", duration);
        if (reason != null) body.addProperty("reason", reason);
        body.addProperty("issuer", issuer);
        body.addProperty("issuerName", issuerName);
        return post("/api/punishments", body.toString());
    }

    /** Revoke the active ban for a player (uuid or name). Resolves id first. */
    public ApiResponse unban(String playerUuid) {
        ApiResponse history = get("/api/punishments/player/" + playerUuid);
        if (!history.ok) return history;
        try {
            JsonObject obj = gson.fromJson(history.body, JsonObject.class);
            if (obj == null || !obj.has("punishments")) return error("Malformed response");
            for (var el : obj.getAsJsonArray("punishments")) {
                JsonObject p = el.getAsJsonObject();
                if (!p.get("active").getAsBoolean()) continue;
                String t = p.get("type").getAsString();
                if (t.endsWith("BAN")) {
                    int id = p.get("id").getAsInt();
                    return delete("/api/punishments/" + id);
                }
            }
            return error("No active ban");
        } catch (Exception e) {
            return error("Parse error: " + e.getMessage());
        }
    }

    public ApiResponse unmute(String playerUuid) {
        ApiResponse history = get("/api/punishments/player/" + playerUuid);
        if (!history.ok) return history;
        try {
            JsonObject obj = gson.fromJson(history.body, JsonObject.class);
            if (obj == null || !obj.has("punishments")) return error("Malformed response");
            for (var el : obj.getAsJsonArray("punishments")) {
                JsonObject p = el.getAsJsonObject();
                if (!p.get("active").getAsBoolean()) continue;
                String t = p.get("type").getAsString();
                if (t.endsWith("MUTE")) {
                    int id = p.get("id").getAsInt();
                    return delete("/api/punishments/" + id);
                }
            }
            return error("No active mute");
        } catch (Exception e) {
            return error("Parse error: " + e.getMessage());
        }
    }

    public ApiResponse history(String playerUuid) {
        return get("/api/punishments/player/" + playerUuid);
    }

    /** Check mute with local cache. Returns null if not muted. */
    public JsonObject checkMuteCached(UUID uuid) {
        long now = System.currentTimeMillis();
        MuteCacheEntry cached = muteCache.get(uuid);
        if (cached != null && now - cached.cachedAtMs < MUTE_CACHE_TTL_MS) {
            return cached.record;
        }
        ApiResponse response = get("/api/punishments/mute/" + uuid);
        if (!response.ok) {
            muteCache.put(uuid, new MuteCacheEntry(null, now));
            return null;
        }
        try {
            JsonObject obj = gson.fromJson(response.body, JsonObject.class);
            JsonObject record = (obj != null && obj.has("punished") && obj.get("punished").getAsBoolean()) ? obj : null;
            muteCache.put(uuid, new MuteCacheEntry(record, now));
            return record;
        } catch (Exception e) {
            return null;
        }
    }

    public void invalidateMute(UUID uuid) { muteCache.remove(uuid); }

    // ── HTTP primitives ──────────────────────────────────────────

    public ApiResponse get(String path) {
        return request("GET", path, null);
    }

    public ApiResponse post(String path, String body) {
        return request("POST", path, body);
    }

    public ApiResponse delete(String path) {
        return request("DELETE", path, null);
    }

    private ApiResponse request(String method, String path, String body) {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(apiUrl + path);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            var stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
            }
            return new ApiResponse(code >= 200 && code < 300, code, sb.toString().trim());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Punishments API " + method + " " + path + " failed: " + e.getMessage());
            return error(e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static ApiResponse error(String msg) {
        return new ApiResponse(false, -1, "{\"success\":false,\"message\":\"" + msg + "\"}");
    }

    public static class ApiResponse {
        public final boolean ok;
        public final int status;
        public final String body;
        public ApiResponse(boolean ok, int status, String body) {
            this.ok = ok; this.status = status; this.body = body;
        }
    }

    private static class MuteCacheEntry {
        final JsonObject record;
        final long cachedAtMs;
        MuteCacheEntry(JsonObject record, long cachedAtMs) {
            this.record = record; this.cachedAtMs = cachedAtMs;
        }
    }
}
