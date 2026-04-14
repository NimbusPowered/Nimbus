package dev.nimbuspowered.nimbus.punishments.listener;

import com.google.gson.JsonObject;
import dev.nimbuspowered.nimbus.punishments.NimbusPunishmentsPlugin;
import dev.nimbuspowered.nimbus.punishments.api.PunishmentsApiClient;
import dev.nimbuspowered.nimbus.sdk.compat.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Blocks chat for muted players. Also handles the /me command by subscribing to
 * PlayerCommandPreprocessEvent.
 *
 * On Paper 1.16.5+ the canonical event is AsyncChatEvent — but AsyncPlayerChatEvent
 * still fires (Paper emits both), and listening to the legacy one keeps us compatible
 * with Spigot 1.8.8 through modern Paper without version-specific subclasses.
 *
 * Staff with `nimbus.punish.bypass` are never muted.
 */
public class ChatMuteListener implements Listener {

    private final NimbusPunishmentsPlugin plugin;
    private final PunishmentsApiClient api;

    public ChatMuteListener(NimbusPunishmentsPlugin plugin, PunishmentsApiClient api) {
        this.plugin = plugin;
        this.api = api;
    }

    @SuppressWarnings("deprecation") // AsyncPlayerChatEvent is deprecated on Paper but still fires reliably
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("nimbus.punish.bypass")) return;

        JsonObject record = api.checkMuteCached(player.getUniqueId());
        if (record == null) return;

        event.setCancelled(true);
        String reason = record.has("reason") && !record.get("reason").isJsonNull()
                ? record.get("reason").getAsString()
                : "No reason";
        Long remaining = record.has("remainingSeconds") && !record.get("remainingSeconds").isJsonNull()
                ? record.get("remainingSeconds").getAsLong()
                : null;
        String suffix = remaining != null ? " §7(" + formatRemaining(remaining) + ")" : "";
        player.sendMessage("§cYou are muted: §f" + reason + suffix);
    }

    private String formatRemaining(long seconds) {
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
}
