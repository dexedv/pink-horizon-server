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
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
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
 * Spieler schlagen drauf → verdienen Geld + virtuelle Shards (in PlayerData, nicht im Inventar).
 * Shards können für Upgrades verwendet werden (Sneak+Rechtsklick → GUI).
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
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != BLOCK_MATERIAL) return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        // Sneak + Rechtsklick → Upgrade-GUI öffnen
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            if (!plugin.getIslandWorldManager().isOwnIsland(world, player.getUniqueId())) return;
            Location expected = getBlockLocation(world);
            if (expected == null) return;
            Location clicked = event.getClickedBlock().getLocation();
            if (clicked.getBlockX() != expected.getBlockX()
                    || clicked.getBlockY() != expected.getBlockY()
                    || clicked.getBlockZ() != expected.getBlockZ()) return;
            event.setCancelled(true);
            plugin.getMiningUpgradeGUI().open(player);
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

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

        // Mining-Spitzhacke muss in der Hand sein
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!plugin.getNavigatorGUI().isMiningPickaxe(hand)) {
            player.sendMessage(MM.deserialize(
                    "<red>Du brauchst die <aqua>Mining-Spitzhacke <red>(Slot 6) um den Block abzubauen!"));
            return;
        }

        processMine(player, world, clicked);
    }

    /** Gemeinsame Mining-Logik für Einzel- und Dauerklick */
    private void processMine(Player player, World world, Location blockLoc) {
        // Cooldown prüfen
        long cooldownMs = plugin.getConfig().getLong("mining-block.cooldown-ms", 500);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        cooldowns.put(player.getUniqueId(), now);

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        int level        = data.getMiningLevel();
        int pickaxeLevel = data.getMiningPickaxeLevel();
        double pickaxeMult = 1.0 + (pickaxeLevel - 1) * 0.15;

        long baseMoney = plugin.getConfig().getLong("mining-block.base-money", 5);
        long earned = (long) (baseMoney * level * pickaxeMult);
        data.addMoney(earned);

        // Partikel + Sound
        world.spawnParticle(Particle.BLOCK_CRUMBLE, blockLoc.clone().add(0.5, 0.5, 0.5), 8,
                0.3, 0.3, 0.3, 0, BLOCK_MATERIAL.createBlockData());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.0f + (level * 0.02f));

        // Shard-Drop (virtuell, kein Inventar-Item)
        double shardChance = plugin.getConfig().getDouble("mining-block.shard-chance", 0.10)
                + (level * 0.005) + (pickaxeLevel * 0.01);
        if (ThreadLocalRandom.current().nextDouble() < shardChance) {
            int shards = level >= 90 ? 4 : level >= 60 ? 3 : level >= 30 ? 2 : 1;
            data.addShards(shards);
            String shardsLabel = shards == 1 ? "+1" : (shards == 2 ? "✦✦ +2" : shards == 3 ? "✦✦✦ +3" : "✦✦✦✦ +4");
            player.sendMessage(MM.deserialize(
                    "<light_purple>" + shardsLabel + " Mining-Shard! <gray>(" + data.getShards() + " total)"
                    + " <dark_gray>| <green>+$" + MoneyManager.formatMoney(earned)));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.0f + (shards * 0.1f));
        }
    }

    // ── BlockDamage (verhindert Abbauen) ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getBlock().getType() != BLOCK_MATERIAL) return;
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!plugin.getIslandWorldManager().isOwnIsland(world, player.getUniqueId())) return;
        Location expected = getBlockLocation(world);
        if (expected == null) return;
        Location blockLoc = event.getBlock().getLocation();
        if (blockLoc.getBlockX() != expected.getBlockX()
                || blockLoc.getBlockY() != expected.getBlockY()
                || blockLoc.getBlockZ() != expected.getBlockZ()) return;
        event.setCancelled(true); // Block soll niemals abgebaut werden
    }

    // ── PlayerAnimation (Dauerklick / Arm-Swing) ─────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!plugin.getIslandWorldManager().isOwnIsland(world, player.getUniqueId())) return;

        // Nur mit Mining-Spitzhacke in der Hand
        if (!plugin.getNavigatorGUI().isMiningPickaxe(player.getInventory().getItemInMainHand())) return;

        // Block in Blickrichtung ermitteln (max 5 Blöcke)
        org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != BLOCK_MATERIAL) return;

        Location expected = getBlockLocation(world);
        if (expected == null) return;
        Location blockLoc = target.getLocation();
        if (blockLoc.getBlockX() != expected.getBlockX()
                || blockLoc.getBlockY() != expected.getBlockY()
                || blockLoc.getBlockZ() != expected.getBlockZ()) return;

        processMine(player, world, blockLoc);
    }

    // ── Upgrade ──────────────────────────────────────────────────────────────

    public enum UpgradeResult { SUCCESS, MAX_LEVEL, NOT_ENOUGH_SHARDS }

    /** Upgrade des Mining-Blocks (Shards werden aus PlayerData abgezogen) */
    public UpgradeResult upgrade(Player player, PlayerData data) {
        int maxLevel = plugin.getConfig().getInt("mining-block.max-level", 50);
        if (data.getMiningLevel() >= maxLevel) return UpgradeResult.MAX_LEVEL;

        int shardsNeeded = data.getMiningLevel()
                * plugin.getConfig().getInt("mining-block.upgrade-shards", 5);
        if (data.getShards() < shardsNeeded) return UpgradeResult.NOT_ENOUGH_SHARDS;

        data.setShards(data.getShards() - shardsNeeded);
        data.setMiningLevel(data.getMiningLevel() + 1);
        return UpgradeResult.SUCCESS;
    }

    /** Upgrade der Mining-Spitzhacke */
    public UpgradeResult upgradePickaxe(Player player, PlayerData data) {
        int maxLevel = plugin.getConfig().getInt("mining-block.pickaxe-max-level", 30);
        if (data.getMiningPickaxeLevel() >= maxLevel) return UpgradeResult.MAX_LEVEL;

        int shardsNeeded = data.getMiningPickaxeLevel()
                * plugin.getConfig().getInt("mining-block.pickaxe-upgrade-shards", 8);
        if (data.getShards() < shardsNeeded) return UpgradeResult.NOT_ENOUGH_SHARDS;

        data.setShards(data.getShards() - shardsNeeded);
        data.setMiningPickaxeLevel(data.getMiningPickaxeLevel() + 1);

        // Pickaxe im Inventar aktualisieren
        player.getInventory().setItem(6,
                plugin.getNavigatorGUI().buildMiningPickaxe(data));
        return UpgradeResult.SUCCESS;
    }

    public int shardsNeededForPickaxe(PlayerData data) {
        return data.getMiningPickaxeLevel()
                * plugin.getConfig().getInt("mining-block.pickaxe-upgrade-shards", 8);
    }

    // ── Shard-Item (nur für GUI-Anzeige, wird NICHT ins Inventar gegeben) ────

    public ItemStack createShardItem(int amount) {
        ItemStack item = new ItemStack(SHARD_MATERIAL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<light_purple>✦ Mining-Shard"));
        meta.lore(List.of(
                MM.deserialize("<gray>Schlag den Mining-Block um Shards zu sammeln"),
                MM.deserialize("<dark_gray>Sneak + Rechtsklick → Upgrade-Menü")
        ));
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    public Location getBlockLocation(World world) {
        double spawnX = plugin.getConfig().getDouble("island.spawn-x", 0.5);
        double spawnY = plugin.getConfig().getDouble("island.spawn-y", 64.0);
        double spawnZ = plugin.getConfig().getDouble("island.spawn-z", 0.5);
        int dx = plugin.getConfig().getInt("mining-block.offset-x", 3);
        return new Location(world, (int) spawnX + dx, (int) spawnY, (int) spawnZ);
    }

    public String getInfo(PlayerData data) {
        int level         = data.getMiningLevel();
        int maxLevel      = plugin.getConfig().getInt("mining-block.max-level", 50);
        int pickaxeLvl    = data.getMiningPickaxeLevel();
        int maxPickaxeLvl = plugin.getConfig().getInt("mining-block.pickaxe-max-level", 30);
        long baseMoney    = plugin.getConfig().getLong("mining-block.base-money", 5);
        double pickMult   = 1.0 + (pickaxeLvl - 1) * 0.15;
        long earned       = (long) (baseMoney * level * pickMult);
        int shardsNeeded  = level * plugin.getConfig().getInt("mining-block.upgrade-shards", 5);
        int pickaxeShards = shardsNeededForPickaxe(data);
        double shardChance = (plugin.getConfig().getDouble("mining-block.shard-chance", 0.10)
                + (level * 0.005) + (pickaxeLvl * 0.01)) * 100;

        int shardMult = level >= 90 ? 4 : level >= 60 ? 3 : level >= 30 ? 2 : 1;
        String shardMultLabel = shardMult == 1 ? "<gray>(normal)"
                : shardMult == 2 ? "<yellow>✦✦ Double (ab Lvl 30)"
                : shardMult == 3 ? "<gold>✦✦✦ Triple (ab Lvl 60)"
                : "<light_purple>✦✦✦✦ Quadruple (ab Lvl 90)";
        String nextBonus = level < 30 ? " <dark_gray>→ Double bei Lvl 30"
                : level < 60 ? " <dark_gray>→ Triple bei Lvl 60"
                : level < 90 ? " <dark_gray>→ 4× bei Lvl 90"
                : "";

        return "<gold>━━ Mining-Block ━━\n"
                + "<gray>Block-Level: <white>" + level + " <dark_gray>/ " + maxLevel + "\n"
                + "<gray>Spitzhacke: <aqua>Lvl " + pickaxeLvl + " <dark_gray>/ " + maxPickaxeLvl + "\n"
                + "<gray>Geld/Schlag: <green>$" + MoneyManager.formatMoney(earned) + "\n"
                + "<gray>Shard-Chance: <light_purple>" + String.format("%.1f", shardChance) + "%\n"
                + "<gray>Shard/Drop: " + shardMultLabel + nextBonus + "\n"
                + "<gray>✦ Shards: <light_purple>" + data.getShards() + "\n"
                + (level < maxLevel ? "<gray>Block-Upgrade: <yellow>" + shardsNeeded + " Shards\n" : "<gold>Block MAX!\n")
                + (pickaxeLvl < maxPickaxeLvl ? "<gray>Spitzhacke-Upgrade: <yellow>" + pickaxeShards + " Shards" : "<gold>Spitzhacke MAX!");
    }
}
