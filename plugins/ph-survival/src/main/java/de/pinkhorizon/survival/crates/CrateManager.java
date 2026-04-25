package de.pinkhorizon.survival.crates;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalHologramManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

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

    private static final List<CrateReward> COSMETIC_REWARDS = List.of(
        // Common (weight 30)
        CrateReward.cosmetic("Wald-Klinge",    1001, 30),
        CrateReward.cosmetic("Ozean-Klinge",   1002, 30),
        CrateReward.cosmetic("Wüsten-Klinge",  1003, 30),
        // Rare (weight 15)
        CrateReward.cosmetic("Feuer-Klinge",   1004, 15),
        CrateReward.cosmetic("Eis-Klinge",     1005, 15),
        CrateReward.cosmetic("Nether-Klinge",  1006, 15),
        // Epic (weight 7)
        CrateReward.cosmetic("Drachen-Klinge", 1007, 7),
        CrateReward.cosmetic("Kristall-Klinge",1008, 7),
        CrateReward.cosmetic("Schatten-Klinge",1009, 7),
        // Legendary (weight 2)
        CrateReward.cosmetic("Legendäre Klinge",1010, 2),
        CrateReward.cosmetic("Galaxie-Klinge", 1011, 2),
        // Ultra-Rare (weight 1)
        CrateReward.cosmetic("Reaper-Klinge",  1012, 1)
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
        "eco",      "Eco-Truhe",
        "claims",   "Claims-Truhe",
        "spawner",  "Spawner-Truhe",
        "cosmetic", "Kosmetik-Truhe"
    );

    public static final Map<String, TextColor> CRATE_COLORS = Map.of(
        "eco",      TextColor.color(0xFFD700),
        "claims",   TextColor.color(0x55FF55),
        "spawner",  TextColor.color(0xFF55FF),
        "cosmetic", TextColor.color(0xFF69B4)
    );

    // ── PDC ────────────────────────────────────────────────────────────────

    public static final String VALID_TYPES_STR = "eco, claims, spawner, cosmetic";
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

    // ── Effects ────────────────────────────────────────────────────────────

    private SurvivalHologramManager holoManager;
    private BukkitTask particleTask;
    private int        particleTick = 0;

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
            case "eco"      -> ECO_REWARDS;
            case "claims"   -> CLAIMS_REWARDS;
            case "spawner"  -> SPAWNER_REWARDS;
            case "cosmetic" -> COSMETIC_REWARDS;
            default         -> List.of();
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
        if (holoManager != null) spawnHologram(key, type);
    }

    public boolean removeCrateLocation(Location loc) {
        String key = locationKey(loc);
        String type = locationMap.remove(key);
        if (type == null) return false;
        List<String> list = cratesCfg.getStringList("locations." + type);
        list.remove(key);
        cratesCfg.set("locations." + type, list);
        saveCrates();
        if (holoManager != null) holoManager.remove("crate_" + key);
        return true;
    }

    // ── Key items ──────────────────────────────────────────────────────────

    public ItemStack createKey(String type) {
        String name = switch (type) {
            case "eco"      -> "§6§lEco-Schlüssel";
            case "claims"   -> "§a§lClaims-Schlüssel";
            case "spawner"  -> "§d§lSpawner-Schlüssel";
            case "cosmetic" -> "§d§lKosmetik-Schlüssel";
            default         -> "§7Unbekannter Schlüssel";
        };
        String lore1 = switch (type) {
            case "eco"      -> "§7Öffnet die §6Eco-Truhe";
            case "claims"   -> "§7Öffnet die §aClaims-Truhe";
            case "spawner"  -> "§7Öffnet die §dSpawner-Truhe";
            case "cosmetic" -> "§7Öffnet die §dKosmetik-Truhe";
            default         -> "";
        };
        String lore2 = switch (type) {
            case "eco"      -> "§7Gewinne §f10k – 150k§7 Coins!";
            case "claims"   -> "§7Gewinne §f1 – 3 §7extra Claim-Slots!";
            case "spawner"  -> "§7Gewinne einen §fzufälligen Spawner§7!";
            case "cosmetic" -> "§7Gewinne einen §fSchwert-Skin§7!";
            default         -> "";
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

    // ── Effects: Holograms + Particles ────────────────────────────────────

    /** Called once after worlds are loaded (from PHSurvival delayed startup task). */
    public void spawnAllEffects(SurvivalHologramManager holoManager) {
        this.holoManager = holoManager;
        // Spawn hologram for every registered crate
        for (Map.Entry<String, String> e : locationMap.entrySet()) {
            spawnHologram(e.getKey(), e.getValue());
        }
        startParticleTask();
    }

    /** Stop particle task and remove all crate holograms. */
    public void stopEffects() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (holoManager != null) {
            for (String key : locationMap.keySet()) {
                holoManager.remove("crate_" + key);
            }
        }
    }

    // ── Private effect helpers ─────────────────────────────────────────────

    private void spawnHologram(String locKey, String type) {
        Location loc = parseLocation(locKey);
        if (loc == null) return;
        // Base = center top of chest block + 1.5 above
        Location base = loc.clone().add(0.5, 1.8, 0.5);
        holoManager.createTemporary("crate_" + locKey, base, holoLines(type), 0.9f);
    }

    private List<String> holoLines(String type) {
        return switch (type) {
            case "eco" -> List.of(
                "<gradient:#FFD700:#FF8C00><bold>✦ Eco-Truhe ✦</bold></gradient>",
                "<gray>Gewinne <gold><bold>10k – 150k Coins</bold></gold></gray>",
                "<yellow>⚷ Eco-Schlüssel benötigt</yellow>"
            );
            case "claims" -> List.of(
                "<gradient:#55FF55:#00AA00><bold>✦ Claims-Truhe ✦</bold></gradient>",
                "<gray>Gewinne <green><bold>+1 bis +3 Claim-Slots</bold></green></gray>",
                "<green>⚷ Claims-Schlüssel benötigt</green>"
            );
            case "spawner" -> List.of(
                "<gradient:#FF55FF:#AA00AA><bold>✦ Spawner-Truhe ✦</bold></gradient>",
                "<gray>Gewinne einen <light_purple><bold>zufälligen Spawner</bold></light_purple></gray>",
                "<light_purple>⚷ Spawner-Schlüssel benötigt</light_purple>"
            );
            case "cosmetic" -> List.of(
                "<gradient:#FF69B4:#FF1493><bold>✦ Kosmetik-Truhe ✦</bold></gradient>",
                "<gray>Gewinne einen <color:#FF69B4><bold>Schwert-Skin</bold></color></gray>",
                "<color:#FF69B4>⚷ Kosmetik-Schlüssel benötigt</color>"
            );
            default -> List.of("<gray>Unbekannte Truhe</gray>");
        };
    }

    private void startParticleTask() {
        if (particleTask != null) particleTask.cancel();
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            particleTick++;
            double angle = particleTick * 0.13; // ~one rotation per 48 runs (≈3.8 s)
            for (Map.Entry<String, String> e : locationMap.entrySet()) {
                spawnParticles(e.getKey(), e.getValue(), angle);
            }
        }, 10L, 4L);
    }

    private void spawnParticles(String locKey, String type, double angle) {
        Location loc = parseLocation(locKey);
        if (loc == null) return;
        World world = loc.getWorld();

        double cx = loc.getBlockX() + 0.5;
        double cy = loc.getBlockY() + 0.7;
        double cz = loc.getBlockZ() + 0.5;

        Color dustColor = switch (type) {
            case "eco"      -> Color.fromRGB(255, 200, 0);
            case "claims"   -> Color.fromRGB(80, 255, 80);
            case "spawner"  -> Color.fromRGB(200, 50, 255);
            case "cosmetic" -> Color.fromRGB(255, 105, 180);
            default         -> Color.WHITE;
        };
        Particle.DustOptions dust      = new Particle.DustOptions(dustColor,      1.0f);
        Particle.DustOptions dustInner = new Particle.DustOptions(dustColor.mixColors(Color.WHITE), 0.7f);

        // Outer ring (r=0.85) – 8 particles rotating clockwise
        for (int i = 0; i < 8; i++) {
            double a = angle + (2 * Math.PI / 8) * i;
            world.spawnParticle(Particle.DUST,
                cx + 0.85 * Math.cos(a), cy, cz + 0.85 * Math.sin(a),
                1, 0, 0, 0, 0, dust);
        }

        // Inner ring (r=0.45) – 4 particles counter-rotating, slightly higher
        for (int i = 0; i < 4; i++) {
            double a = -angle * 0.8 + (Math.PI / 2) * i;
            world.spawnParticle(Particle.DUST,
                cx + 0.45 * Math.cos(a), cy + 0.35, cz + 0.45 * Math.sin(a),
                1, 0, 0, 0, 0, dustInner);
        }

        // Type-specific secondary effect
        switch (type) {
            case "eco" -> {
                // Golden CRIT sparks shooting upward every 5 ticks
                if (particleTick % 5 == 0) {
                    world.spawnParticle(Particle.CRIT, cx, cy + 0.2, cz,
                        3, 0.25, 0.1, 0.25, 0.04);
                }
            }
            case "claims" -> {
                // White END_ROD rising from center every 4 ticks
                if (particleTick % 4 == 0) {
                    world.spawnParticle(Particle.END_ROD, cx, cy, cz,
                        1, 0.15, 0.02, 0.15, 0.01);
                }
            }
            case "spawner" -> {
                // Purple PORTAL swirling in from the outer ring
                double pa = angle * 1.3;
                world.spawnParticle(Particle.PORTAL,
                    cx + 0.9 * Math.cos(pa), cy + 0.15, cz + 0.9 * Math.sin(pa),
                    4, 0.05, 0.25, 0.05, 0.08);
                // WITCH sparkles scattered around
                if (particleTick % 7 == 0) {
                    world.spawnParticle(Particle.WITCH, cx, cy + 0.4, cz,
                        2, 0.4, 0.2, 0.4, 0);
                }
            }
            case "cosmetic" -> {
                // Pink ENCHANT glitters floating upward
                if (particleTick % 3 == 0) {
                    world.spawnParticle(Particle.ENCHANT, cx, cy + 0.1, cz,
                        3, 0.3, 0.3, 0.3, 0.5);
                }
                // Heart particles every 10 ticks
                if (particleTick % 10 == 0) {
                    world.spawnParticle(Particle.HEART, cx, cy + 0.6, cz,
                        1, 0.2, 0.1, 0.2, 0);
                }
            }
        }
    }

    /** Parse a "world,x,y,z" location key safely (world name may contain commas). */
    private Location parseLocation(String key) {
        int last   = key.lastIndexOf(',');
        int second = last   > 0 ? key.lastIndexOf(',', last - 1)   : -1;
        int third  = second > 0 ? key.lastIndexOf(',', second - 1) : -1;
        if (last < 0 || second < 0 || third < 0) return null;
        try {
            String worldName = key.substring(0, third);
            int x = Integer.parseInt(key.substring(third  + 1, second));
            int y = Integer.parseInt(key.substring(second + 1, last));
            int z = Integer.parseInt(key.substring(last   + 1));
            World world = plugin.getServer().getWorld(worldName);
            return world != null ? new Location(world, x, y, z) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private void loadCrates() {
        cratesCfg = YamlConfiguration.loadConfiguration(cratesFile);
        locationMap.clear();
        for (String type : List.of("eco", "claims", "spawner", "cosmetic")) {
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
