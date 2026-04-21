package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShopCommand implements CommandExecutor {

    public static final String UPGRADE_KEY = "shop_upgrade";

    // Upgrade-IDs
    public static final String FLY_10    = "FLY_10";
    public static final String FLY_30    = "FLY_30";
    public static final String FLY_60    = "FLY_60";
    public static final String FLY_PERM  = "FLY_PERM";
    public static final String KI_10     = "KI_10";
    public static final String KI_30     = "KI_30";
    public static final String KI_60     = "KI_60";
    public static final String KI_PERM   = "KI_PERM";
    public static final String CLAIMS_5  = "CLAIMS_5";
    public static final String CLAIMS_15 = "CLAIMS_15";

    private final PHSurvival plugin;

    public ShopCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }
        openShop(player);
        return true;
    }

    public void openShop(Player player) {
        UUID uuid = player.getUniqueId();
        var um = plugin.getUpgradeManager();

        Inventory inv = plugin.getServer().createInventory(null, 54,
                Component.text("§5§lPink Horizon §8| §dShop"));

        // ── Rahmen ───────────────────────────────────────────────────────
        ItemStack pur  = glass(Material.PURPLE_STAINED_GLASS_PANE, "§5 ");
        ItemStack dark = glass(Material.BLACK_STAINED_GLASS_PANE,  "§8 ");
        ItemStack div  = glass(Material.GRAY_STAINED_GLASS_PANE,   "§7 ");
        for (int i =  0; i <  9; i++) inv.setItem(i, pur);
        for (int i = 45; i < 54; i++) inv.setItem(i, pur);
        for (int r = 1; r <= 4; r++) { inv.setItem(r*9, dark); inv.setItem(r*9+8, dark); }
        // Vertikaler Teiler (Spalte 4)
        for (int r = 1; r <= 4; r++) inv.setItem(r*9+4, div);

        // ── Fly-Sektion (links, Slots 10–12 & 19–21) ─────────────────────
        boolean flyActive = um.hasActiveFly(uuid);
        long flyMins = um.getFlyRemainingMs(uuid) / 60_000;
        String flyStatus = flyActive ? "§aAktiv – noch §f" + flyMins + "m" : null;

        boolean flyPerm = um.hasPermFly(uuid);
        String flyPermStatus = flyPerm ? "§a§lDauerhaft aktiv!" : flyStatus;

        addItem(inv, 10, Material.FEATHER,  "§b§lFly §7– 10 Minuten",
                "§7Fliege frei durch die Welt.", "§8Stapelt sich mit aktiver Zeit.",
                2_400, FLY_10, flyPermStatus);
        addItem(inv, 11, Material.FEATHER,  "§b§lFly §7– 30 Minuten",
                "§7Fliege frei durch die Welt.", "§8Stapelt sich mit aktiver Zeit.",
                5_400, FLY_30, flyPermStatus);
        addItem(inv, 12, Material.FEATHER,  "§b§lFly §7– 1 Stunde",
                "§7Fliege frei durch die Welt.", "§8Stapelt sich mit aktiver Zeit.",
                10_500, FLY_60, flyPermStatus);
        addItem(inv, 13, Material.ELYTRA,   "§b§l§nFly §7– Dauerhaft",
                "§7Fliege §limmer§r§7 – niemals ablaufend.", "§cEinmaliger Kauf.",
                1_500_000, FLY_PERM, flyPerm ? "§a§lBereits besessen!" : null);

        // ── Claim-Sektion (rechts, Slots 14–15) ──────────────────────────
        int extra = um.getExtraClaims(uuid);
        String claimMax = extra >= 50 ? "§cMaximum erreicht (+50)!" : null;
        long price5  = um.getClaimPrice(uuid, 4_500);
        long price15 = um.getClaimPrice(uuid, 12_000);
        long nextPrice5  = um.getClaimPrice(uuid, 4_500) * 3 / 2; // nach diesem Kauf
        long nextPrice15 = um.getClaimPrice(uuid, 12_000) * 3 / 2;
        int purchases = um.getClaimPurchases(uuid);

        addItemDynamic(inv, 14, Material.GRASS_BLOCK, "§a§l+5 Claim-Slots",
                "§7Erhöht dein Claim-Limit dauerhaft.",
                "§7Extra bisher: §a+" + extra + "§7/§a+50",
                "§8Kauf #" + (purchases + 1) + (purchases > 0 ? " §8(war §7" + um.getClaimPrice(uuid, 4_500) * 2 / 3 + "§8)" : ""),
                "§7Nächster Kauf: §c" + nextPrice5 + " §7Coins",
                price5, CLAIMS_5, claimMax);
        addItemDynamic(inv, 15, Material.PODZOL,      "§a§l+15 Claim-Slots",
                "§7Erhöht dein Claim-Limit dauerhaft.",
                "§7Extra bisher: §a+" + extra + "§7/§a+50",
                "§8Kauf #" + (purchases + 1) + (purchases > 0 ? " §8(war §7" + um.getClaimPrice(uuid, 12_000) * 2 / 3 + "§8)" : ""),
                "§7Nächster Kauf: §c" + nextPrice15 + " §7Coins",
                price15, CLAIMS_15, claimMax);

        // ── KeepInventory-Sektion (unten links, Slots 19–22) ─────────────
        long kiMins = um.getKiRemainingMs(uuid) / 60_000;
        boolean hasPerm = um.hasPermKI(uuid);
        boolean kiActive = um.hasActiveKI(uuid);
        String kiStatus = hasPerm ? "§a§lDauerhaft aktiv!" :
                          kiActive ? "§aAktiv – noch §f" + kiMins + "m" : null;

        addItem(inv, 19, Material.TOTEM_OF_UNDYING, "§6§lKeepInventory §7– 10 Min.",
                "§7Inventar bleibt beim Tod erhalten.", "§8Stapelt sich mit aktiver Zeit.",
                1_500, KI_10, kiStatus);
        addItem(inv, 20, Material.TOTEM_OF_UNDYING, "§6§lKeepInventory §7– 30 Min.",
                "§7Inventar bleibt beim Tod erhalten.", "§8Stapelt sich mit aktiver Zeit.",
                3_600, KI_30, kiStatus);
        addItem(inv, 21, Material.TOTEM_OF_UNDYING, "§6§lKeepInventory §7– 1 Std.",
                "§7Inventar bleibt beim Tod erhalten.", "§8Stapelt sich mit aktiver Zeit.",
                7_500, KI_60, kiStatus);
        addItem(inv, 22, Material.NETHER_STAR,      "§6§l§nKeepInventory §7– Dauerhaft",
                "§7Inventar §limmer§r §7beim Tod erhalten.", "§cEinmaliger Kauf – niemals ablaufend.",
                1_500_000, KI_PERM, hasPerm ? "§a§lBereits besessen!" : null);

        // ── Status-Item (Slot 31) ─────────────────────────────────────────
        long coins = plugin.getEconomyManager().getBalance(uuid);
        int maxClaims = plugin.getRankManager().getMaxClaims(uuid);
        ItemStack info = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("§e§lDein Status"));
        im.lore(List.of(
                Component.text("§7Coins: §6§l" + coins),
                Component.text("§7Max. Claims: §a" + maxClaims),
                Component.text("§7KeepInventory: " + (kiActive ? (hasPerm ? "§a§lDauerhaft" : "§a§lAktiv (" + kiMins + "m)") : "§c§l✘")),
                Component.text("§7Fly: " + (flyPerm ? "§a§lDauerhaft" : flyActive ? "§a§lAktiv (" + flyMins + "m)" : "§c§l✘"))
        ));
        info.setItemMeta(im);
        inv.setItem(40, info);

        player.openInventory(inv);
    }

    private void addItem(Inventory inv, int slot, Material mat,
                         String name, String desc1, String desc2,
                         int price, String upgradeId, String statusOverride) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(desc1, NamedTextColor.GRAY));
        lore.add(Component.text(desc2, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        if (statusOverride != null) {
            lore.add(Component.text(statusOverride));
        } else {
            lore.add(Component.text("§7Preis: §6§l" + price + " Coins"));
            lore.add(Component.text("§aKlicken zum Kaufen", NamedTextColor.GREEN));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, UPGRADE_KEY), PersistentDataType.STRING, upgradeId);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "shop_price"), PersistentDataType.INTEGER, price);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void addItemDynamic(Inventory inv, int slot, Material mat,
                                String name, String desc1, String desc2,
                                String desc3, String desc4,
                                long price, String upgradeId, String statusOverride) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(desc1, NamedTextColor.GRAY));
        lore.add(Component.text(desc2, NamedTextColor.GRAY));
        lore.add(Component.text(desc3, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        if (statusOverride != null) {
            lore.add(Component.text(statusOverride));
        } else {
            lore.add(Component.text("§7Preis: §6§l" + price + " Coins"));
            lore.add(Component.text(desc4, NamedTextColor.GRAY));
            lore.add(Component.text("§aKlicken zum Kaufen", NamedTextColor.GREEN));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, UPGRADE_KEY), PersistentDataType.STRING, upgradeId);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "shop_price"), PersistentDataType.INTEGER, (int) Math.min(price, Integer.MAX_VALUE));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
