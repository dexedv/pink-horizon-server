package de.pinkhorizon.survival.crates;

import org.bukkit.entity.EntityType;

public record CrateReward(
    String displayName,
    RewardType type,
    long coins,
    int claims,
    EntityType entityType,
    int weight
) {
    public enum RewardType { COINS, CLAIMS, SPAWNER }

    public static CrateReward coins(long amount, int weight) {
        return new CrateReward(formatCoins(amount) + " Coins", RewardType.COINS, amount, 0, null, weight);
    }

    public static CrateReward claims(int amount, int weight) {
        return new CrateReward("+" + amount + " Claim-Slot" + (amount == 1 ? "" : "s"), RewardType.CLAIMS, 0, amount, null, weight);
    }

    public static CrateReward spawner(EntityType type, String name, int weight) {
        return new CrateReward(name, RewardType.SPAWNER, 0, 0, type, weight);
    }

    private static String formatCoins(long amount) {
        if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
        if (amount >= 1_000)     return (amount / 1_000) + "k";
        return String.valueOf(amount);
    }
}
