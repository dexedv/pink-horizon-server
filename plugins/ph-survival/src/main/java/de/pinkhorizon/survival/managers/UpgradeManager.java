package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UpgradeManager {

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;

    private final Map<UUID, Boolean> keepInventoryPerm = new HashMap<>();
    private final Map<UUID, Boolean> flyPerm           = new HashMap<>();
    private final Map<UUID, Long>    kiExpiry          = new HashMap<>(); // ms timestamp
    private final Map<UUID, Long>    flyExpiry         = new HashMap<>(); // ms timestamp
    private final Map<UUID, Integer> extraClaims       = new HashMap<>();

    public UpgradeManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    // ── KeepInventory ────────────────────────────────────────────────────

    public boolean hasActiveKI(UUID uuid) {
        if (keepInventoryPerm.getOrDefault(uuid, false)) return true;
        return isActive(kiExpiry, uuid);
    }

    public boolean hasPermKI(UUID uuid) {
        return keepInventoryPerm.getOrDefault(uuid, false);
    }

    public long getKiRemainingMs(UUID uuid) {
        if (keepInventoryPerm.getOrDefault(uuid, false)) return Long.MAX_VALUE;
        return remainingMs(kiExpiry, uuid);
    }

    public void givePermKI(UUID uuid) {
        keepInventoryPerm.put(uuid, true);
        data.set("upgrades." + uuid + ".keepInventory", true);
        save();
    }

    /** Fügt Temp-KI hinzu. Stapelt sich mit bestehender Zeit. */
    public void grantTempKI(UUID uuid, long durationMs) {
        long current = System.currentTimeMillis();
        long existing = kiExpiry.getOrDefault(uuid, current);
        long newExpiry = Math.max(existing, current) + durationMs;
        kiExpiry.put(uuid, newExpiry);
        data.set("upgrades." + uuid + ".kiExpiry", newExpiry);
        save();
    }

    // ── Fly ──────────────────────────────────────────────────────────────

    public boolean hasPermFly(UUID uuid) {
        return flyPerm.getOrDefault(uuid, false);
    }

    public void givePermFly(Player player) {
        flyPerm.put(player.getUniqueId(), true);
        data.set("upgrades." + player.getUniqueId() + ".flyPerm", true);
        save();
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    public boolean hasActiveFly(UUID uuid) {
        return flyPerm.getOrDefault(uuid, false) || isActive(flyExpiry, uuid);
    }

    public long getFlyRemainingMs(UUID uuid) {
        return remainingMs(flyExpiry, uuid);
    }

    public void grantFly(Player player, long durationMs) {
        long current   = System.currentTimeMillis();
        long existing  = flyExpiry.getOrDefault(player.getUniqueId(), current);
        long newExpiry = Math.max(existing, current) + durationMs;

        flyExpiry.put(player.getUniqueId(), newExpiry);
        data.set("upgrades." + player.getUniqueId() + ".flyExpiry", newExpiry);
        save();

        player.setAllowFlight(true);
        player.setFlying(true);

        long ticks = (newExpiry - System.currentTimeMillis()) / 50;
        if (!flyPerm.getOrDefault(player.getUniqueId(), false)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!hasActiveFly(player.getUniqueId())) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage("§cDein Flug-Booster ist abgelaufen!");
                }
            }, ticks);
        }
    }

    /** Beim Login aktiven Fly wiederherstellen. */
    public void restoreFly(Player player) {
        UUID uuid = player.getUniqueId();
        if (flyPerm.getOrDefault(uuid, false)) {
            player.setAllowFlight(true);
            player.sendMessage("§aFly §ldauerhaft§r§a aktiv.");
            return;
        }
        if (isActive(flyExpiry, uuid)) {
            player.setAllowFlight(true);
            long mins = getFlyRemainingMs(uuid) / 60_000;
            player.sendMessage("§aFly aktiv – noch §f" + mins + " §aMinuten.");

            long ticks = getFlyRemainingMs(uuid) / 50;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!hasActiveFly(uuid)) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage("§cDein Flug-Booster ist abgelaufen!");
                }
            }, ticks);
        }
    }

    // ── Extra Claims ─────────────────────────────────────────────────────

    public int getExtraClaims(UUID uuid) {
        return extraClaims.getOrDefault(uuid, 0);
    }

    public void addExtraClaims(UUID uuid, int amount) {
        int current = extraClaims.getOrDefault(uuid, 0);
        extraClaims.put(uuid, current + amount);
        data.set("upgrades." + uuid + ".extraClaims", current + amount);
        save();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean isActive(Map<UUID, Long> map, UUID uuid) {
        long expiry = map.getOrDefault(uuid, 0L);
        if (expiry <= 0) return false;
        if (System.currentTimeMillis() > expiry) {
            map.remove(uuid);
            return false;
        }
        return true;
    }

    private long remainingMs(Map<UUID, Long> map, UUID uuid) {
        long expiry = map.getOrDefault(uuid, 0L);
        long rem = expiry - System.currentTimeMillis();
        return Math.max(0, rem);
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "upgrades.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("upgrades")) return;
        for (String uuidStr : data.getConfigurationSection("upgrades").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                keepInventoryPerm.put(uuid, data.getBoolean("upgrades." + uuidStr + ".keepInventory", false));
                flyPerm.put(uuid,           data.getBoolean("upgrades." + uuidStr + ".flyPerm",        false));
                extraClaims.put(uuid,       data.getInt("upgrades."     + uuidStr + ".extraClaims",    0));

                long ki  = data.getLong("upgrades." + uuidStr + ".kiExpiry",  0L);
                long fly = data.getLong("upgrades." + uuidStr + ".flyExpiry", 0L);
                long now = System.currentTimeMillis();
                if (ki  > now) kiExpiry.put(uuid, ki);
                if (fly > now) flyExpiry.put(uuid, fly);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Upgrades save failed: " + e.getMessage()); }
    }
}
