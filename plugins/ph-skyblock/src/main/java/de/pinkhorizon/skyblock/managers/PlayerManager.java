package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import de.pinkhorizon.skyblock.database.SkyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verwaltet Spieler-Basisdaten (Name, letzte Anmeldung).
 * Island-Daten werden vollständig von BentoBox verwaltet.
 */
public class PlayerManager {

    private final PHSkyBlock plugin;
    private final SkyDatabase db;
    private final Map<UUID, SkyPlayer> cache = new ConcurrentHashMap<>();

    public PlayerManager(PHSkyBlock plugin, SkyDatabase db) {
        this.plugin = plugin;
        this.db = db;
        initTable();
    }

    private void initTable() {
        try (Connection con = db.getConnection(); var st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_players (
                    uuid       VARCHAR(36) PRIMARY KEY,
                    name       VARCHAR(16) NOT NULL,
                    last_seen  BIGINT      DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "sky_players Tabelle konnte nicht erstellt werden", e);
        }
    }

    public SkyPlayer loadPlayer(UUID uuid, String name) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT name, last_seen FROM sky_players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            SkyPlayer sp;
            if (rs.next()) {
                sp = new SkyPlayer(uuid, rs.getString("name"), rs.getLong("last_seen"));
            } else {
                sp = new SkyPlayer(uuid, name, System.currentTimeMillis());
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO sky_players (uuid, name, last_seen) VALUES (?,?,?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, name);
                    ins.setLong(3, sp.getLastSeen());
                    ins.executeUpdate();
                }
            }
            sp.setName(name);
            cache.put(uuid, sp);
            return sp;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden von " + uuid, e);
            SkyPlayer sp = new SkyPlayer(uuid, name, System.currentTimeMillis());
            cache.put(uuid, sp);
            return sp;
        }
    }

    public SkyPlayer getPlayer(UUID uuid) {
        return cache.get(uuid);
    }

    public void saveAndUnload(UUID uuid) {
        SkyPlayer sp = cache.remove(uuid);
        if (sp == null) return;
        sp.setLastSeen(System.currentTimeMillis());
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO sky_players (uuid, name, last_seen) VALUES (?,?,?) " +
                 "ON DUPLICATE KEY UPDATE name=VALUES(name), last_seen=VALUES(last_seen)")) {
            ps.setString(1, sp.getUuid().toString());
            ps.setString(2, sp.getName());
            ps.setLong(3, sp.getLastSeen());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern von " + uuid, e);
        }
    }
}
