package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.commands.JobsCommand;
import de.pinkhorizon.survival.managers.JobManager;
import de.pinkhorizon.survival.managers.JobManager.Job;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

public class JobsListener implements Listener {

    // ── Auszahlungstabellen: Material/EntityType → {coins, xp} ──────────

    private static final Map<Material, int[]> MINER_BLOCKS    = new EnumMap<>(Material.class);
    private static final Map<Material, int[]> LOGGER_LOGS     = new EnumMap<>(Material.class);
    private static final Map<Material, int[]> FARMER_CROPS    = new EnumMap<>(Material.class);
    private static final Map<EntityType, int[]> HUNTER_MOBS   = new EnumMap<>(EntityType.class);
    private static final Map<Material, int[]> BUILDER_BLOCKS  = new EnumMap<>(Material.class);
    private static final Map<Material, int[]> WEAPONSMITH_CRAFTS = new EnumMap<>(Material.class);

    static {
        // Bergmann
        MINER_BLOCKS.put(Material.COAL_ORE,             new int[]{2,  5});
        MINER_BLOCKS.put(Material.DEEPSLATE_COAL_ORE,   new int[]{2,  5});
        MINER_BLOCKS.put(Material.IRON_ORE,             new int[]{5,  10});
        MINER_BLOCKS.put(Material.DEEPSLATE_IRON_ORE,   new int[]{5,  10});
        MINER_BLOCKS.put(Material.COPPER_ORE,           new int[]{3,  7});
        MINER_BLOCKS.put(Material.DEEPSLATE_COPPER_ORE, new int[]{3,  7});
        MINER_BLOCKS.put(Material.GOLD_ORE,             new int[]{10, 15});
        MINER_BLOCKS.put(Material.DEEPSLATE_GOLD_ORE,   new int[]{10, 15});
        MINER_BLOCKS.put(Material.NETHER_GOLD_ORE,      new int[]{8,  12});
        MINER_BLOCKS.put(Material.LAPIS_ORE,            new int[]{6,  10});
        MINER_BLOCKS.put(Material.DEEPSLATE_LAPIS_ORE,  new int[]{6,  10});
        MINER_BLOCKS.put(Material.REDSTONE_ORE,         new int[]{4,  8});
        MINER_BLOCKS.put(Material.DEEPSLATE_REDSTONE_ORE, new int[]{4, 8});
        MINER_BLOCKS.put(Material.DIAMOND_ORE,          new int[]{30, 40});
        MINER_BLOCKS.put(Material.DEEPSLATE_DIAMOND_ORE, new int[]{30, 40});
        MINER_BLOCKS.put(Material.EMERALD_ORE,          new int[]{25, 35});
        MINER_BLOCKS.put(Material.DEEPSLATE_EMERALD_ORE, new int[]{25, 35});
        MINER_BLOCKS.put(Material.NETHER_QUARTZ_ORE,    new int[]{5,  8});
        MINER_BLOCKS.put(Material.ANCIENT_DEBRIS,       new int[]{80, 60});

        // Holzfäller
        for (Material m : new Material[]{
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
                Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.CHERRY_LOG,
                Material.MANGROVE_LOG, Material.PALE_OAK_LOG}) {
            LOGGER_LOGS.put(m, new int[]{2, 5});
        }
        LOGGER_LOGS.put(Material.DARK_OAK_LOG,  new int[]{3, 6});
        LOGGER_LOGS.put(Material.CRIMSON_STEM,  new int[]{4, 7});
        LOGGER_LOGS.put(Material.WARPED_STEM,   new int[]{4, 7});

        // Bauer (nur vollständig gewachsen, wird im Event geprüft)
        FARMER_CROPS.put(Material.WHEAT,        new int[]{2,  4});
        FARMER_CROPS.put(Material.CARROTS,      new int[]{2,  4});
        FARMER_CROPS.put(Material.POTATOES,     new int[]{2,  4});
        FARMER_CROPS.put(Material.BEETROOTS,    new int[]{2,  4});
        FARMER_CROPS.put(Material.NETHER_WART,  new int[]{3,  6});
        FARMER_CROPS.put(Material.SUGAR_CANE,   new int[]{2,  4});
        FARMER_CROPS.put(Material.PUMPKIN,      new int[]{5,  8});
        FARMER_CROPS.put(Material.MELON,        new int[]{5,  8});
        FARMER_CROPS.put(Material.COCOA,        new int[]{2,  5});
        FARMER_CROPS.put(Material.SWEET_BERRY_BUSH, new int[]{3, 5});

        // Jäger
        HUNTER_MOBS.put(EntityType.ZOMBIE,          new int[]{4,  8});
        HUNTER_MOBS.put(EntityType.ZOMBIE_VILLAGER, new int[]{4,  8});
        HUNTER_MOBS.put(EntityType.SKELETON,        new int[]{5,  9});
        HUNTER_MOBS.put(EntityType.STRAY,           new int[]{5,  9});
        HUNTER_MOBS.put(EntityType.WITHER_SKELETON, new int[]{15, 20});
        HUNTER_MOBS.put(EntityType.CREEPER,         new int[]{6,  10});
        HUNTER_MOBS.put(EntityType.SPIDER,          new int[]{4,  8});
        HUNTER_MOBS.put(EntityType.CAVE_SPIDER,     new int[]{5,  9});
        HUNTER_MOBS.put(EntityType.ENDERMAN,        new int[]{10, 15});
        HUNTER_MOBS.put(EntityType.BLAZE,           new int[]{12, 18});
        HUNTER_MOBS.put(EntityType.WITCH,           new int[]{10, 15});
        HUNTER_MOBS.put(EntityType.GUARDIAN,        new int[]{12, 16});
        HUNTER_MOBS.put(EntityType.ELDER_GUARDIAN,  new int[]{60, 50});
        HUNTER_MOBS.put(EntityType.DROWNED,         new int[]{4,  8});
        HUNTER_MOBS.put(EntityType.HUSK,            new int[]{4,  8});
        HUNTER_MOBS.put(EntityType.VINDICATOR,      new int[]{10, 14});
        HUNTER_MOBS.put(EntityType.PILLAGER,        new int[]{8,  12});
        HUNTER_MOBS.put(EntityType.RAVAGER,         new int[]{40, 40});
        HUNTER_MOBS.put(EntityType.PHANTOM,         new int[]{6,  10});
        HUNTER_MOBS.put(EntityType.PIGLIN_BRUTE,    new int[]{15, 20});
        HUNTER_MOBS.put(EntityType.HOGLIN,          new int[]{10, 14});
        HUNTER_MOBS.put(EntityType.ZOGLIN,          new int[]{12, 16});
        HUNTER_MOBS.put(EntityType.WARDEN,          new int[]{150,80});

        // Baumeister – gecraftete/geschmolzene Blöcke
        for (Material m : new Material[]{
                Material.BRICKS, Material.BRICK_STAIRS, Material.BRICK_SLAB, Material.BRICK_WALL,
                Material.STONE_BRICKS, Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_SLAB, Material.STONE_BRICK_WALL,
                Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS, Material.MOSSY_STONE_BRICK_SLAB, Material.MOSSY_STONE_BRICK_WALL,
                Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
                Material.POLISHED_GRANITE, Material.POLISHED_GRANITE_STAIRS, Material.POLISHED_GRANITE_SLAB,
                Material.POLISHED_DIORITE, Material.POLISHED_DIORITE_STAIRS, Material.POLISHED_DIORITE_SLAB,
                Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE_STAIRS, Material.POLISHED_ANDESITE_SLAB,
                Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_STAIRS, Material.POLISHED_BLACKSTONE_SLAB,
                Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_BRICK_SLAB,
                Material.NETHER_BRICKS, Material.NETHER_BRICK_STAIRS, Material.NETHER_BRICK_SLAB, Material.NETHER_BRICK_WALL,
                Material.RED_NETHER_BRICKS, Material.RED_NETHER_BRICK_STAIRS, Material.RED_NETHER_BRICK_SLAB, Material.RED_NETHER_BRICK_WALL,
                Material.CHISELED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS,
                Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_WALL,
                Material.CRACKED_DEEPSLATE_BRICKS, Material.CHISELED_DEEPSLATE,
                Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_WALL,
                Material.CRACKED_DEEPSLATE_TILES,
                Material.SMOOTH_STONE, Material.SMOOTH_STONE_SLAB,
                Material.SMOOTH_SANDSTONE, Material.SMOOTH_SANDSTONE_STAIRS, Material.SMOOTH_SANDSTONE_SLAB,
                Material.SMOOTH_RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE_STAIRS, Material.SMOOTH_RED_SANDSTONE_SLAB,
                Material.SMOOTH_QUARTZ, Material.SMOOTH_QUARTZ_STAIRS, Material.SMOOTH_QUARTZ_SLAB,
                Material.QUARTZ_BLOCK, Material.QUARTZ_STAIRS, Material.QUARTZ_SLAB,
                Material.CHISELED_QUARTZ_BLOCK, Material.QUARTZ_PILLAR, Material.QUARTZ_BRICKS}) {
            BUILDER_BLOCKS.put(m, new int[]{1, 3});
        }
        // Glas (alle Farben + Scheiben)
        for (Material m : Material.values()) {
            String n = m.name();
            if (m.isBlock() && (n.equals("GLASS") || n.equals("GLASS_PANE") || n.endsWith("_GLASS") || n.endsWith("_GLASS_PANE"))) {
                BUILDER_BLOCKS.put(m, new int[]{1, 2});
            }
        }
        // Beton (alle 16 Farben)
        for (Material m : Material.values()) {
            if (m.isBlock() && m.name().endsWith("_CONCRETE")) {
                BUILDER_BLOCKS.put(m, new int[]{1, 2});
            }
        }
        // Terrakotta & glasierte Terrakotta (alle Farben)
        for (Material m : Material.values()) {
            String n = m.name();
            if (m.isBlock() && (n.equals("TERRACOTTA") || n.endsWith("_TERRACOTTA") || n.endsWith("_GLAZED_TERRACOTTA"))) {
                BUILDER_BLOCKS.put(m, new int[]{1, 3});
            }
        }
        // Wolle (alle 16 Farben)
        for (Material m : Material.values()) {
            if (m.isBlock() && m.name().endsWith("_WOOL")) {
                BUILDER_BLOCKS.put(m, new int[]{1, 2});
            }
        }

        // Waffenschmied – craftbare Waffen & Rüstungsteile
        WEAPONSMITH_CRAFTS.put(Material.WOODEN_SWORD,     new int[]{ 2,  5});
        WEAPONSMITH_CRAFTS.put(Material.STONE_SWORD,      new int[]{ 3,  7});
        WEAPONSMITH_CRAFTS.put(Material.IRON_SWORD,       new int[]{ 8, 15});
        WEAPONSMITH_CRAFTS.put(Material.GOLDEN_SWORD,     new int[]{ 6, 12});
        WEAPONSMITH_CRAFTS.put(Material.DIAMOND_SWORD,    new int[]{30, 50});
        WEAPONSMITH_CRAFTS.put(Material.WOODEN_AXE,       new int[]{ 2,  4});
        WEAPONSMITH_CRAFTS.put(Material.STONE_AXE,        new int[]{ 3,  6});
        WEAPONSMITH_CRAFTS.put(Material.IRON_AXE,         new int[]{ 7, 12});
        WEAPONSMITH_CRAFTS.put(Material.GOLDEN_AXE,       new int[]{ 5, 10});
        WEAPONSMITH_CRAFTS.put(Material.DIAMOND_AXE,      new int[]{25, 40});
        WEAPONSMITH_CRAFTS.put(Material.BOW,              new int[]{ 8, 15});
        WEAPONSMITH_CRAFTS.put(Material.CROSSBOW,         new int[]{12, 20});
        WEAPONSMITH_CRAFTS.put(Material.SHIELD,           new int[]{10, 18});
        WEAPONSMITH_CRAFTS.put(Material.ARROW,            new int[]{ 1,  2});
        WEAPONSMITH_CRAFTS.put(Material.IRON_HELMET,      new int[]{ 6, 12});
        WEAPONSMITH_CRAFTS.put(Material.IRON_CHESTPLATE,  new int[]{10, 18});
        WEAPONSMITH_CRAFTS.put(Material.IRON_LEGGINGS,    new int[]{ 9, 16});
        WEAPONSMITH_CRAFTS.put(Material.IRON_BOOTS,       new int[]{ 5, 10});
        WEAPONSMITH_CRAFTS.put(Material.GOLDEN_HELMET,    new int[]{ 5, 10});
        WEAPONSMITH_CRAFTS.put(Material.GOLDEN_CHESTPLATE,new int[]{ 8, 14});
        WEAPONSMITH_CRAFTS.put(Material.GOLDEN_LEGGINGS,  new int[]{ 7, 12});
        WEAPONSMITH_CRAFTS.put(Material.GOLDEN_BOOTS,     new int[]{ 4,  8});
        WEAPONSMITH_CRAFTS.put(Material.DIAMOND_HELMET,   new int[]{20, 35});
        WEAPONSMITH_CRAFTS.put(Material.DIAMOND_CHESTPLATE,new int[]{30, 55});
        WEAPONSMITH_CRAFTS.put(Material.DIAMOND_LEGGINGS, new int[]{28, 50});
        WEAPONSMITH_CRAFTS.put(Material.DIAMOND_BOOTS,    new int[]{18, 32});
        WEAPONSMITH_CRAFTS.put(Material.LEATHER_HELMET,   new int[]{ 2,  4});
        WEAPONSMITH_CRAFTS.put(Material.LEATHER_CHESTPLATE,new int[]{ 3,  6});
        WEAPONSMITH_CRAFTS.put(Material.LEATHER_LEGGINGS, new int[]{ 3,  5});
        WEAPONSMITH_CRAFTS.put(Material.LEATHER_BOOTS,    new int[]{ 2,  3});
        WEAPONSMITH_CRAFTS.put(Material.CHAINMAIL_HELMET,     new int[]{ 5,  9});
        WEAPONSMITH_CRAFTS.put(Material.CHAINMAIL_CHESTPLATE, new int[]{ 8, 14});
        WEAPONSMITH_CRAFTS.put(Material.CHAINMAIL_LEGGINGS,   new int[]{ 7, 12});
        WEAPONSMITH_CRAFTS.put(Material.CHAINMAIL_BOOTS,      new int[]{ 4,  7});
    }

    private final PHSurvival plugin;

    public JobsListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Block abbauen ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Job job = plugin.getJobManager().getJob(player.getUniqueId());
        if (job == null) return;

        Material mat = event.getBlock().getType();

        switch (job) {
            case MINER -> {
                int[] pay = MINER_BLOCKS.get(mat);
                if (pay != null) plugin.getJobManager().reward(player, pay[0], pay[1]);
            }
            case LUMBERJACK -> {
                int[] pay = LOGGER_LOGS.get(mat);
                if (pay != null) plugin.getJobManager().reward(player, pay[0], pay[1]);
            }
            case FARMER -> {
                int[] pay = FARMER_CROPS.get(mat);
                if (pay == null) return;
                // Nur bei vollständigem Wachstum zahlen
                if (event.getBlock().getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() < ageable.getMaximumAge()) return;
                }
                plugin.getJobManager().reward(player, pay[0], pay[1]);
            }
            default -> {}
        }
    }

    // ── Mob töten ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (plugin.getJobManager().getJob(killer.getUniqueId()) != Job.HUNTER) return;

        int[] pay = HUNTER_MOBS.get(event.getEntityType());
        if (pay != null) plugin.getJobManager().reward(killer, pay[0], pay[1]);
    }

    // ── Fischen ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (plugin.getJobManager().getJob(player.getUniqueId()) != Job.FISHER) return;

        int coins = 3, xp = 8;
        if (event.getCaught() instanceof Item caught) {
            Material type = caught.getItemStack().getType();
            coins = switch (type) {
                case SALMON          -> 5;
                case TROPICAL_FISH   -> 6;
                case PUFFERFISH      -> 5;
                case BOW, FISHING_ROD, ENCHANTED_BOOK, NAME_TAG, SADDLE -> 20; // Schatz
                default              -> 3; // Cod und sonstiges
            };
            xp = switch (type) {
                case SALMON          -> 10;
                case TROPICAL_FISH   -> 12;
                case PUFFERFISH      -> 10;
                case BOW, FISHING_ROD, ENCHANTED_BOOK, NAME_TAG, SADDLE -> 30;
                default              -> 8;
            };
        }
        plugin.getJobManager().reward(player, coins, xp);
    }

    // ── Block platzieren (Baumeister) ─────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getJobManager().getJob(player.getUniqueId()) != Job.BUILDER) return;
        int[] pay = BUILDER_BLOCKS.get(event.getBlock().getType());
        if (pay != null) plugin.getJobManager().reward(player, pay[0], pay[1]);
    }

    // ── Trank nehmen (Brauer) ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getJobManager().getJob(player.getUniqueId()) != Job.BREWER) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().getType() != InventoryType.BREWING) return;
        // Slots 0-2 = Trankflaschen-Ausgabe im Braustand
        int slot = event.getSlot();
        if (slot < 0 || slot > 2) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        String name = item.getType().name();
        if (!name.contains("POTION")) return;
        int coins, xp;
        if (name.equals("LINGERING_POTION")) {
            coins = 10; xp = 25;
        } else if (name.equals("SPLASH_POTION")) {
            coins = 7;  xp = 20;
        } else {
            coins = 5;  xp = 15;
        }
        plugin.getJobManager().reward(player, coins, xp);
    }

    // ── Verzaubern (Verzauberer) ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (plugin.getJobManager().getJob(player.getUniqueId()) != Job.ENCHANTER) return;
        int cost = event.getExpLevelCost();

        // Job-XP + Coins
        int coins, xp;
        if (cost >= 21) {
            coins = 30; xp = 60;
        } else if (cost >= 11) {
            coins = 15; xp = 30;
        } else {
            coins = 5;  xp = 15;
        }
        plugin.getJobManager().reward(player, coins, xp);

        // Aktiver Verzauberungs-Rabatt: XP-Level zurückerstatten
        int discount = plugin.getJobBonusManager().getEnchantDiscount(player.getUniqueId());
        if (discount > 0) {
            int refund = Math.max(1, (int) Math.round(cost * discount / 100.0));
            // 1 Tick verzögert – Minecraft zieht die Kosten erst nach dem Event ab
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> player.giveExpLevels(refund), 1L);
        }
    }

    // ── Waffe/Rüstung craften (Waffenschmied) ────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getJobManager().getJob(player.getUniqueId()) != Job.WEAPONSMITH) return;
        int[] pay = WEAPONSMITH_CRAFTS.get(event.getRecipe().getResult().getType());
        if (pay != null) plugin.getJobManager().reward(player, pay[0], pay[1]);
    }

    // ── GUI-Klick ────────────────────────────────────────────────────────

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("Jobs") || !title.contains("Pink Horizon")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String jobId = meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, JobsCommand.JOB_KEY), PersistentDataType.STRING);
        if (jobId == null) return;

        try {
            Job job = Job.valueOf(jobId);
            plugin.getJobManager().setJob(player, job);
            plugin.getJobsCommand().openGui(player);
        } catch (IllegalArgumentException ignored) {}
    }
}
