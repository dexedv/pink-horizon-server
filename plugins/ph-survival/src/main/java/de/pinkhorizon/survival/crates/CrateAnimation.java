package de.pinkhorizon.survival.crates;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrateAnimation {

    // ── InventoryHolder marker ─────────────────────────────────────────────

    public static class Holder implements InventoryHolder {
        private final CrateAnimation animation;
        public Holder(CrateAnimation anim) { this.animation = anim; }
        public CrateAnimation getAnimation() { return animation; }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Ring of 20 outer slots (clockwise) ─────────────────────────────────

    private static final int[] RING = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,   // top row L→R
        17,                            // right middle
        26, 25, 24, 23, 22, 21, 20, 19, 18, // bottom R→L
        9                             // left middle
    };
    // Center slot (not in ring)
    private static final int CENTER_SLOT = 13;

    // ── Animation timing ───────────────────────────────────────────────────

    private static final int  TOTAL_STEPS = 55;
    private static final int  REEL_SIZE   = 60;
    private static final Random RNG = new Random();

    // ── Fields ─────────────────────────────────────────────────────────────

    private final PHSurvival   plugin;
    private final Player       player;
    private final String       crateType;
    private final CrateReward  winner;
    private final CrateManager manager;
    private final Inventory    gui;
    private final List<ItemStack> reel = new ArrayList<>();

    private int     step      = 0;
    private boolean finished  = false;
    private boolean cancelled = false;

    // ── Constructor ────────────────────────────────────────────────────────

    public CrateAnimation(PHSurvival plugin, Player player, String crateType,
                          CrateReward winner, CrateManager manager) {
        this.plugin    = plugin;
        this.player    = player;
        this.crateType = crateType;
        this.winner    = winner;
        this.manager   = manager;

        // Build GUI
        String title = CrateManager.CRATE_NAMES.getOrDefault(crateType, "Truhe");
        TextColor color = CrateManager.CRATE_COLORS.getOrDefault(crateType, TextColor.color(0xFF55FF));
        gui = Bukkit.createInventory(new Holder(this), 27,
            Component.text("✦ " + title + " ✦", color, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Fill all slots with glass
        ItemStack glass = pane(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) gui.setItem(i, glass);

        // Center: crate type icon
        gui.setItem(CENTER_SLOT, makeCenterIcon(crateType));

        // Build random reel for animation (purely visual)
        List<CrateReward> pool = manager.getAllRewards(crateType);
        if (!pool.isEmpty()) {
            for (int i = 0; i < REEL_SIZE; i++) {
                reel.add(buildDisplayItem(pool.get(RNG.nextInt(pool.size()))));
            }
        }

        player.openInventory(gui);
    }

    // ── Start ──────────────────────────────────────────────────────────────

    public void start() {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::tick, 2L);
    }

    // ── Animation loop ─────────────────────────────────────────────────────

    private void tick() {
        if (cancelled) return;

        if (step >= TOTAL_STEPS) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::finishAnimation, 8L);
            return;
        }

        // Rotate items through ring
        if (!reel.isEmpty()) {
            for (int i = 0; i < RING.length; i++) {
                gui.setItem(RING[i], reel.get((step + i) % reel.size()));
            }
        }

        // Tick sound – pitch slows with animation
        int remaining = TOTAL_STEPS - step;
        float pitch = remaining > 30 ? 1.6f : (remaining > 15 ? 1.2f : 0.9f);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, pitch);

        step++;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::tick, getDelay());
    }

    private long getDelay() {
        int remaining = TOTAL_STEPS - step;
        if (remaining > 40) return 1L;
        if (remaining > 25) return 2L;
        if (remaining > 12) return 4L;
        if (remaining > 5)  return 6L;
        return 9L;
    }

    // ── Finish ─────────────────────────────────────────────────────────────

    private void finishAnimation() {
        if (cancelled) return;
        finished = true;
        manager.removeActiveAnimation(player.getUniqueId());

        // Clear ring, show winner in center
        ItemStack gold = pane(Material.YELLOW_STAINED_GLASS_PANE);
        for (int slot : RING) gui.setItem(slot, gold);
        gui.setItem(CENTER_SLOT, buildWinnerItem(winner));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,  1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.2f);

        // Give reward and close after 3 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
                giveReward();
            }
        }, 60L);
    }

    private void giveReward() {
        switch (winner.type()) {
            case COINS -> {
                plugin.getEconomyManager().deposit(player.getUniqueId(), winner.coins());
            }
            case CLAIMS -> {
                plugin.getUpgradeManager().addExtraClaims(player.getUniqueId(), winner.claims());
            }
            case SPAWNER -> {
                ItemStack spawner = buildSpawnerItem(winner);
                var leftover = player.getInventory().addItem(spawner);
                leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            case COSMETIC -> {
                ItemStack skin = buildCosmeticItem(winner);
                var leftover = player.getInventory().addItem(skin);
                leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }

        String rewardText = switch (winner.type()) {
            case COINS    -> winner.coins() + " Coins";
            case CLAIMS   -> "+" + winner.claims() + " Claim-Slot" + (winner.claims() == 1 ? "" : "s");
            case SPAWNER  -> winner.displayName();
            case COSMETIC -> winner.displayName();
        };

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ Truhen-Belohnung ✦",
            TextColor.color(0xFF55FF), TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("Du hast gewonnen: ", NamedTextColor.GRAY)
            .append(Component.text(rewardText, TextColor.color(0xFFD700), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)));
        player.sendMessage(Component.empty());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    // ── State ──────────────────────────────────────────────────────────────

    public void cancel() {
        cancelled = true;
        manager.removeActiveAnimation(player.getUniqueId());
    }

    public boolean isFinished()  { return finished; }
    public boolean isCancelled() { return cancelled; }
    public String  getCrateType() { return crateType; }
    public Inventory getGui() { return gui; }

    // ── Item builders ──────────────────────────────────────────────────────

    private ItemStack buildDisplayItem(CrateReward reward) {
        Material mat = switch (reward.type()) {
            case COINS    -> Material.GOLD_INGOT;
            case CLAIMS   -> Material.GRASS_BLOCK;
            case SPAWNER  -> Material.SPAWNER;
            case COSMETIC -> Material.DIAMOND_SWORD;
        };
        String colorCode = switch (reward.type()) {
            case COINS    -> "§6";
            case CLAIMS   -> "§a";
            case SPAWNER  -> "§d";
            case COSMETIC -> "§d";
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(colorCode + reward.displayName())
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of());
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                          org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildWinnerItem(CrateReward reward) {
        Material mat = switch (reward.type()) {
            case COINS    -> Material.GOLD_BLOCK;
            case CLAIMS   -> Material.EMERALD_BLOCK;
            case SPAWNER  -> Material.SPAWNER;
            case COSMETIC -> Material.DIAMOND_SWORD;
        };
        String colorCode = switch (reward.type()) {
            case COINS    -> "§6§l";
            case CLAIMS   -> "§a§l";
            case SPAWNER  -> "§d§l";
            case COSMETIC -> "§d§l";
        };
        String loreText = switch (reward.type()) {
            case COINS    -> "§7" + reward.coins() + " Coins wurden deinem Konto gutgeschrieben";
            case CLAIMS   -> "§7" + reward.claims() + " extra Claim-Slot(s) wurden hinzugefügt";
            case SPAWNER  -> "§7" + reward.displayName() + " wurde in dein Inventar gelegt";
            case COSMETIC -> "§7" + reward.displayName() + " wurde in dein Inventar gelegt";
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("★ " + colorCode + reward.displayName() + " ★")
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.empty(),
            Component.text(loreText).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                          org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSpawnerItem(CrateReward reward) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta bsm = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) bsm.getBlockState();
        cs.setSpawnedType(reward.entityType());
        bsm.setBlockState(cs);
        bsm.displayName(Component.text("⚙ " + reward.displayName(),
            TextColor.color(0xFF69B4), TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        bsm.lore(List.of(
            Component.empty(),
            Component.text("  Entity: ", NamedTextColor.GRAY)
                .append(Component.text(reward.entityType().name().replace("_", " "), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false)
        ));
        bsm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                         org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(bsm);
        return item;
    }

    private ItemStack buildCosmeticItem(CrateReward reward) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(reward.customModelData());
        meta.displayName(Component.text("§d§l✦ " + reward.displayName() + " ✦")
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.empty(),
            Component.text("§7Kosmetischer Schwert-Skin", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("§8CustomModelData: " + reward.customModelData())
                .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                          org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                          org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeCenterIcon(String type) {
        Material mat = switch (type) {
            case "eco"      -> Material.GOLD_BLOCK;
            case "claims"   -> Material.GRASS_BLOCK;
            case "spawner"  -> Material.SPAWNER;
            case "cosmetic" -> Material.DIAMOND_SWORD;
            default         -> Material.CHEST;
        };
        TextColor color = CrateManager.CRATE_COLORS.getOrDefault(type, TextColor.color(0xFF55FF));
        String name = CrateManager.CRATE_NAMES.getOrDefault(type, "Truhe");
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("§7Items drehen sich...", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
