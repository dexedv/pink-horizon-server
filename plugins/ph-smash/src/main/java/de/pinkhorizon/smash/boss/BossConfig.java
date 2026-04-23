package de.pinkhorizon.smash.boss;

public record BossConfig(int level, double maxHp, double damage, String displayName) {

    public static BossConfig forLevel(int level) {
        double hp     = Math.max(10, Math.round(100 * Math.pow(1.15, level - 1)));
        double damage = 4.0 + level * 0.8;

        String tier;
        if      (level >= 500) tier = "§4§lLEGENDÄR";
        else if (level >= 100) tier = "§5§lEPIC";
        else if (level >= 50)  tier = "§6§lSELTEN";
        else                   tier = "§c§lBoss";

        String name = tier + " §8[Lv. §c" + level + "§8]";
        return new BossConfig(level, hp, damage, name);
    }

    /** Formatiert HP-Zahl lesbar: 1.174.313 → "1,17M" */
    public String formatHp(double hp) {
        if (hp >= 1_000_000_000) return String.format("%.2fB", hp / 1_000_000_000);
        if (hp >= 1_000_000)     return String.format("%.2fM", hp / 1_000_000);
        if (hp >= 1_000)         return String.format("%.1fK", hp / 1_000);
        return String.format("%.0f", hp);
    }
}
