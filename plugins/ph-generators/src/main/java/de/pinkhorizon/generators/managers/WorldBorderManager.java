package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Verwaltet die Weltgrenze jeder Spieler-Insel.
 * Standard: 40×40 Blöcke, erweiterbar mit Geld.
 */
public class WorldBorderManager {

    private final PHGenerators plugin;

    /**
     * Verfügbare Border-Stufen: {Größe, Kosten}.
     * Größe 40 ist kostenlos (Standard).
     */
    public static final long[][] TIERS = {
            {  40,          0L },
            {  60,     50_000L },
            {  80,    200_000L },
            { 100,    500_000L },
            { 150,  2_000_000L },
            { 200, 10_000_000L },
    };

    public WorldBorderManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Border setzen ────────────────────────────────────────────────────────

    /** Setzt die Weltgrenze für die Inselwelt des Spielers. */
    public void applyBorder(World world, PlayerData data) {
        double cx = plugin.getConfig().getDouble("island.spawn-x", 0.5);
        double cz = plugin.getConfig().getDouble("island.spawn-z", 0.5);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(cx, cz);
        border.setSize(data.getBorderSize());
        border.setWarningDistance(5);
        border.setWarningTime(5);
        border.setDamageBuffer(1.0);
        border.setDamageAmount(1.0);
    }

    // ── Erweiterung kaufen ───────────────────────────────────────────────────

    public enum ExpandResult {
        SUCCESS, NO_MONEY, ALREADY_MAX, NO_DATA
    }

    /**
     * Kauft die nächste Border-Stufe für den Spieler.
     */
    public ExpandResult expand(PlayerData data) {
        long[] next = getNextTier(data.getBorderSize());
        if (next == null) return ExpandResult.ALREADY_MAX;
        if (!data.takeMoney(next[1])) return ExpandResult.NO_MONEY;

        data.setBorderSize((int) next[0]);
        plugin.getRepository().savePlayer(data);

        // Border sofort in der geladenen Welt aktualisieren
        String worldName = plugin.getIslandWorldManager().getWorldName(data.getUuid());
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) applyBorder(world, data);

        return ExpandResult.SUCCESS;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    /** Gibt die nächste Tier-Stufe zurück, oder null wenn bereits max. */
    public long[] getNextTier(int currentSize) {
        for (long[] tier : TIERS) {
            if (tier[0] > currentSize) return tier;
        }
        return null;
    }

    /** Gibt den Index des aktuellen Tiers zurück (0-basiert). */
    public int getCurrentTierIndex(int size) {
        for (int i = TIERS.length - 1; i >= 0; i--) {
            if (size >= TIERS[i][0]) return i;
        }
        return 0;
    }
}
