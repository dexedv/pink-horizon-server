package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

public class MilestoneManager {

    private static final int[] MILESTONES = {25, 50, 100, 200, 500, 999};

    private final PHSmash plugin;

    public MilestoneManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the given bossLevel hits any milestone and rewards the player once.
     * Should be called after a boss kill that advances the player's level.
     */
    public void checkAndReward(Player player, int bossLevel, PHSmash plugin) {
        UUID uuid = player.getUniqueId();

        for (int milestone : MILESTONES) {
            if (bossLevel != milestone) continue;

            // Attempt to INSERT – if already claimed, affected rows = 0
            boolean firstTime = false;
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement(
                     "INSERT IGNORE INTO smash_milestones (uuid, milestone_level) VALUES (?, ?)")) {
                st.setString(1, uuid.toString());
                st.setInt(2, milestone);
                firstTime = st.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().warning("MilestoneManager.checkAndReward: " + e.getMessage());
            }

            if (!firstTime) continue;

            // Give rewards
            giveRewards(player, milestone, plugin);

            // Title
            String titleStr = "§6§l✦ Meilenstein §f" + milestone + "§6§l! ✦";
            String subStr   = "§7Belohnungen erhalten!";
            player.showTitle(Title.title(
                Component.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(titleStr).content()),
                Component.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(subStr).content()),
                Title.Times.times(
                    Duration.ofMillis(500),
                    Duration.ofSeconds(3),
                    Duration.ofMillis(500))
            ));

            player.sendMessage("§6§l✦ §7Meilenstein §f" + milestone + " §7erreicht! Belohnungen wurden gutgeschrieben.");

            // Broadcast for milestone >= 50
            if (milestone >= 50) {
                String broadcast = "§6§l[Meilenstein] §e" + player.getName()
                    + " §7hat Meilenstein §f" + milestone + " §7erreicht! §6✦";
                Bukkit.broadcastMessage(broadcast);
            }

            // Special message at 999
            if (milestone == 999) {
                player.sendMessage("§d✦ §7Prestige ist jetzt freigeschaltet! /stb prestige");
            }
        }
    }

    private void giveRewards(Player player, int milestone, PHSmash plugin) {
        UUID uuid = player.getUniqueId();

        switch (milestone) {
            case 25 -> {
                plugin.getCoinManager().addCoins(uuid, 500);
                plugin.getLootManager().addLoot(uuid, LootItem.IRON_FRAGMENT, 10);
                player.sendMessage("§a+500 Münzen §7| §710x §7Eisen-Splitter");
            }
            case 50 -> {
                plugin.getCoinManager().addCoins(uuid, 1500);
                plugin.getLootManager().addLoot(uuid, LootItem.DIAMOND_SHARD, 5);
                player.sendMessage("§a+1500 Münzen §7| §75x §bBoss-Kristall");
            }
            case 100 -> {
                plugin.getCoinManager().addCoins(uuid, 5000);
                plugin.getLootManager().addLoot(uuid, LootItem.BOSS_CORE, 2);
                player.sendMessage("§a+5000 Münzen §7| §72x §5Boss-Kern");
            }
            case 200 -> {
                plugin.getCoinManager().addCoins(uuid, 15000);
                plugin.getLootManager().addLoot(uuid, LootItem.BOSS_CORE, 5);
                player.sendMessage("§a+15000 Münzen §7| §75x §5Boss-Kern");
            }
            case 500 -> {
                plugin.getCoinManager().addCoins(uuid, 50000);
                plugin.getLootManager().addLoot(uuid, LootItem.BOSS_CORE, 10);
                player.sendMessage("§a+50000 Münzen §7| §710x §5Boss-Kern");
            }
            case 999 -> {
                plugin.getCoinManager().addCoins(uuid, 100000);
                plugin.getLootManager().addLoot(uuid, LootItem.BOSS_CORE, 20);
                player.sendMessage("§a+100000 Münzen §7| §720x §5Boss-Kern");
            }
        }
    }
}
