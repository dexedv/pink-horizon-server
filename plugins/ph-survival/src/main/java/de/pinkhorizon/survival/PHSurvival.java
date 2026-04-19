package de.pinkhorizon.survival;

import de.pinkhorizon.survival.commands.*;
import de.pinkhorizon.survival.listeners.*;
import de.pinkhorizon.survival.managers.*;
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
    private ShopCommand shopCommand;
    private JobsCommand jobsCommand;

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

        HelpHoloCommand helpHoloCmd = new HelpHoloCommand(this);
        getCommand("helpholo").setExecutor(helpHoloCmd);
        getCommand("helpholo").setTabCompleter(helpHoloCmd);

        // Listeners
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalChatListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new JobsListener(this), this);
        getServer().getPluginManager().registerEvents(new AfkListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalProtectionListener(), this);

        // Holograms nach Weltlade spawnen
        getServer().getScheduler().runTaskLater(this, () -> hologramManager.spawnAll(), 60L);

        getLogger().info("PH-Survival gestartet!");
    }

    @Override
    public void onDisable() {
        if (claimManager != null) claimManager.save();
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
}
