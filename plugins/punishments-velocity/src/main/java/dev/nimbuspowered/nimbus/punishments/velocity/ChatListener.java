package dev.nimbuspowered.nimbus.punishments.velocity;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Cancels chat at the proxy for muted players.
 *
 * <h2>Why this is synchronous</h2>
 * On Velocity 1.19.1+, chat messages carry a signature that the proxy verifies
 * before forwarding. If we returned {@code EventTask.async} from the handler,
 * our {@code setResult(denied())} would race the signature-forwarding path and
 * the client would see {@code A Proxy Plugin caused an illegal protocol state}.
 *
 * So we read from a pre-populated {@link MuteCache} instead — the cache is
 * warmed on {@link ServerConnectedEvent} (fires before the player can chat)
 * and invalidated on {@code PUNISHMENT_ISSUED} events. The handler itself is
 * a plain {@code void}, decides from the cache, and returns immediately.
 *
 * Staff with {@code nimbus.punish.bypass} skip the check entirely.
 */
public class ChatListener {

    private final PunishmentsApiClient api;
    private final MuteCache cache;
    private final ProxyServer server;
    private final Logger logger;

    public ChatListener(ProxyServer server, PunishmentsApiClient api, MuteCache cache, Logger logger) {
        this.server = server;
        this.api = api;
        this.cache = cache;
        this.logger = logger;
    }

    /**
     * Warm the cache for the player's new context. This event fires on every
     * server hop so scoped mutes automatically rescope — the previous entry
     * is overwritten with one tagged to the new (group, service) pair.
     *
     * Runs async because the HTTP call shouldn't block Velocity's join path
     * — and chat can't happen until ServerConnectedEvent completes anyway.
     */
    @Subscribe(order = PostOrder.LATE)
    public void onConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String service = event.getServer().getServerInfo().getName();
        String group = deriveGroupName(service);

        // Off the main thread so we don't block the join
        server.getScheduler()
            .buildTask(new Object(), () -> {
                JsonObject record = api.checkMute(uuid, group, service);
                cache.put(uuid, group, service, record);
            })
            .schedule();
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nimbus.punish.bypass")) return;

        ServerConnection current = player.getCurrentServer().orElse(null);
        String service = current == null ? null : current.getServerInfo().getName();
        String group = service == null ? null : deriveGroupName(service);

        JsonObject record = cache.get(player.getUniqueId(), group, service);
        if (record == null) return;

        // Sync deny — no EventTask.async, so signed chat verification sees a
        // consistent "denied" result and doesn't emit the illegal-protocol-state error.
        event.setResult(PlayerChatEvent.ChatResult.denied());
        Component msg = LegacyComponentSerializer.legacySection()
            .deserialize(MessageBuilder.formatMuteLine(record));
        player.sendMessage(msg);
    }

    private static String deriveGroupName(String serverName) {
        int dash = serverName.lastIndexOf('-');
        if (dash > 0) {
            String suffix = serverName.substring(dash + 1);
            try {
                Integer.parseInt(suffix);
                return serverName.substring(0, dash);
            } catch (NumberFormatException ignored) {}
        }
        return serverName;
    }
}
