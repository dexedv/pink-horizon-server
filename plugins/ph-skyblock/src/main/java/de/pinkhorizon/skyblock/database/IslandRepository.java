package de.pinkhorizon.skyblock.database;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class IslandRepository {

    private final PHSkyBlock plugin;
    private final SkyDatabase db;

    public IslandRepository(PHSkyBlock plugin, SkyDatabase db) {
        this.plugin = plugin;
        this.db = db;
        createTables();
    }

    private void createTables() {
        try (Connection con = db.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sb_islands (
                    id            INT AUTO_INCREMENT PRIMARY KEY,
                    uuid          VARCHAR(36)  NOT NULL UNIQUE,
                    owner_uuid    VARCHAR(36)  NOT NULL,
                    owner_name    VARCHAR(16)  NOT NULL,
                    world         VARCHAR(64)  NOT NULL DEFAULT 'skyblock_world',
                    center_x      INT          NOT NULL DEFAULT 0,
                    center_y      INT          NOT NULL DEFAULT 64,
                    center_z      INT          NOT NULL DEFAULT 0,
                    home_x        DOUBLE       NOT NULL DEFAULT 0,
                    home_y        DOUBLE       NOT NULL DEFAULT 65,
                    home_z        DOUBLE       NOT NULL DEFAULT 0,
                    home_yaw      FLOAT        NOT NULL DEFAULT 0,
                    home_pitch    FLOAT        NOT NULL DEFAULT 0,
                    level         BIGINT       NOT NULL DEFAULT 1,
                    score         BIGINT       NOT NULL DEFAULT 0,
                    size          INT          NOT NULL DEFAULT 80,
                    max_members   INT          NOT NULL DEFAULT 4,
                    is_open       TINYINT      NOT NULL DEFAULT 0,
                    warp_enabled  TINYINT      NOT NULL DEFAULT 0,
                    warp_name     VARCHAR(32)  DEFAULT NULL,
                    created_at    BIGINT       NOT NULL DEFAULT 0,
                    last_active   BIGINT       NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sb_island_members (
                    island_id   INT          NOT NULL,
                    uuid        VARCHAR(36)  NOT NULL,
                    name        VARCHAR(16)  NOT NULL,
                    role        VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
                    joined_at   BIGINT       NOT NULL DEFAULT 0,
                    PRIMARY KEY (island_id, uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sb_island_bans (
                    island_id   INT          NOT NULL,
                    uuid        VARCHAR(36)  NOT NULL,
                    name        VARCHAR(16)  NOT NULL,
                    banned_at   BIGINT       NOT NULL DEFAULT 0,
                    PRIMARY KEY (island_id, uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sb_players (
                    uuid            VARCHAR(36)  PRIMARY KEY,
                    name            VARCHAR(16)  NOT NULL,
                    island_id       INT          DEFAULT NULL,
                    island_chat     TINYINT      NOT NULL DEFAULT 0,
                    last_seen       BIGINT       NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sb_island_counter (
                    id INT PRIMARY KEY DEFAULT 1,
                    next_index INT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
            st.executeUpdate("INSERT IGNORE INTO sb_island_counter (id, next_index) VALUES (1, 0)");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der SkyBlock-Tabellen", e);
        }
    }

    public int getAndIncrementIslandIndex() {
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement get = con.prepareStatement(
                    "SELECT next_index FROM sb_island_counter WHERE id=1 FOR UPDATE");
                 PreparedStatement upd = con.prepareStatement(
                    "UPDATE sb_island_counter SET next_index=next_index+1 WHERE id=1")) {
                ResultSet rs = get.executeQuery();
                rs.next();
                int idx = rs.getInt(1);
                upd.executeUpdate();
                con.commit();
                return idx;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Lesen des Island-Index", e);
            return 0;
        }
    }

    // ── Island CRUD ───────────────────────────────────────────────────────────

    public void insertIsland(Island island) {
        String sql = """
            INSERT INTO sb_islands (uuid,owner_uuid,owner_name,world,
                center_x,center_y,center_z,home_x,home_y,home_z,home_yaw,home_pitch,
                level,score,size,max_members,is_open,warp_enabled,warp_name,created_at,last_active)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, island.getIslandUuid().toString());
            ps.setString(2, island.getOwnerUuid().toString());
            ps.setString(3, island.getOwnerName());
            ps.setString(4, island.getWorld());
            ps.setInt(5, island.getCenterX());
            ps.setInt(6, island.getCenterY());
            ps.setInt(7, island.getCenterZ());
            ps.setDouble(8, island.getHomeX());
            ps.setDouble(9, island.getHomeY());
            ps.setDouble(10, island.getHomeZ());
            ps.setFloat(11, island.getHomeYaw());
            ps.setFloat(12, island.getHomePitch());
            ps.setLong(13, island.getLevel());
            ps.setLong(14, island.getScore());
            ps.setInt(15, island.getSize());
            ps.setInt(16, island.getMaxMembers());
            ps.setInt(17, island.isOpen() ? 1 : 0);
            ps.setInt(18, island.isWarpEnabled() ? 1 : 0);
            ps.setString(19, island.getWarpName());
            ps.setLong(20, island.getCreatedAt());
            ps.setLong(21, island.getLastActive());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Einfügen der Insel", e);
        }
    }

    public void updateIsland(Island island) {
        String sql = """
            UPDATE sb_islands SET owner_uuid=?,owner_name=?,home_x=?,home_y=?,home_z=?,
                home_yaw=?,home_pitch=?,level=?,score=?,size=?,max_members=?,is_open=?,
                warp_enabled=?,warp_name=?,last_active=? WHERE id=?
        """;
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, island.getOwnerUuid().toString());
            ps.setString(2, island.getOwnerName());
            ps.setDouble(3, island.getHomeX());
            ps.setDouble(4, island.getHomeY());
            ps.setDouble(5, island.getHomeZ());
            ps.setFloat(6, island.getHomeYaw());
            ps.setFloat(7, island.getHomePitch());
            ps.setLong(8, island.getLevel());
            ps.setLong(9, island.getScore());
            ps.setInt(10, island.getSize());
            ps.setInt(11, island.getMaxMembers());
            ps.setInt(12, island.isOpen() ? 1 : 0);
            ps.setInt(13, island.isWarpEnabled() ? 1 : 0);
            ps.setString(14, island.getWarpName());
            ps.setLong(15, island.getLastActive());
            ps.setInt(16, island.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren der Insel", e);
        }
    }

    public void deleteIsland(int islandId) {
        try (Connection con = db.getConnection()) {
            for (String table : new String[]{"sb_island_members","sb_island_bans"}) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE island_id=?")) {
                    ps.setInt(1, islandId);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM sb_islands WHERE id=?")) {
                ps.setInt(1, islandId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Löschen der Insel", e);
        }
    }

    public Optional<Island> loadIslandByOwner(UUID ownerUuid) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM sb_islands WHERE owner_uuid=?")) {
            ps.setString(1, ownerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Island island = rowToIsland(rs);
                loadMembersInto(con, island);
                return Optional.of(island);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Insel", e);
        }
        return Optional.empty();
    }

    public Optional<Island> loadIslandById(int id) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM sb_islands WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Island island = rowToIsland(rs);
                loadMembersInto(con, island);
                return Optional.of(island);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Insel", e);
        }
        return Optional.empty();
    }

    private Island rowToIsland(ResultSet rs) throws SQLException {
        return new Island(
            rs.getInt("id"),
            UUID.fromString(rs.getString("uuid")),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getString("owner_name"),
            rs.getString("world"),
            rs.getInt("center_x"), rs.getInt("center_y"), rs.getInt("center_z"),
            rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"),
            rs.getFloat("home_yaw"), rs.getFloat("home_pitch"),
            rs.getLong("level"), rs.getLong("score"),
            rs.getInt("size"), rs.getInt("max_members"),
            rs.getInt("is_open") == 1,
            rs.getInt("warp_enabled") == 1,
            rs.getString("warp_name"),
            rs.getLong("created_at"), rs.getLong("last_active")
        );
    }

    private void loadMembersInto(Connection con, Island island) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM sb_island_members WHERE island_id=?")) {
            ps.setInt(1, island.getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Island.MemberRole role;
                try { role = Island.MemberRole.valueOf(rs.getString("role")); }
                catch (IllegalArgumentException e) { role = Island.MemberRole.MEMBER; }
                island.getMembers().add(new Island.IslandMember(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name"),
                    role,
                    rs.getLong("joined_at")
                ));
            }
        }
    }

    public void insertMember(int islandId, UUID uuid, String name, Island.MemberRole role) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO sb_island_members (island_id,uuid,name,role,joined_at) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, islandId);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setString(4, role.name());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Einfügen des Mitglieds", e);
        }
    }

    public void removeMember(int islandId, UUID uuid) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "DELETE FROM sb_island_members WHERE island_id=? AND uuid=?")) {
            ps.setInt(1, islandId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Entfernen des Mitglieds", e);
        }
    }

    public void insertBan(int islandId, UUID uuid, String name) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO sb_island_bans (island_id,uuid,name,banned_at) VALUES (?,?,?,?)")) {
            ps.setInt(1, islandId);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Einfügen des Bans", e);
        }
    }

    public void removeBan(int islandId, UUID uuid) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "DELETE FROM sb_island_bans WHERE island_id=? AND uuid=?")) {
            ps.setInt(1, islandId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Entfernen des Bans", e);
        }
    }

    public boolean isBanned(int islandId, UUID uuid) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM sb_island_bans WHERE island_id=? AND uuid=?")) {
            ps.setInt(1, islandId);
            ps.setString(2, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Player CRUD ───────────────────────────────────────────────────────────

    public SkyPlayer loadOrCreatePlayer(UUID uuid, String name) {
        try (Connection con = db.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM sb_players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    SkyPlayer sp = new SkyPlayer(
                        uuid, rs.getString("name"),
                        rs.getObject("island_id", Integer.class),
                        rs.getInt("island_chat") == 1,
                        rs.getLong("last_seen")
                    );
                    // update name if changed
                    if (!name.equals(sp.getName())) {
                        sp.setName(name);
                        updatePlayerName(con, uuid, name);
                    }
                    return sp;
                }
            }
            // insert new
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sb_players (uuid,name,island_id,island_chat,last_seen) VALUES (?,?,NULL,0,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden/Erstellen des Spielers", e);
        }
        return new SkyPlayer(uuid, name, null, false, System.currentTimeMillis());
    }

    private void updatePlayerName(Connection con, UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE sb_players SET name=? WHERE uuid=?")) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void savePlayer(SkyPlayer sp) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "UPDATE sb_players SET name=?,island_id=?,island_chat=?,last_seen=? WHERE uuid=?")) {
            ps.setString(1, sp.getName());
            if (sp.getIslandId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, sp.getIslandId());
            ps.setInt(3, sp.isIslandChat() ? 1 : 0);
            ps.setLong(4, sp.getLastSeen());
            ps.setString(5, sp.getUuid().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern des Spielers", e);
        }
    }

    public void setPlayerIslandId(UUID uuid, Integer islandId) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "UPDATE sb_players SET island_id=? WHERE uuid=?")) {
            if (islandId == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, islandId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Setzen der Insel-ID", e);
        }
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    public List<Island> loadTopIslands(int limit) {
        List<Island> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM sb_islands ORDER BY score DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rowToIsland(rs));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden des Leaderboards", e);
        }
        return result;
    }
}
