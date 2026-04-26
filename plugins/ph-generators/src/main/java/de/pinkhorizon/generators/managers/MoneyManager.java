package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet den sekündlichen Einkommens-Ticker und Offline-Einkommen.
 */
public class MoneyManager {

    private final PHGenerators plugin;
    private BukkitTask incomeTicker;
    private BukkitTask dbSaveTicker;

    /** Serverweiter Booster (Multiplier + Ablauf-Timestamp) */
    private double serverBoosterMultiplier = 1.0;
    private long serverBoosterExpiry = 0;

    public MoneyManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Income Ticker: jede Sekunde (20 Ticks)
        incomeTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickIncome, 20L, 20L);

        // DB-Save Ticker: alle X Sekunden
        int saveInterval = plugin.getConfig().getInt("db-save-interval", 30);
        dbSaveTicker = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::saveAll, saveInterval * 20L, saveInterval * 20L);
    }

    public void stop() {
        if (incomeTicker != null) incomeTicker.cancel();
        if (dbSaveTicker != null) dbSaveTicker.cancel();
        saveAllSync();
    }

    // ── Income-Tick ──────────────────────────────────────────────────────────

    private void tickIncome() {
        double serverMult = getServerBoosterMultiplier();

        for (Map.Entry<UUID, PlayerData> entry : plugin.getPlayerDataMap().entrySet()) {
            PlayerData data = entry.getValue();
            if (data.getGenerators().isEmpty()) continue;

            double income = calcIncome(data);
            if (income <= 0) continue;

            double totalMult = data.prestigeMultiplier()
                    * data.effectiveBoosterMultiplier()
                    * serverMult
                    * plugin.getSynergyManager().getTotalSynergyMultiplier(data);

            long earned = Math.max(1, Math.round(income * totalMult));
            data.addMoney(earned);

            // ActionBar: aktuelles Einkommen anzeigen
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<green>+$" + formatMoney(earned) + "/s <dark_gray>| <gold>Guthaben: $" + formatMoney(data.getMoney())));
            }
        }
    }

    private double calcIncome(PlayerData data) {
        double sum = 0;
        for (PlacedGenerator gen : data.getGenerators()) {
            sum += gen.incomePerSecond();
        }
        return sum;
    }

    // ── Offline-Einkommen ────────────────────────────────────────────────────

    /**
     * Berechnet und gutschreibt Offline-Einkommen beim Join.
     * Cap: max-offline-hours aus config.
     */
    public long applyOfflineIncome(PlayerData data) {
        long now = System.currentTimeMillis() / 1000;
        long lastSeen = data.getLastSeen();
        if (lastSeen <= 0) {
            data.setLastSeen(now);
            return 0;
        }

        int capHours = plugin.getConfig().getInt("offline-cap-hours", 8);
        long offlineSecs = Math.min(now - lastSeen, (long) capHours * 3600);
        if (offlineSecs <= 0) return 0;

        double incomePerSec = calcIncome(data);
        double totalMult = data.prestigeMultiplier() * data.effectiveBoosterMultiplier();
        long earned = Math.round(incomePerSec * totalMult * offlineSecs);

        if (earned > 0) {
            data.addMoney(earned);
        }
        data.setLastSeen(now);
        return earned;
    }

    // ── Server-Booster ───────────────────────────────────────────────────────

    public void activateServerBooster(double multiplier, int durationMinutes) {
        this.serverBoosterMultiplier = multiplier;
        this.serverBoosterExpiry = System.currentTimeMillis() / 1000 + (long) durationMinutes * 60;

        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                "<gold><bold>✦ SERVER BOOSTER</bold></gold> <yellow>x" + multiplier
                        + " Einkommen für alle für " + durationMinutes + " Minuten! ✦"));
    }

    public double getServerBoosterMultiplier() {
        if (System.currentTimeMillis() / 1000 > serverBoosterExpiry) {
            serverBoosterMultiplier = 1.0;
        }
        return serverBoosterMultiplier;
    }

    public boolean isServerBoosterActive() {
        return System.currentTimeMillis() / 1000 <= serverBoosterExpiry;
    }

    public long getServerBoosterExpiry() {
        return serverBoosterExpiry;
    }

    // ── Persistierung ────────────────────────────────────────────────────────

    private void saveAll() {
        for (PlayerData data : plugin.getPlayerDataMap().values()) {
            plugin.getRepository().savePlayer(data);
        }
    }

    private void saveAllSync() {
        saveAll();
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    public static String formatMoney(long amount) {
        if (amount >= 1_000_000_000L) return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000L) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000L) return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }
}
