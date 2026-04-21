package de.pinkhorizon.minigames.managers;

import de.pinkhorizon.minigames.PHMinigames;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class BedWarsStatsManager {

    public record TopEntry(String name, int wins, int kills, int beds, int games) {}

    private final PHMinigames plugin;

    public BedWarsStatsManager(PHMinigames plugin) {
        this.plugin = plugin;
    }

    public void incrementStat(UUID uuid, String column) {
        String sql = switch (column) {
            case "wins"         -> "INSERT INTO mg_bedwars_stats (uuid, wins) VALUES (?,1) ON DUPLICATE KEY UPDATE wins=wins+1, games_played=games_played+1";
            case "losses"       -> "INSERT INTO mg_bedwars_stats (uuid, losses) VALUES (?,1) ON DUPLICATE KEY UPDATE losses=losses+1, games_played=games_played+1";
            case "kills"        -> "INSERT INTO mg_bedwars_stats (uuid, kills) VALUES (?,1) ON DUPLICATE KEY UPDATE kills=kills+1";
            case "deaths"       -> "INSERT INTO mg_bedwars_stats (uuid, deaths) VALUES (?,1) ON DUPLICATE KEY UPDATE deaths=deaths+1";
            case "beds_broken"  -> "INSERT INTO mg_bedwars_stats (uuid, beds_broken) VALUES (?,1) ON DUPLICATE KEY UPDATE beds_broken=beds_broken+1";
            case "finals"       -> "INSERT INTO mg_bedwars_stats (uuid, finals) VALUES (?,1) ON DUPLICATE KEY UPDATE finals=finals+1";
            default -> null;
        };
        if (sql == null) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getDb().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Stat-Update fehlgeschlagen: " + column, e);
            }
        });
    }

    public int getStat(UUID uuid, String column) {
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT " + column + " FROM mg_bedwars_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getStat Fehler", e);
        }
        return 0;
    }

    public List<TopEntry> getTopByWins(int limit) {
        List<TopEntry> list = new ArrayList<>();
        String sql = """
            SELECT p.name, s.wins, s.kills, s.beds_broken, s.games_played
            FROM mg_bedwars_stats s
            LEFT JOIN pinkhorizon.players p ON s.uuid = p.uuid
            ORDER BY s.wins DESC
            LIMIT ?
        """;
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TopEntry(
                            rs.getString("name"),
                            rs.getInt("wins"),
                            rs.getInt("kills"),
                            rs.getInt("beds_broken"),
                            rs.getInt("games_played")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getTopByWins Fehler", e);
        }
        return list;
    }
}
