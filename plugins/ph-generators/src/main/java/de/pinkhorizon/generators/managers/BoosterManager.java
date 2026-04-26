package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.data.StoredBooster;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Verwaltet persönliche und serverweite Booster.
 */
public class BoosterManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BoosterManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Persönlicher Booster ─────────────────────────────────────────────────

    /**
     * Kauft einen persönlichen Booster für den Spieler.
     * @param multiplier  z.B. 2.0 für x2
     * @param durationMin Dauer in Minuten
     * @param cost        Kosten in $
     */
    public BuyResult buyBooster(Player player, double multiplier, int durationMin, long cost) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return BuyResult.NO_DATA;
        if (data.hasActiveBooster()) return BuyResult.ALREADY_ACTIVE;
        if (!data.takeMoney(cost)) return BuyResult.NO_MONEY;

        data.activateBooster(multiplier, durationMin * 60);

        long expiresAt = data.getBoosterExpiry();
        player.sendMessage(MM.deserialize(
                "<green>✔ Booster aktiviert! <white>x" + multiplier
                        + " für " + durationMin + " Minuten."));
        return BuyResult.SUCCESS;
    }

    /**
     * Gibt einem Spieler einen gespeicherten Booster (Tebex-Kauf oder Admin-Befehl).
     * Der Booster wird nicht sofort aktiviert, sondern im Inventar gespeichert.
     */
    public void giveBooster(Player player, double multiplier, int durationMinutes) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) {
            plugin.getLogger().warning("[BoosterManager] giveBooster: PlayerData für " + player.getName() + " nicht geladen");
            return;
        }
        data.addStoredBooster(new StoredBooster(multiplier, durationMinutes));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getRepository().savePlayer(data));
        player.sendMessage(MM.deserialize(
                "<green>✦ Du hast einen <gold>x" + multiplier + " Booster <green>erhalten!\n"
                + "<gray>Aktivieren im Booster-Menü: <yellow>/gen booster"));
    }

    /**
     * Aktiviert einen serverweiten Booster (Admin-Befehl oder Vote-Event).
     */
    public void activateServerBooster(double multiplier, int durationMin) {
        plugin.getMoneyManager().activateServerBooster(multiplier, durationMin);
    }

    // ── Status ───────────────────────────────────────────────────────────────

    public String getBoosterStatus(PlayerData data) {
        if (!data.hasActiveBooster()) return "<gray>Kein aktiver Booster";
        long remaining = data.getBoosterExpiry() - System.currentTimeMillis() / 1000;
        long min = remaining / 60;
        long sec = remaining % 60;
        return "<yellow>x" + data.getBoosterMultiplier()
                + " Booster <gray>– noch <white>" + min + "m " + sec + "s";
    }

    public enum BuyResult {
        SUCCESS, NO_DATA, ALREADY_ACTIVE, NO_MONEY
    }
}
