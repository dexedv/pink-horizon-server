package de.pinkhorizon.skyblock;

import de.pinkhorizon.skyblock.commands.GeneratorCommand;
import de.pinkhorizon.skyblock.database.GeneratorRepository;
import de.pinkhorizon.skyblock.database.QuestRepository;
import de.pinkhorizon.skyblock.database.SkyDatabase;
import de.pinkhorizon.skyblock.economy.SkyVaultEconomy;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import de.pinkhorizon.skyblock.listeners.BentoBoxListener;
import de.pinkhorizon.skyblock.listeners.FarmingListener;
import de.pinkhorizon.skyblock.listeners.GeneratorListener;
import de.pinkhorizon.skyblock.listeners.GuiListener;
import de.pinkhorizon.skyblock.listeners.PlayerListener;
import de.pinkhorizon.skyblock.managers.AchievementManager;
import de.pinkhorizon.skyblock.managers.CoinManager;
import de.pinkhorizon.skyblock.managers.GeneratorManager;
import de.pinkhorizon.skyblock.managers.HologramManager;
import de.pinkhorizon.skyblock.managers.InfoHologramManager;
import de.pinkhorizon.skyblock.managers.LeaderboardManager;
import de.pinkhorizon.skyblock.managers.NpcManager;
import de.pinkhorizon.skyblock.managers.PlayerManager;
import de.pinkhorizon.skyblock.managers.QuestManager;
import de.pinkhorizon.skyblock.managers.SkyScoreboardManager;
import de.pinkhorizon.skyblock.managers.TitleManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class PHSkyBlock extends JavaPlugin {

    private static PHSkyBlock instance;

    // ── Datenbank ─────────────────────────────────────────────────────────────
    private SkyDatabase database;
    private GeneratorRepository generatorRepository;
    private QuestRepository questRepository;

    // ── Manager ───────────────────────────────────────────────────────────────
    private PlayerManager playerManager;
    private SkyScoreboardManager scoreboardManager;
    private CoinManager coinManager;
    private HologramManager hologramManager;
    private GeneratorManager generatorManager;
    private QuestManager questManager;
    private AchievementManager achievementManager;
    private TitleManager titleManager;
    private NpcManager npcManager;
    private InfoHologramManager infoHologramManager;
    private LeaderboardManager leaderboardManager;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] <white>";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // ── BentoBox Integration ──────────────────────────────────────────────
        BentoBoxHook.init();

        // ── Datenbank ─────────────────────────────────────────────────────────
        database            = new SkyDatabase(this);
        generatorRepository = new GeneratorRepository(this, database);
        questRepository     = new QuestRepository(this, database);

        // ── Manager ───────────────────────────────────────────────────────────
        playerManager      = new PlayerManager(this, database);
        scoreboardManager  = new SkyScoreboardManager(this);
        hologramManager    = new HologramManager(this);
        coinManager        = new CoinManager(this, generatorRepository);
        achievementManager = new AchievementManager(this, generatorRepository);
        titleManager       = new TitleManager(this, generatorRepository);
        questManager       = new QuestManager(this, questRepository, generatorRepository);
        generatorManager   = new GeneratorManager(this, generatorRepository);
        npcManager          = new NpcManager(this);
        infoHologramManager = new InfoHologramManager(this);
        leaderboardManager  = new LeaderboardManager(this);

        generatorManager.startTasks();

        // ── Vault Economy registrieren (optional) ─────────────────────────────
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(
                Economy.class, new SkyVaultEconomy(this), this, ServicePriority.Normal);
            getLogger().info("[PH-SkyBlock] Vault Economy registriert (SkyCoins).");
        }

        // NPCs + Hologramme nach Weltlade verzögert starten
        getServer().getScheduler().runTaskLater(this, () -> {
            npcManager.reloadNpcs();
            infoHologramManager.reloadAll();
        }, 60L);

        // ── Kommandos ─────────────────────────────────────────────────────────
        var genCmd = new GeneratorCommand(this);
        getCommand("phsk").setExecutor(genCmd);
        getCommand("phsk").setTabCompleter(genCmd);

        // ── Listener ──────────────────────────────────────────────────────────
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new GeneratorListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new FarmingListener(this), this);

        // BentoBox-Events nur registrieren wenn BentoBox verfügbar
        if (BentoBoxHook.isAvailable()) {
            pm.registerEvents(new BentoBoxListener(this), this);
        }

        getLogger().info("PH-SkyBlock v2.2.0 gestartet! (BentoBox-Integration, Generatoren, Quests, Achievements, Titel)");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null)     hologramManager.removeAll();
        if (infoHologramManager != null) infoHologramManager.removeAll();
        if (generatorManager != null)    generatorManager.saveAll();

        getServer().getOnlinePlayers().forEach(p -> {
            if (questManager != null)     questManager.savePlayer(p.getUniqueId());
            if (playerManager != null)    playerManager.saveAndUnload(p.getUniqueId());
        });

        if (database != null) database.close();
        getLogger().info("PH-SkyBlock gestoppt.");
    }

    public Component msg(String key, Object... args) {
        String raw = MESSAGES.getOrDefault(key, "<red>Unbekannte Nachricht: " + key);
        for (int i = 0; i < args.length - 1; i += 2) {
            raw = raw.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return MM.deserialize(PREFIX + raw);
    }

    public Component msgNoPrefix(String key, Object... args) {
        String raw = MESSAGES.getOrDefault(key, "<red>Unbekannte Nachricht: " + key);
        for (int i = 0; i < args.length - 1; i += 2) {
            raw = raw.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return MM.deserialize(raw);
    }

    // ── Nachrichten ───────────────────────────────────────────────────────────

    private static final java.util.Map<String, String> MESSAGES = java.util.Map.ofEntries(
        java.util.Map.entry("no-permission",         "<red>Du hast keine Berechtigung dafür."),
        java.util.Map.entry("only-players",          "<red>Nur Spieler können diesen Befehl nutzen."),
        java.util.Map.entry("player-not-found",      "<red>Spieler <yellow>{name}</yellow> nicht gefunden."),
        java.util.Map.entry("help-header",           "<light_purple>━━━ <bold>SkyBlock Hilfe</bold> ━━━"),
        java.util.Map.entry("help-footer",           "<light_purple>━━━━━━━━━━━━━━━━━━━━━")
    );

    // ── Getters ───────────────────────────────────────────────────────────────

    public static PHSkyBlock getInstance()              { return instance; }
    public SkyDatabase getDatabase()                    { return database; }
    public PlayerManager getPlayerManager()             { return playerManager; }
    public GeneratorRepository getGeneratorRepository() { return generatorRepository; }
    public QuestRepository getQuestRepository()         { return questRepository; }
    public SkyScoreboardManager getScoreboardManager()  { return scoreboardManager; }
    public CoinManager getCoinManager()                 { return coinManager; }
    public HologramManager getHologramManager()         { return hologramManager; }
    public GeneratorManager getGeneratorManager()       { return generatorManager; }
    public QuestManager getQuestManager()               { return questManager; }
    public AchievementManager getAchievementManager()   { return achievementManager; }
    public TitleManager getTitleManager()               { return titleManager; }
    public NpcManager getNpcManager()                   { return npcManager; }
    public InfoHologramManager getInfoHologramManager() { return infoHologramManager; }
    public LeaderboardManager getLeaderboardManager()   { return leaderboardManager; }
}
