package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Verwaltet AFK-Belohnungsboxen.
 * Nach X Minuten AFK ohne Bewegung → Belohnungsbox.
 * Max. 1 Box alle 24h.
 */
public class AfkRewardManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Random RNG = new Random();

    /** UUID → letzte bekannte Position (für Bewegungs-Check) */
    private final Map<UUID, Location> lastPosition = new HashMap<>();
    /** UUID → Zeitstempel seit wann AFK (Sekunden) */
    private final Map<UUID, Long> afkSince = new HashMap<>();
    /** UUID → letzter Box-Zeitstempel (Sekunden) */
    private final Map<UUID, Long> lastBoxTime = new HashMap<>();

    private BukkitTask checkTask;

    public AfkRewardManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Jede Minute AFK-Status prüfen (1200 Ticks)
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAfk, 1200L, 1200L);
    }

    public void stop() {
        if (checkTask != null) checkTask.cancel();
    }

    public void onJoin(Player player) {
        lastPosition.put(player.getUniqueId(), player.getLocation());
        afkSince.put(player.getUniqueId(), System.currentTimeMillis() / 1000);
        long lastBox = plugin.getRepository().getLastBoxTime(player.getUniqueId());
        lastBoxTime.put(player.getUniqueId(), lastBox);
    }

    public void onQuit(UUID uuid) {
        lastPosition.remove(uuid);
        afkSince.remove(uuid);
        lastBoxTime.remove(uuid);
    }

    public void onMove(Player player) {
        Location prev = lastPosition.get(player.getUniqueId());
        Location curr = player.getLocation();
        if (prev == null) {
            lastPosition.put(player.getUniqueId(), curr);
            return;
        }
        // Wenn Spieler sich bewegt hat: AFK-Timer zurücksetzen
        if (prev.getBlockX() != curr.getBlockX() || prev.getBlockZ() != curr.getBlockZ()) {
            afkSince.put(player.getUniqueId(), System.currentTimeMillis() / 1000);
            lastPosition.put(player.getUniqueId(), curr);
        }
    }

    private void checkAfk() {
        int requiredMinutes = plugin.getConfig().getInt("afk-reward-minutes", 120);
        long requiredSecs = (long) requiredMinutes * 60;
        long cooldownSecs = 24L * 3600;
        long now = System.currentTimeMillis() / 1000;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long since = afkSince.getOrDefault(uuid, now);
            long afkDuration = now - since;

            if (afkDuration < requiredSecs) continue;

            long lastBox = lastBoxTime.getOrDefault(uuid, 0L);
            if (now - lastBox < cooldownSecs) continue;

            // Belohnungsbox geben!
            giveRewardBox(player);
            lastBoxTime.put(uuid, now);
            plugin.getRepository().setAfkData(uuid, since, now);
            afkSince.put(uuid, now); // Timer zurücksetzen

            PlayerData data = plugin.getPlayerDataMap().get(uuid);
            if (data != null) {
                data.incrementAfkBoxes();
                plugin.getAchievementManager().track(data, "afk_box_10", 1);
            }
        }
    }

    private void giveRewardBox(Player player) {
        Reward reward = rollReward();

        player.sendMessage(MM.deserialize(
                "<gold>✦ AFK-Belohnungsbox! ✦ <yellow>" + reward.description));

        switch (reward.type) {
            case MONEY -> {
                PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
                if (data != null) data.addMoney(reward.moneyAmount);
                player.sendMessage(MM.deserialize("<green>+$" + MoneyManager.formatMoney(reward.moneyAmount) + " erhalten!"));
            }
            case BOOSTER -> {
                PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
                if (data != null && !data.hasActiveBooster()) {
                    data.activateBooster(2.0, 30 * 60); // 30 Minuten x2
                    player.sendMessage(MM.deserialize("<yellow>x2 Booster für 30 Minuten aktiviert!"));
                } else {
                    // Fallback: Geld
                    if (data != null) data.addMoney(10_000);
                    player.sendMessage(MM.deserialize("<green>+$10.000 (Booster bereits aktiv)"));
                }
            }
            case UPGRADE_TOKEN -> {
                // Upgrade-Token als Item (erhöht nächstes Upgrade um 1 Level gratis)
                ItemStack token = createUpgradeToken();
                player.getInventory().addItem(token);
                player.sendMessage(MM.deserialize("<aqua>Upgrade-Token erhalten! Nutze ihn mit /gen upgrade."));
            }
        }
    }

    private Reward rollReward() {
        int roll = RNG.nextInt(100);
        if (roll < 50) {
            // 50% Geld-Belohnung
            long[] amounts = {5_000, 10_000, 25_000, 50_000, 100_000};
            long amount = amounts[RNG.nextInt(amounts.length)];
            return new Reward(RewardType.MONEY, "$" + MoneyManager.formatMoney(amount) + " Geld-Bonus", amount);
        } else if (roll < 80) {
            // 30% Booster
            return new Reward(RewardType.BOOSTER, "x2 Einkommen-Booster (30 Min)", 0);
        } else {
            // 20% Upgrade-Token
            return new Reward(RewardType.UPGRADE_TOKEN, "Generator-Upgrade-Token", 0);
        }
    }

    private ItemStack createUpgradeToken() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        var meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<aqua>✦ Upgrade-Token</aqua>"));
        meta.lore(java.util.List.of(
                MM.deserialize("<gray>Ermöglicht ein kostenloses Generator-Upgrade."),
                MM.deserialize("<yellow>Nutze /gen upgrade um ihn einzulösen.")
        ));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("ph-generators", "upgrade_token"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean hasUpgradeToken(Player player) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("ph-generators", "upgrade_token");
        for (ItemStack item : player.getInventory()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer()
                    .has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeUpgradeToken(Player player) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("ph-generators", "upgrade_token");
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer()
                    .has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private enum RewardType { MONEY, BOOSTER, UPGRADE_TOKEN }
    private record Reward(RewardType type, String description, long moneyAmount) {}
}
