package dev.nimbuspowered.nimbus.punishments;

import dev.nimbuspowered.nimbus.punishments.api.PunishmentsApiClient;
import dev.nimbuspowered.nimbus.punishments.command.PunishmentCommands;
import dev.nimbuspowered.nimbus.punishments.listener.ChatMuteListener;
import dev.nimbuspowered.nimbus.sdk.Nimbus;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spigot/Paper/Folia plugin that enforces network-wide punishments.
 *
 * Responsibilities:
 *   - `/ban`, `/mute`, `/kick`, `/warn`, `/unban`, `/unmute`, `/history` commands → controller REST API
 *   - Chat mute enforcement via AsyncChatEvent (Paper) / AsyncPlayerChatEvent (Spigot)
 *
 * Login enforcement happens upstream at the Velocity bridge — this plugin does not
 * intercept logins because backends are connected to by the proxy after its checks pass.
 */
public class NimbusPunishmentsPlugin extends JavaPlugin {

    private PunishmentsApiClient api;
    private String apiUrl;
    private String token;

    @Override
    public void onEnable() {
        if (!Nimbus.isManaged()) {
            getLogger().warning("Not running in a Nimbus-managed service — NimbusPunishments disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        apiUrl = System.getProperty("nimbus.api.url");
        String envToken = System.getenv("NIMBUS_API_TOKEN");
        token = (envToken != null && !envToken.isEmpty())
                ? envToken
                : System.getProperty("nimbus.api.token", "");

        if (apiUrl == null || apiUrl.isEmpty()) {
            getLogger().warning("No API URL configured — NimbusPunishments disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (apiUrl.endsWith("/")) apiUrl = apiUrl.substring(0, apiUrl.length() - 1);

        this.api = new PunishmentsApiClient(this, apiUrl, token);

        // Register commands
        PunishmentCommands commands = new PunishmentCommands(this, api);
        for (String cmd : new String[] {
                "ban", "tempban", "mute", "tempmute", "kick", "warn",
                "unban", "unmute", "history"
        }) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(commands);
                getCommand(cmd).setTabCompleter(commands);
            }
        }

        // Register chat mute listener
        getServer().getPluginManager().registerEvents(new ChatMuteListener(this, api), this);

        getLogger().info("NimbusPunishments enabled");
    }

    public PunishmentsApiClient getApi() { return api; }
    public String getApiUrl() { return apiUrl; }
    public String getToken() { return token; }
}
