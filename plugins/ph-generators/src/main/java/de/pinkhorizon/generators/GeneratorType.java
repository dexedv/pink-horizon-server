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
    ULTRA_NETHERITE  (Material.BEACON,        "<yellow><bold>◆ Ultra-Netherite</bold></yellow>",             8_000.0,  -1L,400_000L, true, false),

    // ── God-Tiers (3× Ultra, nicht kaufbar) ───────────────────────────────
    GOD_COBBLESTONE(Material.GILDED_BLACKSTONE, "<gold><bold>★ God-Cobblestone</bold></gold>",              64.0,  -1L,   3_200L, true, false),
    GOD_IRON       (Material.CRYING_OBSIDIAN,   "<light_purple><bold>★ God-Iron</bold></light_purple>",    320.0,  -1L,  16_000L, true, false),
    GOD_GOLD       (Material.ANCIENT_DEBRIS,    "<gold><bold>★ God-Gold</bold></gold>",                    960.0,  -1L,  48_000L, true, false),
    GOD_LAPIS      (Material.PRISMARINE_BRICKS, "<aqua><bold>★ God-Lapis</bold></aqua>",                 2_560.0,  -1L, 128_000L, true, false),
    GOD_DIAMOND    (Material.PURPUR_BLOCK,      "<dark_purple><bold>★ God-Diamond</bold></dark_purple>",  6_400.0,  -1L, 320_000L, true, false),
    GOD_NETHERITE  (Material.RESPAWN_ANCHOR,    "<red><bold>★ God-Netherite</bold></red>",              32_000.0,  -1L,1_600_000L,true, false),

    // ── Titan-Tiers (3× God, nicht kaufbar) ───────────────────────────────
    TITAN_COBBLESTONE(Material.SCULK,                  "<dark_red><bold>❋ Titan-Cobblestone</bold></dark_red>",       256.0,  -1L,   12_800L, true, false),
    TITAN_IRON       (Material.DEEPSLATE_TILES,        "<gray><bold>❋ Titan-Iron</bold></gray>",                    1_280.0,  -1L,   64_000L, true, false),
    TITAN_GOLD       (Material.SHROOMLIGHT,            "<gold><bold>❋ Titan-Gold</bold></gold>",                    3_840.0,  -1L,  192_000L, true, false),
    TITAN_LAPIS      (Material.VERDANT_FROGLIGHT,      "<green><bold>❋ Titan-Lapis</bold></green>",                10_240.0,  -1L,  512_000L, true, false),
    TITAN_DIAMOND    (Material.OCHRE_FROGLIGHT,        "<yellow><bold>❋ Titan-Diamond</bold></yellow>",            25_600.0,  -1L,1_280_000L, true, false),
    TITAN_NETHERITE  (Material.REINFORCED_DEEPSLATE,   "<dark_red><bold>❋ Titan-Netherite</bold></dark_red>",     128_000.0,  -1L,6_400_000L, true, false);

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
            // Ultra → God
            case ULTRA_COBBLESTONE -> GOD_COBBLESTONE;
            case ULTRA_IRON        -> GOD_IRON;
            case ULTRA_GOLD        -> GOD_GOLD;
            case ULTRA_LAPIS       -> GOD_LAPIS;
            case ULTRA_DIAMOND     -> GOD_DIAMOND;
            case ULTRA_NETHERITE   -> GOD_NETHERITE;
            // God → Titan
            case GOD_COBBLESTONE -> TITAN_COBBLESTONE;
            case GOD_IRON        -> TITAN_IRON;
            case GOD_GOLD        -> TITAN_GOLD;
            case GOD_LAPIS       -> TITAN_LAPIS;
            case GOD_DIAMOND     -> TITAN_DIAMOND;
            case GOD_NETHERITE   -> TITAN_NETHERITE;
            // Titan → keine weitere Fusion
            default              -> null;
        };
    }

    /** @deprecated Benutze getNextFusionTier() */
    public GeneratorType getMegaTier() {
        return getNextFusionTier();
    }

    /**
     * Nächstes Tier innerhalb derselben Fusionsstufe:
     * Normal: Cobblestone→Iron→…→Netherite
     * Mega:   Mega-Cobblestone→Mega-Iron→…→Mega-Netherite
     * Ultra:  Ultra-Cobblestone→Ultra-Iron→…→Ultra-Netherite
     */
    public GeneratorType getNextTier() {
        return switch (this) {
            // Normal
            case COBBLESTONE      -> IRON;
            case IRON             -> GOLD;
            case GOLD             -> LAPIS;
            case LAPIS            -> DIAMOND;
            case DIAMOND          -> NETHERITE;
            // Mega
            case MEGA_COBBLESTONE -> MEGA_IRON;
            case MEGA_IRON        -> MEGA_GOLD;
            case MEGA_GOLD        -> MEGA_LAPIS;
            case MEGA_LAPIS       -> MEGA_DIAMOND;
            case MEGA_DIAMOND     -> MEGA_NETHERITE;
            // Ultra
            case ULTRA_COBBLESTONE -> ULTRA_IRON;
            case ULTRA_IRON        -> ULTRA_GOLD;
            case ULTRA_GOLD        -> ULTRA_LAPIS;
            case ULTRA_LAPIS       -> ULTRA_DIAMOND;
            case ULTRA_DIAMOND     -> ULTRA_NETHERITE;
            // God
            case GOD_COBBLESTONE -> GOD_IRON;
            case GOD_IRON        -> GOD_GOLD;
            case GOD_GOLD        -> GOD_LAPIS;
            case GOD_LAPIS       -> GOD_DIAMOND;
            case GOD_DIAMOND     -> GOD_NETHERITE;
            // Titan
            case TITAN_COBBLESTONE -> TITAN_IRON;
            case TITAN_IRON        -> TITAN_GOLD;
            case TITAN_GOLD        -> TITAN_LAPIS;
            case TITAN_LAPIS       -> TITAN_DIAMOND;
            case TITAN_DIAMOND     -> TITAN_NETHERITE;
            default                -> null;
        };
    }

    /**
     * Kosten für ein Tier-Upgrade (Max-Level → nächstes Tier).
     * Normal:  = Kaufpreis des Ziel-Tiers
     * Mega:    = 4× der entsprechenden Normal-Tier-Kosten
     * Ultra:   = 16× der entsprechenden Normal-Tier-Kosten
     * Gibt -1 zurück wenn kein nächstes Tier existiert.
     */
    public long getTierUpgradeCost() {
        if (getNextTier() == null) return -1L;
        if (!isMega()) {
            // Normal → nächstes Normal: Kaufpreis des Ziel-Tiers
            return getNextTier().getBuyPrice();
        }
        // Basis-Typ bestimmen (z.B. MEGA_IRON → IRON)
        GeneratorType base = getBaseTier();
        if (base == null) return -1L;
        long normalCost = base.getTierUpgradeCost();
        if (isTitan()) return normalCost * 256L;
        if (isGod())   return normalCost * 64L;
        if (isUltra()) return normalCost * 16L;
        return normalCost * 4L;
    }

    public GeneratorType getBaseTier() {
        if (!mega) return null;
        return switch (this) {
            case MEGA_COBBLESTONE, ULTRA_COBBLESTONE, GOD_COBBLESTONE, TITAN_COBBLESTONE -> COBBLESTONE;
            case MEGA_IRON,        ULTRA_IRON,        GOD_IRON,        TITAN_IRON        -> IRON;
            case MEGA_GOLD,        ULTRA_GOLD,        GOD_GOLD,        TITAN_GOLD        -> GOLD;
            case MEGA_LAPIS,       ULTRA_LAPIS,       GOD_LAPIS,       TITAN_LAPIS       -> LAPIS;
            case MEGA_DIAMOND,     ULTRA_DIAMOND,     GOD_DIAMOND,     TITAN_DIAMOND     -> DIAMOND;
            case MEGA_NETHERITE,   ULTRA_NETHERITE,   GOD_NETHERITE,   TITAN_NETHERITE   -> NETHERITE;
            default                                                                       -> null;
        };
    }

    public boolean isUltra() { return name().startsWith("ULTRA_"); }
    public boolean isGod()   { return name().startsWith("GOD_"); }
    public boolean isTitan() { return name().startsWith("TITAN_"); }

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
