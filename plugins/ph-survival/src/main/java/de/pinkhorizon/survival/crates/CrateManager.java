package de.pinkhorizon.survival.crates;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CrateManager {

    // ── Reward pools ────────────────────────────────────────────────────────

    private static final List<CrateReward> ECO_REWARDS = List.of(
        CrateReward.coins(10_000,  35),
        CrateReward.coins(25_000,  25),
        CrateReward.coins(50_000,  20),
        CrateReward.coins(75_000,  10),
        CrateReward.coins(100_000,  7),
        CrateReward.coins(150_000,  3)
    );

    private static final List<CrateReward> CLAIMS_REWARDS = List.of(
        CrateReward.claims(1, 55),
        CrateReward.claims(2, 30),
        CrateReward.claims(3, 15)
    );

    private static final List<CrateReward> SPAWNER_REWARDS = List.of(
        // Tier 1 – Häufig
        CrateReward.spawner(EntityType.CHICKEN,        "Huhn-Spawner",               15),
        CrateReward.spawner(EntityType.COW,            "Kuh-Spawner",                15),
        CrateReward.spawner(EntityType.PIG,            "Schwein-Spawner",            15),
        CrateReward.spawner(EntityType.SHEEP,          "Schaf-Spawner",              15),
        CrateReward.spawner(EntityType.SQUID,          "Tintenfisch-Spawner",        12),
        CrateReward.spawner(EntityType.RABBIT,         "Kaninchen-Spawner",          12),
        CrateReward.spawner(EntityType.BAT,            "Fledermaus-Spawner",         10),
        // Tier 2 – Ungewöhnlich
        CrateReward.spawner(EntityType.ZOMBIE,         "Zombie-Spawner",              7),
        CrateReward.spawner(EntityType.SKELETON,       "Skelett-Spawner",             7),
        CrateReward.spawner(EntityType.SPIDER,         "Spinnen-Spawner",             6),
        CrateReward.spawner(EntityType.CAVE_SPIDER,    "Höhlenspinnen-Spawner",       5),
        CrateReward.spawner(EntityType.CREEPER,        "Creeper-Spawner",             5),
        CrateReward.spawner(EntityType.SLIME,          "Schleim-Spawner",             4),
        CrateReward.spawner(EntityType.WITCH,          "Hexen-Spawner",               4),
        CrateReward.spawner(EntityType.DROWNED,        "Ertrunkener-Spawner",         5),
        CrateReward.spawner(EntityType.HUSK,           "Leichen-Spawner",             6),
        // Tier 3 – Selten
        CrateReward.spawner(EntityType.BLAZE,              "Blaze-Spawner",           2),
        CrateReward.spawner(EntityType.MAGMA_CUBE,         "Magmawürfel-Spawner",     2),
        CrateReward.spawner(EntityType.ENDERMAN,           "Enderman-Spawner",        2),
        CrateReward.spawner(EntityType.WITHER_SKELETON,    "Wither-Skelett-Spawner",  1),
        CrateReward.spawner(EntityType.PIGLIN,             "Piglin-Spawner",          2),
        CrateReward.spawner(EntityType.ZOMBIFIED_PIGLIN,   "Zombifizierter Piglin-Spawner", 2),
        CrateReward.spawner(EntityType.GHAST,              "Ghast-Spawner",           1),
        CrateReward.spawner(EntityType.SILVERFISH,         "Silberfisch-Spawner",     2),
        // Tier 4 – Legendär
        CrateReward.spawner(EntityType.IRON_GOLEM,     "Eisengolem-Spawner",         1),
        CrateReward.spawner(EntityType.GUARDIAN,       "Wächter-Spawner",            1),
        CrateReward.spawner(EntityType.ELDER_GUARDIAN, "Alter-Wächter-Spawner",      1),
        CrateReward.spawner(EntityType.PIGLIN_BRUTE,   "Piglin-Brute-Spawner",       1),
        CrateReward.spawner(EntityType.SHULKER,        "Shulker-Spawner",            1)
    );

    // ── Display names & colours ─────────────────────────────────────────────

    public static final Map<String, String> CRATE_NAMES = Map.of(
        "eco",     "Eco-Truhe",
        "claims",  "Claims-Truhe",
        "spawner", "Spawner-Truhe"
    );

    public static final Map<String, TextColor> CRATE_COLORS = Map.of(
        "eco",     TextColor.color(0xFFD700),
        "claims",  TextColor.color(0x55FF55),
        "spawner", TextColor.color(0xFF55FF)
    );

    // ── PDC ────────────────────────────────────────────────────────────────

    public static final String VALID_TYPES_STR = "eco, claims, spawner";
    private final NamespacedKey pdcKey;

    // ── State ──────────────────────────────────────────────────────────────

    private final PHSurvival plugin;
    private final File cratesFile;
    private FileConfiguration cratesCfg;

    /** chest location string → crate type */
    private final Map<String, String> locationMap = new HashMap<>();

    /** player UUID → running animation */
    private final Map<UUID, CrateAnimation> activeAnimations = new HashMap<>();

    private static final Random RNG = new Random();

    // ── Constructor ────────────────────────────────────────────────────────

    public CrateManager(PHSurvival plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "crate_key_type");
        plugin.saveResource("crates.yml", false);
        cratesFile = new File(plugin.getDataFolder(), "crates.yml");
        loadCrates();
    }

    // ── Rewards ────────────────────────────────────────────────────────────

    public List<CrateReward> getAllRewards(String type) {
        return switch (type) {
            case "eco"     -> ECO_REWARDS;
            case "claims"  -> CLAIMS_REWARDS;
            case "spawner" -> SPAWNER_REWARDS;
            default        -> List.of();
        };
    }

    public CrateReward getRandomReward(String type) {
        List<CrateReward> pool = getAllRewards(type);
        int total = pool.stream().mapToInt(CrateReward::weight).sum();
        int roll  = RNG.nextInt(total);
        int cumulative = 0;
        for (CrateReward r : pool) {
            cumulative += r.weight();
            if (roll < cumulative) return r;
        }
        return pool.get(pool.size() - 1);
    }

    // ── Crate locations ────────────────────────────────────────────────────

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public String getCrateType(Location loc) {
        return locationMap.get(locationKey(loc));
    }

    public boolean isCrate(Location loc) {
        return locationMap.containsKey(locationKey(loc));
    }

    public void addCrateLocation(String type, Location loc) {
        String key = locationKey(loc);
        locationMap.put(key, type);
        List<String> list = cratesCfg.getStringList("locations." + type);
        if (!list.contains(key)) {
            list.add(key);
            cratesCfg.set("locations." + type, list);
            saveCrates();
        }
    }

    public boolean removeCrateLocation(Location loc) {
        String key = locationKey(loc);
        String type = locationMap.remove(key);
        if (type == null) return false;
        List<String> list = cratesCfg.getStringList("locations." + type);
        list.remove(key);
        cratesCfg.set("locations." + type, list);
        saveCrates();
        return true;
    }

    // ── Key items ──────────────────────────────────────────────────────────

    public ItemStack createKey(String type) {
        String name = switch (type) {
            case "eco"     -> "§6§lEco-Schlüssel";
            case "claims"  -> "§a§lClaims-Schlüssel";
            case "spawner" -> "§d§lSpawner-Schlüssel";
            default        -> "§7Unbekannter Schlüssel";
        };
        String lore1 = switch (type) {
            case "eco"     -> "§7Öffnet die §6Eco-Truhe";
            case "claims"  -> "§7Öffnet die §aClaims-Truhe";
            case "spawner" -> "§7Öffnet die §dSpawner-Truhe";
            default        -> "";
        };
        String lore2 = switch (type) {
            case "eco"     -> "§7Gewinne §f10k – 150k§7 Coins!";
            case "claims"  -> "§7Gewinne §f1 – 3 §7extra Claim-Slots!";
            case "spawner" -> "§7Gewinne einen §fzufälligen Spawner§7!";
            default        -> "";
        };

        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(lore1).decoration(TextDecoration.ITALIC, false),
            Component.text(lore2).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("§8Rechtsklick auf die Truhe").decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                          org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    public String getKeyType(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
    }

    // ── Active animations ──────────────────────────────────────────────────

    public boolean hasActiveAnimation(UUID uuid) {
        CrateAnimation a = activeAnimations.get(uuid);
        return a != null && !a.isFinished() && !a.isCancelled();
    }

    public void addActiveAnimation(UUID uuid, CrateAnimation anim) {
        activeAnimations.put(uuid, anim);
    }

    public void removeActiveAnimation(UUID uuid) {
        activeAnimations.remove(uuid);
    }

    public CrateAnimation getActiveAnimation(UUID uuid) {
        return activeAnimations.get(uuid);
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private void loadCrates() {
        cratesCfg = YamlConfiguration.loadConfiguration(cratesFile);
        locationMap.clear();
        for (String type : List.of("eco", "claims", "spawner")) {
            for (String entry : cratesCfg.getStringList("locations." + type)) {
                locationMap.put(entry, type);
            }
        }
    }

    private void saveCrates() {
        try {
            cratesCfg.save(cratesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("CrateManager: Konnte crates.yml nicht speichern: " + e.getMessage());
        }
    }
}
