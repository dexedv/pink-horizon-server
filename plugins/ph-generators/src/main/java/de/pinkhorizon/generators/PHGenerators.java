package de.pinkhorizon.generators;

import de.pinkhorizon.generators.commands.GeneratorCommand;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.database.GenDatabaseManager;
import de.pinkhorizon.generators.database.GeneratorRepository;
import de.pinkhorizon.generators.gui.BlockShopGUI;
import de.pinkhorizon.generators.gui.NavigatorGUI;
import de.pinkhorizon.generators.gui.ShopGUI;
import de.pinkhorizon.generators.gui.UpgradeGUI;
import de.pinkhorizon.generators.listeners.GeneratorBlockListener;
import de.pinkhorizon.generators.listeners.PlayerListener;
import de.pinkhorizon.generators.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PH-Generators – IdleForge Spielmodus
 * Hauptklasse: initialisiert alle Manager und registriert Commands/Listener.
 */
public class PHGenerators extends JavaPlugin {

    private static PHGenerators instance;

    // Datenbank
    private GenDatabaseManager databaseManager;
    private GeneratorRepository repository;

    // Manager
    private IslandWorldManager islandWorldManager;
    private GeneratorManager  generatorManager;
    private MoneyManager      moneyManager;
    private PrestigeManager   prestigeManager;
    private HologramManager   hologramManager;
    private BoosterManager    boosterManager;
    private SynergyManager    synergyManager;
    private QuestManager      questManager;
    private AchievementManager achievementManager;
    private GuildManager      guildManager;
    private AfkRewardManager  afkRewardManager;
    private LeaderboardManager leaderboardManager;

    // GUIs
    private ShopGUI      shopGUI;
    private UpgradeGUI   upgradeGUI;
    private BlockShopGUI blockShopGUI;
    private NavigatorGUI navigatorGUI;

    // Scoreboard
    private ScoreboardManager scoreboardManager;

    // Online-Spielerdaten
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // ── Datenbank ─────────────────────────────────────────────────────────
        databaseManager = new GenDatabaseManager(this);
        databaseManager.init();
        repository = new GeneratorRepository(databaseManager, getLogger());

        // ── Manager (Reihenfolge wichtig) ─────────────────────────────────────
        islandWorldManager = new IslandWorldManager(this);
        hologramManager   = new HologramManager(this);
        synergyManager    = new SynergyManager(this);
        generatorManager  = new GeneratorManager(this);
        moneyManager      = new MoneyManager(this);
        prestigeManager   = new PrestigeManager(this);
        boosterManager    = new BoosterManager(this);
        questManager      = new QuestManager(this);
        achievementManager = new AchievementManager(this);
        guildManager      = new GuildManager(this);
        afkRewardManager  = new AfkRewardManager(this);
        leaderboardManager = new LeaderboardManager(this);

        // ── GUIs ──────────────────────────────────────────────────────────────
        shopGUI      = new ShopGUI(this);
        upgradeGUI   = new UpgradeGUI(this);
        blockShopGUI  = new BlockShopGUI(this);
        navigatorGUI  = new NavigatorGUI(this);
        scoreboardManager = new ScoreboardManager(this);

        // ── Starten ───────────────────────────────────────────────────────────
        hologramManager.startUpdateTask();
        moneyManager.start();
        questManager.start();
        afkRewardManager.start();
        leaderboardManager.start();
        scoreboardManager.start();

        // ── Commands registrieren ─────────────────────────────────────────────
        GeneratorCommand cmd = new GeneratorCommand(this);
        getCommand("gen").setExecutor(cmd);
        getCommand("gen").setTabCompleter(cmd);
        getCommand("booster").setExecutor(cmd);

        // ── Listener registrieren ─────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GeneratorBlockListener(this), this);
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager().registerEvents(upgradeGUI, this);
        getServer().getPluginManager().registerEvents(blockShopGUI, this);
        getServer().getPluginManager().registerEvents(navigatorGUI, this);

        getLogger().info("PH-Generators (IdleForge) gestartet!");
    }

    @Override
    public void onDisable() {
        // Ticker stoppen
        if (scoreboardManager != null) scoreboardManager.stop();
        if (moneyManager != null) moneyManager.stop();
        if (hologramManager != null) hologramManager.stopUpdateTask();
        if (questManager != null) questManager.stop();
        if (afkRewardManager != null) afkRewardManager.stop();
        if (leaderboardManager != null) leaderboardManager.stop();

        // Alle Online-Spieler speichern und Inseln entladen
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            entry.getValue().setLastSeen(System.currentTimeMillis() / 1000);
            repository.savePlayer(entry.getValue());
            islandWorldManager.unloadIsland(entry.getKey());
        }
        playerDataMap.clear();

        // DB schließen
        if (databaseManager != null) databaseManager.close();

        getLogger().info("PH-Generators gestoppt. Alle Daten gespeichert.");
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    public static PHGenerators getInstance()             { return instance; }

    public GenDatabaseManager getDatabaseManager()       { return databaseManager; }
    public GeneratorRepository getRepository()           { return repository; }

    public IslandWorldManager getIslandWorldManager()    { return islandWorldManager; }
    public GeneratorManager  getGeneratorManager()       { return generatorManager; }
    public MoneyManager      getMoneyManager()           { return moneyManager; }
    public PrestigeManager   getPrestigeManager()        { return prestigeManager; }
    public HologramManager   getHologramManager()        { return hologramManager; }
    public BoosterManager    getBoosterManager()         { return boosterManager; }
    public SynergyManager    getSynergyManager()         { return synergyManager; }
    public QuestManager      getQuestManager()           { return questManager; }
    public AchievementManager getAchievementManager()   { return achievementManager; }
    public GuildManager      getGuildManager()           { return guildManager; }
    public AfkRewardManager  getAfkRewardManager()       { return afkRewardManager; }
    public LeaderboardManager getLeaderboardManager()    { return leaderboardManager; }

    public ShopGUI      getShopGUI()                     { return shopGUI; }
    public UpgradeGUI   getUpgradeGUI()                  { return upgradeGUI; }
    public BlockShopGUI getBlockShopGUI()                { return blockShopGUI; }
    public NavigatorGUI getNavigatorGUI()                { return navigatorGUI; }
    public ScoreboardManager getScoreboardManager()      { return scoreboardManager; }

    public Map<UUID, PlayerData> getPlayerDataMap()      { return playerDataMap; }
}
