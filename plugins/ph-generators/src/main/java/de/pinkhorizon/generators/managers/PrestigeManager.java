package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Verwaltet das Prestige-System.
 */
public class PrestigeManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public PrestigeManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public PrestigeResult tryPrestige(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return PrestigeResult.NO_DATA;

        int maxPrestige = plugin.getConfig().getInt("max-prestige", 50);
        if (data.getPrestige() >= maxPrestige) return PrestigeResult.MAX_REACHED;

        long cost = data.nextPrestigeCost();
        if (data.getMoney() < cost) return PrestigeResult.NO_MONEY;

        data.takeMoney(cost);
        int newPrestige = data.getPrestige() + 1;
        data.setPrestige(newPrestige);

        // Alle Generatoren auf Level 1 zurücksetzen
        for (PlacedGenerator gen : data.getGenerators()) {
            gen.setLevel(1);
            plugin.getRepository().updateGeneratorLevel(gen);
            plugin.getHologramManager().updateHologram(gen, data);
        }

        // Synergien neu berechnen
        plugin.getSynergyManager().recalculateAll(data);

        // Prestige-Effekte
        applyPrestigeEffects(player, newPrestige);

        // Achievement-Tracking
        plugin.getAchievementManager().track(data, "prestige_1", newPrestige);
        plugin.getAchievementManager().track(data, "prestige_10", newPrestige);
        plugin.getAchievementManager().track(data, "prestige_50", newPrestige);

        plugin.getRepository().savePlayer(data);
        return PrestigeResult.SUCCESS;
    }

    private void applyPrestigeEffects(Player player, int prestige) {
        String msg = buildPrestigeMessage(prestige);
        player.sendMessage(MM.deserialize(msg));

        // Partikel-Explosion am Spieler
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.3);

        // Spezielle Meilenstein-Nachrichten
        if (prestige == 10 || prestige == 25 || prestige == 50) {
            org.bukkit.Bukkit.broadcast(MM.deserialize(
                    "<gold><bold>✦ PRESTIGE " + prestige + "</bold></gold> "
                            + "<yellow>" + player.getName()
                            + " hat Prestige " + prestige + " erreicht!"));
        }
    }

    private String buildPrestigeMessage(int prestige) {
        String bonus = String.format("%.0f%%", prestige * 5.0);
        String title = getPrestigeTitle(prestige);
        return "<light_purple><bold>✦ PRESTIGE " + prestige + " ✦</bold></light_purple>\n"
                + "<gray>Permanenter Einkommen-Bonus: <green>+" + bonus + "\n"
                + "<gray>Max. Generator-Level: <aqua>" + (prestige * 10) + "\n"
                + "<yellow>Titel: " + title;
    }

    public String getPrestigeTitle(int prestige) {
        if (prestige >= 50) return "<dark_purple>✦ God of Generators ✦</dark_purple>";
        if (prestige >= 25) return "<light_purple>★ Prestige-Meister ★</light_purple>";
        if (prestige >= 10) return "<red>⚡ Prestige-König ⚡</red>";
        if (prestige >= 5)  return "<gold>♦ Fortgeschrittener ♦</gold>";
        if (prestige >= 1)  return "<green>✔ Aufsteiger</green>";
        return "<gray>Anfänger</gray>";
    }

    public String getPrestigePrefix(int prestige) {
        if (prestige <= 0) return "";
        return "<dark_purple>[P" + prestige + "]</dark_purple> ";
    }

    public enum PrestigeResult {
        SUCCESS, NO_DATA, MAX_REACHED, NO_MONEY
    }
}
