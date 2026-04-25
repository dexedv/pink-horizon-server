package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpawnerShopGui implements Listener {

    // ── Datenmodelle ──────────────────────────────────────────────────────

    private record SpawnerEntry(EntityType type, String displayName, long price) {}

    private record Tier(int index, String name, TextColor color, Material border, List<SpawnerEntry> entries) {}

    // ── Tier-Definitionen ────────────────────────────────────────────────

    private static final List<Tier> TIERS = List.of(
        new Tier(0, "Basis", TextColor.color(0x55FF55), Material.LIME_STAINED_GLASS_PANE, List.of(
            new SpawnerEntry(EntityType.CHICKEN,  "Huhn-Spawner",      25_000),
            new SpawnerEntry(EntityType.COW,      "Kuh-Spawner",       35_000),
            new SpawnerEntry(EntityType.PIG,      "Schwein-Spawner",   35_000),
            new SpawnerEntry(EntityType.SHEEP,    "Schaf-Spawner",     35_000),
            new SpawnerEntry(EntityType.SQUID,    "Tintenfisch-Spawner", 45_000),
            new SpawnerEntry(EntityType.RABBIT,   "Kaninchen-Spawner", 40_000),
            new SpawnerEntry(EntityType.BAT,      "Fledermaus-Spawner",20_000)
        )),
        new Tier(1, "Fortgeschritten", TextColor.color(0x5555FF), Material.BLUE_STAINED_GLASS_PANE, List.of(
            new SpawnerEntry(EntityType.ZOMBIE,      "Zombie-Spawner",       75_000),
            new SpawnerEntry(EntityType.SKELETON,    "Skelett-Spawner",      75_000),
            new SpawnerEntry(EntityType.SPIDER,      "Spinnen-Spawner",      85_000),
            new SpawnerEntry(EntityType.CAVE_SPIDER, "Höhlenspinnen-Spawner",100_000),
            new SpawnerEntry(EntityType.CREEPER,     "Creeper-Spawner",      120_000),
            new SpawnerEntry(EntityType.SLIME,       "Schleim-Spawner",      150_000),
            new SpawnerEntry(EntityType.WITCH,       "Hexen-Spawner",        175_000),
            new SpawnerEntry(EntityType.DROWNED,     "Ertrunkener-Spawner",  130_000),
            new SpawnerEntry(EntityType.HUSK,        "Leichen-Spawner",       90_000)
        )),
        new Tier(2, "Selten", TextColor.color(0xAA00AA), Material.PURPLE_STAINED_GLASS_PANE, List.of(
            new SpawnerEntry(EntityType.BLAZE,           "Blaze-Spawner",           350_000),
            new SpawnerEntry(EntityType.MAGMA_CUBE,      "Magmawürfel-Spawner",     300_000),
            new SpawnerEntry(EntityType.ENDERMAN,        "Enderman-Spawner",        500_000),
            new SpawnerEntry(EntityType.WITHER_SKELETON, "Wither-Skelett-Spawner",  750_000),
            new SpawnerEntry(EntityType.PIGLIN,          "Piglin-Spawner",          400_000),
            new SpawnerEntry(EntityType.ZOMBIFIED_PIGLIN,"Zombifizierter Piglin-Spawner", 450_000),
            new SpawnerEntry(EntityType.GHAST,           "Ghast-Spawner",           900_000),
            new SpawnerEntry(EntityType.SILVERFISH,      "Silberfisch-Spawner",     600_000)
        )),
        new Tier(3, "Legendär", TextColor.color(0xFFAA00), Material.ORANGE_STAINED_GLASS_PANE, List.of(
            new SpawnerEntry(EntityType.IRON_GOLEM,    "Eisengolem-Spawner",    1_500_000),
            new SpawnerEntry(EntityType.GUARDIAN,      "Wächter-Spawner",       2_000_000),
            new SpawnerEntry(EntityType.ELDER_GUARDIAN,"Alter-Wächter-Spawner", 3_500_000),
            new SpawnerEntry(EntityType.PIGLIN_BRUTE,  "Piglin-Brute-Spawner",  2_500_000),
            new SpawnerEntry(EntityType.SHULKER,       "Shulker-Spawner",       3_000_000)
        ))
    );

    // ── Holder ────────────────────────────────────────────────────────────

    public static class ShopHolder implements InventoryHolder {
        public final int tierIndex;
        ShopHolder(int tierIndex) { this.tierIndex = tierIndex; }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Felder ────────────────────────────────────────────────────────────

    private final PHSurvival plugin;
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.GERMAN);

    public SpawnerShopGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── GUI öffnen ────────────────────────────────────────────────────────

    public void open(Player player, int tierIndex) {
        Tier tier = TIERS.get(Math.clamp(tierIndex, 0, TIERS.size() - 1));
        int rows = 6;
        Inventory inv = Bukkit.createInventory(new ShopHolder(tierIndex), rows * 9,
            Component.text("✦ Spawner-Shop ", TextColor.color(0xFF69B4), TextDecoration.BOLD)
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .append(Component.text(tier.name(), tier.color(), TextDecoration.BOLD)));

        // Rahmen
        ItemStack border = pane(tier.border());
        for (int i = 0; i < 9; i++)          inv.setItem(i, border);
        for (int i = 45; i < 54; i++)         inv.setItem(i, border);
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, border);
            inv.setItem(i * 9 + 8, border);
        }

        // Tier-Navigation (Slot 45-48: Tier-Buttons)
        for (int t = 0; t < TIERS.size(); t++) {
            Tier bt = TIERS.get(t);
            boolean active = t == tierIndex;
            ItemStack btn = makeItem(
                active ? Material.NETHER_STAR : Material.GRAY_DYE,
                Component.text((active ? "▶ " : "") + "Tier " + (t + 1) + ": " + bt.name(),
                    active ? bt.color() : NamedTextColor.GRAY,
                    TextDecoration.BOLD),
                List.of(
                    Component.text(bt.entries().size() + " Spawner", NamedTextColor.GRAY),
                    active ? Component.text("◀ Aktive Seite", NamedTextColor.GREEN)
                           : Component.text("Klicken zum Öffnen", NamedTextColor.YELLOW)
                )
            );
            inv.setItem(45 + t, btn);
        }

        // Schließen-Button (Slot 53)
        inv.setItem(53, makeItem(Material.BARRIER,
            Component.text("✖ Schließen", NamedTextColor.RED, TextDecoration.BOLD),
            List.of()));

        // Spawner-Items platzieren (Slots 10-43, Randslots überspringen)
        int[] itemSlots = buildItemSlots(tier.entries().size());
        List<SpawnerEntry> entries = tier.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (i >= itemSlots.length) break;
            inv.setItem(itemSlots[i], buildSpawnerItem(entries.get(i)));
        }

        player.openInventory(inv);
    }

    /** Berechnet zentrierte Slots für n Items im Bereich 10-43 (ohne Rand). */
    private int[] buildItemSlots(int count) {
        // Nutzbare Slots: 4 Zeilen × 7 Spalten = 28
        int[] available = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                available[idx++] = row * 9 + col;
            }
        }
        if (count >= 28) return available;
        // Zentrieren: Items gleichmäßig verteilen
        int[] result = new int[count];
        int offset = (28 - count) / 2;
        for (int i = 0; i < count; i++) result[i] = available[offset + i];
        return result;
    }

    // ── Item-Bau ──────────────────────────────────────────────────────────

    private ItemStack buildSpawnerItem(SpawnerEntry entry) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta bsm = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) bsm.getBlockState();
        cs.setSpawnedType(entry.type());
        bsm.setBlockState(cs);

        bsm.displayName(Component.text("⚙ " + entry.displayName(),
            TextColor.color(0xFF69B4), TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Entity: ", NamedTextColor.GRAY)
            .append(Component.text(entry.type().name().replace("_", " "), NamedTextColor.WHITE))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Preis:  ", NamedTextColor.GRAY)
            .append(Component.text(FMT.format(entry.price()) + " Coins", TextColor.color(0xFFD700), TextDecoration.BOLD))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  ▶ Klicken zum Kaufen", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        bsm.lore(lore);
        item.setItemMeta(bsm);
        return item;
    }

    private ItemStack pane(Material mat) {
        ItemStack p = new ItemStack(mat);
        ItemMeta m = p.getItemMeta();
        m.displayName(Component.empty());
        p.setItemMeta(m);
        return p;
    }

    private ItemStack makeItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (Component c : lore) l.add(c.decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        item.setItemMeta(meta);
        return item;
    }

    // ── Events ────────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) return;

        event.setCancelled(true); // Immer canceln → kein Item-Diebstahl möglich

        // Klick außerhalb des oberen Inventars ignorieren
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        int tierIndex = holder.tierIndex;
        Tier tier = TIERS.get(tierIndex);

        // Schließen
        if (slot == 53) { player.closeInventory(); return; }

        // Tier-Navigation (Slots 45-48)
        for (int t = 0; t < TIERS.size(); t++) {
            if (slot == 45 + t && t != tierIndex) {
                open(player, t);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                return;
            }
        }

        // Spawner kaufen
        int[] itemSlots = buildItemSlots(tier.entries().size());
        for (int i = 0; i < itemSlots.length; i++) {
            if (slot != itemSlots[i]) continue;
            SpawnerEntry entry = tier.entries().get(i);
            handlePurchase(player, entry, tierIndex);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    // ── Kauf-Logik ────────────────────────────────────────────────────────

    private void handlePurchase(Player player, SpawnerEntry entry, int tierIndex) {
        long balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        if (balance < entry.price()) {
            long missing = entry.price() - balance;
            player.sendMessage(Component.text("✖ Zu wenig Coins! Dir fehlen noch ", NamedTextColor.RED)
                .append(Component.text(FMT.format(missing) + " Coins", TextColor.color(0xFFD700), TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }

        boolean success = plugin.getEconomyManager().withdraw(player.getUniqueId(), entry.price());
        if (!success) {
            player.sendMessage(Component.text("✖ Kauf fehlgeschlagen. Bitte erneut versuchen.", NamedTextColor.RED));
            return;
        }

        // Spawner in Inventar legen oder zu Füßen droppen
        ItemStack spawner = buildSpawnerItem(entry);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), spawner);
            player.sendMessage(Component.text("⚠ Inventar voll – Spawner wurde gedropt!", NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(spawner);
        }

        player.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
            .append(Component.text(entry.displayName(), TextColor.color(0xFF69B4), TextDecoration.BOLD))
            .append(Component.text(" gekauft! Verbleibend: ", NamedTextColor.GREEN))
            .append(Component.text(FMT.format(plugin.getEconomyManager().getBalance(player.getUniqueId())) + " Coins",
                TextColor.color(0xFFD700), TextDecoration.BOLD)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);

        // GUI aktualisieren (neue Balance in Lore)
        open(player, tierIndex);
    }
}
