package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Token-Shop: Upgrade-Tokens gegen Geld oder Booster tauschen.
 * Kein physisches Item – alles wird direkt gutgeschrieben.
 */
public class TokenShopGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Eine Eintrag im Token-Shop.
     * @param slot       Inventar-Slot
     * @param icon       Icon-Material
     * @param name       Anzeigename (MiniMessage)
     * @param lore       Beschreibung (MiniMessage-Zeilen)
     * @param cost       Token-Kosten
     * @param type       "money" oder "booster"
     * @param moneyAmt   Geld-Betrag (nur bei type=money)
     * @param boostMult  Booster-Multiplikator (nur bei type=booster)
     * @param boostMin   Booster-Dauer in Minuten (nur bei type=booster)
     */
    private record ShopEntry(
            int slot, Material icon, String name, List<String> lore,
            int cost, String type, long moneyAmt, double boostMult, int boostMin) {}

    private static final String TITLE = "Token-Shop";

    private static final List<ShopEntry> ENTRIES = List.of(
        // ── Geld-Tausch ──────────────────────────────────────────────────────
        new ShopEntry(10, Material.GOLD_NUGGET,
            "<yellow><b>Kleines Bündel",
            List.of("§8────────────────────", "§7Erhalte §e$50.000§7.", "§8────────────────────",
                    "§6Kosten: §e1 Upgrade-Token"),
            1, "money", 50_000, 0, 0),

        new ShopEntry(12, Material.GOLD_INGOT,
            "<gold><b>Mittleres Bündel",
            List.of("§8────────────────────", "§7Erhalte §e$250.000§7.", "§8────────────────────",
                    "§6Kosten: §e3 Upgrade-Token"),
            3, "money", 250_000, 0, 0),

        new ShopEntry(14, Material.GOLD_BLOCK,
            "<yellow><b>Großes Bündel",
            List.of("§8────────────────────", "§7Erhalte §e$1.000.000§7.", "§8────────────────────",
                    "§6Kosten: §e8 Upgrade-Token"),
            8, "money", 1_000_000, 0, 0),

        new ShopEntry(16, Material.NETHERITE_INGOT,
            "<dark_purple><b>Mega-Bündel",
            List.of("§8────────────────────", "§7Erhalte §e$5.000.000§7.", "§8────────────────────",
                    "§6Kosten: §e25 Upgrade-Token"),
            25, "money", 5_000_000, 0, 0),

        // ── Booster-Tausch ────────────────────────────────────────────────────
        new ShopEntry(28, Material.BLAZE_POWDER,
            "<light_purple><b>Kleiner Booster",
            List.of("§8────────────────────", "§7Erhalte einen §dx1.5§7-Booster.", "§730 Minuten Laufzeit.",
                    "§7Wird in deinem Booster-Inventar gespeichert.", "§8────────────────────",
                    "§6Kosten: §e2 Upgrade-Token"),
            2, "booster", 0, 1.5, 30),

        new ShopEntry(30, Material.BLAZE_ROD,
            "<light_purple><b>Standard-Booster",
            List.of("§8────────────────────", "§7Erhalte einen §dx2.0§7-Booster.", "§760 Minuten Laufzeit.",
                    "§7Wird in deinem Booster-Inventar gespeichert.", "§8────────────────────",
                    "§6Kosten: §e5 Upgrade-Token"),
            5, "booster", 0, 2.0, 60),

        new ShopEntry(32, Material.NETHER_STAR,
            "<light_purple><b>Großer Booster",
            List.of("§8────────────────────", "§7Erhalte einen §dx2.5§7-Booster.", "§7120 Minuten Laufzeit.",
                    "§7Wird in deinem Booster-Inventar gespeichert.", "§8────────────────────",
                    "§6Kosten: §e10 Upgrade-Token"),
            10, "booster", 0, 2.5, 120),

        new ShopEntry(34, Material.END_CRYSTAL,
            "<red><b>Mega-Booster",
            List.of("§8────────────────────", "§7Erhalte einen §cx3.0§7-Booster.", "§7240 Minuten Laufzeit.",
                    "§7Wird in deinem Booster-Inventar gespeichert.", "§8────────────────────",
                    "§6Kosten: §e20 Upgrade-Token"),
            20, "booster", 0, 3.0, 240)
    );

    private final PHGenerators plugin;
    private final NamespacedKey keySlot;

    public TokenShopGUI(PHGenerators plugin) {
        this.plugin = plugin;
        this.keySlot = new NamespacedKey(plugin, "tokenshop_slot");
    }

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE));

        // Hintergrund
        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), null);
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        // Token-Anzeige oben mittig
        ItemStack tokenDisplay = makeItem(Material.EMERALD,
                MM.deserialize("<aqua><b>Deine Upgrade-Tokens"),
                List.of("§7Du hast: §b" + data.getUpgradeTokens() + " §7Token(s)",
                        "",
                        "§7Tokens können nicht verkauft werden."));
        inv.setItem(4, tokenDisplay);

        // Shop-Einträge
        for (ShopEntry entry : ENTRIES) {
            boolean canAfford = data.getUpgradeTokens() >= entry.cost();
            ItemStack item = buildEntryItem(entry, canAfford);
            inv.setItem(entry.slot(), item);
        }

        // Zurück-Button
        ItemStack back = makeItem(Material.ARROW, MM.deserialize("<gray>← Zurück"), List.of("§7Schließt den Shop."));
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    private ItemStack buildEntryItem(ShopEntry entry, boolean canAfford) {
        ItemStack item = new ItemStack(entry.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(entry.name()));

        List<Component> lore = entry.lore().stream()
                .map(Component::text)
                .collect(java.util.stream.Collectors.toList());
        lore.add(Component.text(canAfford ? "§a▶ Klicken zum Einlösen" : "§c✗ Nicht genug Tokens"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, entry.slot());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, Component name, List<String> loreStr) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (loreStr != null)
            meta.lore(loreStr.stream().map(Component::text).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(TITLE))) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        Integer slotTag = clicked.getItemMeta().getPersistentDataContainer()
                .get(keySlot, PersistentDataType.INTEGER);
        if (slotTag == null) return;

        ShopEntry entry = ENTRIES.stream()
                .filter(e -> e.slot() == slotTag)
                .findFirst().orElse(null);
        if (entry == null) return;

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (data.getUpgradeTokens() < entry.cost()) {
            player.sendMessage(MM.deserialize("<red>✗ Du hast nicht genug Upgrade-Tokens! (Benötigt: "
                    + entry.cost() + ", Vorhanden: " + data.getUpgradeTokens() + ")"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // Tokens abziehen
        for (int i = 0; i < entry.cost(); i++) data.useUpgradeToken();

        // Belohnung gutschreiben
        switch (entry.type()) {
            case "money" -> {
                data.addMoney(entry.moneyAmt());
                player.sendMessage(MM.deserialize(
                        "<green>✔ Du hast §e$" + String.format("%,d", entry.moneyAmt())
                        + "§a erhalten! §7(" + entry.cost() + " Token verwendet, "
                        + data.getUpgradeTokens() + " übrig)"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
            case "booster" -> {
                plugin.getBoosterManager().giveBooster(player, entry.boostMult(), entry.boostMin());
                player.sendMessage(MM.deserialize(
                        "<light_purple>✔ Booster §dx" + entry.boostMult()
                        + "§d für §f" + entry.boostMin() + " Minuten§d erhalten und gespeichert! "
                        + "§7(" + entry.cost() + " Token verwendet, "
                        + data.getUpgradeTokens() + " übrig)"));
                player.sendMessage(MM.deserialize("<gray>Aktiviere ihn mit §d/gen booster§gray."));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.1f);
            }
        }

        // GUI aktualisieren
        open(player);
    }
}
