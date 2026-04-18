package dev.nimbuspowered.nimbus.auth.velocity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /dashboard} command for Velocity proxies.
 *
 * <p>Mirrors the backend-side command but renders messages via Velocity's
 * Adventure API directly.
 *
 * <p>Authenticates to the controller with the proxy service's existing
 * {@code NIMBUS_API_TOKEN}.
 */
public class DashboardVelocityCommand implements SimpleCommand {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> SUBCOMMANDS = List.of("login", "sessions", "logout-all");

    private final AuthApiClient api;

    public DashboardVelocityCommand(AuthApiClient api) {
        this.api = api;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can use /dashboard.", NamedTextColor.RED));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "login" -> {
                boolean wantLink = args.length >= 2 && "link".equalsIgnoreCase(args[1]);
                if (wantLink) requestMagicLink(player);
                else requestCode(player);
            }
            case "sessions" -> listSessions(player);
            case "logout-all", "logoutall" -> logoutAll(player);
            default -> sendUsage(player);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && "login".equalsIgnoreCase(args[0])) {
            return "link".startsWith(args[1].toLowerCase(Locale.ROOT))
                    ? Collections.singletonList("link") : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    // ── Actions ──────────────────────────────────────────────────────

    private void requestCode(Player player) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUniqueId().toString());
        body.addProperty("name", player.getUsername());
        api.post("/api/auth/generate-code", body).whenComplete((result, err) -> {
            if (err != null || result == null || result.statusCode() < 0) {
                player.sendMessage(LEGACY.deserialize("&c[Nimbus] &fFailed to reach the controller."));
                return;
            }
            if (!result.isSuccess()) {
                handleError(player, result);
                return;
            }
            JsonObject json = result.asJson();
            String code = json.get("code").getAsString();
            long ttl = json.has("ttlSeconds") ? json.get("ttlSeconds").getAsLong() : 60;
            String pretty = code.length() == 6 ? code.substring(0, 3) + " " + code.substring(3) : code;
            player.sendMessage(LEGACY.deserialize(
                    "&a[Nimbus] &fDein Login-Code: &e&l" + pretty + "&r &7(" + ttl + "s gültig). Öffne das Dashboard und gib ihn ein."));
        });
    }

    private void requestMagicLink(Player player) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUniqueId().toString());
        body.addProperty("name", player.getUsername());
        api.post("/api/auth/request-magic-link", body).whenComplete((result, err) -> {
            if (err != null || result == null || result.statusCode() < 0) {
                player.sendMessage(LEGACY.deserialize("&c[Nimbus] &fFailed to reach the controller."));
                return;
            }
            if (!result.isSuccess()) {
                handleError(player, result);
                return;
            }
            JsonObject json = result.asJson();
            String url = json.get("url").getAsString();
            long ttl = json.has("ttlSeconds") ? json.get("ttlSeconds").getAsLong() : 60;

            Component clickable = LEGACY.deserialize("&e&l[Klick zum Einloggen]")
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(LEGACY.deserialize(
                            "&7Öffnet das Nimbus-Dashboard und loggt dich automatisch ein.")));

            Component msg = LEGACY.deserialize("&d✨ &f[Nimbus] &fDein magischer Login-Link ist bereit! ")
                    .append(clickable)
                    .append(LEGACY.deserialize(" &7(" + ttl + "s gültig)"));
            player.sendMessage(msg);
        });
    }

    private void listSessions(Player player) {
        String path = "/api/auth/sessions?uuid=" + urlencode(player.getUniqueId().toString());
        api.get(path).whenComplete((result, err) -> {
            if (err != null || result == null || result.statusCode() < 0) {
                player.sendMessage(LEGACY.deserialize("&c[Nimbus] &fFailed to reach the controller."));
                return;
            }
            if (!result.isSuccess()) {
                handleError(player, result);
                return;
            }
            JsonArray sessions = result.asJson().getAsJsonArray("sessions");
            if (sessions == null || sessions.size() == 0) {
                player.sendMessage(LEGACY.deserialize("&e[Nimbus] &fDu hast keine aktiven Dashboard-Sessions."));
                return;
            }
            player.sendMessage(LEGACY.deserialize("&a[Nimbus] &fAktive Sessions (" + sessions.size() + "):"));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            for (JsonElement el : sessions) {
                JsonObject s = el.getAsJsonObject();
                String id = s.get("sessionId").getAsString();
                String ip = s.has("ip") && !s.get("ip").isJsonNull() ? s.get("ip").getAsString() : "?";
                String method = s.has("loginMethod") ? s.get("loginMethod").getAsString() : "?";
                long lastUsed = s.has("lastUsedAt") ? s.get("lastUsedAt").getAsLong() : 0L;
                String lastUsedStr = lastUsed > 0 ? fmt.format(Instant.ofEpochMilli(lastUsed)) : "?";
                player.sendMessage(LEGACY.deserialize(
                        "  &7- &f" + id + " &7| &f" + method + " &7| " + ip + " &7| last used " + lastUsedStr));
            }
        });
    }

    private void logoutAll(Player player) {
        String path = "/api/auth/logout-all?uuid=" + urlencode(player.getUniqueId().toString());
        api.post(path, null).whenComplete((result, err) -> {
            if (err != null || result == null || result.statusCode() < 0) {
                player.sendMessage(LEGACY.deserialize("&c[Nimbus] &fFailed to reach the controller."));
                return;
            }
            if (!result.isSuccess()) {
                handleError(player, result);
                return;
            }
            JsonObject json = result.asJson();
            int revoked = json.has("revoked") ? json.get("revoked").getAsInt() : 0;
            player.sendMessage(LEGACY.deserialize("&a[Nimbus] &f" + revoked + " Session(s) revoked."));
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void sendUsage(Player player) {
        player.sendMessage(LEGACY.deserialize(
                "&a[Nimbus] &fUsage: &e/dashboard <login [link] | sessions | logout-all>"));
    }

    private void handleError(Player player, AuthApiClient.ApiResult result) {
        String msg = "&c[Nimbus] &fError " + result.statusCode() + ".";
        try {
            JsonObject json = result.asJson();
            if (json.has("message")) msg += " " + json.get("message").getAsString();
            else if (json.has("error")) msg += " " + json.get("error").getAsString();
        } catch (Exception ignored) {
        }
        player.sendMessage(LEGACY.deserialize(msg));
    }

    private static String urlencode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
