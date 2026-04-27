package de.pinkhorizon.generators.database;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.data.StoredBooster;

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
                // New fields (default 0/false if column doesn't exist yet)
                try { data.setLastDaily(rs.getLong("last_daily")); } catch (SQLException ignored) {}
                try { data.setDailyStreak(rs.getInt("daily_streak")); } catch (SQLException ignored) {}
                try { data.setAutoUpgrade(rs.getInt("auto_upgrade") == 1); } catch (SQLException ignored) {}
                try { data.setUpgradeTokens(rs.getInt("upgrade_tokens")); } catch (SQLException ignored) {}
                try { data.setTalentPoints(rs.getInt("talent_points")); } catch (SQLException ignored) {}
                try { data.setMilestoneReached(rs.getInt("milestone_reached")); } catch (SQLException ignored) {}
                try { data.setBonusSlots(rs.getInt("bonus_slots")); } catch (SQLException ignored) {}
                try { data.setPrestigeTokens(rs.getInt("prestige_tokens")); } catch (SQLException ignored) {}
                try { data.setAutoTierUpgrade(rs.getInt("auto_tier_upgrade") == 1); } catch (SQLException ignored) {}
                try { data.setMiningLevel(rs.getInt("mining_level")); } catch (SQLException ignored) {}
                try { data.setMiningPickaxeLevel(rs.getInt("mining_pickaxe_level")); } catch (SQLException ignored) {}
                try { data.setShards(rs.getInt("shards")); } catch (SQLException ignored) {}
                try { data.setPetLevel(rs.getInt("pet_level")); } catch (SQLException ignored) {}
                try { data.setPetXp(rs.getLong("pet_xp")); } catch (SQLException ignored) {}
                try {
                    int mbx = rs.getInt("mining_block_x");
                    if (!rs.wasNull()) {
                        data.setMiningBlockLocation(mbx, rs.getInt("mining_block_y"), rs.getInt("mining_block_z"));
                    }
                } catch (SQLException ignored) {}
                try {
                    String sbStr = rs.getString("stored_boosters");
                    if (sbStr != null && !sbStr.isBlank()) {
                        for (String part : sbStr.split("\\|")) {
                            if (!part.isBlank()) data.addStoredBooster(StoredBooster.deserialize(part));
                        }
                    }
                } catch (SQLException ignored) {}
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
                    afk_boxes_opened, booster_expiry, booster_mult, last_seen, border_size,
                    last_daily, daily_streak, auto_upgrade, upgrade_tokens, talent_points,
                    milestone_reached, stored_boosters, bonus_slots, prestige_tokens, auto_tier_upgrade,
                    mining_level, mining_pickaxe_level, shards, mining_block_x, mining_block_y, mining_block_z,
                    pet_level, pet_xp)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    name=VALUES(name), money=VALUES(money), prestige=VALUES(prestige),
                    total_earned=VALUES(total_earned), total_upgrades=VALUES(total_upgrades),
                    afk_boxes_opened=VALUES(afk_boxes_opened), booster_expiry=VALUES(booster_expiry),
                    booster_mult=VALUES(booster_mult), last_seen=VALUES(last_seen),
                    border_size=VALUES(border_size), last_daily=VALUES(last_daily),
                    daily_streak=VALUES(daily_streak), auto_upgrade=VALUES(auto_upgrade),
                    upgrade_tokens=VALUES(upgrade_tokens), talent_points=VALUES(talent_points),
                    milestone_reached=VALUES(milestone_reached),
                    stored_boosters=VALUES(stored_boosters),
                    bonus_slots=VALUES(bonus_slots), prestige_tokens=VALUES(prestige_tokens),
                    auto_tier_upgrade=VALUES(auto_tier_upgrade),
                    mining_level=VALUES(mining_level),
                    mining_pickaxe_level=VALUES(mining_pickaxe_level),
                    shards=VALUES(shards),
                    mining_block_x=VALUES(mining_block_x),
                    mining_block_y=VALUES(mining_block_y),
                    mining_block_z=VALUES(mining_block_z),
                    pet_level=VALUES(pet_level),
                    pet_xp=VALUES(pet_xp)
            """;
        } else {
            sql = """
                INSERT OR REPLACE INTO gen_players (uuid, name, money, prestige, total_earned,
                    total_upgrades, afk_boxes_opened, booster_expiry, booster_mult, last_seen,
                    border_size, last_daily, daily_streak, auto_upgrade, upgrade_tokens,
                    talent_points, milestone_reached, stored_boosters, bonus_slots, prestige_tokens,
                    auto_tier_upgrade, mining_level, mining_pickaxe_level, shards,
                    mining_block_x, mining_block_y, mining_block_z, pet_level, pet_xp)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
            stmt.setLong(12, data.getLastDaily());
            stmt.setInt(13, data.getDailyStreak());
            stmt.setInt(14, data.isAutoUpgrade() ? 1 : 0);
            stmt.setInt(15, data.getUpgradeTokens());
            stmt.setInt(16, data.getTalentPoints());
            stmt.setInt(17, data.getMilestoneReached());
            stmt.setString(18, serializeStoredBoosters(data));
            stmt.setInt(19, data.getBonusSlots());
            stmt.setInt(20, data.getPrestigeTokens());
            stmt.setInt(21, data.isAutoTierUpgrade() ? 1 : 0);
            stmt.setInt(22, data.getMiningLevel());
            stmt.setInt(23, data.getMiningPickaxeLevel());
            stmt.setInt(24, data.getShards());
            if (data.hasMiningBlockCustomPos()) {
                stmt.setInt(25, data.getMiningBlockCustomX());
                stmt.setInt(26, data.getMiningBlockCustomY());
                stmt.setInt(27, data.getMiningBlockCustomZ());
            } else {
                stmt.setNull(25, java.sql.Types.INTEGER);
                stmt.setNull(26, java.sql.Types.INTEGER);
                stmt.setNull(27, java.sql.Types.INTEGER);
            }
            stmt.setInt(28, data.getPetLevel());
            stmt.setLong(29, data.getPetXp());
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

    public void saveMiningBlockLocation(UUID uuid, int x, int y, int z) {
        String sql = "UPDATE gen_players SET mining_block_x=?, mining_block_y=?, mining_block_z=? WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, x);
            stmt.setInt(2, y);
            stmt.setInt(3, z);
            stmt.setString(4, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] saveMiningBlockLocation: " + e.getMessage());
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
                try {
                    String enc = rs.getString("enchant");
                    if (enc != null && !enc.isBlank()) gen.setEnchant(enc);
                } catch (SQLException ignored) {}
                list.add(gen);
            }
        } catch (SQLException e) {
            log.warning("[GenRepo] loadGenerators: " + e.getMessage());
        }
        return list;
    }

    public void insertGenerator(PlacedGenerator gen) {
        String sql = """
            INSERT INTO gen_generators (uuid, world, x, y, z, tier, level, enchant)
            VALUES (?,?,?,?,?,?,?,?)
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
            stmt.setString(8, gen.getEnchant());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) gen.setDbId(keys.getInt(1));
        } catch (SQLException e) {
            log.warning("[GenRepo] insertGenerator: " + e.getMessage());
        }
    }

    public void updateGeneratorEnchant(PlacedGenerator gen) {
        if (gen.getDbId() < 0) return;
        String sql = "UPDATE gen_generators SET enchant=? WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, gen.getEnchant());
            stmt.setInt(2, gen.getDbId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] updateGeneratorEnchant: " + e.getMessage());
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

    // ── Talents ──────────────────────────────────────────────────────────────

    public java.util.Set<String> loadTalents(UUID uuid) {
        java.util.Set<String> set = new java.util.HashSet<>();
        String sql = "SELECT talent_id FROM gen_talents WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) set.add(rs.getString("talent_id"));
        } catch (SQLException e) {
            log.warning("[GenRepo] loadTalents: " + e.getMessage());
        }
        return set;
    }

    public void saveTalent(UUID uuid, String talentId) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = "INSERT IGNORE INTO gen_talents (uuid, talent_id) VALUES (?,?)";
        } else {
            sql = "INSERT OR IGNORE INTO gen_talents (uuid, talent_id) VALUES (?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, talentId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] saveTalent: " + e.getMessage());
        }
    }

    // ── Income Log ───────────────────────────────────────────────────────────

    public void addIncomeLog(UUID uuid, long hour, long earned) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = """
                INSERT INTO gen_income_log (uuid, hour, earned) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE earned = earned + VALUES(earned)
            """;
        } else {
            sql = """
                INSERT INTO gen_income_log (uuid, hour, earned) VALUES (?,?,?)
                ON CONFLICT(uuid, hour) DO UPDATE SET earned = earned + excluded.earned
            """;
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, hour);
            stmt.setLong(3, earned);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] addIncomeLog: " + e.getMessage());
        }
    }

    /** Letzte 24 Stunden Einkommen-Log */
    public List<long[]> getIncomeHistory(UUID uuid, int hours) {
        List<long[]> list = new ArrayList<>();
        long nowHour = System.currentTimeMillis() / 3_600_000L;
        String sql = "SELECT hour, earned FROM gen_income_log WHERE uuid=? AND hour >= ? ORDER BY hour ASC";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, nowHour - hours);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(new long[]{rs.getLong("hour"), rs.getLong("earned")});
        } catch (SQLException e) {
            log.warning("[GenRepo] getIncomeHistory: " + e.getMessage());
        }
        return list;
    }

    // ── Season Leaderboard ───────────────────────────────────────────────────

    public void saveSeasonEntry(int seasonNo, int rank, String name, long money, int prestige) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = """
                INSERT INTO gen_seasons (season_no, rank_pos, name, money, prestige, snapshot_at)
                VALUES (?,?,?,?,?,?)
            """;
        } else {
            sql = "INSERT INTO gen_seasons (season_no, rank_pos, name, money, prestige, snapshot_at) VALUES (?,?,?,?,?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, seasonNo);
            stmt.setInt(2, rank);
            stmt.setString(3, name);
            stmt.setLong(4, money);
            stmt.setInt(5, prestige);
            stmt.setLong(6, System.currentTimeMillis() / 1000);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] saveSeasonEntry: " + e.getMessage());
        }
    }

    public List<String[]> getSeasonLeaderboard(int seasonNo) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT rank_pos, name, money, prestige FROM gen_seasons WHERE season_no=? ORDER BY rank_pos ASC";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, seasonNo);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("rank_pos")),
                    rs.getString("name"),
                    String.valueOf(rs.getLong("money")),
                    String.valueOf(rs.getInt("prestige"))
                });
            }
        } catch (SQLException e) {
            log.warning("[GenRepo] getSeasonLeaderboard: " + e.getMessage());
        }
        return list;
    }

    public int getMaxSeasonNo() {
        String sql = "SELECT MAX(season_no) FROM gen_seasons";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Math.max(0, rs.getInt(1));
        } catch (SQLException e) {
            log.warning("[GenRepo] getMaxSeasonNo: " + e.getMessage());
        }
        return 0;
    }

    // ── Marketplace ──────────────────────────────────────────────────────────

    public int insertMarketListing(UUID seller, String itemType, long price) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = "INSERT INTO gen_market (seller_uuid, item_type, price, listed_at) VALUES (?,?,?,?)";
        } else {
            sql = "INSERT INTO gen_market (seller_uuid, item_type, price, listed_at) VALUES (?,?,?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, seller.toString());
            stmt.setString(2, itemType);
            stmt.setLong(3, price);
            stmt.setLong(4, System.currentTimeMillis() / 1000);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            log.warning("[GenRepo] insertMarketListing: " + e.getMessage());
        }
        return -1;
    }

    public void deleteMarketListing(int id) {
        String sql = "DELETE FROM gen_market WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] deleteMarketListing: " + e.getMessage());
        }
    }

    /** Gibt alle Marktplatz-Listings zurück: [id, seller_uuid, item_type, price, listed_at] */
    public List<Object[]> getMarketListings() {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT id, seller_uuid, item_type, price, listed_at FROM gen_market ORDER BY listed_at DESC LIMIT 100";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new Object[]{
                    rs.getInt("id"),
                    rs.getString("seller_uuid"),
                    rs.getString("item_type"),
                    rs.getLong("price"),
                    rs.getLong("listed_at")
                });
            }
        } catch (SQLException e) {
            log.warning("[GenRepo] getMarketListings: " + e.getMessage());
        }
        return list;
    }

    // ── Guild Upgrades ────────────────────────────────────────────────────────

    public int getGuildUpgradeLevel(int guildId, String upgradeId) {
        String sql = "SELECT level FROM gen_guild_upgrades WHERE guild_id=? AND upgrade_id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, guildId);
            stmt.setString(2, upgradeId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("level");
        } catch (SQLException e) {
            log.warning("[GenRepo] getGuildUpgradeLevel: " + e.getMessage());
        }
        return 0;
    }

    public void setGuildUpgradeLevel(int guildId, String upgradeId, int level) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = """
                INSERT INTO gen_guild_upgrades (guild_id, upgrade_id, level) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE level=VALUES(level)
            """;
        } else {
            sql = "INSERT OR REPLACE INTO gen_guild_upgrades (guild_id, upgrade_id, level) VALUES (?,?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, guildId);
            stmt.setString(2, upgradeId);
            stmt.setInt(3, level);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] setGuildUpgradeLevel: " + e.getMessage());
        }
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    public String getMeta(String key) {
        String sql = "SELECT val FROM gen_meta WHERE key_name=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("val");
        } catch (SQLException e) {
            log.warning("[GenRepo] getMeta: " + e.getMessage());
        }
        return null;
    }

    public void setMeta(String key, String value) {
        String sql;
        if (db.getDbType().equals("mysql")) {
            sql = "INSERT INTO gen_meta (key_name, val) VALUES (?,?) ON DUPLICATE KEY UPDATE val=VALUES(val)";
        } else {
            sql = "INSERT OR REPLACE INTO gen_meta (key_name, val) VALUES (?,?)";
        }
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[GenRepo] setMeta: " + e.getMessage());
        }
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

    // ── Stored Boosters ───────────────────────────────────────────────────────

    private String serializeStoredBoosters(PlayerData data) {
        List<StoredBooster> list = data.getStoredBoosters();
        if (list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (StoredBooster b : list) {
            if (sb.length() > 0) sb.append("|");
            sb.append(b.serialize());
        }
        return sb.toString();
    }
}
