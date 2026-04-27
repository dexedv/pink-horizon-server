package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;
import de.pinkhorizon.skyblock.database.GeneratorRepository;
import de.pinkhorizon.skyblock.enums.GeneratorTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herzstück des Generator-Systems.
 * Verwaltet alle platzierten Generatoren, tickt sie und koordiniert Produktion,
 * Auto-Sell, Upgrades und Hologramme.
 */
public class GeneratorManager {

    public static final NamespacedKey GEN_ITEM_KEY =
        new NamespacedKey("ph_skyblock", "generator_item");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;
    private final GeneratorRepository repo;

    /** posKey → Generator */
    private final Map<String, Generator> generators = new ConcurrentHashMap<>();

    public GeneratorManager(PHSkyBlock plugin, GeneratorRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Startet den Tick-Task und den DB-Speicher-Task. */
    public void startTasks() {
        // Produktions-Tick: jede Tick (Generator prüft selbst sein Intervall)
        new BukkitRunnable() {
            @Override public void run() { tickAll(); }
        }.runTaskTimer(plugin, 1L, 1L);

        // Hologramm-Update alle 2 Sekunden
        new BukkitRunnable() {
            @Override public void run() { updateAllHolograms(); }
        }.runTaskTimer(plugin, 40L, 40L);

        // DB-Speicherung alle 30 Sekunden (async)
        new BukkitRunnable() {
            @Override public void run() { saveAll(); }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L);
    }

    // ── Spieler laden / entladen ──────────────────────────────────────────────

    public void loadForPlayer(UUID uuid) {
        repo.loadByUuid(uuid).forEach(g -> generators.put(g.getPosKey(), g));
        // Hologramme spawnen
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Generator g : generators.values()) {
                if (g.getOwnerUuid().equals(uuid) && g.getLocation().getWorld() != null) {
                    plugin.getHologramManager().spawnHologram(g);
                }
            }
        });
    }

    public void saveAndUnloadForPlayer(UUID uuid) {
        Iterator<Map.Entry<String, Generator>> it = generators.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Generator> entry = it.next();
            if (entry.getValue().getOwnerUuid().equals(uuid)) {
                repo.update(entry.getValue());
                plugin.getHologramManager().removeHologram(entry.getKey());
                it.remove();
            }
        }
    }

    public void saveAll() {
        for (Generator gen : generators.values()) {
            repo.update(gen);
        }
    }

    // ── Generator-Item ────────────────────────────────────────────────────────

    public ItemStack createGeneratorItem() {
        ItemStack item = new ItemStack(Material.FURNACE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MM.deserialize("<gold><bold>⚙ Generator</bold>")
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.empty(),
            MM.deserialize("<gray>Platziere diesen Block,").decoration(TextDecoration.ITALIC, false),
            MM.deserialize("<gray>um einen Generator zu starten!").decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            MM.deserialize("<yellow>» <white>Rechtsklick: <yellow>Verwalten").decoration(TextDecoration.ITALIC, false),
            MM.deserialize("<yellow>» <white>Abbauen: <yellow>Item zurückholen").decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer()
            .set(GEN_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGeneratorItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(GEN_ITEM_KEY, PersistentDataType.BYTE);
    }

    // ── Platzieren / Entfernen ────────────────────────────────────────────────

    public Generator placeGenerator(UUID uuid, Location loc) {
        Generator gen = repo.insert(uuid, loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (gen == null) return null;
        generators.put(gen.getPosKey(), gen);

        // Visuellen Block setzen (Furnace)
        loc.getBlock().setType(GeneratorTier.visualMaterial(1));

        // Hologramm spawnen
        plugin.getHologramManager().spawnHologram(gen);

        return gen;
    }

    public void removeGenerator(String world, int x, int y, int z) {
        String key = world + ":" + x + ":" + y + ":" + z;
        generators.remove(key);
        plugin.getHologramManager().removeHologram(key);
        repo.delete(world, x, y, z);
    }

    // ── Abfragen ──────────────────────────────────────────────────────────────

    public Generator getGeneratorAt(String world, int x, int y, int z) {
        return generators.get(world + ":" + x + ":" + y + ":" + z);
    }

    public Generator getGeneratorAt(Location loc) {
        if (loc.getWorld() == null) return null;
        return getGeneratorAt(loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public List<Generator> getGeneratorsOf(UUID uuid) {
        List<Generator> result = new ArrayList<>();
        for (Generator g : generators.values()) {
            if (g.getOwnerUuid().equals(uuid)) result.add(g);
        }
        return result;
    }

    // ── Upgrade ───────────────────────────────────────────────────────────────

    public boolean upgradeGenerator(Player player, Generator gen) {
        if (!gen.canUpgrade()) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Dieser Generator ist bereits auf dem maximalen Level!"));
            return false;
        }

        long cost = gen.getUpgradeCost();
        if (!plugin.getCoinManager().deductCoins(player.getUniqueId(), cost)) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Nicht genug Coins! Benötigt: <gold>"
                + String.format("%,d", cost)));
            return false;
        }

        gen.upgrade();
        repo.update(gen);

        // Block-Material aktualisieren
        Location loc = gen.getLocation();
        if (loc.getWorld() != null) {
            loc.getBlock().setType(GeneratorTier.visualMaterial(gen.getLevel()));
        }

        // Hologramm aktualisieren
        plugin.getHologramManager().updateHologram(gen);

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Generator auf <yellow>Level " + gen.getLevel()
            + " <green>aufgewertet!"));
        player.playSound(player.getLocation(),
            org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);

        // Callbacks
        plugin.getQuestManager().onGeneratorUpgrade(player.getUniqueId());
        plugin.getAchievementManager().checkGeneratorLevelAchievements(player, gen.getLevel());

        return true;
    }

    // ── Auto-Sell toggle ──────────────────────────────────────────────────────

    public void toggleAutoSell(Player player, Generator gen) {
        gen.setAutoSell(!gen.isAutoSell());
        repo.update(gen);
        plugin.getHologramManager().updateHologram(gen);

        if (gen.isAutoSell()) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<green>Auto-Sell <bold>aktiviert</bold>. Items werden automatisch verkauft."));
            plugin.getAchievementManager().onFirstAutoSell(player);
        } else {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<yellow>Auto-Sell <bold>deaktiviert</bold>."));
        }
    }

    // ── Buffer leeren ─────────────────────────────────────────────────────────

    public void collectBuffer(Player player, Generator gen) {
        if (gen.getBuffer().isEmpty()) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<gray>Der Generator-Puffer ist leer."));
            return;
        }

        List<ItemStack> items = gen.collectAll();
        int given = 0;
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(i ->
                    player.getWorld().dropItemNaturally(player.getLocation(), i));
            }
            given++;
        }

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>" + given + " Items aus dem Generator erhalten."));

        plugin.getQuestManager().onGeneratorCollect(player.getUniqueId());
        plugin.getHologramManager().updateHologram(gen);

        repo.update(gen);
    }

    // ── Tick-Logik ────────────────────────────────────────────────────────────

    private void tickAll() {
        for (Generator gen : generators.values()) {
            ItemStack produced = gen.tryProduce();
            if (produced == null) continue;

            if (gen.isAutoSell() && !gen.getBuffer().isEmpty()) {
                long coins = gen.calcAutoSellCoins();
                gen.collectAll();
                if (coins > 0) {
                    plugin.getCoinManager().addCoins(gen.getOwnerUuid(), coins);
                    plugin.getQuestManager().onCoinsEarned(gen.getOwnerUuid(), coins);
                }
            }
        }
    }

    private void updateAllHolograms() {
        for (Generator gen : generators.values()) {
            plugin.getHologramManager().updateHologram(gen);
        }
    }
}
