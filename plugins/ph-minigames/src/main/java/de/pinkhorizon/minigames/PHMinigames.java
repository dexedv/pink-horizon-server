package de.pinkhorizon.minigames;

import de.pinkhorizon.minigames.commands.JoinBedWarsCommand;
import de.pinkhorizon.minigames.commands.JoinSkyWarsCommand;
import de.pinkhorizon.minigames.commands.StatsCommand;
import de.pinkhorizon.minigames.listeners.GameListener;
import de.pinkhorizon.minigames.managers.ArenaManager;
import de.pinkhorizon.minigames.managers.StatsManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PHMinigames extends JavaPlugin {

    private static PHMinigames instance;
    private ArenaManager arenaManager;
    private StatsManager statsManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        arenaManager = new ArenaManager(this);
        statsManager = new StatsManager(this);

        getCommand("joinbedwars").setExecutor(new JoinBedWarsCommand(this));
        getCommand("joinskywars").setExecutor(new JoinSkyWarsCommand(this));
        getCommand("stats").setExecutor(new StatsCommand(this));

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("PH-Minigames gestartet!");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) arenaManager.stopAll();
        getLogger().info("PH-Minigames gestoppt.");
    }

    public static PHMinigames getInstance() { return instance; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public StatsManager getStatsManager() { return statsManager; }
}
