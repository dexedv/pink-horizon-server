package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class AchievementManager {

    public enum Achievement {
        FIRST_STEPS    ("Erste Schritte",      "Logge dich zum ersten Mal ein.",                  100,  "✦"),
        COIN_COLLECTOR ("Münzsammler",          "Sammle insgesamt 1.000 Coins.",                   200,  "✦"),
        COIN_MASTER    ("Münzmeister",          "Sammle insgesamt 10.000 Coins.",                  500,  "✦✦"),
        RICH           ("Wohlhabend",           "Habe 50.000 Coins im Wallet.",                    500,  "✦✦"),
        JOB_BEGINNER   ("Berufseinsteiger",     "Nimm deinen ersten Job an.",                      150,  "✦"),
        JOB_EXPERT     ("Berufsexperte",        "Erreiche Level 5 in einem Job.",                  400,  "✦✦"),
        JOB_MASTER     ("Berufsmeister",        "Erreiche Level 10 in einem Job.",                1000,  "✦✦✦"),
        HOMEOWNER      ("Hausbesitzer",         "Setze dein erstes Home.",                         100,  "✦"),
        CLAIMER        ("Landeigentümer",       "Claime deinen ersten Chunk.",                     150,  "✦"),
        TRADER         ("Händler",              "Kaufe zum ersten Mal etwas im Shop.",             100,  "✦"),
        BANKIER        ("Bankier",              "Habe 100.000 Coins auf der Bank.",                750,  "✦✦✦"),
        PLAY_1H        ("Stundenverbringer",    "Spiele mindestens 1 Stunde.",                     100,  "✦"),
        PLAY_10H       ("Veterane",             "Spiele mindestens 10 Stunden.",                   500,  "✦✦"),
        QUEST_DONE     ("Pflichtbewusst",       "Schließe deine erste tägliche Quest ab.",         200,  "✦"),
        FRIENDS        ("Beliebte Person",      "Habe mindestens 3 Freunde.",                      300,  "✦✦");

        public final String displayName;
        public final String description;
        public final long   reward;
        public final String stars;

        Achievement(String displayName, String description, long reward, String stars) {
            this.displayName = displayName;
            this.description = description;
            this.reward = reward;
            this.stars = stars;
        }
    }

    private final PHSurvival plugin;

    public AchievementManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public boolean hasAchievement(UUID uuid, Achievement achievement) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT 1 FROM sv_achievements WHERE uuid=? AND achievement=?")) {
            st.setString(1, uuid.toString());
            st.setString(2, achievement.name());
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AchievementManager.hasAchievement: " + e.getMessage());
        }
        return false;
    }

    public boolean unlock(Player player, Achievement achievement) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT IGNORE INTO sv_achievements (uuid, achievement) VALUES (?,?)")) {
            st.setString(1, player.getUniqueId().toString());
            st.setString(2, achievement.name());
            if (st.executeUpdate() == 0) return false;
        } catch (SQLException e) {
            plugin.getLogger().warning("AchievementManager.unlock: " + e.getMessage());
            return false;
        }
        plugin.getEconomyManager().deposit(player.getUniqueId(), achievement.reward);
        player.sendMessage(Component.text(
            "§6§l✦ Achievement: §f" + achievement.displayName + " §8(+" + achievement.reward + " Coins)"));
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        return true;
    }

    public Set<Achievement> getUnlocked(UUID uuid) {
        Set<Achievement> result = EnumSet.noneOf(Achievement.class);
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT achievement FROM sv_achievements WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    try { result.add(Achievement.valueOf(rs.getString(1))); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AchievementManager.getUnlocked: " + e.getMessage());
        }
        return result;
    }

    // ── Trigger helpers ──────────────────────────────────────────────────

    public void checkCoins(Player player) {
        long bal = plugin.getEconomyManager().getBalance(player.getUniqueId());
        if (bal >= 1_000)  unlock(player, Achievement.COIN_COLLECTOR);
        if (bal >= 10_000) unlock(player, Achievement.COIN_MASTER);
        if (bal >= 50_000) unlock(player, Achievement.RICH);
    }

    public void checkBank(Player player) {
        long bal = plugin.getBankManager().getBalance(player.getUniqueId());
        if (bal >= 100_000) unlock(player, Achievement.BANKIER);
    }

    public void checkPlaytime(Player player) {
        long minutes = plugin.getStatsManager().getPlaytime(player.getUniqueId());
        if (minutes >= 60)  unlock(player, Achievement.PLAY_1H);
        if (minutes >= 600) unlock(player, Achievement.PLAY_10H);
    }

    public void checkJobLevel(Player player, int level) {
        if (level >= 5)  unlock(player, Achievement.JOB_EXPERT);
        if (level >= 10) unlock(player, Achievement.JOB_MASTER);
    }

    public void checkFriends(Player player) {
        if (plugin.getFriendManager().getFriends(player.getUniqueId()).size() >= 3)
            unlock(player, Achievement.FRIENDS);
    }
}
