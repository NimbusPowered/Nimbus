package dev.nimbuspowered.nimbus.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces network-wide punishments at the proxy login layer.
 *
 * Strategy: on PreLogin, call {@code GET /api/punishments/check/<uuid>?ip=<ip>} and
 * deny the connection if a ban is active. A short per-uuid in-memory cache (5s by
 * default) protects the controller from reconnect spam.
 *
 * The UUID is Mojang's online-mode UUID — available from
 * {@link PreLoginEvent#getUniqueId()} only if online-mode is on. In offline/bungee
 * mode we fall back to an IP-only lookup, which still catches IPBANs but misses
 * name-based bans.
 */
public class PunishmentLoginListener {

    private final NimbusApiClient api;
    private final Logger logger;
    private final Gson gson = new Gson();

    // Cache entry: the check response body + when it was populated.
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5_000;

    public PunishmentLoginListener(NimbusApiClient api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            if (!event.getResult().isAllowed()) return;   // another listener already denied
            String name = event.getUsername();
            String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

            // Velocity can give us the online-mode UUID via getUniqueId() only after
            // GameProfileRequestEvent. For PreLogin we derive an offline UUID from the name
            // (matches standard offline-mode UUID) so name-based bans still match.
            String uuid = java.util.UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)
            ).toString();

            CacheKey key = new CacheKey(uuid, ip);
            CacheEntry cached = cache.get(key.toString());
            long now = System.currentTimeMillis();
            JsonObject record;
            if (cached != null && now - cached.cachedAtMs < CACHE_TTL_MS) {
                record = cached.record;
            } else {
                String path = "/api/punishments/check/" + uuid
                        + "?ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8);
                try {
                    var response = api.get(path).join();
                    if (!response.isSuccess()) {
                        logger.debug("Punishment check returned HTTP {} for {} — allowing login", response.statusCode(), name);
                        return;
                    }
                    JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
                    record = (obj != null && obj.has("punished") && obj.get("punished").getAsBoolean()) ? obj : null;
                } catch (Exception e) {
                    logger.warn("Punishment check failed for {} ({}): {} — allowing login", name, ip, e.getMessage());
                    return;
                }
                cache.put(key.toString(), new CacheEntry(record, now));
            }

            if (record == null) return;

            Component kick = buildKickMessage(record);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kick));
            logger.info("Denied login for {} ({}): {} ({})",
                    name, ip,
                    record.has("type") ? record.get("type").getAsString() : "?",
                    record.has("reason") ? record.get("reason").getAsString() : "no reason");
        });
    }

    private Component buildKickMessage(JsonObject record) {
        String type = record.has("type") ? record.get("type").getAsString() : "BAN";
        String reason = record.has("reason") && !record.get("reason").isJsonNull()
                ? record.get("reason").getAsString() : "No reason given";
        String issuer = record.has("issuerName") && !record.get("issuerName").isJsonNull()
                ? record.get("issuerName").getAsString() : "Console";
        String remaining = formatRemaining(record);

        Component header;
        if ("IPBAN".equals(type)) {
            header = Component.text("Your IP is banned from the network", NamedTextColor.RED);
        } else if ("TEMPBAN".equals(type)) {
            header = Component.text("You are temporarily banned", NamedTextColor.RED);
        } else {
            header = Component.text("You are banned from the network", NamedTextColor.RED);
        }

        Component msg = header
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("Reason: ", NamedTextColor.GRAY))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Banned by: ", NamedTextColor.GRAY))
                .append(Component.text(issuer, NamedTextColor.WHITE));

        if (remaining != null) {
            msg = msg.append(Component.newline())
                    .append(Component.text("Expires in: ", NamedTextColor.GRAY))
                    .append(Component.text(remaining, NamedTextColor.WHITE));
        }
        return msg;
    }

    private String formatRemaining(JsonObject record) {
        if (!record.has("remainingSeconds") || record.get("remainingSeconds").isJsonNull()) return null;
        long seconds = record.get("remainingSeconds").getAsLong();
        if (seconds <= 0) return "expired";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || sb.length() == 0) sb.append(Math.max(m, 1)).append("m");
        return sb.toString().trim();
    }

    private record CacheKey(String uuid, String ip) {
        @Override public String toString() { return uuid + "|" + ip; }
    }

    private record CacheEntry(JsonObject record, long cachedAtMs) {}
}
