package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Verwaltet das Platzieren, Entfernen und Upgraden von Generatoren.
 */
public class GeneratorManager {

    private final PHGenerators plugin;

    /** locationKey → Generator (für schnellen Block-Lookup) */
    private final Map<String, PlacedGenerator> byLocation = new HashMap<>();

    public static final NamespacedKey KEY_GENERATOR_TYPE  = new NamespacedKey("ph-generators", "generator_type");
    public static final NamespacedKey KEY_GENERATOR_LEVEL = new NamespacedKey("ph-generators", "generator_level");

    public GeneratorManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Laden beim Join ──────────────────────────────────────────────────────

    public void loadForPlayer(PlayerData data) {
        List<PlacedGenerator> gens = plugin.getRepository().loadGenerators(data.getUuid());
        data.getGenerators().clear();
        data.getGenerators().addAll(gens);
        for (PlacedGenerator g : gens) {
            byLocation.put(g.locationKey(), g);
        }
        // Hologramme + Block-Wiederherstellung erfolgen in loadHolograms(),
        // das erst nach dem Laden der Inselwelt aufgerufen wird.
    }

    /**
     * Stellt fehlende Generator-Blöcke wieder her und spawnt Hologramme.
     * Muss aufgerufen werden NACHDEM die Inselwelt geladen ist.
     */
    public void loadHolograms(PlayerData data) {
        for (PlacedGenerator g : data.getGenerators()) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(g.getWorld());
            if (world != null) {
                // Block wiederherstellen falls er fehlt
                org.bukkit.block.Block block = world.getBlockAt(g.getX(), g.getY(), g.getZ());
                if (block.getType() != g.getType().getBlock()) {
                    block.setType(g.getType().getBlock());
                }
            }
            plugin.getHologramManager().spawnHologram(g);
        }
    }

    public void unloadForPlayer(PlayerData data) {
        for (PlacedGenerator g : data.getGenerators()) {
            byLocation.remove(g.locationKey());
            plugin.getHologramManager().removeHologram(g);
        }
    }

    // ── Platzieren ───────────────────────────────────────────────────────────

    /**
     * Registriert einen Generator an der gegebenen Location.
     * Wird von GeneratorBlockListener aufgerufen.
     */
    public boolean placeGenerator(Player player, Location loc, GeneratorType type) {
        return placeGenerator(player, loc, type, 1);
    }

    public boolean placeGenerator(Player player, Location loc, GeneratorType type, int level) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return false;

        int baseSlots = plugin.getConfig().getInt("max-generators", 10);
        int perPrestige = plugin.getConfig().getInt("generator-slot-per-prestige", 2);
        if (data.getGenerators().size() >= data.maxGeneratorSlots(baseSlots, perPrestige)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Du hast dein Generator-Limit erreicht! Mache mehr Prestige für mehr Slots."));
            return false;
        }

        String generatorWorld = plugin.getConfig().getString("generator-world", "");
        if (!generatorWorld.isBlank() && loc.getWorld() != null
                && !loc.getWorld().getName().equals(generatorWorld)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Generatoren können nur in der Welt <yellow>" + generatorWorld + "</yellow> platziert werden."));
            return false;
        }

        PlacedGenerator gen = new PlacedGenerator(
                player.getUniqueId(),
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                type, Math.max(1, level)
        );

        data.getGenerators().add(gen);
        byLocation.put(gen.locationKey(), gen);
        plugin.getRepository().insertGenerator(gen);
        plugin.getHologramManager().spawnHologram(gen);
        plugin.getSynergyManager().recalculate(gen, data);

        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>✔ <white>" + type.getDisplayName() + " <green>platziert!"));

        plugin.getAchievementManager().track(data, "first_generator", 1);
        return true;
    }

    // ── Entfernen ────────────────────────────────────────────────────────────

    /**
     * Entfernt einen Generator (Block wurde abgebaut).
     */
    public boolean removeGenerator(Location loc, UUID playerUUID) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        PlacedGenerator gen = byLocation.remove(key);
        if (gen == null) return false;

        PlayerData data = plugin.getPlayerDataMap().get(gen.getOwnerUUID());
        if (data != null) {
            data.getGenerators().remove(gen);
            plugin.getSynergyManager().recalculateAll(data);
        }
        plugin.getHologramManager().removeHologram(gen);
        plugin.getRepository().deleteGenerator(gen);
        return true;
    }

    /**
     * Wie removeGenerator, aber gibt das Generator-Item mit erhaltenem Level zurück.
     * Wird von GeneratorBlockListener aufgerufen.
     */
    public ItemStack removeGeneratorWithDrop(Location loc, UUID playerUUID) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        PlacedGenerator gen = byLocation.remove(key);
        if (gen == null) return null;

        PlayerData data = plugin.getPlayerDataMap().get(gen.getOwnerUUID());
        if (data != null) {
            data.getGenerators().remove(gen);
            plugin.getSynergyManager().recalculateAll(data);
        }
        plugin.getHologramManager().removeHologram(gen);
        plugin.getRepository().deleteGenerator(gen);

        return createGeneratorItem(gen.getType(), gen.getLevel(), 1);
    }

    // ── Upgraden ─────────────────────────────────────────────────────────────

    public UpgradeResult upgrade(Player player, PlacedGenerator gen) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return UpgradeResult.NO_DATA;

        if (!gen.getOwnerUUID().equals(player.getUniqueId())) return UpgradeResult.NOT_OWNER;

        int maxLevel = data.maxGeneratorLevel();
        if (gen.getLevel() >= maxLevel) return UpgradeResult.MAX_LEVEL;

        long cost = gen.upgradeCost();
        if (!data.takeMoney(cost)) return UpgradeResult.NO_MONEY;

        gen.setLevel(gen.getLevel() + 1);
        data.incrementUpgrades();
        plugin.getRepository().updateGeneratorLevel(gen);
        plugin.getHologramManager().updateHologram(gen, data);
        plugin.getAchievementManager().track(data, "upgrade_100", 1);
        return UpgradeResult.SUCCESS;
    }

    // ── Tier-Upgrade ─────────────────────────────────────────────────────────

    /**
     * Wertet einen Generator auf das nächste Tier auf (z.B. Cobblestone → Iron).
     * Nur möglich wenn der Generator maxLevel erreicht hat.
     * Kosten = Kaufpreis des Ziel-Tiers. Level wird auf 1 zurückgesetzt.
     */
    public TierUpgradeResult tierUpgrade(Player player, PlacedGenerator gen) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return TierUpgradeResult.NO_DATA;
        if (!gen.getOwnerUUID().equals(player.getUniqueId())) return TierUpgradeResult.NOT_OWNER;

        int maxLevel = data.maxGeneratorLevel();
        if (gen.getLevel() < maxLevel) return TierUpgradeResult.NOT_MAX_LEVEL;

        GeneratorType nextTier = gen.getType().getNextTier();
        if (nextTier == null) return TierUpgradeResult.NO_NEXT_TIER;

        long cost = gen.getType().getTierUpgradeCost();
        if (cost < 0 || !data.takeMoney(cost)) return TierUpgradeResult.NO_MONEY;

        gen.setType(nextTier);
        gen.setLevel(1);

        // Block in der Welt auf das neue Material aktualisieren
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(gen.getWorld());
        if (world != null) {
            world.getBlockAt(gen.getX(), gen.getY(), gen.getZ()).setType(nextTier.getBlock());
        }

        plugin.getRepository().updateGeneratorLevel(gen);
        plugin.getHologramManager().updateHologram(gen, data);
        plugin.getSynergyManager().recalculate(gen, data);
        return TierUpgradeResult.SUCCESS;
    }

    // ── Fusion ───────────────────────────────────────────────────────────────

    /**
     * Fusioniert 3 max-level Generatoren desselben Typs zu einem Mega-Generator.
     * Der Spieler wählt die 3 Generatoren via Kommando (Positionen werden übergeben).
     */
    public FuseResult fuse(Player player, List<PlacedGenerator> targets) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return FuseResult.NO_DATA;
        if (targets.size() != 3) return FuseResult.WRONG_COUNT;

        GeneratorType baseType = targets.get(0).getType();
        int maxLevel = data.maxGeneratorLevel();

        // Fusions-Ergebnis ermitteln (Normal→Mega, Mega→Ultra, Ultra→null)
        GeneratorType resultType = baseType.getNextFusionTier();
        if (resultType == null) return FuseResult.ALREADY_MEGA;

        for (PlacedGenerator g : targets) {
            if (!g.getOwnerUUID().equals(player.getUniqueId())) return FuseResult.NOT_OWNER;
            if (g.getType() != baseType) return FuseResult.DIFFERENT_TYPE;
            if (g.getLevel() < maxLevel) return FuseResult.NOT_MAX_LEVEL;
        }

        // Ersten Generator zum Ergebnis-Typ machen, die anderen zwei löschen
        PlacedGenerator keeper = targets.get(0);
        keeper.setType(resultType);
        keeper.setLevel(1);

        // Block des Keepers auf neues Material aktualisieren (wichtig für Ultra-Tiers)
        org.bukkit.World keeperWorld = org.bukkit.Bukkit.getWorld(keeper.getWorld());
        if (keeperWorld != null) {
            keeperWorld.getBlockAt(keeper.getX(), keeper.getY(), keeper.getZ())
                    .setType(resultType.getBlock());
        }

        plugin.getRepository().updateGeneratorLevel(keeper);
        plugin.getHologramManager().updateHologram(keeper, data);

        for (int i = 1; i < 3; i++) {
            PlacedGenerator g = targets.get(i);
            data.getGenerators().remove(g);
            byLocation.remove(g.locationKey());
            plugin.getHologramManager().removeHologram(g);
            plugin.getRepository().deleteGenerator(g);
            // Block in der Welt entfernen
            if (g.getWorld() != null) {
                org.bukkit.Bukkit.getWorld(g.getWorld()).getBlockAt(g.getX(), g.getY(), g.getZ())
                        .setType(org.bukkit.Material.AIR);
            }
        }

        plugin.getAchievementManager().track(data, "fuse_first", 1);
        return FuseResult.SUCCESS;
    }

    // ── Item-Erstellung ──────────────────────────────────────────────────────

    /** Erstellt ein Generator-Item mit NBT-Tag für Typ und Level */
    public ItemStack createGeneratorItem(GeneratorType type, int level, int amount) {
        ItemStack item = new ItemStack(type.getBlock(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(type.getDisplayName()));
        meta.lore(List.of(
                MiniMessage.miniMessage().deserialize("<gray>Typ: <white>" + type.name()),
                MiniMessage.miniMessage().deserialize("<gray>Level: <aqua>" + level),
                MiniMessage.miniMessage().deserialize("<gray>Einkommen: <green>$"
                        + String.format("%.1f", type.incomeAt(level)) + "/s"),
                MiniMessage.miniMessage().deserialize("<yellow>Platziere diesen Block um den Generator zu aktivieren!")
        ));
        meta.getPersistentDataContainer().set(KEY_GENERATOR_TYPE, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(KEY_GENERATOR_LEVEL, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        return item;
    }

    /** @deprecated Benutze createGeneratorItem(type, level, amount) */
    public ItemStack createGeneratorItem(GeneratorType type, int amount) {
        return createGeneratorItem(type, 1, amount);
    }

    /** Prüft ob ein ItemStack ein Generator-Item ist und gibt den Typ zurück */
    public GeneratorType getGeneratorType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String typeStr = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_GENERATOR_TYPE, PersistentDataType.STRING);
        if (typeStr == null) return null;
        try {
            return GeneratorType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Liest das gespeicherte Level aus einem Generator-Item (Standard: 1) */
    public int getGeneratorLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_GENERATOR_LEVEL, PersistentDataType.INTEGER);
        return level != null ? level : 1;
    }

    // ── Lookup ───────────────────────────────────────────────────────────────

    public PlacedGenerator getAt(Location loc) {
        if (loc.getWorld() == null) return null;
        return byLocation.get(loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ());
    }

    public boolean isGenerator(Location loc) {
        return getAt(loc) != null;
    }

    public Map<String, PlacedGenerator> getAllByLocation() {
        return Collections.unmodifiableMap(byLocation);
    }

    // ── Ergebnis-Enums ───────────────────────────────────────────────────────

    public enum UpgradeResult {
        SUCCESS, NO_DATA, NOT_OWNER, MAX_LEVEL, NO_MONEY
    }

    public enum TierUpgradeResult {
        SUCCESS, NO_DATA, NOT_OWNER, NOT_MAX_LEVEL, NO_NEXT_TIER, NO_MONEY
    }

    public enum FuseResult {
        SUCCESS, NO_DATA, NOT_OWNER, WRONG_COUNT, DIFFERENT_TYPE, NOT_MAX_LEVEL, ALREADY_MEGA, NO_MEGA_TIER
    }
}
