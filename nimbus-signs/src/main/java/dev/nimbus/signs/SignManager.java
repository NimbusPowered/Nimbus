package dev.nimbus.signs;

import dev.nimbus.sdk.Nimbus;
import dev.nimbus.sdk.NimbusService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages all Nimbus signs — loads from config, updates them live.
 */
public class SignManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final SignConfig config;
    private final CopyOnWriteArrayList<NimbusSign> signs = new CopyOnWriteArrayList<>();

    public SignManager(JavaPlugin plugin, SignConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        signs.clear();
        signs.addAll(config.loadSigns());
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public List<NimbusSign> getSigns() { return List.copyOf(signs); }
    public int getSignCount() { return signs.size(); }

    public NimbusSign getSign(Location location) {
        return signs.stream()
                .filter(s -> s.getLocation().getBlockX() == location.getBlockX() &&
                        s.getLocation().getBlockY() == location.getBlockY() &&
                        s.getLocation().getBlockZ() == location.getBlockZ() &&
                        s.getLocation().getWorld().equals(location.getWorld()))
                .findFirst().orElse(null);
    }

    public void addSign(NimbusSign sign) {
        signs.removeIf(s -> s.getId().equals(sign.getId()));
        signs.add(sign);
        config.addSign(sign);
    }

    public boolean removeSign(Location location) {
        NimbusSign sign = getSign(location);
        if (sign == null) return false;
        signs.remove(sign);
        config.removeSign(sign.getId());
        return true;
    }

    /** Update all signs with live data from the Nimbus cache. */
    public void updateAll() {
        for (NimbusSign nSign : signs) {
            plugin.getServer().getScheduler().runTask(plugin, () -> updateSign(nSign));
        }
    }

    private void updateSign(NimbusSign nSign) {
        Block block = nSign.getLocation().getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        int players;
        int maxPlayers;
        int servers;
        String state;
        boolean available;
        String serviceName;

        if (nSign.isServiceTarget()) {
            // Specific service target (e.g. "Survival-1")
            NimbusService service = findService(nSign.getTarget());
            if (service != null) {
                players = service.getPlayerCount();
                maxPlayers = 0; // not available from service response
                servers = 1;
                state = service.getCustomState() != null ? service.getCustomState() : service.getState();
                available = service.isReady();
                serviceName = service.getName();
            } else {
                players = 0;
                maxPlayers = 0;
                servers = 0;
                state = "OFFLINE";
                available = false;
                serviceName = nSign.getTarget();
            }
        } else {
            // Group target (e.g. "BedWars")
            List<NimbusService> services = Nimbus.services(nSign.getTarget());
            List<NimbusService> routable = Nimbus.routable(nSign.getTarget());
            players = services.stream().mapToInt(NimbusService::getPlayerCount).sum();
            maxPlayers = 0;
            servers = services.size();
            available = !routable.isEmpty();
            state = available ? "ONLINE" : "OFFLINE";
            serviceName = nSign.getTarget();
        }

        // Replace placeholders and set lines
        sign.line(0, replacePlaceholders(nSign.getLine1(), players, maxPlayers, servers, state, serviceName));
        sign.line(1, replacePlaceholders(nSign.getLine2(), players, maxPlayers, servers, state, serviceName));
        sign.line(2, replacePlaceholders(nSign.getLine3(), players, maxPlayers, servers, state, serviceName));

        String line4Template = available ? nSign.getLine4Available() : nSign.getLine4Unavailable();
        sign.line(3, replacePlaceholders(line4Template, players, maxPlayers, servers, state, serviceName));

        sign.update();
    }

    /**
     * Replace placeholders in a line template.
     * Supported: {players}, {max_players}, {servers}, {state}, {target}, {name}
     */
    private Component replacePlaceholders(String template, int players, int maxPlayers,
                                           int servers, String state, String name) {
        String text = template
                .replace("{players}", String.valueOf(players))
                .replace("{max_players}", String.valueOf(maxPlayers))
                .replace("{servers}", String.valueOf(servers))
                .replace("{state}", state)
                .replace("{target}", name)
                .replace("{name}", name);
        return LEGACY.deserialize(text);
    }

    private NimbusService findService(String name) {
        return Nimbus.services().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst().orElse(null);
    }
}
