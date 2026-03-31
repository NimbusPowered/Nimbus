package dev.nimbus.perms.display;

import com.google.gson.JsonObject;
import dev.nimbus.perms.provider.PermissionProvider;
import dev.nimbus.sdk.ColorUtil;
import dev.nimbus.sdk.Nimbus;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles chat formatting on Paper backend servers using the proxy sync config
 * and permission group prefix/suffix from the permission provider.
 */
public class ChatRenderer implements Listener, io.papermc.paper.chat.ChatRenderer {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String apiUrl;
    private final String token;
    private final HttpClient httpClient;
    private final PermissionProvider provider;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private volatile String chatFormat = "{prefix}{player}{suffix} <dark_gray>» <gray>{message}";
    private volatile boolean chatEnabled = true;

    public ChatRenderer(JavaPlugin plugin, String apiUrl, String token, PermissionProvider provider) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.token = token;
        this.provider = provider;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void start() {
        fetchChatConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (Nimbus.events() != null) {
            Nimbus.events().onEvent("CHAT_FORMAT_UPDATED", e -> {
                String fmt = e.get("format");
                if (fmt != null) chatFormat = fmt;
                String en = e.get("enabled");
                if (en != null) chatEnabled = Boolean.parseBoolean(en);
                logger.fine("Chat format updated via event");
            });

            Nimbus.events().onEvent("PERMISSION_GROUP_UPDATED", e -> provider.refreshAll());

            Nimbus.events().onEvent("PLAYER_PERMISSIONS_UPDATED", e -> {
                String uuid = e.get("uuid");
                if (uuid != null) {
                    try {
                        provider.refresh(UUID.fromString(uuid));
                    } catch (IllegalArgumentException ignored) {}
                }
            });
        }

        logger.info("Chat renderer started");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!chatEnabled) return;
        event.renderer(this);
    }

    @Override
    public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName,
                                      @NotNull Component message, @NotNull Audience viewer) {
        String prefix = provider.getPrefix(source.getUniqueId());
        String suffix = provider.getSuffix(source.getUniqueId());

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);

        String formatted = chatFormat
                .replace("{prefix}", prefix != null ? prefix : "")
                .replace("{suffix}", suffix != null ? suffix : "")
                .replace("{player}", source.getName())
                .replace("{message}", plainMessage)
                .replace("{server}", Nimbus.isManaged() ? Nimbus.name() : "")
                .replace("{group}", Nimbus.isManaged() ? Nimbus.group() : "");

        return miniMessage.deserialize(ColorUtil.translate(formatted));
    }

    public void removePlayer(UUID uuid) {
        // Display cache cleanup is handled by provider.onQuit()
    }

    private void fetchChatConfig() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/api/proxy/chat"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                logger.warning("Failed to fetch chat config: HTTP " + response.statusCode());
                return;
            }

            JsonObject json = new com.google.gson.Gson().fromJson(response.body(), JsonObject.class);
            if (json.has("format")) chatFormat = json.get("format").getAsString();
            if (json.has("enabled")) chatEnabled = json.get("enabled").getAsBoolean();
            logger.info("Loaded chat format from API");
        } catch (Exception e) {
            logger.warning("Failed to fetch chat config: " + e.getMessage());
        }
    }
}
