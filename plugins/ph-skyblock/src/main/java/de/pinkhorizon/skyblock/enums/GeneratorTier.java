package de.pinkhorizon.skyblock.enums;

import org.bukkit.Material;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Definiert die Loot-Tabelle des Generators basierend auf Level.
 * Level 1–50, jede 5 Stufen neues Erz freigeschaltet.
 */
public enum GeneratorTier {

    COBBLESTONE(1,  "§7Cobblestone",        Material.COBBLESTONE,        1),
    STONE      (5,  "§fStein",              Material.STONE,              2),
    COAL       (10, "§8Kohle",              Material.COAL_ORE,           8),
    IRON       (15, "§7Eisen",             Material.IRON_ORE,           25),
    GOLD       (20, "§6Gold",              Material.GOLD_ORE,           75),
    REDSTONE   (25, "§cRedstone",          Material.REDSTONE_ORE,       40),
    LAPIS      (30, "§9Lapislazuli",       Material.LAPIS_ORE,          55),
    DIAMOND    (35, "§bDiamant",           Material.DIAMOND_ORE,        500),
    EMERALD    (40, "§aSmaragd",           Material.EMERALD_ORE,        800),
    ANCIENT    (45, "§5Alter Schutt",      Material.ANCIENT_DEBRIS,     2500),
    NETHERITE  (50, "§4Netherit",          Material.NETHERITE_SCRAP,    5000);

    private final int unlockLevel;
    private final String displayName;
    private final Material material;
    private final int coinValue; // Coins beim Auto-Sell

    GeneratorTier(int unlockLevel, String displayName, Material material, int coinValue) {
        this.unlockLevel = unlockLevel;
        this.displayName = displayName;
        this.material = material;
        this.coinValue = coinValue;
    }

    public int getUnlockLevel()  { return unlockLevel; }
    public String getDisplayName(){ return displayName; }
    public Material getMaterial() { return material; }
    public int getCoinValue()    { return coinValue; }

    // ── Loot-Tabelle: gibt gewichtetes zufälliges Tier zurück ─────────────────

    private static final Random RNG = new Random();

    /**
     * Berechnet für das gegebene Level die Loot-Tabelle (Tier → Gewicht)
     * und gibt ein zufälliges Tier zurück.
     */
    public static GeneratorTier rollLoot(int level) {
        Map<GeneratorTier, Integer> table = buildTable(level);
        int total = table.values().stream().mapToInt(i -> i).sum();
        int roll  = RNG.nextInt(total);
        int cum   = 0;
        for (Map.Entry<GeneratorTier, Integer> e : table.entrySet()) {
            cum += e.getValue();
            if (roll < cum) return e.getKey();
        }
        return COBBLESTONE;
    }

    private static Map<GeneratorTier, Integer> buildTable(int lvl) {
        Map<GeneratorTier, Integer> t = new LinkedHashMap<>();

        // Cobblestone nimmt ab
        t.put(COBBLESTONE, Math.max(5, 95 - lvl * 2));

        if (lvl >= 5)  t.put(STONE,     Math.min(15, 3 + (lvl - 5)));
        if (lvl >= 10) t.put(COAL,      Math.min(12, 2 + (lvl - 10)));
        if (lvl >= 15) t.put(IRON,      Math.min(12, 2 + (lvl - 15)));
        if (lvl >= 20) t.put(GOLD,      Math.min(10, 2 + (lvl - 20)));
        if (lvl >= 25) t.put(REDSTONE,  Math.min(8,  2 + (lvl - 25)));
        if (lvl >= 30) t.put(LAPIS,     Math.min(8,  2 + (lvl - 30)));
        if (lvl >= 35) t.put(DIAMOND,   Math.min(6,  1 + (lvl - 35)));
        if (lvl >= 40) t.put(EMERALD,   Math.min(5,  1 + (lvl - 40)));
        if (lvl >= 45) t.put(ANCIENT,   Math.min(4,  1 + (lvl - 45)));
        if (lvl >= 50) t.put(NETHERITE, 3);

        return t;
    }

    /** Produktionsintervall in Ticks (20 = 1 Sekunde). */
    public static int tickInterval(int level) {
        // Level 1 = alle 5s (100 Ticks), Level 50 = alle 1s (20 Ticks)
        int ticks = (int) Math.max(20, 100 - level * 1.6);
        return ticks;
    }

    /** Upgrade-Kosten für Level → Level+1. */
    public static long upgradeCost(int currentLevel) {
        return (long) (50 * Math.pow(currentLevel, 2.3));
    }

    /** Block-Material das den Generator visuell repräsentiert (ändert sich mit Level). */
    public static Material visualMaterial(int level) {
        if (level < 5)  return Material.FURNACE;
        if (level < 10) return Material.SMOKER;
        if (level < 15) return Material.BLAST_FURNACE;
        if (level < 20) return Material.IRON_BLOCK;
        if (level < 25) return Material.GOLD_BLOCK;
        if (level < 30) return Material.LAPIS_BLOCK;
        if (level < 35) return Material.REDSTONE_BLOCK;
        if (level < 40) return Material.DIAMOND_BLOCK;
        if (level < 45) return Material.EMERALD_BLOCK;
        if (level < 50) return Material.NETHERITE_BLOCK;
        return Material.BEACON; // Stufe 50 = Beacon (max)
    }
}
