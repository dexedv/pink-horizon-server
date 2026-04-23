package de.pinkhorizon.smash.managers;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * Manages random boss modifier rolls and provides helper queries for combat logic.
 * All state is ephemeral — modifiers are rolled per boss spawn and passed around.
 */
public class BossModifierManager {

    // -------------------------------------------------------------------------
    // Inner enum
    // -------------------------------------------------------------------------

    public enum BossModifier {

        GEPANZERT   ("Gepanzert",      "§7⛏", "§7-30% Schaden an Boss"),
        RASEND      ("Rasend",         "§c⚡", "§c+50% Boss-Geschwindigkeit"),
        REGENERIEREND("Regenerierend", "§a♥", "§a+0.5% HP/s"),
        VERGIFTET   ("Vergiftet",      "§2☠", "§2Spieler vergiftet bei Treffer"),
        GESPIEGELT  ("Gespiegelt",     "§b↩", "§b10% Schaden reflektiert");

        public final String name;
        public final String icon;
        public final String description;

        BossModifier(String name, String icon, String description) {
            this.name        = name;
            this.icon        = icon;
            this.description = description;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private static final BossModifier[] VALUES = BossModifier.values();
    private final Random rng = new Random();

    // -------------------------------------------------------------------------
    // Rolling
    // -------------------------------------------------------------------------

    /**
     * Rolls a set of boss modifiers based on the boss level.
     * <ul>
     *   <li>level &lt; 10  → always empty</li>
     *   <li>level 10–49   → 40% chance of 1 modifier</li>
     *   <li>level 50–99   → 60% chance of 1, 20% chance of 2</li>
     *   <li>level 100+    → 70% chance of 1, 40% chance of 2</li>
     * </ul>
     */
    public Set<BossModifier> rollModifiers(int bossLevel) {
        Set<BossModifier> result = EnumSet.noneOf(BossModifier.class);

        if (bossLevel < 10) {
            return result;
        }

        double roll = rng.nextDouble();

        if (bossLevel >= 100) {
            // 70% chance of at least 1 modifier
            if (roll < 0.70) {
                result.add(pickRandom(null));
                // 40% chance of a second modifier
                if (rng.nextDouble() < 0.40) {
                    BossModifier first = result.iterator().next();
                    BossModifier second = pickRandom(first);
                    result.add(second);
                }
            }
        } else if (bossLevel >= 50) {
            // 60% chance of at least 1 modifier
            if (roll < 0.60) {
                result.add(pickRandom(null));
                // 20% chance of a second modifier
                if (rng.nextDouble() < 0.20) {
                    BossModifier first = result.iterator().next();
                    BossModifier second = pickRandom(first);
                    result.add(second);
                }
            }
        } else {
            // level 10–49: 40% chance of 1 modifier
            if (roll < 0.40) {
                result.add(pickRandom(null));
            }
        }

        return result;
    }

    /** Picks a random modifier, optionally excluding one already chosen. */
    private BossModifier pickRandom(BossModifier exclude) {
        BossModifier picked;
        do {
            picked = VALUES[rng.nextInt(VALUES.length)];
        } while (picked == exclude);
        return picked;
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    /**
     * Builds a single-line modifier bar string, e.g. "§7⛏ §c⚡".
     * Returns an empty string if the set is empty.
     */
    public String buildModifierBar(Set<BossModifier> mods) {
        if (mods == null || mods.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (BossModifier mod : mods) {
            if (!first) sb.append(' ');
            sb.append(mod.icon);
            first = false;
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Convenience checks
    // -------------------------------------------------------------------------

    public boolean isGepanzert(Set<BossModifier> mods) {
        return mods != null && mods.contains(BossModifier.GEPANZERT);
    }

    public boolean isRasend(Set<BossModifier> mods) {
        return mods != null && mods.contains(BossModifier.RASEND);
    }

    public boolean isRegenerierend(Set<BossModifier> mods) {
        return mods != null && mods.contains(BossModifier.REGENERIEREND);
    }

    public boolean isVergiftet(Set<BossModifier> mods) {
        return mods != null && mods.contains(BossModifier.VERGIFTET);
    }

    public boolean isGespiegelt(Set<BossModifier> mods) {
        return mods != null && mods.contains(BossModifier.GESPIEGELT);
    }
}
