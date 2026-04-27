package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.data.StoredBooster;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop-GUI: Alle 6 Basis-Generator-Typen kaufen + Booster (gespeichert).
 * 54-Slot Layout:
 *   Row 0: Info-Item (Slot 4)
 *   Row 1: 6 Generator-Typen (Slots 10–15)
 *   Row 3: 3 Booster-Items (Slots 29, 31, 33) → werden ins Booster-Inventar gelegt
 *   Row 5: Rang-Info (Slot 48), Schließen (Slot 49)
 */
public class ShopGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "Generator-Shop";

    // Kaufbare Generator-Typen im Shop
    private static final GeneratorType[] GEN_TYPES = {
        GeneratorType.COBBLESTONE
    };
    private static final int[] GEN_SLOTS = {13};

    // Booster-Definitionen (werden ins Inventar gelegt, nicht sofort aktiviert)
    private static final int[]      BOOSTER_SLOTS  = {29, 31, 33};
    private static final double[]   BOOSTER_MULT   = {1.5, 2.0, 3.0};
    private static final int[]      BOOSTER_MINS   = {30, 60, 30};
    private static final long[]     BOOSTER_COST   = {25_000L, 75_000L, 200_000L};
    private static final Material[] BOOSTER_MATS   = {
        Material.BLAZE_POWDER, Material.BLAZE_ROD, Material.FIRE_CHARGE
    };

    public ShopGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<gold>" + TITLE));

        // ── Hintergrund ──────────────────────────────────────────────────────
        ItemStack bg = filler(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // ── Sektion: Generatoren ─────────────────────────────────────────────
        ItemStack genHeader = filler(Material.LIME_STAINED_GLASS_PANE, "<green>── Generatoren ──");
        for (int i = 9; i <= 17; i++) inv.setItem(i, genHeader);

        for (int i = 0; i < GEN_TYPES.length; i++) {
            GeneratorType type = GEN_TYPES[i];
            boolean canAfford = data != null && (type.getBuyPrice() == 0 || data.getMoney() >= type.getBuyPrice());
            inv.setItem(GEN_SLOTS[i], buildGenItem(type, canAfford, data));
        }

        // ── Sektion: Shard-Generator ─────────────────────────────────────────
        ItemStack shardHeader = filler(Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>── Spezial-Generator ──");
        for (int i = 18; i <= 26; i++) inv.setItem(i, shardHeader);
        inv.setItem(22, buildShardGenItem(data));

        // ── Sektion: Booster ─────────────────────────────────────────────────
        ItemStack boostHeader = filler(Material.ORANGE_STAINED_GLASS_PANE, "<gold>── Booster kaufen ──");
        for (int i = 27; i <= 35; i++) inv.setItem(i, boostHeader);

        for (int i = 0; i < BOOSTER_SLOTS.length; i++) {
            boolean canAfford = data != null && data.getMoney() >= BOOSTER_COST[i];
            inv.setItem(BOOSTER_SLOTS[i], buildBoosterItem(
                BOOSTER_MULT[i], BOOSTER_MINS[i], BOOSTER_COST[i], BOOSTER_MATS[i], canAfford));
        }

        // ── Info und Navigation ──────────────────────────────────────────────
        inv.setItem(4, buildInfoItem(data));
        inv.setItem(48, buildRankItem(data));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(MM.deserialize("<red>Schließen"));
        close.setItemMeta(cm);
        inv.setItem(49, close);

        // Booster-Inventar-Shortcut
        ItemStack boosterBtn = new ItemStack(Material.ENDER_CHEST);
        ItemMeta bm = boosterBtn.getItemMeta();
        bm.displayName(MM.deserialize("<gold>⚡ Meine Booster"));
        bm.lore(List.of(
            MM.deserialize("<gray>Gespeicherte Booster: <white>" + (data != null ? data.getStoredBoosters().size() : 0)),
            MM.deserialize("<dark_gray>Öffnet /gen booster"),
            MM.deserialize(""),
            MM.deserialize("<yellow>▶ Klick zum Öffnen")
        ));
        boosterBtn.setItemMeta(bm);
        inv.setItem(50, boosterBtn);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<gold>" + TITLE))) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 50) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBoosterGUI().open(player));
            return;
        }

        // Generator kaufen
        for (int i = 0; i < GEN_SLOTS.length; i++) {
            if (slot == GEN_SLOTS[i]) {
                buyGenerator(player, data, GEN_TYPES[i]);
                open(player);
                return;
            }
        }

        // Shard-Generator kaufen
        if (slot == 22) {
            buyGenerator(player, data, GeneratorType.SHARD_GENERATOR);
            open(player);
            return;
        }

        // Booster kaufen
        for (int i = 0; i < BOOSTER_SLOTS.length; i++) {
            if (slot == BOOSTER_SLOTS[i]) {
                purchaseBooster(player, data, BOOSTER_MULT[i], BOOSTER_MINS[i], BOOSTER_COST[i]);
                return;
            }
        }
    }

    // ── Logik ────────────────────────────────────────────────────────────────

    private void buyGenerator(Player player, PlayerData data, GeneratorType type) {
        if (type.isShardGenerator() && data.hasShardGenerator()) {
            player.sendMessage(MM.deserialize("<red>Du besitzt bereits einen Shard-Generator!"));
            return;
        }
        if (type.getBuyPrice() == 0) {
            player.getInventory().addItem(plugin.getGeneratorManager().createGeneratorItem(type, 1));
            player.sendMessage(MM.deserialize("<green>✔ " + type.getDisplayName() + " <green>erhalten!"));
            return;
        }
        if (!data.takeMoney(type.getBuyPrice())) {
            player.sendMessage(MM.deserialize("<red>Nicht genug Geld! Benötigt: <yellow>$"
                    + MoneyManager.formatMoney(type.getBuyPrice())
                    + " <red>| Du hast: <yellow>$" + MoneyManager.formatMoney(data.getMoney())));
            return;
        }
        player.getInventory().addItem(plugin.getGeneratorManager().createGeneratorItem(type, 1));
        player.sendMessage(MM.deserialize("<green>✔ " + type.getDisplayName() + " <green>gekauft!"));
    }

    /**
     * Kauft einen Booster mit Ingame-Geld und legt ihn ins Booster-Inventar (nicht sofort aktivieren).
     */
    private void purchaseBooster(Player player, PlayerData data, double mult, int minutes, long cost) {
        if (data.getMoney() < cost) {
            player.sendMessage(MM.deserialize("<red>Nicht genug Geld! Benötigt: <yellow>$"
                    + MoneyManager.formatMoney(cost)));
            return;
        }
        data.takeMoney(cost);
        data.addStoredBooster(new StoredBooster(mult, minutes));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().savePlayer(data));
        player.sendMessage(MM.deserialize(
                "<green>✔ Booster erworben: <gold>x" + mult + " <gray>(" + minutes + " Min.)\n"
                + "<gray>Öffne dein Booster-Inventar: <yellow>/gen booster"));
        open(player);
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    private ItemStack buildGenItem(GeneratorType type, boolean canAfford, PlayerData data) {
        ItemStack item = new ItemStack(type.getBlock());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(type.getDisplayName()));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Basis-Einkommen: <green>$" + (long) type.getBaseIncomePerSec() + "/s"));
        lore.add(MM.deserialize("<gray>Upgrade-Basis: <yellow>$" + MoneyManager.formatMoney(type.getBaseUpgradeCost())));
        if (data != null && data.getPrestige() > 0) {
            double mult = data.prestigeMultiplier() * data.getRankMultiplier();
            long effective = Math.round(type.getBaseIncomePerSec() * mult);
            lore.add(MM.deserialize("<gray>Mit Boni: <green>$" + MoneyManager.formatMoney(effective) + "/s"));
        }
        lore.add(MM.deserialize(""));
        if (type.getBuyPrice() == 0) {
            lore.add(MM.deserialize("<green>★ KOSTENLOS"));
        } else {
            lore.add(MM.deserialize((canAfford ? "<green>" : "<red>") + "Preis: $"
                    + MoneyManager.formatMoney(type.getBuyPrice())));
        }
        lore.add(MM.deserialize(""));
        lore.add(MM.deserialize(canAfford ? "<yellow>▶ Klick zum Kaufen" : "<red>✗ Zu wenig Geld"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBoosterItem(double mult, int minutes, long cost, Material mat, boolean canAfford) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold>⚡ x" + mult + " Booster"));
        meta.lore(List.of(
                MM.deserialize("<gray>Multiplikator: <gold>x" + mult),
                MM.deserialize("<gray>Dauer: <yellow>" + minutes + " Minuten"),
                MM.deserialize((canAfford ? "<green>" : "<red>") + "Preis: $" + MoneyManager.formatMoney(cost)),
                MM.deserialize(""),
                MM.deserialize("<dark_gray>Wird ins Booster-Inventar gelegt."),
                MM.deserialize("<dark_gray>Aktivieren mit <yellow>/gen booster"),
                MM.deserialize(""),
                MM.deserialize(canAfford ? "<yellow>▶ Klick zum Kaufen" : "<red>✗ Zu wenig Geld")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold><bold>Dein Konto</bold>"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (data != null) {
            lore.add(MM.deserialize("<gray>Guthaben: <green>$" + MoneyManager.formatMoney(data.getMoney())));
            lore.add(MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige()));
            lore.add(MM.deserialize("<gray>Max Level: <aqua>" + data.maxGeneratorLevel()));
            lore.add(MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size()));
            if (data.hasActiveBooster()) {
                long rem = data.getBoosterExpiry() - System.currentTimeMillis() / 1000;
                lore.add(MM.deserialize("<gold>⚡ Booster: <yellow>x" + data.getBoosterMultiplier()
                        + " <gray>(" + rem / 60 + "m " + rem % 60 + "s)"));
            }
            if (!data.getStoredBoosters().isEmpty()) {
                lore.add(MM.deserialize("<gray>Gespeicherte Booster: <aqua>" + data.getStoredBoosters().size()));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRankItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (data == null) {
            meta.displayName(MM.deserialize("<gray>Kein Rang"));
            item.setItemMeta(meta);
            return item;
        }
        String group = data.getLpGroup();
        String rankColor = switch (group) {
            case "nexus"    -> "<gold>";
            case "catalyst" -> "<light_purple>";
            case "rune"     -> "<dark_purple>";
            default         -> "<gray>";
        };
        String rankName = switch (group) {
            case "nexus"    -> "Nexus";
            case "catalyst" -> "Catalyst";
            case "rune"     -> "Rune";
            default         -> "Spieler";
        };
        meta.displayName(MM.deserialize(rankColor + "<bold>✦ " + rankName + "-Rang</bold>"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        int bonusPct = (int) ((data.getRankMultiplier() - 1.0) * 100);
        if (bonusPct > 0)
            lore.add(MM.deserialize("<gray>Einkommensbonus: <green>+" + bonusPct + "%"));
        if (data.getRankExtraSlots() > 0)
            lore.add(MM.deserialize("<gray>Extra Slots: <white>+" + data.getRankExtraSlots()));
        if (data.rankAllowsBulkUpgrade())
            lore.add(MM.deserialize("<aqua>✔ Bulk-Upgrade aktiv"));
        if (data.rankAllowsAutoUpgrade())
            lore.add(MM.deserialize("<aqua>✔ Auto-Upgrade aktiv"));
        if (group.equals("default"))
            lore.add(MM.deserialize("<dark_gray>Ränge gibt es im Tebex-Shop!"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildShardGenItem(PlayerData data) {
        GeneratorType type = GeneratorType.SHARD_GENERATOR;
        boolean owned    = data != null && data.hasShardGenerator();
        boolean canAfford = data != null && data.getMoney() >= type.getBuyPrice();

        ItemStack item = new ItemStack(type.getBlock());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(type.getDisplayName()));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Produziert <light_purple>Mining-Shards</light_purple> <gray>passiv"));
        lore.add(MM.deserialize("<gray>Basis: <light_purple>1.0 Shard/s"));
        lore.add(MM.deserialize("<gray>Skaliert mit Level & Prestige"));
        lore.add(MM.deserialize(""));
        lore.add(MM.deserialize("<yellow>⚠ Max. <white>1 <yellow>pro Spieler"));
        lore.add(MM.deserialize(""));
        if (owned) {
            lore.add(MM.deserialize("<green>✔ Bereits besessen"));
        } else {
            lore.add(MM.deserialize((canAfford ? "<green>" : "<red>") + "Preis: $"
                    + MoneyManager.formatMoney(type.getBuyPrice())));
            if (!canAfford) lore.add(MM.deserialize("<red>Nicht genug Geld"));
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<yellow>▶ Klick zum Kaufen"));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }
}
