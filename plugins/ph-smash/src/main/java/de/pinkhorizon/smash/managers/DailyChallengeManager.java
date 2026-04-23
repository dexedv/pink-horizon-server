package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DailyChallengeManager {

    // -------------------------------------------------------------------------
    // Challenge type definitions
    // -------------------------------------------------------------------------

    public enum ChallengeType {
        BOSS_KILLS  ("boss_kills",   "§c💀 Boss-Kills",       "Besiege {target} Bosse",          10,    new long[]{500,  1000,  2000}),
        TOTAL_DAMAGE("total_damage", "§e⚔ Schaden",           "Verursache {target} Schaden",      50000, new long[]{800,  1500,  3000}),
        COINS_EARNED("coins_earned", "§6★ Münzen verdienen",  "Verdiene {target} Münzen",         2000,  new long[]{400,   800,  2000}),
        DODGE_HITS  ("dodge_hits",   "§b⚡ Ausweichen",        "Weiche {target} Treffern aus",     15,    new long[]{300,   600,  1500}),
        STREAK_REACH("streak_reach", "§6🔥 Streak",           "Erreiche einen {target}er Streak", 3,     new long[]{600,  1200,  3000});

        public final String   id;
        public final String   displayName;
        public final String   descriptionTemplate;
        public final int      defaultTarget;
        /** Coin rewards for each of the 3 daily challenge slots */
        public final long[]   rewards;

        ChallengeType(String id, String displayName, String descriptionTemplate,
                      int defaultTarget, long[] rewards) {
            this.id                  = id;
            this.displayName         = displayName;
            this.descriptionTemplate = descriptionTemplate;
            this.defaultTarget       = defaultTarget;
            this.rewards             = rewards;
        }

        public String getDescription(int target) {
            return descriptionTemplate.replace("{target}", String.valueOf(target));
        }
    }

    // -------------------------------------------------------------------------

    private final PHSmash plugin;

    public DailyChallengeManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the 3 daily challenges for today (deterministic / date-seeded).
     */
    public List<ChallengeType> getTodaysChallenges() {
        ChallengeType[] values = ChallengeType.values();
        int base = (int) (LocalDate.now().toEpochDay() % values.length);
        List<ChallengeType> result = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            result.add(values[(base + i) % values.length]);
        }
        return result;
    }

    /**
     * Returns the current progress for the given challenge today, or 0.
     */
    public int getProgress(UUID uuid, ChallengeType type) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT progress FROM smash_daily WHERE uuid = ? AND challenge_date = ? AND challenge_id = ?")) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(LocalDate.now()));
            st.setString(3, type.id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("progress");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DailyChallengeManager.getProgress: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns whether the challenge has been completed today.
     */
    public boolean isCompleted(UUID uuid, ChallengeType type) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT completed FROM smash_daily WHERE uuid = ? AND challenge_date = ? AND challenge_id = ?")) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(LocalDate.now()));
            st.setString(3, type.id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("completed") > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DailyChallengeManager.isCompleted: " + e.getMessage());
        }
        return false;
    }

    /**
     * Adds progress for the given challenge. Auto-completes when target is reached.
     */
    public void addProgress(UUID uuid, ChallengeType type, int amount) {
        if (isCompleted(uuid, type)) return;

        int target = getTarget(type);

        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_daily (uuid, challenge_date, challenge_id, progress, completed)
                 VALUES (?, ?, ?, ?, 0)
                 ON DUPLICATE KEY UPDATE progress = LEAST(progress + VALUES(progress), ?)
                 """)) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(LocalDate.now()));
            st.setString(3, type.id);
            st.setInt(4, amount);
            st.setInt(5, target);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DailyChallengeManager.addProgress: " + e.getMessage());
            return;
        }

        // Auto-complete check
        int newProgress = getProgress(uuid, type);
        if (newProgress >= target) {
            // Mark completed directly without a Player reference for the DB part
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement(
                     "UPDATE smash_daily SET completed = 1 " +
                     "WHERE uuid = ? AND challenge_date = ? AND challenge_id = ? AND completed = 0")) {
                st.setString(1, uuid.toString());
                st.setDate(2, Date.valueOf(LocalDate.now()));
                st.setString(3, type.id);
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DailyChallengeManager.addProgress(complete): " + e.getMessage());
            }
        }
    }

    /**
     * Marks a challenge completed and gives the coin reward. Returns true on first completion.
     * The slot index (0-2) determines the reward tier.
     */
    public boolean completeChallenge(UUID uuid, ChallengeType type, Player player, PHSmash plugin) {
        if (isCompleted(uuid, type)) return false;

        // Determine reward by slot index in today's challenges
        List<ChallengeType> today = getTodaysChallenges();
        int slotIndex = today.indexOf(type);
        long reward = (slotIndex >= 0 && slotIndex < type.rewards.length)
            ? type.rewards[slotIndex]
            : type.rewards[0];

        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_daily SET completed = 1 " +
                 "WHERE uuid = ? AND challenge_date = ? AND challenge_id = ? AND completed = 0")) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(LocalDate.now()));
            st.setString(3, type.id);
            int rows = st.executeUpdate();
            if (rows == 0) return false; // already done
        } catch (SQLException e) {
            plugin.getLogger().warning("DailyChallengeManager.completeChallenge: " + e.getMessage());
            return false;
        }

        plugin.getCoinManager().addCoins(uuid, reward);
        if (player != null) {
            player.sendMessage("§a✔ §7Tägliche Herausforderung §f" + type.displayName
                + " §7abgeschlossen! §a+" + reward + " §7Münzen.");
        }
        return true;
    }

    /**
     * Returns the target for the given challenge type (currently the default target).
     */
    public int getTarget(ChallengeType type) {
        return type.defaultTarget;
    }
}
