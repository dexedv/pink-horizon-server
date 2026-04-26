package de.pinkhorizon.generators;

import org.bukkit.Material;

/**
 * Alle verfügbaren Generator-Typen inkl. Mega- und Ultra-Varianten.
 * Fusionskette: Normal → Mega (3×Normal) → Ultra (3×Mega)
 */
public enum GeneratorType {

    // ── Normale Tiers ──────────────────────────────────────────────────────
    COBBLESTONE(Material.COBBLESTONE,    "<gray>Cobblestone-Generator</gray>",            1.0,      0L,        50L,      false, false),
    IRON       (Material.IRON_BLOCK,     "<white>Iron-Generator</white>",                 5.0,    500L,       250L,      false, false),
    GOLD       (Material.GOLD_BLOCK,     "<gold>Gold-Generator</gold>",                  15.0,  2_500L,       750L,      false, false),
    LAPIS      (Material.LAPIS_BLOCK,    "<blue>Lapis-Generator</blue>",                 40.0, 10_000L,     2_000L,      false, false),
    DIAMOND    (Material.DIAMOND_BLOCK,  "<aqua>Diamond-Generator</aqua>",              100.0, 50_000L,     5_000L,      false, false),
    NETHERITE  (Material.NETHERITE_BLOCK,"<dark_gray>Netherite-Generator</dark_gray>",  500.0,250_000L,    25_000L,      false, false),

    // ── Mega-Tiers (3× Normal, nicht kaufbar) ─────────────────────────────
    MEGA_COBBLESTONE(Material.COBBLESTONE,    "<gray><bold>✦ Mega-Cobblestone</bold></gray>",           4.0,  -1L,   200L, true, false),
    MEGA_IRON       (Material.IRON_BLOCK,     "<white><bold>✦ Mega-Iron</bold></white>",               20.0,  -1L, 1_000L, true, false),
    MEGA_GOLD       (Material.GOLD_BLOCK,     "<gold><bold>✦ Mega-Gold</bold></gold>",                 60.0,  -1L, 3_000L, true, false),
    MEGA_LAPIS      (Material.LAPIS_BLOCK,    "<blue><bold>✦ Mega-Lapis</bold></blue>",               160.0,  -1L, 8_000L, true, false),
    MEGA_DIAMOND    (Material.DIAMOND_BLOCK,  "<aqua><bold>✦ Mega-Diamond</bold></aqua>",             400.0,  -1L,20_000L, true, false),
    MEGA_NETHERITE  (Material.NETHERITE_BLOCK,"<dark_gray><bold>✦ Mega-Netherite</bold></dark_gray>",2000.0,  -1L,100_000L,true, false),

    // ── Ultra-Tiers (3× Mega, nicht kaufbar) ──────────────────────────────
    ULTRA_COBBLESTONE(Material.OBSIDIAN,      "<dark_purple><bold>◆ Ultra-Cobblestone</bold></dark_purple>",    16.0,  -1L,    800L, true, false),
    ULTRA_IRON       (Material.QUARTZ_BLOCK,  "<white><bold>◆ Ultra-Iron</bold></white>",                       80.0,  -1L,  4_000L, true, false),
    ULTRA_GOLD       (Material.AMETHYST_BLOCK,"<light_purple><bold>◆ Ultra-Gold</bold></light_purple>",        240.0,  -1L, 12_000L, true, false),
    ULTRA_LAPIS      (Material.BLUE_ICE,      "<aqua><bold>◆ Ultra-Lapis</bold></aqua>",                       640.0,  -1L, 32_000L, true, false),
    ULTRA_DIAMOND    (Material.SEA_LANTERN,   "<aqua><bold>◆ Ultra-Diamond</bold></aqua>",                   1_600.0,  -1L, 80_000L, true, false),
    ULTRA_NETHERITE  (Material.BEACON,        "<yellow><bold>◆ Ultra-Netherite</bold></yellow>",             8_000.0,  -1L,400_000L, true, false);

    private final Material block;
    private final String displayName;
    private final double baseIncomePerSec;
    private final long buyPrice;          // -1 = nicht kaufbar
    private final long baseUpgradeCost;
    private final boolean mega;           // true für Mega UND Ultra
    private final boolean seasonal;

    GeneratorType(Material block, String displayName, double baseIncomePerSec,
                  long buyPrice, long baseUpgradeCost, boolean mega, boolean seasonal) {
        this.block = block;
        this.displayName = displayName;
        this.baseIncomePerSec = baseIncomePerSec;
        this.buyPrice = buyPrice;
        this.baseUpgradeCost = baseUpgradeCost;
        this.mega = mega;
        this.seasonal = seasonal;
    }

    // ── Formeln ─────────────────────────────────────────────────────────────

    public double incomeAt(int level) {
        return baseIncomePerSec * (1.0 + (level - 1) * 0.10);
    }

    public long upgradeCostAt(int level) {
        if (baseUpgradeCost <= 0) return Long.MAX_VALUE;
        return Math.round(baseUpgradeCost * Math.pow(1.6, level));
    }

    // ── Fusionskette ─────────────────────────────────────────────────────────

    /**
     * Nächstes Fusions-Ergebnis: Normal→Mega, Mega→Ultra, Ultra→null.
     */
    public GeneratorType getNextFusionTier() {
        return switch (this) {
            // Normal → Mega
            case COBBLESTONE      -> MEGA_COBBLESTONE;
            case IRON             -> MEGA_IRON;
            case GOLD             -> MEGA_GOLD;
            case LAPIS            -> MEGA_LAPIS;
            case DIAMOND          -> MEGA_DIAMOND;
            case NETHERITE        -> MEGA_NETHERITE;
            // Mega → Ultra
            case MEGA_COBBLESTONE -> ULTRA_COBBLESTONE;
            case MEGA_IRON        -> ULTRA_IRON;
            case MEGA_GOLD        -> ULTRA_GOLD;
            case MEGA_LAPIS       -> ULTRA_LAPIS;
            case MEGA_DIAMOND     -> ULTRA_DIAMOND;
            case MEGA_NETHERITE   -> ULTRA_NETHERITE;
            // Ultra → keine weitere Fusion
            default               -> null;
        };
    }

    /** @deprecated Benutze getNextFusionTier() */
    public GeneratorType getMegaTier() {
        return getNextFusionTier();
    }

    /**
     * Nächstes normales Tier (Cobblestone→Iron→…→Netherite).
     */
    public GeneratorType getNextTier() {
        return switch (this) {
            case COBBLESTONE -> IRON;
            case IRON        -> GOLD;
            case GOLD        -> LAPIS;
            case LAPIS       -> DIAMOND;
            case DIAMOND     -> NETHERITE;
            default          -> null;
        };
    }

    public GeneratorType getBaseTier() {
        if (!mega) return null;
        return switch (this) {
            case MEGA_COBBLESTONE,  ULTRA_COBBLESTONE -> COBBLESTONE;
            case MEGA_IRON,         ULTRA_IRON        -> IRON;
            case MEGA_GOLD,         ULTRA_GOLD        -> GOLD;
            case MEGA_LAPIS,        ULTRA_LAPIS       -> LAPIS;
            case MEGA_DIAMOND,      ULTRA_DIAMOND     -> DIAMOND;
            case MEGA_NETHERITE,    ULTRA_NETHERITE   -> NETHERITE;
            default                                   -> null;
        };
    }

    public boolean isUltra() { return name().startsWith("ULTRA_"); }

    // ── Getter ───────────────────────────────────────────────────────────────

    public Material getBlock()            { return block; }
    public String getDisplayName()        { return displayName; }
    public double getBaseIncomePerSec()   { return baseIncomePerSec; }
    public long getBuyPrice()             { return buyPrice; }
    public long getBaseUpgradeCost()      { return baseUpgradeCost; }
    public boolean isMega()               { return mega; }
    public boolean isSeasonal()           { return seasonal; }
    public boolean isBuyable()            { return buyPrice >= 0; }
}
