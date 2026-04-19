package de.pinkhorizon.lobby;

import de.pinkhorizon.lobby.commands.BorderCommand;
import de.pinkhorizon.lobby.commands.BuildCommand;
import de.pinkhorizon.lobby.commands.NavigatorCommand;
import de.pinkhorizon.lobby.commands.PortalCommand;
import de.pinkhorizon.lobby.commands.RankCommand;
import de.pinkhorizon.lobby.commands.SetSpawnCommand;
import de.pinkhorizon.lobby.commands.TogglePlayersCommand;
import de.pinkhorizon.lobby.commands.VanishCommand;
import de.pinkhorizon.lobby.listeners.DoubleJumpListener;
import de.pinkhorizon.lobby.listeners.HotbarListener;
import de.pinkhorizon.lobby.listeners.JoinTitleListener;
import de.pinkhorizon.lobby.listeners.LobbyListener;
import de.pinkhorizon.lobby.listeners.SignListener;
import de.pinkhorizon.lobby.managers.AfkManager;
import de.pinkhorizon.lobby.managers.RankManager;
import de.pinkhorizon.lobby.managers.ScoreboardManager;
import de.pinkhorizon.lobby.managers.TabManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PHLobby extends JavaPlugin {

    private static PHLobby instance;
    private ScoreboardManager  scoreboardManager;
    private TabManager         tabManager;
    private NavigatorCommand   navigatorCommand;
    private PortalCommand      portalCommand;
    private VanishCommand      vanishCommand;
    private TogglePlayersCommand togglePlayersCommand;
    private BuildCommand       buildCommand;
    private AfkManager         afkManager;
    private RankManager        rankManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Manager
        scoreboardManager    = new ScoreboardManager(this);
        tabManager           = new TabManager(this);
        afkManager           = new AfkManager(this);
        rankManager          = new RankManager(this);

        // Commands
        navigatorCommand     = new NavigatorCommand(this);
        vanishCommand        = new VanishCommand(this);
        togglePlayersCommand = new TogglePlayersCommand(this);
        buildCommand         = new BuildCommand(this);
        portalCommand        = new PortalCommand(this);

        getCommand("navigator").setExecutor(navigatorCommand);
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this));
        getCommand("vanish").setExecutor(vanishCommand);
        getCommand("toggleplayers").setExecutor(togglePlayersCommand);
        getCommand("build").setExecutor(buildCommand);
        getCommand("portal").setExecutor(portalCommand);
        getCommand("portal").setTabCompleter(portalCommand);

        RankCommand rankCommand = new RankCommand(this);
        getCommand("rank").setExecutor(rankCommand);
        getCommand("rank").setTabCompleter(rankCommand);

        BorderCommand borderCommand = new BorderCommand(this);
        getCommand("border").setExecutor(borderCommand);
        getCommand("border").setTabCompleter(borderCommand);

        // Listeners
        getServer().getPluginManager().registerEvents(new JoinTitleListener(this, scoreboardManager, tabManager), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        getServer().getPluginManager().registerEvents(new HotbarListener(this), this);
        getServer().getPluginManager().registerEvents(new DoubleJumpListener(this), this);

        // Border laden + Entities + Gamerules beim Start
        org.bukkit.Bukkit.getScheduler().runTaskLater(this, () -> {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(
                getConfig().getString("spawn.world", "world"));
            if (world != null) {
                borderCommand.loadSavedBorder(world);
                world.getEntities().stream()
                    .filter(e -> !(e instanceof org.bukkit.entity.Player))
                    .forEach(org.bukkit.entity.Entity::remove);
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                world.setTime(6000);
                world.setStorm(false);
                world.setThundering(false);
                getLogger().info("Lobby-Welt initialisiert.");
            }
        }, 40L);

        getLogger().info("PH-Lobby gestartet!");
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) scoreboardManager.stopAll();
        if (tabManager        != null) tabManager.stop();
        if (portalCommand     != null) portalCommand.stopParticleTask();
        if (afkManager        != null) afkManager.stop();
        getLogger().info("PH-Lobby gestoppt.");
    }

    public static PHLobby getInstance()                        { return instance; }
    public ScoreboardManager getScoreboardManager()            { return scoreboardManager; }
    public TabManager getTabManager()                          { return tabManager; }
    public NavigatorCommand getNavigatorCommand()              { return navigatorCommand; }
    public VanishCommand getVanishCommand()                    { return vanishCommand; }
    public TogglePlayersCommand getTogglePlayersCommand()      { return togglePlayersCommand; }
    public BuildCommand getBuildCommand()                      { return buildCommand; }
    public AfkManager getAfkManager()                         { return afkManager; }
    public RankManager getRankManager()                       { return rankManager; }
}
