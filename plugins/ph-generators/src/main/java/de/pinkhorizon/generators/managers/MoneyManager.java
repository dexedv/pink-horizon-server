package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
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

    /** Fraktions-Akkumulator für Shard-Generator (sub-1-shard-per-second Raten) */
    private final Map<UUID, Double> shardAccumulator = new HashMap<>();

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

    /** Akkumuliertes Einkommen für Income-Log (UUID → earned since last log flush) */
    private final Map<UUID, Long> incomeAccumulator = new java.util.concurrent.ConcurrentHashMap<>();
    private long lastLogFlushHour = 0;

    private void tickIncome() {
        double serverMult = getServerBoosterMultiplier();
        long nowHour = System.currentTimeMillis() / 3_600_000L;

        // Flush income log every hour
        if (nowHour > lastLogFlushHour) {
            flushIncomeLog();
            lastLogFlushHour = nowHour;
        }

        long petCoinsPerLevel = plugin.getConfig().getLong("pet.coins-per-level", 2);

        for (Map.Entry<UUID, PlayerData> entry : plugin.getPlayerDataMap().entrySet()) {
            PlayerData data = entry.getValue();

            // Pet passives Einkommen (unabhängig von Generatoren)
            long petPassive = data.getPetPassiveIncome(petCoinsPerLevel);
            if (petPassive > 0) data.addMoney(petPassive);

            // Shard-Generator Income (level-basiert, Akkumulator für sub-1/s Raten)
            double shardIncome = calcShardIncome(data);
            if (shardIncome > 0) {
                UUID uuid = entry.getKey();
                double acc = shardAccumulator.getOrDefault(uuid, 0.0) + shardIncome;
                int whole = (int) acc;
                shardAccumulator.put(uuid, acc - whole);
                if (whole > 0) data.addShards(whole);
            }

            if (data.getGenerators().isEmpty()) continue;

            double income = calcIncome(data);
            if (income <= 0) continue;

            double eventMult = plugin.getEventManager() != null
                    ? plugin.getEventManager().getIncomeMultiplier() : 1.0;
            double talentMult = plugin.getTalentManager() != null
                    ? plugin.getTalentManager().getIncomeMultiplier(data) : 1.0;
            double guildMult = plugin.getGuildManager().getGuildPerkMultiplier(entry.getKey());

            double totalMult = data.prestigeMultiplier()
                    * data.effectiveBoosterMultiplier()
                    * serverMult
                    * plugin.getSynergyManager().getTotalSynergyMultiplier(data)
                    * eventMult
                    * talentMult
                    * guildMult
                    * data.getRankMultiplier()
                    * data.getPetIncomeMultiplier();

            long earned = Math.max(1, Math.round(income * totalMult));
            data.addMoney(earned);

            // Accumulate for income log
            incomeAccumulator.merge(entry.getKey(), earned, Long::sum);

            // Milestone-Check
            if (plugin.getMilestoneManager() != null) {
                plugin.getMilestoneManager().check(entry.getKey(), data);
            }

            // Auto-Upgrade
            if (data.isAutoUpgrade()) {
                doAutoUpgrade(data);
            }

            // ActionBar: aktuelles Einkommen anzeigen
            Player player = Bukkit.getPlayer(entry.getKey());

            // Auto-Tier-Upgrade (Nexus oder Admin)
            if (data.isAutoTierUpgrade() && (data.rankAllowsTierUpgradeAll()
                    || (player != null && player.hasPermission("ph.generators.admin")))) {
                doAutoTierUpgrade(data);
            }
            if (player != null) {
                String autoTag = data.isAutoUpgrade() ? " <aqua>[AUTO]" : "";
                String tierTag = data.isAutoTierUpgrade() ? " <gold>[TIER-AUTO]" : "";
                player.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<green>+$" + formatMoney(earned) + "/s" + autoTag + tierTag + " <dark_gray>| <gold>$" + formatMoney(data.getMoney())));
            }
        }
    }

    private void doAutoUpgrade(PlayerData data) {
        // Find cheapest upgradeable generator and upgrade it once
        PlacedGenerator cheapest = null;
        long lowestCost = Long.MAX_VALUE;
        int maxLevel = data.maxGeneratorLevel();
        for (PlacedGenerator gen : data.getGenerators()) {
            if (gen.getLevel() >= maxLevel) continue;
            long cost = gen.upgradeCost();
            if (cost < lowestCost && data.getMoney() >= cost) {
                lowestCost = cost;
                cheapest = gen;
            }
        }
        if (cheapest != null) {
            data.takeMoney(lowestCost);
            cheapest.setLevel(cheapest.getLevel() + 1);
            data.incrementUpgrades();
            final PlacedGenerator g = cheapest;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getRepository().updateGeneratorLevel(g);
                plugin.getHologramManager().updateHologram(g, data);
            });
        }
    }

    private void doAutoTierUpgrade(PlayerData data) {
        int maxLevel = data.maxGeneratorLevel();
        for (PlacedGenerator gen : data.getGenerators()) {
            if (gen.getLevel() < maxLevel) continue;
            de.pinkhorizon.generators.GeneratorType nextTier = gen.getType().getNextTier();
            if (nextTier == null) continue;
            long cost = gen.getType().getTierUpgradeCost();
            if (cost < 0 || data.getMoney() < cost) continue;

            data.takeMoney(cost);
            gen.setType(nextTier);
            gen.setLevel(1);

            // Block + Hologramm + Synergie aktualisieren (bereits auf Main-Thread)
            org.bukkit.World world = Bukkit.getWorld(gen.getWorld());
            if (world != null) {
                world.getBlockAt(gen.getX(), gen.getY(), gen.getZ()).setType(nextTier.getBlock());
            }
            plugin.getRepository().updateGeneratorLevel(gen);
            plugin.getHologramManager().updateHologram(gen, data);
            plugin.getSynergyManager().recalculate(gen, data);
            break; // nur ein Tier-Upgrade pro Tick
        }
    }

    private void flushIncomeLog() {
        if (incomeAccumulator.isEmpty()) return;
        long hour = System.currentTimeMillis() / 3_600_000L;
        Map<UUID, Long> snapshot = new java.util.HashMap<>(incomeAccumulator);
        incomeAccumulator.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<UUID, Long> e : snapshot.entrySet()) {
                plugin.getRepository().addIncomeLog(e.getKey(), hour, e.getValue());
            }
        });
    }

    private double calcIncome(PlayerData data) {
        double sum = 0;
        for (PlacedGenerator gen : data.getGenerators()) {
            if (!gen.getType().isShardGenerator()) sum += gen.incomePerSecond();
        }
        return sum;
    }

    /**
     * Shards/s des Shard-Generators.
     * Formel: level × (maxShardsPerHour / 100) / 3600
     * → Level 100 = maxShardsPerHour/h, Level 1 = maxShardsPerHour/100/h
     */
    private double calcShardIncome(PlayerData data) {
        PlacedGenerator shardGen = data.getGenerators().stream()
                .filter(g -> g.getType().isShardGenerator())
                .findFirst().orElse(null);
        if (shardGen == null) return 0;
        int maxPerHour = plugin.getConfig().getInt("shard-generator.max-shards-per-hour", 1000);
        return shardGen.getLevel() * (maxPerHour / 100.0) / 3600.0;
    }

    /** Shards/Stunde für Anzeige in GUI/Holo */
    public double getShardIncomePerHour(PlayerData data) {
        PlacedGenerator shardGen = data.getGenerators().stream()
                .filter(g -> g.getType().isShardGenerator())
                .findFirst().orElse(null);
        if (shardGen == null) return 0;
        int maxPerHour = plugin.getConfig().getInt("shard-generator.max-shards-per-hour", 1000);
        return shardGen.getLevel() * (maxPerHour / 100.0);
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
        if (amount >= 1_000_000_000_000L) return String.format("%.1fT", amount / 1_000_000_000_000.0);
        if (amount >= 1_000_000_000L)     return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000L)         return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000L)             return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }
}
