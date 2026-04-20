package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class BankGui implements Listener {

    private static final String TITLE = "§6§lBank";

    private final PHSurvival plugin;

    public BankGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ───────────────────────────────────────────────────────────

    public void open(Player player) {
        long wallet = plugin.getEconomyManager().getBalance(player.getUniqueId());
        long bank   = plugin.getBankManager().getBalance(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // ── Infoleiste (Zeile 1) ───────────────────────────────────────
        inv.setItem(11, make(Material.GOLD_INGOT,
                txt("Wallet", NamedTextColor.YELLOW),
                List.of(
                    txt(wallet + " Coins", NamedTextColor.WHITE),
                    empty(),
                    txt("Verfügbares Guthaben", NamedTextColor.GRAY)
                )));

        inv.setItem(13, make(Material.CLOCK,
                txt("Zinsen", NamedTextColor.GOLD),
                List.of(
                    txt("2% täglich", NamedTextColor.WHITE),
                    txt("Basis: max. 100.000 Coins", NamedTextColor.GRAY),
                    empty(),
                    txt("Zinsen werden täglich", NamedTextColor.DARK_GRAY),
                    txt("automatisch gutgeschrieben.", NamedTextColor.DARK_GRAY)
                )));

        inv.setItem(15, make(Material.GOLD_BLOCK,
                txt("Bank", NamedTextColor.GOLD),
                List.of(
                    txt(bank + " Coins", NamedTextColor.WHITE),
                    empty(),
                    txt("Bankguthaben (sicher, verzinst)", NamedTextColor.GRAY)
                )));

        // ── Einzahlen (Zeile 3) ───────────────────────────────────────
        inv.setItem(27, make(Material.LIME_WOOL,
                txt("💸 Einzahlen", NamedTextColor.GREEN),
                List.of(txt("Coins vom Wallet auf die Bank", NamedTextColor.GRAY))));

        long[] depAmounts = {100, 1_000, 10_000, 100_000};
        for (int i = 0; i < depAmounts.length; i++) {
            long amt = depAmounts[i];
            boolean canAfford = wallet >= amt;
            inv.setItem(28 + i, make(Material.EMERALD,
                    txt("+ " + fmt(amt) + " Coins", canAfford ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                    List.of(
                        txt("Wallet: " + fmt(wallet), NamedTextColor.GRAY),
                        canAfford ? txt("Klicken zum Einzahlen", NamedTextColor.GREEN)
                                  : txt("Nicht genug Coins!", NamedTextColor.RED)
                    )));
        }
        // Alles einzahlen
        inv.setItem(32, make(Material.DIAMOND,
                txt("+ Alles einzahlen", wallet > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                List.of(
                    txt("Wallet: " + fmt(wallet) + " Coins", NamedTextColor.GRAY),
                    wallet > 0 ? txt("Alles einzahlen", NamedTextColor.GREEN)
                               : txt("Wallet ist leer!", NamedTextColor.RED)
                )));

        // ── Abheben (Zeile 4) ─────────────────────────────────────────
        inv.setItem(36, make(Material.RED_WOOL,
                txt("💰 Abheben", NamedTextColor.RED),
                List.of(txt("Coins von der Bank zum Wallet", NamedTextColor.GRAY))));

        long[] witAmounts = {100, 1_000, 10_000};
        for (int i = 0; i < witAmounts.length; i++) {
            long amt = witAmounts[i];
            boolean canAfford = bank >= amt;
            inv.setItem(37 + i, make(Material.REDSTONE,
                    txt("- " + fmt(amt) + " Coins", canAfford ? NamedTextColor.RED : NamedTextColor.DARK_GRAY),
                    List.of(
                        txt("Bank: " + fmt(bank), NamedTextColor.GRAY),
                        canAfford ? txt("Klicken zum Abheben", NamedTextColor.RED)
                                  : txt("Nicht genug auf der Bank!", NamedTextColor.DARK_RED)
                    )));
        }
        // Alles abheben
        inv.setItem(40, make(Material.BARRIER,
                txt("- Alles abheben", bank > 0 ? NamedTextColor.RED : NamedTextColor.DARK_GRAY),
                List.of(
                    txt("Bank: " + fmt(bank) + " Coins", NamedTextColor.GRAY),
                    bank > 0 ? txt("Alles abheben", NamedTextColor.RED)
                             : txt("Bank ist leer!", NamedTextColor.DARK_RED)
                )));

        // ── Glasrahmen ────────────────────────────────────────────────
        fillGlass(inv);

        player.openInventory(inv);
    }

    // ── Click-Handler ─────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null ||
                !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();

        // Einzahlen
        if (slot >= 28 && slot <= 32) {
            long[] depAmts = {100, 1_000, 10_000, 100_000, -1L};
            long amount = depAmts[slot - 28];
            if (amount == -1L) amount = plugin.getEconomyManager().getBalance(player.getUniqueId());
            if (amount <= 0) return;
            if (plugin.getBankManager().deposit(player.getUniqueId(), amount)) {
                plugin.getAchievementManager().checkBank(player);
                player.sendActionBar(Component.text("§a✔ " + fmt(amount) + " Coins eingezahlt!"));
            } else {
                player.sendActionBar(Component.text("§cNicht genug Coins im Wallet!"));
            }
            refresh(player);
            return;
        }

        // Abheben
        if (slot >= 37 && slot <= 40) {
            long[] witAmts = {100, 1_000, 10_000, -1L};
            long amount = witAmts[slot - 37];
            if (amount == -1L) amount = plugin.getBankManager().getBalance(player.getUniqueId());
            if (amount <= 0) return;
            if (plugin.getBankManager().withdraw(player.getUniqueId(), amount)) {
                player.sendActionBar(Component.text("§a✔ " + fmt(amount) + " Coins abgehoben!"));
            } else {
                player.sendActionBar(Component.text("§cNicht genug Guthaben auf der Bank!"));
            }
            refresh(player);
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private void refresh(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> open(player));
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%,.0f", (double) n).replace(',', '.');
        return String.valueOf(n);
    }

    private ItemStack make(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
    private static Component empty() {
        return Component.empty();
    }

    private void fillGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta  = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }
}
