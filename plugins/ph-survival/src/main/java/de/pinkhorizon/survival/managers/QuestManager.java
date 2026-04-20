package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class QuestManager {

    public enum QuestType {
        MINE_ORES    ("50 Erze abbauen",      50),
        CUT_TREES    ("30 Bäume fällen",       30),
        KILL_MOBS    ("25 Monster töten",      25),
        CATCH_FISH   ("20 Fische angeln",      20),
        HARVEST_CROPS("40 Pflanzen ernten",    40);

        public final String description;
        public final int    goal;

        QuestType(String description, int goal) {
            this.description = description;
            this.goal = goal;
        }
    }

    public record Quest(QuestType type, int progress, boolean completed) {}

    private static final long QUEST_REWARD = 300L;

    private final PHSurvival plugin;

    public QuestManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    /** Returns today's quests for the player, generating new ones if needed. */
    public List<Quest> getDailyQuests(UUID uuid) {
        Date today = Date.valueOf(LocalDate.now());
        List<Quest> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT quest_id, progress, completed FROM sv_quests WHERE uuid=? AND quest_date=?")) {
            st.setString(1, uuid.toString());
            st.setDate(2, today);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(new Quest(
                            QuestType.valueOf(rs.getString("quest_id")),
                            rs.getInt("progress"),
                            rs.getBoolean("completed")));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("QuestManager.getDailyQuests: " + e.getMessage());
        }
        if (!result.isEmpty()) return result;
        return generateDailyQuests(uuid, today);
    }

    /** Called from listeners to add quest progress. */
    public void addProgress(Player player, QuestType type, int amount) {
        UUID  uuid  = player.getUniqueId();
        Date  today = Date.valueOf(LocalDate.now());
        List<Quest> quests = getDailyQuests(uuid);

        Quest quest = quests.stream().filter(q -> q.type() == type).findFirst().orElse(null);
        if (quest == null || quest.completed()) return;

        int  newProgress = Math.min(quest.progress() + amount, type.goal);
        boolean done     = newProgress >= type.goal;

        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE sv_quests SET progress=?, completed=? WHERE uuid=? AND quest_date=? AND quest_id=?")) {
            st.setInt(1, newProgress);
            st.setBoolean(2, done);
            st.setString(3, uuid.toString());
            st.setDate(4, today);
            st.setString(5, type.name());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("QuestManager.addProgress: " + e.getMessage());
            return;
        }

        if (done) {
            plugin.getEconomyManager().deposit(uuid, QUEST_REWARD);
            player.sendMessage(Component.text(
                "§6§l✦ Quest abgeschlossen: §f" + type.description + " §8(+" + QUEST_REWARD + " Coins)"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            plugin.getAchievementManager().unlock(player, AchievementManager.Achievement.QUEST_DONE);
            plugin.getAchievementManager().checkCoins(player);
        }
    }

    private List<Quest> generateDailyQuests(UUID uuid, Date today) {
        List<QuestType> all = new ArrayList<>(Arrays.asList(QuestType.values()));
        Collections.shuffle(all);
        List<QuestType> selected = all.subList(0, Math.min(3, all.size()));

        try (Connection c = con()) {
            // Remove stale quests for other dates
            try (PreparedStatement del = c.prepareStatement(
                     "DELETE FROM sv_quests WHERE uuid=? AND quest_date != ?")) {
                del.setString(1, uuid.toString());
                del.setDate(2, today);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                     "INSERT INTO sv_quests (uuid, quest_date, quest_id, progress, completed) VALUES (?,?,?,0,FALSE)")) {
                for (QuestType type : selected) {
                    ins.setString(1, uuid.toString());
                    ins.setDate(2, today);
                    ins.setString(3, type.name());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("QuestManager.generateDailyQuests: " + e.getMessage());
        }

        return selected.stream().map(t -> new Quest(t, 0, false)).toList();
    }
}
