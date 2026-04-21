package de.pinkhorizon.minigames;

import de.pinkhorizon.minigames.commands.BedWarsCommand;
import de.pinkhorizon.minigames.database.MinigamesDatabaseManager;
import de.pinkhorizon.minigames.gui.BedWarsShopGui;
import de.pinkhorizon.minigames.hub.HubGui;
import de.pinkhorizon.minigames.hub.HubListener;
import de.pinkhorizon.minigames.hub.HubManager;
import de.pinkhorizon.minigames.listeners.BedWarsListener;
import de.pinkhorizon.minigames.managers.BedWarsArenaManager;
import de.pinkhorizon.minigames.managers.BedWarsStatsManager;
import de.pinkhorizon.minigames.managers.MinigamesHologramManager;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class PHMinigames extends JavaPlugin {

    private static PHMinigames instance;

    private MinigamesDatabaseManager db;
    private BedWarsArenaManager      arenaManager;
    private BedWarsStatsManager      statsManager;
    private MinigamesHologramManager hologramManager;
    private BedWarsShopGui           shopGui;
    private HubManager               hubManager;
    private HubGui                   hubGui;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Datenbank
        try {
            db = new MinigamesDatabaseManager(this);
        } catch (SQLException e) {
            getLogger().severe("Datenbankverbindung fehlgeschlagen: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Manager
        arenaManager    = new BedWarsArenaManager(this);
        statsManager    = new BedWarsStatsManager(this);
        hologramManager = new MinigamesHologramManager(this);
        shopGui         = new BedWarsShopGui(this);
        hubManager      = new HubManager(this);
        hubGui          = new HubGui(this);

        // Commands
        BedWarsCommand bwCmd = new BedWarsCommand(this);
        getCommand("bw").setExecutor(bwCmd);
        getCommand("bw").setTabCompleter(bwCmd);

        // Listener
        getServer().getPluginManager().registerEvents(new BedWarsListener(this), this);
        getServer().getPluginManager().registerEvents(new HubListener(this), this);

        // Holograms + Hub-Regeln nach Welt-Load anwenden
        getServer().getScheduler().runTaskLater(this, () -> {
            hologramManager.spawnAll();
            hubManager.applyHubWorldRulesFromConfig();
        }, 60L);

        getLogger().info("PH-Minigames gestartet! Arenen: " + arenaManager.getArenas().size() + " | Hub+TabList aktiv");
    }

    @Override
    public void onDisable() {
        if (arenaManager    != null) arenaManager.stopAll();
        if (hologramManager != null) hologramManager.stopAll();
        if (db              != null) db.close();
        getLogger().info("PH-Minigames gestoppt.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        return new VoidGenerator();
    }

    public static PHMinigames getInstance()             { return instance; }
    public MinigamesDatabaseManager getDb()             { return db; }
    public BedWarsArenaManager      getArenaManager()   { return arenaManager; }
    public BedWarsStatsManager      getStatsManager()   { return statsManager; }
    public MinigamesHologramManager getHologramManager(){ return hologramManager; }
    public BedWarsShopGui           getShopGui()        { return shopGui; }
    public HubManager               getHubManager()     { return hubManager; }
    public HubGui                   getHubGui()         { return hubGui; }
}
