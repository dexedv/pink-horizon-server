package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mining-Block: fester AMETHYST_BLOCK neben dem Insel-Spawn.
 * Spieler schlagen drauf → verdienen Geld + zufällige Shard-Drops.
 * Shards können für Upgrades verwendet werden (/gen mining upgrade).
 */
public class MiningBlockManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Material BLOCK_MATERIAL = Material.AMETHYST_BLOCK;
    private static final Material SHARD_MATERIAL = Material.AMETHYST_SHARD;

    private final PHGenerators plugin;
    private final NamespacedKey shardKey;

    /** Cooldown: UUID → letzter Hit-Timestamp in ms */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MiningBlockManager(PHGenerators plugin) {
        this.plugin = plugin;
        this.shardKey = new NamespacedKey(plugin, "mining_shard");
    }

    // ── Block platzieren ─────────────────────────────────────────────────────

    /**
     * Stellt sicher, dass der Mining-Block auf der Insel existiert.
     * Wird nach dem Teleportieren des Spielers aufgerufen.
     */
    public void ensureMiningBlock(World world) {
        Location loc = getBlockLocation(world);
        if (loc == null) return;
        world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, true);
        if (loc.getBlock().getType() != BLOCK_MATERIAL) {
            loc.getBlock().setType(BLOCK_MATERIAL);
        }
    }

    // ── Event ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != BLOCK_MATERIAL) return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        // Nur auf eigener Insel
        if (!plugin.getIslandWorldManager().isOwnIsland(world, player.getUniqueId())) return;

        // Block muss am erwarteten Ort sein
        Location expected = getBlockLocation(world);
        if (expected == null) return;
        Location clicked = event.getClickedBlock().getLocation();
        if (clicked.getBlockX() != expected.getBlockX()
                || clicked.getBlockY() != expected.getBlockY()
                || clicked.getBlockZ() != expected.getBlockZ()) return;

        event.setCancelled(true);

        // Cooldown prüfen
        long cooldownMs = plugin.getConfig().getLong("mining-block.cooldown-ms", 500);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        cooldowns.put(player.getUniqueId(), now);

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        int level = data.getMiningLevel();
        long baseMoney = plugin.getConfig().getLong("mining-block.base-money", 5);
        long earned = baseMoney * level;
        data.addMoney(earned);

        // Partikel + Sound
        world.spawnParticle(Particle.BLOCK_CRUMBLE, clicked.add(0.5, 0.5, 0.5), 8,
                0.3, 0.3, 0.3, 0, BLOCK_MATERIAL.createBlockData());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.0f + (level * 0.02f));

        // Nachricht (nicht jedes Mal – nur wenn shard dropt)
        double shardChance = plugin.getConfig().getDouble("mining-block.shard-chance", 0.10)
                + (level * 0.005);
        if (ThreadLocalRandom.current().nextDouble() < shardChance) {
            // Shard droppen
            ItemStack shard = createShardItem(1);
            var leftover = player.getInventory().addItem(shard);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover.values().iterator().next());
            }
            player.sendMessage(MM.deserialize(
                    "<light_purple>✦ Mining-Shard erhalten! <gray>(" + data.getShards() + " total)"
                    + " <dark_gray>| <green>+$" + MoneyManager.formatMoney(earned)));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.2f);
        }
    }

    // ── Upgrade ──────────────────────────────────────────────────────────────

    public enum UpgradeResult { SUCCESS, MAX_LEVEL, NOT_ENOUGH_SHARDS }

    public UpgradeResult upgrade(Player player, PlayerData data) {
        int maxLevel = plugin.getConfig().getInt("mining-block.max-level", 50);
        if (data.getMiningLevel() >= maxLevel) return UpgradeResult.MAX_LEVEL;

        int shardsNeeded = data.getMiningLevel()
                * plugin.getConfig().getInt("mining-block.upgrade-shards", 5);

        // Shards aus Inventar zählen + entfernen
        int shardsInInv = countShards(player);
        if (shardsInInv < shardsNeeded) return UpgradeResult.NOT_ENOUGH_SHARDS;

        removeShards(player, shardsNeeded);
        data.setMiningLevel(data.getMiningLevel() + 1);
        return UpgradeResult.SUCCESS;
    }

    // ── Shard-Item ───────────────────────────────────────────────────────────

    public ItemStack createShardItem(int amount) {
        ItemStack item = new ItemStack(SHARD_MATERIAL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<light_purple>✦ Mining-Shard"));
        meta.lore(List.of(
                MM.deserialize("<gray>Upgrade deinen Mining-Block"),
                MM.deserialize("<dark_gray>/gen mining upgrade")
        ));
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isShard(ItemStack item) {
        if (item == null || item.getType() != SHARD_MATERIAL) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(shardKey, PersistentDataType.BYTE);
    }

    private int countShards(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isShard(item)) count += item.getAmount();
        }
        return count;
    }

    private void removeShards(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isShard(item)) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private Location getBlockLocation(World world) {
        double spawnX = plugin.getConfig().getDouble("island.spawn-x", 0.5);
        double spawnY = plugin.getConfig().getDouble("island.spawn-y", 64.0);
        double spawnZ = plugin.getConfig().getDouble("island.spawn-z", 0.5);
        int dx = plugin.getConfig().getInt("mining-block.offset-x", 3);
        return new Location(world, (int) spawnX + dx, (int) spawnY, (int) spawnZ);
    }

    public String getInfo(PlayerData data) {
        int level     = data.getMiningLevel();
        int maxLevel  = plugin.getConfig().getInt("mining-block.max-level", 50);
        long baseMoney = plugin.getConfig().getLong("mining-block.base-money", 5);
        long earned   = baseMoney * level;
        int shardsNeeded = level * plugin.getConfig().getInt("mining-block.upgrade-shards", 5);
        double shardChance = (plugin.getConfig().getDouble("mining-block.shard-chance", 0.10)
                + (level * 0.005)) * 100;

        return "<gold>━━ Mining-Block ━━\n"
                + "<gray>Level: <white>" + level + " <dark_gray>/ " + maxLevel + "\n"
                + "<gray>Geld pro Schlag: <green>$" + MoneyManager.formatMoney(earned) + "\n"
                + "<gray>Shard-Chance: <light_purple>" + String.format("%.1f", shardChance) + "%\n"
                + (level < maxLevel
                    ? "<gray>Upgrade-Kosten: <yellow>" + shardsNeeded + " Shards"
                    : "<gold>MAX LEVEL erreicht!");
    }
}
