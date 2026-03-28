package dev.nimbus.signs;

import dev.nimbus.sdk.Nimbus;
import org.bukkit.plugin.java.JavaPlugin;

public class NimbusSignsPlugin extends JavaPlugin {

    private SignManager signManager;

    @Override
    public void onEnable() {
        if (!Nimbus.isManaged()) {
            getLogger().warning("Not running in a Nimbus-managed service — signs will not work!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        SignConfig signConfig = new SignConfig(this);
        signManager = new SignManager(this, signConfig);
        signManager.load();

        // Register events
        getServer().getPluginManager().registerEvents(new SignListener(signManager), this);

        // Register /nsign command
        var cmd = getCommand("nsign");
        if (cmd != null) {
            SignCommand signCommand = new SignCommand(signManager);
            cmd.setExecutor(signCommand);
            cmd.setTabCompleter(signCommand);
        }

        // Start update loop
        int interval = signConfig.getUpdateInterval();
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            signManager.updateAll();
        }, interval, interval);

        getLogger().info("Nimbus Signs loaded — " + signManager.getSignCount() + " sign(s)");
    }
}
