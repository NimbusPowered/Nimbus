package dev.nimbus.signs;

import dev.nimbus.sdk.RoutingStrategy;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads and saves sign definitions from config.yml.
 *
 * <pre>
 * # config.yml
 * update-interval: 40  # ticks (40 = 2 seconds)
 *
 * signs:
 *   bedwars-sign:
 *     target: BedWars          # group name → routes to best server in group
 *     strategy: least          # least / fill / random
 *     location:
 *       world: world
 *       x: 100
 *       y: 65
 *       z: 200
 *     lines:
 *       line1: "&1&l★ BedWars ★"
 *       line2: "&8{players} playing"
 *       line3: "&8{servers} server(s)"
 *       line4-available: "&2▶ Click to join!"
 *       line4-unavailable: "&4✖ No servers"
 *
 *   survival-sign:
 *     target: Survival-1       # specific service name → always connects to this one
 *     location:
 *       world: world
 *       x: 104
 *       y: 65
 *       z: 200
 *     lines:
 *       line1: "&1&l★ Survival ★"
 *       line2: "&8{players}/{max_players} online"
 *       line3: "&8{state}"
 *       line4-available: "&2▶ Click to join!"
 *       line4-unavailable: "&4✖ Offline"
 * </pre>
 */
public class SignConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    public SignConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public int getUpdateInterval() {
        return plugin.getConfig().getInt("update-interval", 40);
    }

    public List<NimbusSign> loadSigns() {
        List<NimbusSign> signs = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("signs");
        if (section == null) return signs;

        for (String key : section.getKeys(false)) {
            ConfigurationSection signSection = section.getConfigurationSection(key);
            if (signSection == null) continue;

            try {
                NimbusSign sign = parseSign(key, signSection);
                if (sign != null) {
                    signs.add(sign);
                }
            } catch (Exception e) {
                logger.warning("Failed to load sign '" + key + "': " + e.getMessage());
            }
        }

        return signs;
    }

    private NimbusSign parseSign(String id, ConfigurationSection section) {
        String target = section.getString("target");
        if (target == null || target.isBlank()) {
            logger.warning("Sign '" + id + "' has no target defined");
            return null;
        }

        // Location
        ConfigurationSection locSection = section.getConfigurationSection("location");
        if (locSection == null) {
            logger.warning("Sign '" + id + "' has no location defined");
            return null;
        }

        String worldName = locSection.getString("world", "world");
        var world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            logger.warning("Sign '" + id + "': world '" + worldName + "' not loaded");
            return null;
        }

        Location location = new Location(world,
                locSection.getInt("x"), locSection.getInt("y"), locSection.getInt("z"));

        // Strategy (only relevant for group targets)
        String strategyStr = section.getString("strategy", "least");
        RoutingStrategy strategy = switch (strategyStr.toLowerCase()) {
            case "fill", "fill_first" -> RoutingStrategy.FILL_FIRST;
            case "random" -> RoutingStrategy.RANDOM;
            default -> RoutingStrategy.LEAST_PLAYERS;
        };

        // Determine if target is a group or specific service
        // Convention: if target contains "-" followed by a number, it's a service name
        boolean isService = target.matches(".*-\\d+$");

        // Lines (with defaults)
        ConfigurationSection lines = section.getConfigurationSection("lines");
        String line1 = getLine(lines, "line1", "&1&l★ " + target + " ★");
        String line2 = getLine(lines, "line2", "&8{players} playing");
        String line3 = getLine(lines, "line3", "&8{servers} server(s)");
        String line4Available = getLine(lines, "line4-available", "&2▶ Click to join!");
        String line4Unavailable = getLine(lines, "line4-unavailable", "&4✖ Offline");

        return new NimbusSign(id, location, target, isService, strategy,
                line1, line2, line3, line4Available, line4Unavailable);
    }

    private String getLine(ConfigurationSection lines, String key, String defaultValue) {
        if (lines == null) return defaultValue;
        return lines.getString(key, defaultValue);
    }

    /**
     * Saves a new sign definition back to config.yml.
     */
    public void addSign(NimbusSign sign) {
        String path = "signs." + sign.getId();
        plugin.getConfig().set(path + ".target", sign.getTarget());
        if (!sign.isServiceTarget()) {
            plugin.getConfig().set(path + ".strategy", sign.getStrategy().name().toLowerCase());
        }
        plugin.getConfig().set(path + ".location.world", sign.getLocation().getWorld().getName());
        plugin.getConfig().set(path + ".location.x", sign.getLocation().getBlockX());
        plugin.getConfig().set(path + ".location.y", sign.getLocation().getBlockY());
        plugin.getConfig().set(path + ".location.z", sign.getLocation().getBlockZ());
        plugin.getConfig().set(path + ".lines.line1", sign.getLine1());
        plugin.getConfig().set(path + ".lines.line2", sign.getLine2());
        plugin.getConfig().set(path + ".lines.line3", sign.getLine3());
        plugin.getConfig().set(path + ".lines.line4-available", sign.getLine4Available());
        plugin.getConfig().set(path + ".lines.line4-unavailable", sign.getLine4Unavailable());
        plugin.saveConfig();
    }

    /**
     * Removes a sign from config.yml.
     */
    public void removeSign(String id) {
        plugin.getConfig().set("signs." + id, null);
        plugin.saveConfig();
    }
}
