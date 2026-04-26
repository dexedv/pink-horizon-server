package de.pinkhorizon.generators.data;

import de.pinkhorizon.generators.GeneratorType;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Repräsentiert einen platzierten Generator in der Welt.
 */
public class PlacedGenerator {

    private final UUID ownerUUID;
    private final String world;
    private final int x, y, z;
    private GeneratorType type;
    private int level;

    /** DB-ID, -1 wenn noch nicht in DB gespeichert */
    private int dbId = -1;

    /** Enchant: "FORTUNE", "EFFICIENCY", "HASTE", "SYNERGY" oder null */
    private String enchant = null;

    public PlacedGenerator(UUID ownerUUID, String world, int x, int y, int z,
                           GeneratorType type, int level) {
        this.ownerUUID = ownerUUID;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.level = level;
    }

    // ── Einkommen ────────────────────────────────────────────────────────────

    /**
     * Einkommen pro Sekunde inkl. Level-Bonus und Enchant-Bonus.
     * Prestige-/Booster-Multiplikatoren werden im MoneyManager angewendet.
     */
    public double incomePerSecond() {
        double base = type.incomeAt(level);
        if ("FORTUNE".equals(enchant)) return base * 1.25;
        if ("HASTE".equals(enchant))   return base * 1.10;
        return base;
    }

    /** Upgrade-Kosten für nächstes Level (EFFICIENCY reduziert um 20%) */
    public long upgradeCost() {
        long base = type.upgradeCostAt(level);
        if ("EFFICIENCY".equals(enchant)) return Math.round(base * 0.80);
        return base;
    }

    // ── Location Helper ──────────────────────────────────────────────────────

    public String locationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public boolean matchesLocation(Location loc) {
        return loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z
                && loc.getWorld() != null && loc.getWorld().getName().equals(world);
    }

    // ── Getter & Setter ──────────────────────────────────────────────────────

    public UUID getOwnerUUID()        { return ownerUUID; }
    public String getWorld()          { return world; }
    public int getX()                 { return x; }
    public int getY()                 { return y; }
    public int getZ()                 { return z; }
    public GeneratorType getType()    { return type; }
    public int getLevel()             { return level; }
    public int getDbId()              { return dbId; }

    public void setType(GeneratorType type) { this.type = type; }
    public void setLevel(int level)         { this.level = level; }
    public void setDbId(int dbId)           { this.dbId = dbId; }
    public String getEnchant()              { return enchant; }
    public void setEnchant(String enchant)  { this.enchant = enchant; }
    public boolean hasEnchant()             { return enchant != null; }
}
