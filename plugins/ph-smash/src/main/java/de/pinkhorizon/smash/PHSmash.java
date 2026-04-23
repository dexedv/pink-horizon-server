package de.pinkhorizon.smash;

import de.pinkhorizon.smash.arena.ArenaManager;
import de.pinkhorizon.smash.commands.SmashCommand;
import de.pinkhorizon.smash.database.SmashDatabaseManager;
import de.pinkhorizon.smash.gui.AbilityGui;
import de.pinkhorizon.smash.gui.ShopGui;
import de.pinkhorizon.smash.gui.UpgradeGui;
import de.pinkhorizon.smash.hologram.HologramManager;
import de.pinkhorizon.smash.listeners.SmashCombatListener;
import de.pinkhorizon.smash.listeners.SmashJoinListener;
import de.pinkhorizon.smash.listeners.SmashNavigatorListener;
import de.pinkhorizon.smash.managers.*;
import de.pinkhorizon.smash.npc.NpcManager;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class PHSmash extends JavaPlugin {

    private SmashDatabaseManager   db;
    private PlayerDataManager      playerDataManager;
    private LootManager            lootManager;
    private UpgradeManager         upgradeManager;
    private CoinManager            coinManager;
    private AbilityManager         abilityManager;
    private ArenaManager           arenaManager;
    private SmashScoreboardManager scoreboardManager;
    private SmashTabManager        tabManager;
    private UpgradeGui             upgradeGui;
    private AbilityGui             abilityGui;
    private ShopGui                shopGui;
    private NpcManager             npcManager;
    private HologramManager        hologramManager;

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

        // Plugin-Messaging-Kanal für Velocity/BungeeCord (sendToLobby)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Manager (Reihenfolge wichtig)
        playerDataManager  = new PlayerDataManager(this);
        lootManager        = new LootManager(this);
        upgradeManager     = new UpgradeManager(this);
        coinManager        = new CoinManager(this);
        abilityManager     = new AbilityManager(this);
        arenaManager       = new ArenaManager(this);   // löscht verwaiste Arena-Welten
        scoreboardManager  = new SmashScoreboardManager(this);
        tabManager         = new SmashTabManager(this);
        upgradeGui         = new UpgradeGui(this);
        abilityGui         = new AbilityGui(this);
        shopGui            = new ShopGui(this);

        // Commands
        SmashCommand smashCmd = new SmashCommand(this);
        getCommand("stb").setExecutor(smashCmd);
        getCommand("stb").setTabCompleter(smashCmd);

        // NPC-Manager + Holograms
        npcManager      = new NpcManager(this);
        hologramManager = new HologramManager(this);

        // Listener
        getServer().getPluginManager().registerEvents(new SmashCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SmashJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new SmashNavigatorListener(this), this);
        getServer().getPluginManager().registerEvents(upgradeGui, this);
        getServer().getPluginManager().registerEvents(abilityGui, this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        getServer().getPluginManager().registerEvents(npcManager, this);

        // Template-Welt (Lobby) konfigurieren: kein Mob-Spawn, kein Tageszyklus
        getServer().getScheduler().runTask(this, () -> {
            for (World w : getServer().getWorlds()) {
                w.setGameRule(GameRule.DO_MOB_SPAWNING,   false);
                w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setGameRule(GameRule.DO_WEATHER_CYCLE,  false);
                w.setGameRule(GameRule.DO_FIRE_TICK,      false);
                w.setDifficulty(Difficulty.PEACEFUL);
                w.setTime(6000);
            }
        });

        getLogger().info("PH-Smash gestartet! Jeder Spieler bekommt seine eigene Arena.");
    }

    @Override
    public void onDisable() {
        if (hologramManager   != null) hologramManager.removeAll();
        if (arenaManager      != null) arenaManager.destroyAll();
        if (scoreboardManager != null) scoreboardManager.stopAll();
        if (tabManager        != null) tabManager.stop();
        if (db                != null) db.close();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("PH-Smash gestoppt.");
    }

    public SmashDatabaseManager   getDb()               { return db; }
    public PlayerDataManager      getPlayerDataManager(){ return playerDataManager; }
    public LootManager            getLootManager()      { return lootManager; }
    public UpgradeManager         getUpgradeManager()   { return upgradeManager; }
    public CoinManager            getCoinManager()      { return coinManager; }
    public AbilityManager         getAbilityManager()   { return abilityManager; }
    public ArenaManager           getArenaManager()     { return arenaManager; }
    public SmashScoreboardManager getScoreboardManager(){ return scoreboardManager; }
    public SmashTabManager        getTabManager()       { return tabManager; }
    public UpgradeGui             getUpgradeGui()       { return upgradeGui; }
    public AbilityGui             getAbilityGui()       { return abilityGui; }
    public ShopGui                getShopGui()          { return shopGui; }
    public NpcManager             getNpcManager()       { return npcManager; }
    public HologramManager        getHologramManager()  { return hologramManager; }
}
