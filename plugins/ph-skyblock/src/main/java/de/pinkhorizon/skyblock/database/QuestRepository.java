package de.pinkhorizon.skyblock.database;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.PlayerQuest;
import de.pinkhorizon.skyblock.enums.QuestType;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestRepository {

    private final PHSkyBlock plugin;
    private final SkyDatabase db;

    public QuestRepository(PHSkyBlock plugin, SkyDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Lädt alle Quests des Spielers für den heutigen Tag. */
    public List<PlayerQuest> loadTodayQuests(UUID uuid) {
        List<PlayerQuest> list = new ArrayList<>();
        String sql = "SELECT * FROM sky_quests WHERE uuid=? AND quest_date=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDate(2, Date.valueOf(LocalDate.now()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QuestType type = QuestType.byId(rs.getString("quest_type"));
                    if (type == null) continue;
                    list.add(new PlayerQuest(
                        rs.getString("quest_id"),
                        type,
                        rs.getInt("difficulty"),
                        rs.getLong("progress"),
                        rs.getBoolean("completed"),
                        rs.getBoolean("reward_claimed"),
                        rs.getDate("quest_date").toLocalDate()
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadTodayQuests fehler: " + e.getMessage());
        }
        return list;
    }

    /** Speichert eine neue Quest (INSERT IGNORE → kein Duplikat). */
    public void insertQuest(UUID uuid, PlayerQuest q) {
        String sql = """
            INSERT IGNORE INTO sky_quests
              (uuid, quest_id, quest_type, difficulty, progress, completed, reward_claimed, quest_date)
              VALUES (?,?,?,?,?,?,?,?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, q.getQuestId());
            ps.setString(3, q.getType().getId());
            ps.setInt(4, q.getDifficulty());
            ps.setLong(5, q.getProgress());
            ps.setBoolean(6, q.isCompleted());
            ps.setBoolean(7, q.isRewardClaimed());
            ps.setDate(8, Date.valueOf(q.getQuestDate()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("insertQuest fehler: " + e.getMessage());
        }
    }

    /** Aktualisiert Fortschritt, abgeschlossen-Status und Belohnungs-Flag. */
    public void saveQuest(UUID uuid, PlayerQuest q) {
        String sql = """
            UPDATE sky_quests SET progress=?, completed=?, reward_claimed=?
            WHERE uuid=? AND quest_id=? AND quest_date=?
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, q.getProgress());
            ps.setBoolean(2, q.isCompleted());
            ps.setBoolean(3, q.isRewardClaimed());
            ps.setString(4, uuid.toString());
            ps.setString(5, q.getQuestId());
            ps.setDate(6, Date.valueOf(q.getQuestDate()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("saveQuest fehler: " + e.getMessage());
        }
    }
}
