package de.pinkhorizon.smash;

import de.pinkhorizon.smash.commands.SmashCommand;
import de.pinkhorizon.smash.database.SmashDatabaseManager;
import de.pinkhorizon.smash.gui.UpgradeGui;
import de.pinkhorizon.smash.listeners.SmashCombatListener;
import de.pinkhorizon.smash.listeners.SmashJoinListener;
import de.pinkhorizon.smash.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class PHSmash extends JavaPlugin {

    private SmashDatabaseManager  db;
    private PlayerDataManager     playerDataManager;
    private LootManager           lootManager;
    private UpgradeManager        upgradeManager;
    private BossManager           bossManager;
    private SmashScoreboardManager scoreboardManager;
    private SmashTabManager       tabManager;
    private UpgradeGui            upgradeGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Datenbank
        try {
            db = new SmashDatabaseManager(this);
        } catch (SQLException e) {
            getLogger().severe("Datenbankverbindung fehlgeschlagen: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Manager (Reihenfolge wichtig wegen Abhängigkeiten)
        playerDataManager  = new PlayerDataManager(this);
        lootManager        = new LootManager(this);
        upgradeManager     = new UpgradeManager(this);
        bossManager        = new BossManager(this);
        scoreboardManager  = new SmashScoreboardManager(this);
        tabManager         = new SmashTabManager(this);
        upgradeGui         = new UpgradeGui(this);

        // Commands
        SmashCommand smashCmd = new SmashCommand(this);
        getCommand("stb").setExecutor(smashCmd);
        getCommand("stb").setTabCompleter(smashCmd);

        // Listener
        getServer().getPluginManager().registerEvents(new SmashCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SmashJoinListener(this), this);
        getServer().getPluginManager().registerEvents(upgradeGui, this);

        // Ersten Boss nach Welt-Load spawnen (5s Verzögerung)
        getServer().getScheduler().runTaskLater(this, () -> {
            int level = playerDataManager.getGlobalBossLevel();
            bossManager.spawnBoss(level);
            getLogger().info("Boss Level " + level + " gespawnt.");
        }, 100L);

        getLogger().info("PH-Smash gestartet! Smash the Boss ist bereit.");
    }

    @Override
    public void onDisable() {
        if (bossManager      != null) bossManager.removeBoss();
        if (scoreboardManager != null) scoreboardManager.stopAll();
        if (tabManager       != null) tabManager.stop();
        if (db               != null) db.close();
        getLogger().info("PH-Smash gestoppt.");
    }

    public SmashDatabaseManager   getDb()               { return db; }
    public PlayerDataManager      getPlayerDataManager(){ return playerDataManager; }
    public LootManager            getLootManager()      { return lootManager; }
    public UpgradeManager         getUpgradeManager()   { return upgradeManager; }
    public BossManager            getBossManager()      { return bossManager; }
    public SmashScoreboardManager getScoreboardManager(){ return scoreboardManager; }
    public SmashTabManager        getTabManager()       { return tabManager; }
    public UpgradeGui             getUpgradeGui()       { return upgradeGui; }
}
