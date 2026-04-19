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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

public class JobsListener implements Listener {

    // ── Auszahlungstabellen: Material/EntityType → {coins, xp} ──────────

    private static final Map<Material, int[]> MINER_BLOCKS = new EnumMap<>(Material.class);
    private static final Map<Material, int[]> LOGGER_LOGS  = new EnumMap<>(Material.class);
    private static final Map<Material, int[]> FARMER_CROPS = new EnumMap<>(Material.class);
    private static final Map<EntityType, int[]> HUNTER_MOBS = new EnumMap<>(EntityType.class);

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
