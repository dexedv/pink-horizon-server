package de.pinkhorizon.survival;

import de.pinkhorizon.survival.commands.*;
import de.pinkhorizon.survival.listeners.*;
import de.pinkhorizon.survival.managers.*;
import de.pinkhorizon.survival.managers.AchievementManager;
import de.pinkhorizon.survival.managers.BankManager;
import de.pinkhorizon.survival.managers.FriendManager;
import de.pinkhorizon.survival.managers.MailManager;
import de.pinkhorizon.survival.managers.QuestManager;
import de.pinkhorizon.survival.managers.TradeManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PHSurvival extends JavaPlugin {

    private static PHSurvival instance;
    private ClaimManager claimManager;
    private EconomyManager economyManager;
    private HomeManager homeManager;
    private WarpManager warpManager;
    private SurvivalRankManager rankManager;
    private SurvivalScoreboardManager scoreboardManager;
    private SurvivalTabManager tabManager;
    private UpgradeManager upgradeManager;
    private JobManager jobManager;
    private AfkManager afkManager;
    private StatsManager statsManager;
    private SurvivalHologramManager hologramManager;
    private ClaimBorderVisualizer claimBorderVisualizer;
    private de.pinkhorizon.survival.listeners.ChestShopListener chestShopListener;
    private ShopCommand shopCommand;
    private JobsCommand jobsCommand;
    private MailManager mailManager;
    private FriendManager friendManager;
    private BankManager bankManager;
    private AchievementManager achievementManager;
    private QuestManager questManager;
    private TradeManager tradeManager;
    private SpawnPasteManager spawnPasteManager;
    private SpawnBorderManager spawnBorderManager;
    private de.pinkhorizon.survival.managers.NpcManager npcManager;
    private de.pinkhorizon.survival.managers.AuctionManager auctionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Managers
        claimManager = new ClaimManager(this);
        economyManager = new EconomyManager(this);
        homeManager = new HomeManager(this);
        warpManager = new WarpManager(this);
        upgradeManager = new UpgradeManager(this);
        jobManager = new JobManager(this);
        statsManager = new StatsManager(this);
        hologramManager = new SurvivalHologramManager(this);
        rankManager = new SurvivalRankManager(this);
        scoreboardManager = new SurvivalScoreboardManager(this);
        tabManager = new SurvivalTabManager(this);
        afkManager = new AfkManager(this);
        claimBorderVisualizer = new ClaimBorderVisualizer(this);
        new PlaytimeRewardManager(this);
        mailManager        = new MailManager(this);
        friendManager      = new FriendManager(this);
        achievementManager = new AchievementManager(this);
        bankManager        = new BankManager(this);
        questManager       = new QuestManager(this);
        tradeManager       = new TradeManager(this);
        spawnPasteManager  = new SpawnPasteManager(this);
        spawnBorderManager = new SpawnBorderManager(this);
        npcManager         = new de.pinkhorizon.survival.managers.NpcManager(this);
        auctionManager     = new de.pinkhorizon.survival.managers.AuctionManager(this);

        // Commands
        ClaimCommand claimCmd = new ClaimCommand(this);
        HomeCommand homeCmd = new HomeCommand(this);
        WarpCommand warpCmd = new WarpCommand(this);
        TpaCommand tpaCmd = new TpaCommand(this);
        shopCommand = new ShopCommand(this);
        jobsCommand = new JobsCommand(this);

        getCommand("claim").setExecutor(claimCmd);
        getCommand("unclaim").setExecutor(claimCmd);
        getCommand("claimlist").setExecutor(claimCmd);

        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("shop").setExecutor(shopCommand);
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("eco").setExecutor(new EcoCommand(this));
        getCommand("sell").setExecutor(new SellCommand(this));

        getCommand("sethome").setExecutor(homeCmd);
        getCommand("sethome").setTabCompleter(homeCmd);
        getCommand("home").setExecutor(homeCmd);
        getCommand("home").setTabCompleter(homeCmd);
        getCommand("delhome").setExecutor(homeCmd);
        getCommand("delhome").setTabCompleter(homeCmd);
        getCommand("homes").setExecutor(homeCmd);

        getCommand("back").setExecutor(new BackCommand());
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("setspawn").setExecutor(new SpawnCommand(this));

        getCommand("tpa").setExecutor(tpaCmd);
        getCommand("tpaccept").setExecutor(tpaCmd);
        getCommand("tpdeny").setExecutor(tpaCmd);

        getCommand("setwarp").setExecutor(warpCmd);
        getCommand("setwarp").setTabCompleter(warpCmd);
        getCommand("warp").setExecutor(warpCmd);
        getCommand("warp").setTabCompleter(warpCmd);
        getCommand("delwarp").setExecutor(warpCmd);
        getCommand("delwarp").setTabCompleter(warpCmd);
        getCommand("warps").setExecutor(warpCmd);

        SurvivalRankCommand rankCmd = new SurvivalRankCommand(this);
        getCommand("srank").setExecutor(rankCmd);
        getCommand("srank").setTabCompleter(rankCmd);

        ClaimTrustCommand trustCmd = new ClaimTrustCommand(this);
        getCommand("trust").setExecutor(trustCmd);
        getCommand("untrust").setExecutor(trustCmd);
        getCommand("trustlist").setExecutor(trustCmd);

        getCommand("jobs").setExecutor(jobsCommand);

        MailCommand mailCmd = new MailCommand(this);
        getCommand("mail").setExecutor(mailCmd);
        getCommand("mail").setTabCompleter(mailCmd);

        FriendCommand friendCmd = new FriendCommand(this);
        getCommand("friend").setExecutor(friendCmd);
        getCommand("friend").setTabCompleter(friendCmd);

        BankCommand bankCmd = new BankCommand(this);
        getCommand("bank").setExecutor(bankCmd);
        getCommand("bank").setTabCompleter(bankCmd);

        getCommand("achievements").setExecutor(new AchievementCommand(this));

        getCommand("quests").setExecutor(new QuestCommand(this));

        LeaderboardCommand lbCmd = new LeaderboardCommand(this);
        getCommand("lb").setExecutor(lbCmd);
        getCommand("lb").setTabCompleter(lbCmd);

        TradeCommand tradeCmd = new TradeCommand(this);
        getCommand("trade").setExecutor(tradeCmd);
        getCommand("trade").setTabCompleter(tradeCmd);

        getCommand("resetworld").setExecutor(new ResetWorldCommand(this));

        SpawnBorderCommand sbc = new SpawnBorderCommand(this);
        getCommand("spawnborder").setExecutor(sbc);
        getCommand("spawnborder").setTabCompleter(sbc);
        if (spawnBorderManager.isPermanentDisplay()) {
            getServer().getScheduler().runTaskLater(this, sbc::startPermanentTask, 20L);
        }

        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            SchematicCommand schemCmd = new SchematicCommand(this);
            getCommand("schem").setExecutor(schemCmd);
            getCommand("schem").setTabCompleter(schemCmd);
            getCommand("flatarea").setExecutor(new FlatAreaCommand(this));
            getLogger().info("FAWE erkannt – /schem und /flatarea aktiviert.");
            spawnPasteManager.checkAndExecute();
        }

        StatsCommand statsCmd = new StatsCommand(this);
        getCommand("stats").setExecutor(statsCmd);
        getCommand("stats").setTabCompleter(statsCmd);

        KitCommand kitCmd = new KitCommand(this);
        getCommand("kit").setExecutor(kitCmd);
        getCommand("kit").setTabCompleter(kitCmd);

        ReportCommand reportCmd = new ReportCommand(this);
        getCommand("report").setExecutor(reportCmd);
        getCommand("report").setTabCompleter(reportCmd);

        getCommand("rtp").setExecutor(new RtpCommand(this));

        de.pinkhorizon.survival.commands.NpcCommand npcCmd = new de.pinkhorizon.survival.commands.NpcCommand(this);
        getCommand("npc").setExecutor(npcCmd);
        getCommand("npc").setTabCompleter(npcCmd);

        de.pinkhorizon.survival.gui.TutorialGui tutorialGui = new de.pinkhorizon.survival.gui.TutorialGui();
        getCommand("tutorial").setExecutor(new de.pinkhorizon.survival.commands.TutorialCommand(tutorialGui));
        getServer().getPluginManager().registerEvents(tutorialGui, this);

        de.pinkhorizon.survival.gui.AhGui ahGui = new de.pinkhorizon.survival.gui.AhGui(this);
        de.pinkhorizon.survival.commands.AhCommand ahCmd = new de.pinkhorizon.survival.commands.AhCommand(this, ahGui);
        getCommand("ah").setExecutor(ahCmd);
        getCommand("ah").setTabCompleter(ahCmd);
        getServer().getPluginManager().registerEvents(ahGui, this);

        HelpHoloCommand helpHoloCmd = new HelpHoloCommand(this);
        getCommand("helpholo").setExecutor(helpHoloCmd);
        getCommand("helpholo").setTabCompleter(helpHoloCmd);

        CreateShopCommand shopCmd = new CreateShopCommand(this);
        getCommand("createshop").setExecutor(shopCmd);
        getCommand("removeshop").setExecutor(shopCmd);

        // Listeners
        chestShopListener = new de.pinkhorizon.survival.listeners.ChestShopListener(this);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(chestShopListener, this);
        getServer().getPluginManager().registerEvents(new SurvivalJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalChatListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new JobsListener(this), this);
        getServer().getPluginManager().registerEvents(new AfkListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new DeathChestListener(this), this);
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnBorderWandListener(this), this);
        getServer().getPluginManager().registerEvents(new de.pinkhorizon.survival.listeners.NpcListener(this), this);

        // Holograms + NPCs nach Weltlade spawnen
        getServer().getScheduler().runTaskLater(this, () -> {
            hologramManager.spawnAll();
            npcManager.spawnAll();
        }, 60L);

        getLogger().info("PH-Survival gestartet!");
    }

    @Override
    public void onDisable() {
        if (claimManager != null) claimManager.save();
        if (auctionManager != null) auctionManager.save();
        if (claimBorderVisualizer != null) claimBorderVisualizer.stop();
        if (scoreboardManager != null) scoreboardManager.stopAll();
        if (tabManager != null) tabManager.stop();
        getLogger().info("PH-Survival gestoppt.");
    }

    public static PHSurvival getInstance() { return instance; }
    public ClaimManager getClaimManager() { return claimManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public HomeManager getHomeManager() { return homeManager; }
    public WarpManager getWarpManager() { return warpManager; }
    public SurvivalRankManager getRankManager() { return rankManager; }
    public UpgradeManager getUpgradeManager() { return upgradeManager; }
    public SurvivalScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public SurvivalTabManager getTabManager() { return tabManager; }
    public ShopCommand getShopCommand() { return shopCommand; }
    public JobsCommand getJobsCommand() { return jobsCommand; }
    public JobManager getJobManager() { return jobManager; }
    public AfkManager getAfkManager() { return afkManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public SurvivalHologramManager getHologramManager() { return hologramManager; }
    public de.pinkhorizon.survival.listeners.ChestShopListener getChestShopListener() { return chestShopListener; }
    public MailManager getMailManager()               { return mailManager; }
    public FriendManager getFriendManager()           { return friendManager; }
    public BankManager getBankManager()               { return bankManager; }
    public AchievementManager getAchievementManager() { return achievementManager; }
    public QuestManager getQuestManager()             { return questManager; }
    public TradeManager getTradeManager()             { return tradeManager; }
    public SpawnBorderManager getSpawnBorderManager() { return spawnBorderManager; }
    public de.pinkhorizon.survival.managers.NpcManager getNpcManager() { return npcManager; }
    public de.pinkhorizon.survival.managers.AuctionManager getAuctionManager() { return auctionManager; }
}
