package dev.nimbus.signs;

import dev.nimbus.sdk.Nimbus;
import dev.nimbus.sdk.NimbusService;
import dev.nimbus.sdk.RoutingStrategy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * /nsign command for managing Nimbus signs.
 *
 * <pre>
 * /nsign set <group|service> [strategy]  — look at a sign, set it as Nimbus sign
 * /nsign remove                          — look at a sign, remove it
 * /nsign list                            — list all signs
 * /nsign reload                          — reload config
 * </pre>
 */
public class SignCommand implements CommandExecutor, TabCompleter {

    private final SignManager signManager;

    public SignCommand(SignManager signManager) {
        this.signManager = signManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> handleSet(player, args);
            case "remove" -> handleRemove(player);
            case "list" -> handleList(player);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleSet(Player player, String[] args) {
        if (!player.hasPermission("nimbus.signs.create")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /nsign set <group|service> [least|fill|random]", NamedTextColor.RED));
            return;
        }

        // Get the sign the player is looking at
        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof Sign)) {
            player.sendMessage(Component.text("Look at a sign first!", NamedTextColor.RED));
            return;
        }

        String target = args[1];
        boolean isService = target.matches(".*-\\d+$");

        RoutingStrategy strategy = RoutingStrategy.LEAST_PLAYERS;
        if (args.length >= 3) {
            strategy = switch (args[2].toLowerCase()) {
                case "fill", "fill_first" -> RoutingStrategy.FILL_FIRST;
                case "random" -> RoutingStrategy.RANDOM;
                default -> RoutingStrategy.LEAST_PLAYERS;
            };
        }

        // Generate ID
        var loc = block.getLocation();
        String id = target.toLowerCase() + "-" + loc.getBlockX() + "-" + loc.getBlockY() + "-" + loc.getBlockZ();

        // Create sign with default lines
        NimbusSign nSign = new NimbusSign(id, loc, target, isService, strategy,
                "&1&l★ " + target + " ★",
                "&8{players} playing",
                isService ? "&8{state}" : "&8{servers} server(s)",
                "&2▶ Click to join!",
                "&4✖ Offline");

        signManager.addSign(nSign);

        player.sendMessage(
                Component.text("Sign set for ", NamedTextColor.GREEN)
                        .append(Component.text(target, NamedTextColor.WHITE))
                        .append(Component.text(isService ? " (service)" : " (group, " + strategy.name().toLowerCase() + ")", NamedTextColor.GRAY))
        );
    }

    private void handleRemove(Player player) {
        if (!player.hasPermission("nimbus.signs.remove")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof Sign)) {
            player.sendMessage(Component.text("Look at a sign first!", NamedTextColor.RED));
            return;
        }

        NimbusSign nSign = signManager.getSign(block.getLocation());
        if (nSign == null) {
            player.sendMessage(Component.text("This is not a Nimbus sign.", NamedTextColor.RED));
            return;
        }

        signManager.removeSign(block.getLocation());
        player.sendMessage(
                Component.text("Sign removed: ", NamedTextColor.YELLOW)
                        .append(Component.text(nSign.getTarget(), NamedTextColor.WHITE))
        );
    }

    private void handleList(Player player) {
        if (!player.hasPermission("nimbus.signs.create")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        var signs = signManager.getSigns();
        player.sendMessage(Component.text("Nimbus Signs (" + signs.size() + ")", NamedTextColor.AQUA));

        if (signs.isEmpty()) {
            player.sendMessage(Component.text("  No signs configured.", NamedTextColor.GRAY));
            return;
        }

        for (NimbusSign sign : signs) {
            var loc = sign.getLocation();
            player.sendMessage(
                    Component.text("  " + sign.getId(), NamedTextColor.WHITE)
                            .append(Component.text(" → " + sign.getTarget(), NamedTextColor.GREEN))
                            .append(Component.text(" @ " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(), NamedTextColor.GRAY))
            );
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("nimbus.signs.reload")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        signManager.reload();
        player.sendMessage(Component.text("Reloaded " + signManager.getSignCount() + " sign(s).", NamedTextColor.GREEN));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("Nimbus Signs Commands:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /nsign set <group|service> [strategy]", NamedTextColor.WHITE)
                .append(Component.text(" — set sign you're looking at", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /nsign remove", NamedTextColor.WHITE)
                .append(Component.text(" — remove sign you're looking at", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /nsign list", NamedTextColor.WHITE)
                .append(Component.text(" — list all signs", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /nsign reload", NamedTextColor.WHITE)
                .append(Component.text(" — reload config", NamedTextColor.GRAY)));
    }

    // ── Tab Complete ──────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("set", "remove", "list", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            // Suggest group names + service names from cache
            List<String> suggestions = new ArrayList<>();
            try {
                // Group names
                for (String group : Nimbus.cache().getGroupNames()) {
                    suggestions.add(group);
                }
                // Service names
                for (NimbusService service : Nimbus.services()) {
                    suggestions.add(service.getName());
                }
            } catch (Exception ignored) {}
            return filter(suggestions, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(List.of("least", "fill", "random"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
