package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Berechnet Synergie-Boni zwischen benachbarten Generatoren.
 * Synergie-Paare: Iron+Gold, Gold+Diamond, Diamond+Netherite
 * Jedes aktive Paar: +15% Einkommen für beide Generatoren.
 *
 * Implementierung: Synergien sind ein globaler Bonus auf das Gesamteinkommen
 * des Spielers, da physische Nachbarschaft auf dem Server komplex zu tracken wäre
 * und AFK-Spieler oft ihre Inseln nicht laden.
 * Stattdessen: Synergie-Bonus wenn der Spieler beide Typen besitzt.
 */
public class SynergyManager {

    private final PHGenerators plugin;

    /** UUID → aktueller Synergie-Multiplikator (>= 1.0) */
    private final Map<UUID, Double> synergyCache = new HashMap<>();

    // Synergie-Paare: beide Typen müssen vorhanden sein für den Bonus
    private static final SynergyPair[] PAIRS = {
            new SynergyPair(GeneratorType.IRON,     GeneratorType.GOLD,      0.15),
            new SynergyPair(GeneratorType.GOLD,     GeneratorType.DIAMOND,   0.15),
            new SynergyPair(GeneratorType.DIAMOND,  GeneratorType.NETHERITE, 0.15),
            new SynergyPair(GeneratorType.COBBLESTONE, GeneratorType.IRON,   0.10),
            new SynergyPair(GeneratorType.LAPIS,    GeneratorType.DIAMOND,   0.12),
            // Mega-Synergien
            new SynergyPair(GeneratorType.MEGA_IRON,     GeneratorType.MEGA_GOLD,      0.25),
            new SynergyPair(GeneratorType.MEGA_DIAMOND,  GeneratorType.MEGA_NETHERITE, 0.25),
    };

    public SynergyManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void recalculate(PlacedGenerator changed, PlayerData data) {
        recalculateAll(data);
    }

    public void recalculateAll(PlayerData data) {
        double totalBonus = 1.0;
        for (SynergyPair pair : PAIRS) {
            if (hasType(data, pair.a) && hasType(data, pair.b)) {
                totalBonus += pair.bonus;
            }
        }
        synergyCache.put(data.getUuid(), totalBonus);
    }

    public double getTotalSynergyMultiplier(PlayerData data) {
        return synergyCache.getOrDefault(data.getUuid(), 1.0);
    }

    public void remove(UUID uuid) {
        synergyCache.remove(uuid);
    }

    /** Liefert lesbare Synergie-Info für den Spieler */
    public String getSynergyInfo(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        for (SynergyPair pair : PAIRS) {
            boolean active = hasType(data, pair.a) && hasType(data, pair.b);
            String status = active ? "<green>✔</green>" : "<red>✗</red>";
            sb.append("\n ").append(status).append(" ")
                    .append(pair.a.getDisplayName()).append(" + ").append(pair.b.getDisplayName())
                    .append(" <gray>(+").append((int) (pair.bonus * 100)).append("%)");
        }
        double total = getTotalSynergyMultiplier(data);
        return "<yellow>Synergien (gesamt x" + String.format("%.2f", total) + "):" + sb;
    }

    private boolean hasType(PlayerData data, GeneratorType type) {
        return data.getGenerators().stream().anyMatch(g -> g.getType() == type);
    }

    private record SynergyPair(GeneratorType a, GeneratorType b, double bonus) {}
}
