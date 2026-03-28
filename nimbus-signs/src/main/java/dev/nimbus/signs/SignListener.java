package dev.nimbus.signs;

import dev.nimbus.sdk.Nimbus;
import dev.nimbus.sdk.NimbusService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles sign interactions:
 * - Right-click → connect to server (cancel sign edit)
 * - Break → remove if Nimbus sign
 */
public class SignListener implements Listener {

    private final SignManager signManager;

    public SignListener(SignManager signManager) {
        this.signManager = signManager;
    }

    /**
     * Right-click a Nimbus sign → connect to server.
     * Also cancels the sign edit GUI for Nimbus signs.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        NimbusSign nSign = signManager.getSign(block.getLocation());
        if (nSign == null) return;

        // Cancel the sign edit GUI
        event.setCancelled(true);

        Player player = event.getPlayer();

        if (nSign.isServiceTarget()) {
            // Direct connect to specific service
            NimbusService service = Nimbus.services().stream()
                    .filter(s -> s.getName().equals(nSign.getTarget()))
                    .findFirst().orElse(null);

            if (service == null || !service.isReady()) {
                player.sendMessage(Component.text(nSign.getTarget() + " is not available.", NamedTextColor.RED));
                return;
            }

            player.sendMessage(
                    Component.text("Connecting to ", NamedTextColor.GREEN)
                            .append(Component.text(nSign.getTarget(), NamedTextColor.WHITE))
                            .append(Component.text("...", NamedTextColor.GREEN))
            );
            Nimbus.client().sendPlayer(player.getName(), nSign.getTarget())
                    .exceptionally(e -> {
                        player.sendMessage(Component.text("Failed to connect.", NamedTextColor.RED));
                        return null;
                    });
        } else {
            // Route to best server in group
            NimbusService best = Nimbus.bestServer(nSign.getTarget(), nSign.getStrategy());
            if (best == null) {
                player.sendMessage(Component.text("No " + nSign.getTarget() + " server available.", NamedTextColor.RED));
                return;
            }

            player.sendMessage(
                    Component.text("Connecting to ", NamedTextColor.GREEN)
                            .append(Component.text(best.getName(), NamedTextColor.WHITE))
                            .append(Component.text("...", NamedTextColor.GREEN))
            );
            Nimbus.route(player.getName(), nSign.getTarget(), nSign.getStrategy())
                    .exceptionally(e -> {
                        player.sendMessage(Component.text("Failed to connect.", NamedTextColor.RED));
                        return null;
                    });
        }
    }

    /**
     * Breaking a Nimbus sign removes it.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) return;

        NimbusSign nSign = signManager.getSign(event.getBlock().getLocation());
        if (nSign == null) return;

        if (!event.getPlayer().hasPermission("nimbus.signs.remove")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        signManager.removeSign(event.getBlock().getLocation());
        event.getPlayer().sendMessage(
                Component.text("Sign removed: ", NamedTextColor.YELLOW)
                        .append(Component.text(nSign.getTarget(), NamedTextColor.WHITE))
        );
    }
}
