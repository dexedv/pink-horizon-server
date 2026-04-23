package de.pinkhorizon.smash.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player kill combos entirely in memory (no DB persistence).
 * A combo resets when the player dies, leaves, or explicitly resets.
 */
public class ComboManager {

    private final Map<UUID, Integer> combos = new HashMap<>();

    /**
     * Increments the combo counter for the given player by 1.
     *
     * @return the new combo count after incrementing
     */
    public int increment(UUID uuid) {
        int newCount = combos.getOrDefault(uuid, 0) + 1;
        combos.put(uuid, newCount);
        return newCount;
    }

    /**
     * Resets the combo counter for the given player to 0.
     */
    public void reset(UUID uuid) {
        combos.put(uuid, 0);
    }

    /**
     * Returns the current combo count for the player, or 0 if none is tracked.
     */
    public int getCombo(UUID uuid) {
        return combos.getOrDefault(uuid, 0);
    }

    /**
     * Returns the damage multiplier for the current combo.
     * Formula: 1.0 + 0.02 * min(combo, 25) — capped at 1.5 at combo 25.
     */
    public double getMultiplier(UUID uuid) {
        int combo = getCombo(uuid);
        return 1.0 + 0.02 * Math.min(combo, 25);
    }

    /**
     * Returns a formatted combo display string for use in action bars / messages.
     * Shows "§e⚡ §f{n}x Combo" when n >= 3, otherwise an empty string.
     */
    public String getComboDisplay(UUID uuid) {
        int n = getCombo(uuid);
        if (n >= 3) {
            return "§e⚡ §f" + n + "x Combo";
        }
        return "";
    }

    /**
     * Clears combo data for all players (e.g. on plugin shutdown or arena reset).
     */
    public void resetAll() {
        combos.clear();
    }
}
