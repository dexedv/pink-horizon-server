package de.pinkhorizon.generators.database;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Alle SQL-Operationen für PH-Generators.
 */
public class GeneratorRepository {

    private final GenDatabaseManager db;
    private final Logger log;

    public GeneratorRepository(GenDatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    // ── Spieler ──────────────────────────────────────────────────────────────

    public PlayerData loadPlayer(UUID uuid) {
        String sql = "SELECT * FROM gen_players WHERE uuid = ?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                PlayerData data = new PlayerData(
                        uuid,
                        rs.getString("name"),
                        rs.getLong("money"),
                        rs.getInt("prestige"),
                        rs.getLong("last_seen")
                );
                data.setTotalEarned(rs.getLong("total_earned"));
                data.setTotalUpgrades(rs.getInt("total_upgrades"));
                data.setAfkBoxesOpened(rs.getInt("afk_boxes_opened"));
                data.setBoosterExpiry(rs.getLong("booster_expiry"));
                data.setBoosterMultiplier(rs.getDouble("booster_mult"));
                int bs = rs.getInt("border_size");
                data.setBorderSize(rs.wasNull() ? 40 : bs);
                String hw = rs.getString("holo_world");
                if (hw != null && !hw.isEmpty()) {
                    data.setHoloLocation(hw, rs.getInt("holo_x"), rs.getInt("holo_y"), rs.getInt("holo_z"));
                }
                data.setTutorialDone(rs.getInt("tutorial_done") == 1);
                String lbhw = rs.getString("lb_holo_world");
                if (lbhw != null && !lbhw.isEmpty()) {
                    data.setLbHoloLocation(lbhw, rs.getInt("lb_holo_x"), rs.getInt("lb_holo_y"), rs.getInt("lb_holo_z"));
                }
                return data;
            }
        } catch (SQLException e) {
            log.warning("[GenRepo] loadPlayer: " + e.getMessage());
        }
        return null;
    }

    public void savePlayer(PlayerData data) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = """
                INSERT INTO gen_players (uuid, name, money, prestige, total_earned, total_upgrades,
                    afk_boxes_opened, booster_expiry, booster_mult, last_seen, border_size)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    name=VALUES(name), money=VALUES(money), prestige=VALUES(prestige),
                    total_earned=VALUES(total_earned), total_upgrades=VALUES(total_upgrades),
                    afk_boxes_opened=VALUES(afk_boxes_opened), booster_expiry=VALUES(booster_expiry),
                    booster_mult=VALUES(booster_mult), last_seen=VALUES(last_seen),
                    border_size=VALUES(border_size)
            """;
        } else {
            sql = """
                INSERT OR REPLACE INTO gen_players (uuid, name, money, prestige, total_earned,
                    total_upgrades, afk_boxes_opened, booster_expiry, booster_mult, last_seen,
                    border_size)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, data.getUuid().toString());
            stmt.setString(2, data.getName());
            stmt.setLong(3, data.getMoney());
            stmt.setInt(4, data.getPrestige());
            stmt.setLong(5, data.getTotalEarned());
            stmt.setInt(6, data.getTotalUpgrades());
            stmt.setInt(7, data.getAfkBoxesOpened());
            stmt.setLong(8, data.getBoosterExpiry());
            stmt.setDouble(9, data.getBoosterMultiplier());
            stmt.setLong(10, data.getLastSeen());
            stmt.setInt(11, data.getBorderSize());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] savePlayer: " + e.getMessage());
        }
    }

    public void saveHoloLocation(UUID uuid, String world, int x, int y, int z) {
        String sql = "UPDATE gen_players SET holo_world=?, holo_x=?, holo_y=?, holo_z=? WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.setString(5, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] saveHoloLocation: " + e.getMessage());
        }
    }

    public void saveLbHoloLocation(UUID uuid, String world, int x, int y, int z) {
        String sql = "UPDATE gen_players SET lb_holo_world=?, lb_holo_x=?, lb_holo_y=?, lb_holo_z=? WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.setString(5, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] saveLbHoloLocation: " + e.getMessage());
        }
    }

    public void clearLbHoloLocation(UUID uuid) {
        String sql = "UPDATE gen_players SET lb_holo_world=NULL WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] clearLbHoloLocation: " + e.getMessage());
        }
    }

    public void saveTutorialDone(UUID uuid) {
        String sql = "UPDATE gen_players SET tutorial_done=1 WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] saveTutorialDone: " + e.getMessage());
        }
    }

    public void clearHoloLocation(UUID uuid) {
        String sql = "UPDATE gen_players SET holo_world=NULL WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] clearHoloLocation: " + e.getMessage());
        }
    }

    /** Top-N Spieler nach money */
    public List<PlayerData> getTopPlayers(int limit) {
        List<PlayerData> list = new ArrayList<>();
        String sql = "SELECT * FROM gen_players ORDER BY money DESC LIMIT ?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                PlayerData d = new PlayerData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getLong("money"),
                        rs.getInt("prestige"),
                        rs.getLong("last_seen")
                );
                list.add(d);
            }
        } catch (SQLException e) {
            log.warning("[GenRepo] getTopPlayers: " + e.getMessage());
        }
        return list;
    }

    // ── Generatoren ──────────────────────────────────────────────────────────

    public List<PlacedGenerator> loadGenerators(UUID uuid) {
        List<PlacedGenerator> list = new ArrayList<>();
        String sql = "SELECT * FROM gen_generators WHERE uuid = ?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                GeneratorType type;
                try {
                    type = GeneratorType.valueOf(rs.getString("tier"));
                } catch (IllegalArgumentException e) {
                    continue; // unbekannter Typ (z.B. alter Seasonal)
                }
                PlacedGenerator gen = new PlacedGenerator(
                        uuid,
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        type,
                        rs.getInt("level")
                );
                gen.setDbId(rs.getInt("id"));
                list.add(gen);
            }
        } catch (SQLException e) {
            log.warning("[GenRepo] loadGenerators: " + e.getMessage());
        }
        return list;
    }

    public void insertGenerator(PlacedGenerator gen) {
        String sql = """
            INSERT INTO gen_generators (uuid, world, x, y, z, tier, level)
            VALUES (?,?,?,?,?,?,?)
        """;
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, gen.getOwnerUUID().toString());
            stmt.setString(2, gen.getWorld());
            stmt.setInt(3, gen.getX());
            stmt.setInt(4, gen.getY());
            stmt.setInt(5, gen.getZ());
            stmt.setString(6, gen.getType().name());
            stmt.setInt(7, gen.getLevel());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) gen.setDbId(keys.getInt(1));
        } catch (SQLException e) {
            log.warning("[GenRepo] insertGenerator: " + e.getMessage());
        }
    }

    public void updateGeneratorLevel(PlacedGenerator gen) {
        if (gen.getDbId() < 0) return;
        String sql = "UPDATE gen_generators SET level=?, tier=? WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, gen.getLevel());
            stmt.setString(2, gen.getType().name());
            stmt.setInt(3, gen.getDbId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] updateGeneratorLevel: " + e.getMessage());
        }
    }

    public void deleteGenerator(PlacedGenerator gen) {
        if (gen.getDbId() < 0) return;
        String sql = "DELETE FROM gen_generators WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, gen.getDbId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] deleteGenerator: " + e.getMessage());
        }
    }

    public void deleteAllGenerators(UUID uuid) {
        String sql = "DELETE FROM gen_generators WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] deleteAllGenerators: " + e.getMessage());
        }
    }

    // ── Gilden ───────────────────────────────────────────────────────────────

    public int createGuild(String name, UUID leader) {
        String sql = "INSERT INTO gen_guilds (name, leader_uuid, created_at) VALUES (?,?,?)";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, leader.toString());
            stmt.setLong(3, System.currentTimeMillis() / 1000);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            log.warning("[GenRepo] createGuild: " + e.getMessage());
        }
        return -1;
    }

    public void addGuildMember(int guildId, UUID uuid) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = "INSERT IGNORE INTO gen_guild_members (guild_id, uuid) VALUES (?,?)";
        } else {
            sql = "INSERT OR IGNORE INTO gen_guild_members (guild_id, uuid) VALUES (?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, guildId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] addGuildMember: " + e.getMessage());
        }
    }

    public void removeGuildMember(UUID uuid) {
        String sql = "DELETE FROM gen_guild_members WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] removeGuildMember: " + e.getMessage());
        }
    }

    public void deleteGuild(int guildId) {
        try (Connection con = db.getConnection()) {
            con.prepareStatement("DELETE FROM gen_guild_members WHERE guild_id=" + guildId).execute();
            con.prepareStatement("DELETE FROM gen_guilds WHERE id=" + guildId).execute();
        } catch (SQLException e) {
            log.warning("[GenRepo] deleteGuild: " + e.getMessage());
        }
    }

    /** Gibt guild_id für den Spieler zurück, -1 wenn kein Mitglied */
    public int getGuildId(UUID uuid) {
        String sql = "SELECT guild_id FROM gen_guild_members WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("guild_id");
        } catch (SQLException e) {
            log.warning("[GenRepo] getGuildId: " + e.getMessage());
        }
        return -1;
    }

    public List<UUID> getGuildMembers(int guildId) {
        List<UUID> list = new ArrayList<>();
        String sql = "SELECT uuid FROM gen_guild_members WHERE guild_id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, guildId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(UUID.fromString(rs.getString("uuid")));
        } catch (SQLException e) {
            log.warning("[GenRepo] getGuildMembers: " + e.getMessage());
        }
        return list;
    }

    // ── Achievement / Quest ──────────────────────────────────────────────────

    public long getAchievementProgress(UUID uuid, String id) {
        String sql = "SELECT progress FROM gen_achievements WHERE uuid=? AND achievement_id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("progress");
        } catch (SQLException e) {
            log.warning("[GenRepo] getAchievementProgress: " + e.getMessage());
        }
        return 0;
    }

    public void upsertAchievement(UUID uuid, String id, long progress, boolean completed) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = """
                INSERT INTO gen_achievements (uuid, achievement_id, progress, completed)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE progress=VALUES(progress), completed=VALUES(completed)
            """;
        } else {
            sql = """
                INSERT OR REPLACE INTO gen_achievements (uuid, achievement_id, progress, completed)
                VALUES (?,?,?,?)
            """;
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, id);
            stmt.setLong(3, progress);
            stmt.setInt(4, completed ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] upsertAchievement: " + e.getMessage());
        }
    }

    public ResultSet getAllAchievements(UUID uuid) throws SQLException {
        Connection con = db.getConnection();
        PreparedStatement stmt = con.prepareStatement(
                "SELECT * FROM gen_achievements WHERE uuid=?");
        stmt.setString(1, uuid.toString());
        return stmt.executeQuery();
    }

    // ── AFK ──────────────────────────────────────────────────────────────────

    public long getLastBoxTime(UUID uuid) {
        String sql = "SELECT last_box_time FROM gen_afk WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("last_box_time");
        } catch (SQLException e) {
            log.warning("[GenRepo] getLastBoxTime: " + e.getMessage());
        }
        return 0;
    }

    public void setAfkData(UUID uuid, long afkSince, long lastBoxTime) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = """
                INSERT INTO gen_afk (uuid, afk_since, last_box_time)
                VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE afk_since=VALUES(afk_since), last_box_time=VALUES(last_box_time)
            """;
        } else {
            sql = "INSERT OR REPLACE INTO gen_afk (uuid, afk_since, last_box_time) VALUES (?,?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, afkSince);
            stmt.setLong(3, lastBoxTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] setAfkData: " + e.getMessage());
        }
    }
}
