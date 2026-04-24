package de.pinkhorizon.vote;

import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class VoteListener implements Listener {

    private final PHVote plugin;

    public VoteListener(PHVote plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        String playerName = event.getVote().getUsername();
        int    reward     = plugin.getConfig().getInt("vote-reward.coins", 1);
        boolean broadcast = plugin.getConfig().getBoolean("vote-reward.broadcast", true);
        String msg        = plugin.getConfig().getString("vote-reward.broadcast-message",
            "&d&l[Vote] &f%player% &7hat gevotet!");

        // Coins vergeben (async DB)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // UUID per Name nachschlagen (online oder aus DB)
            Player online = Bukkit.getPlayer(playerName);
            UUID   uuid;
            String name;

            if (online != null) {
                uuid = online.getUniqueId();
                name = online.getName();
            } else {
                // Offline: UUID über Paper-API (cached)
                try {
                    var profile = Bukkit.createProfile(playerName);
                    profile.complete(false);
                    uuid = profile.getId();
                    name = playerName;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Vote: UUID für " + playerName + " nicht gefunden.");
                    return;
                }
                if (uuid == null) return;
            }

            plugin.getVoteCoinManager().addCoins(uuid, name, reward, true);

            // Nachricht im Main-Thread
            final UUID   finalUuid = uuid;
            final String finalName = name;
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Broadcast
                if (broadcast) {
                    String bcastMsg = color(msg.replace("%player%", finalName));
                    Bukkit.broadcastMessage(bcastMsg);
                }

                // Direktnachricht + Sound wenn online
                Player p = Bukkit.getPlayer(finalUuid);
                if (p != null && p.isOnline()) {
                    int total = plugin.getVoteCoinManager().getCoins(finalUuid);
                    p.sendMessage(color("&d&l[Vote] &7Danke fürs Voten! Du hast &d+" + reward
                        + " VoteCoin(s) &7erhalten. &8(Gesamt: " + total + ")"));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
                }
            });
        });
    }

    private String color(String s) {
        return s.replace("&", "\u00a7");
    }
}
