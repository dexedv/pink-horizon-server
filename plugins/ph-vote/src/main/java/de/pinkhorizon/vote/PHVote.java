package de.pinkhorizon.vote;

import de.pinkhorizon.vote.commands.VoteAdminCommand;
import de.pinkhorizon.vote.commands.VoteCoinsCommand;
import de.pinkhorizon.vote.commands.VoteCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PHVote extends JavaPlugin {

    private VoteCoinManager voteCoinManager;
    private VoteShopGUI     voteShopGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        voteCoinManager = new VoteCoinManager(this);
        voteShopGUI     = new VoteShopGUI(this);

        // Listener
        getServer().getPluginManager().registerEvents(voteShopGUI, this);

        // NuVotifier optional einbinden
        if (getServer().getPluginManager().getPlugin("NuVotifier") != null) {
            getServer().getPluginManager().registerEvents(new VoteListener(this), this);
            getLogger().info("NuVotifier gefunden – Vote-Listener aktiv.");
        } else {
            getLogger().warning("NuVotifier nicht gefunden – Votes werden nicht automatisch verarbeitet.");
        }

        // Commands
        getCommand("vote").setExecutor(new VoteCommand(this));
        getCommand("votecoins").setExecutor(new VoteCoinsCommand(this));
        getCommand("voteshop").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("§cNur für Spieler.");
                return true;
            }
            voteShopGUI.open(player);
            return true;
        });

        VoteAdminCommand adminCmd = new VoteAdminCommand(this);
        getCommand("voteadmin").setExecutor(adminCmd);
        getCommand("voteadmin").setTabCompleter(adminCmd);

        getLogger().info("PH-Vote gestartet!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PH-Vote gestoppt.");
    }

    public VoteCoinManager getVoteCoinManager() { return voteCoinManager; }
    public VoteShopGUI     getVoteShopGUI()     { return voteShopGUI;     }
}
