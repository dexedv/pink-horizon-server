package de.pinkhorizon.skyblock.enums;

import org.bukkit.Material;

public enum QuestType {

    MINE_COBBLESTONE("cobble_miner",   "§7Cobblestone abbauen",      "Baue §e{goal} §7Cobblestone ab.",            Material.COBBLESTONE,  new long[]{200, 500, 1500, 5000}),
    MINE_IRON       ("iron_miner",     "§7Eisen abbauen",            "Baue §e{goal} §7Eisen-Erz ab.",              Material.IRON_ORE,     new long[]{20, 50, 150, 500}),
    MINE_GOLD       ("gold_miner",     "§6Gold abbauen",             "Baue §e{goal} §6Gold-Erz ab.",               Material.GOLD_ORE,     new long[]{10, 25, 75, 200}),
    MINE_DIAMOND    ("diamond_miner",  "§bDiamant abbauen",          "Baue §e{goal} §bDiamant-Erz ab.",            Material.DIAMOND_ORE,  new long[]{5, 15, 40, 100}),
    MINE_EMERALD    ("emerald_miner",  "§aSmaragd abbauen",          "Baue §e{goal} §aSmaragd-Erz ab.",            Material.EMERALD_ORE,  new long[]{3, 10, 30, 75}),
    UPGRADE_GEN     ("gen_upgrader",   "§eGenerator upgraden",       "Upgrade deinen Generator §e{goal}x.",        Material.FURNACE,      new long[]{1, 3, 5, 10}),
    EARN_COINS      ("coin_earner",    "§6Coins verdienen",          "Verdiene §e{goal} §6Coins.",                 Material.GOLD_NUGGET,  new long[]{500, 2000, 10000, 50000}),
    PLACE_BLOCKS    ("builder",        "§aBlöcke platzieren",        "Platziere §e{goal} §aBlöcke auf deiner Insel.", Material.BRICKS,    new long[]{50, 200, 500, 2000}),
    COLLECT_GEN     ("gen_collector",  "§7Generator leeren",         "Leere deinen Generator §e{goal}x.",          Material.CHEST,        new long[]{2, 5, 10, 20}),
    ISLAND_SCORE    ("score_hunter",   "§dInsel-Score erhöhen",      "Erreiche einen Insel-Score von §e{goal}.",   Material.NETHER_STAR,  new long[]{100, 500, 2000, 10000}),
    VISIT_ISLANDS   ("explorer",       "§eInseln besuchen",          "Besuche §e{goal} §everschiedene Inseln.",    Material.COMPASS,      new long[]{2, 5, 10, 25}),
    MINE_ANCIENT    ("debris_hunter",  "§5Alten Schutt abbauen",     "Baue §e{goal} §5Alten Schutt ab.",           Material.ANCIENT_DEBRIS, new long[]{1, 3, 8, 20});

    private final String id;
    private final String name;
    private final String description;
    private final Material icon;
    private final long[] goalTiers; // 4 Schwierigkeitsstufen: Leicht, Normal, Schwer, Episch

    QuestType(String id, String name, String description, Material icon, long[] goalTiers) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.goalTiers = goalTiers;
    }

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Material getIcon()      { return icon; }

    /** Gibt das Ziel für die Schwierigkeit zurück (0=Leicht, 1=Normal, 2=Schwer, 3=Episch). */
    public long getGoal(int difficulty) {
        return goalTiers[Math.max(0, Math.min(3, difficulty))];
    }

    /** Belohnung in Coins (skaliert mit Schwierigkeit). */
    public long getReward(int difficulty) {
        return switch (difficulty) {
            case 0 -> 200;
            case 1 -> 750;
            case 2 -> 2500;
            case 3 -> 10000;
            default -> 200;
        };
    }

    /** Bonus-XP (Insel-Score) als Belohnung. */
    public long getScoreReward(int difficulty) {
        return switch (difficulty) {
            case 0 -> 10;
            case 1 -> 35;
            case 2 -> 100;
            case 3 -> 400;
            default -> 10;
        };
    }

    public String getDifficultyName(int diff) {
        return switch (diff) {
            case 0 -> "§a[Leicht]";
            case 1 -> "§e[Normal]";
            case 2 -> "§6[Schwer]";
            case 3 -> "§c[Episch]";
            default -> "§7[?]";
        };
    }

    public static QuestType byId(String id) {
        if (id == null) return null;
        for (QuestType t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
