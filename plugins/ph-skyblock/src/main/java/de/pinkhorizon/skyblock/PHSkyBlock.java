package de.pinkhorizon.skyblock;

import de.pinkhorizon.skyblock.commands.IslandCommand;
import de.pinkhorizon.skyblock.managers.IslandManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PHSkyBlock extends JavaPlugin {

    private static PHSkyBlock instance;
    private IslandManager islandManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        islandManager = new IslandManager(this);

        getCommand("island").setExecutor(new IslandCommand(this));

        getLogger().info("PH-SkyBlock gestartet!");
    }

    @Override
    public void onDisable() {
        if (islandManager != null) islandManager.saveAll();
        getLogger().info("PH-SkyBlock gestoppt.");
    }

    public static PHSkyBlock getInstance() { return instance; }
    public IslandManager getIslandManager() { return islandManager; }
}
