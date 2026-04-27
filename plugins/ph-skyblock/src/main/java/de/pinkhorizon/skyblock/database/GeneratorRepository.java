package de.pinkhorizon.skyblock.database;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;

import java.sql.*;
import java.util.*;

public class GeneratorRepository {

    private final PHSkyBlock plugin;
    private final SkyDatabase db;

    public GeneratorRepository(PHSkyBlock plugin, SkyDatabase db) {
        this.plugin = plugin;
        this.db = db;
        createTable();
    }

    private void createTable() {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_generators (
                    id            INT AUTO_INCREMENT PRIMARY KEY,
                    uuid          VARCHAR(36) NOT NULL,
                    world         VARCHAR(64) NOT NULL,
                    x INT, y INT, z INT,
                    level         INT         DEFAULT 1,
                    auto_sell     TINYINT     DEFAULT 0,
                    total_produced BIGINT     DEFAULT 0,
                    UNIQUE KEY pos (world, x, y, z),
                    INDEX idx_uuid (uuid)
                )
            """);
            // Erweiterter Spieler-Datensatz
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_player_ext (
                    uuid               VARCHAR(36) PRIMARY KEY,
                    coins              BIGINT      DEFAULT 0,
                    active_title       VARCHAR(64) DEFAULT 'none',
                    total_mined        BIGINT      DEFAULT 0,
                    daily_login_streak INT         DEFAULT 0,
                    last_login_date    DATE        DEFAULT NULL,
                    total_quests_done  INT         DEFAULT 0
                )
            """);
            // Achievements
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_achievements (
                    uuid            VARCHAR(36) NOT NULL,
                    achievement_id  VARCHAR(64) NOT NULL,
                    unlocked_at     BIGINT      NOT NULL,
                    PRIMARY KEY (uuid, achievement_id)
                )
            """);
            // Tägliche Quests
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_quests (
                    uuid           VARCHAR(36) NOT NULL,
                    quest_id       VARCHAR(64) NOT NULL,
                    quest_type     VARCHAR(64) NOT NULL,
                    difficulty     INT         NOT NULL,
                    progress       BIGINT      DEFAULT 0,
                    completed      TINYINT     DEFAULT 0,
                    reward_claimed TINYINT     DEFAULT 0,
                    quest_date     DATE        NOT NULL,
                    PRIMARY KEY (uuid, quest_id, quest_date)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Generator-Tabellen konnten nicht erstellt werden: " + e.getMessage());
        }
    }

    // ── Generator CRUD ────────────────────────────────────────────────────────

    public Generator insert(UUID uuid, String world, int x, int y, int z) {
        String sql = "INSERT INTO sky_generators (uuid, world, x, y, z) VALUES (?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            ps.setInt(3, x); ps.setInt(4, y); ps.setInt(5, z);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return new Generator(rs.getInt(1), uuid, world, x, y, z, 1, false, 0);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Generator insert fehler: " + e.getMessage());
        }
        return null;
    }

    public void update(Generator gen) {
        String sql = """
            UPDATE sky_generators SET level=?, auto_sell=?, total_produced=?
            WHERE world=? AND x=? AND y=? AND z=?
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, gen.getLevel());
            ps.setBoolean(2, gen.isAutoSell());
            ps.setLong(3, gen.getTotalProduced());
            ps.setString(4, gen.getWorld());
            ps.setInt(5, gen.getX()); ps.setInt(6, gen.getY()); ps.setInt(7, gen.getZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Generator update fehler: " + e.getMessage());
        }
    }

    public void delete(String world, int x, int y, int z) {
        String sql = "DELETE FROM sky_generators WHERE world=? AND x=? AND y=? AND z=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Generator delete fehler: " + e.getMessage());
        }
    }

    public List<Generator> loadByUuid(UUID uuid) {
        List<Generator> list = new ArrayList<>();
        String sql = "SELECT * FROM sky_generators WHERE uuid=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Generator load fehler: " + e.getMessage());
        }
        return list;
    }

    public Generator loadByPos(String world, int x, int y, int z) {
        String sql = "SELECT * FROM sky_generators WHERE world=? AND x=? AND y=? AND z=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Generator loadByPos fehler: " + e.getMessage());
        }
        return null;
    }

    private Generator map(ResultSet rs) throws SQLException {
        return new Generator(
            rs.getInt("id"),
            UUID.fromString(rs.getString("uuid")),
            rs.getString("world"),
            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
            rs.getInt("level"),
            rs.getBoolean("auto_sell"),
            rs.getLong("total_produced")
        );
    }

    // ── Player Extension ──────────────────────────────────────────────────────

    public void ensurePlayerExt(UUID uuid) {
        String sql = "INSERT IGNORE INTO sky_player_ext (uuid) VALUES (?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerExt ensure fehler: " + e.getMessage());
        }
    }

    public long getCoins(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT coins FROM sky_player_ext WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) { return 0; }
    }

    public void addCoins(UUID uuid, long amount) {
        String sql = "INSERT INTO sky_player_ext (uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = coins + ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, amount); ps.setLong(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("addCoins fehler: " + e.getMessage());
        }
    }

    public boolean deductCoins(UUID uuid, long amount) {
        String sql = "UPDATE sky_player_ext SET coins = coins - ? WHERE uuid=? AND coins >= ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, amount); ps.setString(2, uuid.toString()); ps.setLong(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public String getActiveTitle(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT active_title FROM sky_player_ext WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "none";
            }
        } catch (SQLException e) { return "none"; }
    }

    public void setActiveTitle(UUID uuid, String titleId) {
        String sql = "INSERT INTO sky_player_ext (uuid, active_title) VALUES (?, ?) ON DUPLICATE KEY UPDATE active_title=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString()); ps.setString(2, titleId); ps.setString(3, titleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("setActiveTitle fehler: " + e.getMessage());
        }
    }

    public long getTotalMined(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT total_mined FROM sky_player_ext WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) { return 0; }
    }

    public void addMined(UUID uuid, long amount) {
        String sql = "INSERT INTO sky_player_ext (uuid, total_mined) VALUES (?, ?) ON DUPLICATE KEY UPDATE total_mined = total_mined + ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString()); ps.setLong(2, amount); ps.setLong(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("addMined fehler: " + e.getMessage());
        }
    }

    public void incrementQuestsDone(UUID uuid) {
        String sql = "INSERT INTO sky_player_ext (uuid, total_quests_done) VALUES (?, 1) ON DUPLICATE KEY UPDATE total_quests_done = total_quests_done + 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("incrementQuestsDone fehler: " + e.getMessage());
        }
    }

    public int getTotalQuestsDone(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT total_quests_done FROM sky_player_ext WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) { return 0; }
    }

    // ── Achievements ──────────────────────────────────────────────────────────

    public Set<String> loadAchievements(UUID uuid) {
        Set<String> set = new HashSet<>();
        String sql = "SELECT achievement_id FROM sky_achievements WHERE uuid=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) set.add(rs.getString(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadAchievements fehler: " + e.getMessage());
        }
        return set;
    }

    public void unlockAchievement(UUID uuid, String achievementId) {
        String sql = "INSERT IGNORE INTO sky_achievements (uuid, achievement_id, unlocked_at) VALUES (?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, achievementId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("unlockAchievement fehler: " + e.getMessage());
        }
    }
}
