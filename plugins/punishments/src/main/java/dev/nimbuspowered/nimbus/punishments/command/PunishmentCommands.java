package dev.nimbuspowered.nimbus.punishments.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.nimbuspowered.nimbus.punishments.NimbusPunishmentsPlugin;
import dev.nimbuspowered.nimbus.punishments.api.PunishmentsApiClient;
import dev.nimbuspowered.nimbus.sdk.compat.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Single CommandExecutor handling all punishment commands.
 * Dispatches based on the bound command label and offloads HTTP to an async thread.
 */
public class PunishmentCommands implements TabExecutor {

    private final NimbusPunishmentsPlugin plugin;
    private final PunishmentsApiClient api;
    private final Gson gson = new Gson();

    public PunishmentCommands(NimbusPunishmentsPlugin plugin, PunishmentsApiClient api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        switch (name) {
            case "ban":      return issue(sender, args, "BAN",      false);
            case "tempban":  return issue(sender, args, "TEMPBAN",  true);
            case "mute":     return issue(sender, args, "MUTE",     false);
            case "tempmute": return issue(sender, args, "TEMPMUTE", true);
            case "kick":     return issue(sender, args, "KICK",     false);
            case "warn":     return issue(sender, args, "WARN",     false);
            case "unban":    return revoke(sender, args, true);
            case "unmute":   return revoke(sender, args, false);
            case "history":  return showHistory(sender, args);
            default:
                return false;
        }
    }

    private boolean issue(CommandSender sender, String[] args, String type, boolean needsDuration) {
        int minArgs = needsDuration ? 3 : 2;
        if (args.length < minArgs) {
            sender.sendMessage("§cUsage: §7/" + type.toLowerCase() + " <player>"
                    + (needsDuration ? " <duration>" : "") + " <reason>");
            return true;
        }
        String target = args[0];
        String duration = needsDuration ? args[1] : null;
        int reasonStart = needsDuration ? 2 : 1;
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = reasonStart; i < args.length; i++) {
            if (reasonBuilder.length() > 0) reasonBuilder.append(' ');
            reasonBuilder.append(args[i]);
        }
        String reason = reasonBuilder.toString();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String targetUuid = offline.getUniqueId().toString();
        String targetName = offline.getName() != null ? offline.getName() : target;

        String issuer = (sender instanceof Player) ? ((Player) sender).getUniqueId().toString() : "console";
        String issuerName = sender.getName();

        CompletableFuture.runAsync(() -> {
            PunishmentsApiClient.ApiResponse resp = api.issue(
                    type, targetUuid, targetName, null, duration, reason, issuer, issuerName
            );
            SchedulerCompat.runTask(plugin, () -> {
                if (resp.ok) {
                    sender.sendMessage("§a" + type + " issued against §f" + targetName + " §7- " + reason);
                    // Kick target if ban and still online (mute/warn handled elsewhere)
                    if (type.endsWith("BAN")) {
                        Player online = Bukkit.getPlayerExact(targetName);
                        if (online != null) online.kickPlayer(parseKickMessage(resp.body));
                    } else if (type.equals("KICK")) {
                        Player online = Bukkit.getPlayerExact(targetName);
                        if (online != null) online.kickPlayer("§cKicked: §f" + reason);
                    } else if (type.endsWith("MUTE")) {
                        api.invalidateMute(offline.getUniqueId());
                    }
                } else {
                    sender.sendMessage("§cFailed: §7" + resp.body);
                }
            });
        });
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args, boolean isBan) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: §7/" + (isBan ? "unban" : "unmute") + " <player>");
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        String uuid = offline.getUniqueId().toString();

        CompletableFuture.runAsync(() -> {
            PunishmentsApiClient.ApiResponse resp = isBan ? api.unban(uuid) : api.unmute(uuid);
            SchedulerCompat.runTask(plugin, () -> {
                if (resp.ok) {
                    sender.sendMessage("§a" + (isBan ? "Unbanned " : "Unmuted ") + "§f" + args[0]);
                    if (!isBan) api.invalidateMute(offline.getUniqueId());
                } else {
                    sender.sendMessage("§cFailed: §7" + resp.body);
                }
            });
        });
        return true;
    }

    private boolean showHistory(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: §7/history <player>");
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        String uuid = offline.getUniqueId().toString();

        CompletableFuture.runAsync(() -> {
            PunishmentsApiClient.ApiResponse resp = api.history(uuid);
            SchedulerCompat.runTask(plugin, () -> {
                if (!resp.ok) {
                    sender.sendMessage("§cFailed: §7" + resp.body);
                    return;
                }
                try {
                    JsonObject obj = gson.fromJson(resp.body, JsonObject.class);
                    int total = obj.has("total") ? obj.get("total").getAsInt() : 0;
                    sender.sendMessage("§7Punishment history for §f" + args[0] + " §7(" + total + ")");
                    if (total == 0) {
                        sender.sendMessage("§8  (none)");
                        return;
                    }
                    for (var el : obj.getAsJsonArray("punishments")) {
                        JsonObject p = el.getAsJsonObject();
                        String status = p.get("active").getAsBoolean() ? "§c●" : "§8●";
                        String type = p.get("type").getAsString();
                        String issuer = p.get("issuerName").getAsString();
                        String reason = p.get("reason").getAsString();
                        sender.sendMessage(status + " §e" + type + " §7by §f" + issuer + " §7- §f" + reason);
                    }
                } catch (Exception e) {
                    sender.sendMessage("§cParse error: §7" + e.getMessage());
                }
            });
        });
        return true;
    }

    private String parseKickMessage(String body) {
        try {
            JsonObject obj = gson.fromJson(body, JsonObject.class);
            String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Banned";
            String issuer = obj.has("issuerName") ? obj.get("issuerName").getAsString() : "Console";
            return "§c§lYou are banned\n§7Reason: §f" + reason + "\n§7By: §f" + issuer;
        } catch (Exception e) {
            return "§cYou are banned from the network";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> results = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) results.add(p.getName());
            }
            return results;
        }
        return List.of();
    }
}
