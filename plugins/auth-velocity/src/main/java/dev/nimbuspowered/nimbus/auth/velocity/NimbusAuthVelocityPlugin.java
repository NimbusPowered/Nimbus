package dev.nimbuspowered.nimbus.auth.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.nimbuspowered.nimbus.sdk.NimbusClient;
import dev.nimbuspowered.nimbus.sdk.NimbusEventStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Velocity companion to the Nimbus dashboard-auth module.
 *
 * <p>Registers the proxy-side {@code /dashboard} command so players on the
 * proxy can request a login code / magic link. Since every Nimbus player
 * connects through the Velocity proxy, the proxy also delivers
 * dashboard-initiated magic-link chat components — no backend plugin needed.
 *
 * <p>All HTTP calls use the service's {@code NIMBUS_API_TOKEN} against the
 * auth module's SERVICE-auth endpoints.
 *
 * <p>Deployment: auto-installed on every Velocity proxy by the auth
 * controller module via {@code PluginDeployment(target = VELOCITY)}.
 */
@Plugin(
    id = "nimbus-auth",
    name = "Nimbus Auth",
    version = "0.0.0",  // replaced at build time from gradle.properties
    description = "In-game /dashboard command + magic-link delivery for Nimbus Velocity proxies",
    authors = {"NimbusPowered"}
)
public class NimbusAuthVelocityPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer server;
    private final Logger logger;

    private NimbusClient sdkClient;
    private NimbusEventStream eventStream;

    @Inject
    public NimbusAuthVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        String apiUrl = System.getProperty("nimbus.api.url");
        String envToken = System.getenv("NIMBUS_API_TOKEN");
        String token = (envToken != null && !envToken.isEmpty())
                ? envToken
                : System.getProperty("nimbus.api.token", "");

        if (apiUrl == null || apiUrl.isEmpty()) {
            logger.warn("No nimbus.api.url configured — NimbusAuth proxy-side /dashboard disabled");
            return;
        }
        if (apiUrl.endsWith("/")) apiUrl = apiUrl.substring(0, apiUrl.length() - 1);

        AuthApiClient api = new AuthApiClient(apiUrl, token, logger);

        var meta = server.getCommandManager().metaBuilder("dashboard")
            .plugin(this)
            .build();
        server.getCommandManager().register(meta, new DashboardVelocityCommand(api));

        // Subscribe to AUTH_MAGIC_LINK_DELIVERY module events. The controller
        // fires one whenever the dashboard login page requests a magic link
        // for a MC name; we render a clickable Adventure component directly
        // to the target player if they're on this proxy.
        try {
            sdkClient = new NimbusClient(apiUrl, token);
            eventStream = sdkClient.createEventStream();
            eventStream.onEvent("AUTH_MAGIC_LINK_DELIVERY", ev -> {
                String uuidStr = ev.get("uuid");
                String url = ev.get("url");
                String rawTtl = ev.get("ttl");
                final String ttl = (rawTtl == null) ? "60" : rawTtl;
                if (uuidStr == null || url == null) return;
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException ex) {
                    return;
                }
                server.getPlayer(uuid).ifPresent(p -> sendMagicLink(p, url, ttl));
            });
            eventStream.connect();
        } catch (Exception e) {
            logger.warn("Failed to subscribe to AUTH_MAGIC_LINK_DELIVERY — "
                    + "dashboard-initiated magic links will not reach players on this proxy: {}",
                    e.getMessage());
        }

        logger.info("NimbusAuth enabled — /dashboard registered, AUTH_MAGIC_LINK_DELIVERY subscribed");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (eventStream != null) {
            try {
                eventStream.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void sendMagicLink(Player player, String url, String ttl) {
        Component clickable = LEGACY.deserialize("&e&l[Klick zum Einloggen]")
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize(
                        "&7Öffnet das Nimbus-Dashboard und loggt dich automatisch ein.")));

        Component msg = LEGACY.deserialize("&d✨ &f[Nimbus] &fDein magischer Login-Link ist bereit! ")
                .append(clickable)
                .append(LEGACY.deserialize(" &7(" + ttl + "s gültig)"));
        player.sendMessage(msg);
    }
}
