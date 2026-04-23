package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks how many times each player has killed each mob type.
 *
 * DB table (create if not present):
 *   smash_bestiary (uuid CHAR(36), mob_type VARCHAR(32), kills INT DEFAULT 0,
 *                   PRIMARY KEY(uuid, mob_type))
 */
public class BestiaryManager {

    private final PHSmash plugin;

    public BestiaryManager(PHSmash plugin) {
        this.plugin = plugin;
        createTable();
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    private void createTable() {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS smash_bestiary (
                     uuid     CHAR(36)    NOT NULL,
                     mob_type VARCHAR(32) NOT NULL,
                     kills    INT         NOT NULL DEFAULT 0,
                     PRIMARY KEY (uuid, mob_type)
                 )
                 """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("BestiaryManager.createTable: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Core methods
    // -------------------------------------------------------------------------

    /**
     * Records a kill for the given mob type.
     * Does NOT handle first-kill detection — use {@link #trackKillAndCheckFirst} for that.
     */
    public void trackKill(UUID uuid, String mobType) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_bestiary (uuid, mob_type, kills) VALUES (?, ?, 1)
                 ON DUPLICATE KEY UPDATE kills = kills + 1
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, mobType.toUpperCase());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("BestiaryManager.trackKill: " + e.getMessage());
        }
    }

    /**
     * Records a kill and checks whether this was the player's first kill of that mob type.
     * If it is the first kill, awards 200 coins and notifies the player.
     *
     * @return true if this was the very first kill (row inserted), false otherwise
     */
    public boolean trackKillAndCheckFirst(UUID uuid, String mobType) {
        String upperType = mobType.toUpperCase();
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_bestiary (uuid, mob_type, kills) VALUES (?, ?, 1)
                 ON DUPLICATE KEY UPDATE kills = kills + 1
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, upperType);
            int affected = st.executeUpdate();

            // affected == 1 → new row inserted → first kill
            // affected == 2 → existing row updated (ON DUPLICATE KEY UPDATE counts as 2)
            boolean firstKill = (affected == 1);

            if (firstKill) {
                plugin.getCoinManager().addCoins(uuid, 200);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage("§6✦ §eErste Begegnung: §f" + getMobDisplayName(upperType)
                            + " §7— §a+200 Münzen");
                }
            }

            return firstKill;
        } catch (SQLException e) {
            plugin.getLogger().warning("BestiaryManager.trackKillAndCheckFirst: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the total recorded kills for the given mob type.
     */
    public int getKills(UUID uuid, String mobType) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT kills FROM smash_bestiary WHERE uuid = ? AND mob_type = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, mobType.toUpperCase());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("kills");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BestiaryManager.getKills: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns a map of all tracked mob types and their kill counts for the given player.
     */
    public Map<String, Integer> getAllKills(UUID uuid) {
        Map<String, Integer> result = new HashMap<>();
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT mob_type, kills FROM smash_bestiary WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("mob_type"), rs.getInt("kills"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BestiaryManager.getAllKills: " + e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a human-readable German name for the given EntityType name string.
     */
    public String getMobDisplayName(String mobType) {
        switch (mobType.toUpperCase()) {
            case "ZOMBIE":         return "Zombie";
            case "SKELETON":       return "Skelett";
            case "CREEPER":        return "Creeper";
            case "SPIDER":         return "Spinne";
            case "CAVE_SPIDER":    return "Höhlenspinne";
            case "ENDERMAN":       return "Enderman";
            case "BLAZE":          return "Lohe";
            case "WITCH":          return "Hexe";
            case "WARDEN":         return "Wächter";
            case "RAVAGER":        return "Verwüster";
            case "EVOKER":         return "Beschwörer";
            case "IRON_GOLEM":     return "Eisengolem";
            case "VINDICATOR":     return "Rächer";
            case "PILLAGER":       return "Plünderer";
            case "PHANTOM":        return "Phantom";
            case "DROWNED":        return "Ertrunkener";
            case "HUSK":           return "Husk";
            case "STRAY":          return "Irrläufer";
            case "PIGLIN_BRUTE":   return "Piglinhüne";
            case "PIGLIN":         return "Piglin";
            case "ZOMBIFIED_PIGLIN": return "Zombifizierter Piglin";
            case "ELDER_GUARDIAN": return "Ältester Wächter";
            case "GUARDIAN":       return "Wächter";
            case "SHULKER":        return "Schüler";
            case "GHAST":          return "Ghast";
            case "MAGMA_CUBE":     return "Magmawürfel";
            case "SLIME":          return "Schleim";
            case "ENDERMITE":      return "Endermilbe";
            case "SILVERFISH":     return "Silberfischchen";
            case "ILLUSIONER":     return "Illusionist";
            default:
                // Best-effort: capitalize first letter, lowercase rest
                String lower = mobType.toLowerCase().replace('_', ' ');
                if (lower.isEmpty()) return mobType;
                return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }
}
