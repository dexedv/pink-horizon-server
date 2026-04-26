package de.pinkhorizon.generators;

import org.bukkit.Material;

/**
 * Alle verfügbaren Generator-Typen inkl. Mega-Varianten.
 * Seasonal-Generatoren werden separat aus der config.yml geladen.
 */
public enum GeneratorType {

    // ── Normale Tiers ──────────────────────────────────────────────────────
    COBBLESTONE(Material.COBBLESTONE, "<gray>Cobblestone-Generator</gray>", 1.0,   0L,       50L,    false, false),
    IRON       (Material.IRON_BLOCK,  "<white>Iron-Generator</white>",      5.0,   500L,     250L,   false, false),
    GOLD       (Material.GOLD_BLOCK,  "<gold>Gold-Generator</gold>",        15.0,  2_500L,   750L,   false, false),
    LAPIS      (Material.LAPIS_BLOCK, "<blue>Lapis-Generator</blue>",       40.0,  10_000L,  2_000L, false, false),
    DIAMOND    (Material.DIAMOND_BLOCK,"<aqua>Diamond-Generator</aqua>",    100.0, 50_000L,  5_000L, false, false),
    NETHERITE  (Material.NETHERITE_BLOCK,"<dark_gray>Netherite-Generator</dark_gray>", 500.0, 250_000L, 25_000L, false, false),

    // ── Mega-Tiers (durch Fusion, nicht kaufbar) ───────────────────────────
    MEGA_COBBLESTONE(Material.COBBLESTONE, "<gray><bold>✦ Mega-Cobblestone</bold></gray>", 4.0,    -1L, -1L, true, false),
    MEGA_IRON       (Material.IRON_BLOCK,  "<white><bold>✦ Mega-Iron</bold></white>",      20.0,   -1L, -1L, true, false),
    MEGA_GOLD       (Material.GOLD_BLOCK,  "<gold><bold>✦ Mega-Gold</bold></gold>",        60.0,   -1L, -1L, true, false),
    MEGA_LAPIS      (Material.LAPIS_BLOCK, "<blue><bold>✦ Mega-Lapis</bold></blue>",       160.0,  -1L, -1L, true, false),
    MEGA_DIAMOND    (Material.DIAMOND_BLOCK,"<aqua><bold>✦ Mega-Diamond</bold></aqua>",    400.0,  -1L, -1L, true, false),
    MEGA_NETHERITE  (Material.NETHERITE_BLOCK,"<dark_gray><bold>✦ Mega-Netherite</bold></dark_gray>", 2000.0, -1L, -1L, true, false);

    private final Material block;
    private final String displayName;       // MiniMessage-Format
    private final double baseIncomePerSec;  // $/s bei Level 1
    private final long buyPrice;            // -1 = nicht kaufbar
    private final long baseUpgradeCost;     // Basis-Upgradekosten bei Level 1 → 2
    private final boolean mega;
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

    /**
     * Einkommen pro Sekunde bei gegebenem Level.
     * Formel: base * (1 + (level - 1) * 0.10)
     * Mega-Generatoren: base * 4 * (1 + (level - 1) * 0.10)
     */
    public double incomeAt(int level) {
        double base = mega ? baseIncomePerSec : baseIncomePerSec;
        return base * (1.0 + (level - 1) * 0.10);
    }

    /**
     * Kosten für Upgrade von level auf level+1.
     * Formel: baseUpgradeCost * pow(1.6, level)
     */
    public long upgradeCostAt(int level) {
        if (baseUpgradeCost <= 0) return Long.MAX_VALUE;
        return Math.round(baseUpgradeCost * Math.pow(1.6, level));
    }

    /**
     * Gibt den normalen (nicht-Mega) Typ zurück, aus dem dieser Mega-Typ entstand.
     * Null wenn kein Mega-Typ.
     */
    public GeneratorType getBaseTier() {
        if (!mega) return null;
        return switch (this) {
            case MEGA_COBBLESTONE -> COBBLESTONE;
            case MEGA_IRON        -> IRON;
            case MEGA_GOLD        -> GOLD;
            case MEGA_LAPIS       -> LAPIS;
            case MEGA_DIAMOND     -> DIAMOND;
            case MEGA_NETHERITE   -> NETHERITE;
            default               -> null;
        };
    }

    /**
     * Gibt den Mega-Typ für diesen normalen Typ zurück.
     * Null wenn schon Mega oder seasonal.
     */
    public GeneratorType getMegaTier() {
        return switch (this) {
            case COBBLESTONE -> MEGA_COBBLESTONE;
            case IRON        -> MEGA_IRON;
            case GOLD        -> MEGA_GOLD;
            case LAPIS       -> MEGA_LAPIS;
            case DIAMOND     -> MEGA_DIAMOND;
            case NETHERITE   -> MEGA_NETHERITE;
            default          -> null;
        };
    }

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
