package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClaimMapGui implements InventoryHolder {

    public static final String KEY_CX  = "map_cx";
    public static final String KEY_CZ  = "map_cz";
    public static final String KEY_NAV = "map_nav";

    public final UUID playerUuid;
    public final int offX;
    public final int offZ;

    private final Inventory inventory;

    public ClaimMapGui(UUID playerUuid, int offX, int offZ) {
        this.playerUuid = playerUuid;
        this.offX = offX;
        this.offZ = offZ;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("§5§lClaim-Karte"));
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public static void open(Player player, PHSurvival plugin, int offX, int offZ) {
        ClaimMapGui gui = new ClaimMapGui(player.getUniqueId(), offX, offZ);
        populate(gui.inventory, player, plugin, offX, offZ);
        player.openInventory(gui.inventory);
    }

    static void populate(Inventory inv, Player player, PHSurvival plugin, int offX, int offZ) {
        UUID uuid = player.getUniqueId();
        String worldName = player.getWorld().getName();
        int playerCX = player.getLocation().getChunk().getX();
        int playerCZ = player.getLocation().getChunk().getZ();

        NamespacedKey keyCX  = new NamespacedKey(plugin, KEY_CX);
        NamespacedKey keyCZ  = new NamespacedKey(plugin, KEY_CZ);
        NamespacedKey keyNav = new NamespacedKey(plugin, KEY_NAV);

        // ── 9×5 chunk grid (slots 0–44) ────────────────────────────────────
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                int cx = playerCX + offX + (col - 4);
                int cz = playerCZ + offZ + (row - 2);

                boolean isPlayerHere = (cx == playerCX && cz == playerCZ);
                boolean claimed = plugin.getClaimManager().isClaimedAt(worldName, cx, cz);
                UUID owner = claimed ? plugin.getClaimManager().getOwnerAt(worldName, cx, cz) : null;
                boolean isOwn     = claimed && uuid.equals(owner);
                boolean isTrusted = claimed && !isOwn && plugin.getClaimManager().isTrustedAt(worldName, cx, cz, uuid);

                Material mat;
                String displayName;
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§8" + cx + "§7, §8" + cz));

                if (!claimed) {
                    mat = isPlayerHere ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
                    displayName = isPlayerHere ? "§a§l★ Du bist hier §8| §7Frei" : "§7Frei";
                    lore.add(Component.text("§aKlick: Claimen"));
                } else if (isOwn) {
                    mat = isPlayerHere ? Material.LIME_CONCRETE : Material.GREEN_TERRACOTTA;
                    displayName = isPlayerHere ? "§a§l★ Du bist hier §8| §aDein Claim" : "§aDein Claim";
                    lore.add(Component.text("§cKlick: Unclaimen"));
                } else if (isTrusted) {
                    mat = isPlayerHere ? Material.CYAN_CONCRETE : Material.CYAN_TERRACOTTA;
                    String ownerName = Bukkit.getOfflinePlayer(owner).getName();
                    displayName = isPlayerHere ? "§a§l★ Du bist hier §8| §bVertraut" : "§bVertraut";
                    lore.add(Component.text("§7Besitzer: §b" + (ownerName != null ? ownerName : "Unbekannt")));
                } else {
                    mat = isPlayerHere ? Material.PINK_TERRACOTTA : Material.RED_TERRACOTTA;
                    String ownerName = owner != null ? Bukkit.getOfflinePlayer(owner).getName() : null;
                    displayName = isPlayerHere ? "§a§l★ Du bist hier §8| §cFremdclaim" : "§cFremdclaim";
                    lore.add(Component.text("§7Besitzer: §c" + (ownerName != null ? ownerName : "Unbekannt")));
                }

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(displayName));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(keyCX, PersistentDataType.INTEGER, cx);
                meta.getPersistentDataContainer().set(keyCZ, PersistentDataType.INTEGER, cz);
                item.setItemMeta(meta);
                inv.setItem(slot, item);
            }
        }

        // ── Bottom row: navigation + info (slots 45–53) ───────────────────
        int usedClaims = plugin.getClaimManager().getClaimCount(uuid);
        int maxClaims  = plugin.getRankManager().getMaxClaims(uuid);

        inv.setItem(45, navArrow("§e← West", "w", keyNav));
        inv.setItem(46, navArrow("§e↑ Nord", "n", keyNav));
        inv.setItem(47, navCenter(keyNav));
        inv.setItem(48, navArrow("§e↓ Süd",  "s", keyNav));
        inv.setItem(49, navArrow("§e→ Ost",  "e", keyNav));

        ItemStack sep = spacer();
        inv.setItem(50, sep); inv.setItem(51, sep); inv.setItem(52, sep);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("§e§lInfo"));
        im.lore(List.of(
            Component.text("§7Claims: §a" + usedClaims + " §7/ §a" + maxClaims),
            Component.text("§7Offset: §f" + offX + "§7, §f" + offZ),
            Component.text(""),
            Component.text("§8Pfeile verschieben die Ansicht um 4 Chunks")
        ));
        info.setItemMeta(im);
        inv.setItem(53, info);
    }

    private static ItemStack navArrow(String name, String dir, NamespacedKey navKey) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.getPersistentDataContainer().set(navKey, PersistentDataType.STRING, dir);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navCenter(NamespacedKey navKey) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§aZentrieren"));
        meta.lore(List.of(Component.text("§7Ansicht zurück auf deinen Chunk")));
        meta.getPersistentDataContainer().set(navKey, PersistentDataType.STRING, "c");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack spacer() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }
}
