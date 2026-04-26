package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.TalentManager;
import de.pinkhorizon.generators.managers.TalentManager.Talent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI für den Talent-Baum.
 */
public class TalentsGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "✦ Talent-Baum";

    // Slot-Positionen für die 15 Talente (3 Reihen à 5)
    private static final int[] TALENT_SLOTS = {
        10, 11, 12, 13, 14,   // Tier 1
        19, 20, 21, 22, 23,   // Tier 2
        28, 29, 30, 31, 32    // Tier 3
    };

    public TalentsGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<light_purple>" + TITLE));
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());

        // Hintergrund
        ItemStack bg = filler(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // Tier-Trenner
        ItemStack t1 = filler(Material.LIME_STAINED_GLASS_PANE, "<green>── Tier 1 ──");
        ItemStack t2 = filler(Material.CYAN_STAINED_GLASS_PANE, "<aqua>── Tier 2 (3× Tier1 + P5) ──");
        ItemStack t3 = filler(Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>── Tier 3 (3× Tier2 + P25) ──");
        for (int i = 9; i <= 17; i++)  inv.setItem(i, i == 10 || (i >= 11 && i <= 14) ? null : t1);
        for (int i = 18; i <= 26; i++) inv.setItem(i, i == 19 || (i >= 20 && i <= 23) ? null : t2);
        for (int i = 27; i <= 35; i++) inv.setItem(i, i == 28 || (i >= 29 && i <= 32) ? null : t3);

        // Tier-Label-Items
        inv.setItem(9,  filler(Material.LIME_STAINED_GLASS_PANE, "<green>── Tier 1 ──"));
        inv.setItem(18, filler(Material.CYAN_STAINED_GLASS_PANE, "<aqua>── Tier 2 ──"));
        inv.setItem(27, filler(Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>── Tier 3 ──"));

        // Talente platzieren
        if (data != null) {
            List<Talent> talents = new ArrayList<>(TalentManager.ALL_TALENTS.values());
            for (int i = 0; i < Math.min(talents.size(), TALENT_SLOTS.length); i++) {
                inv.setItem(TALENT_SLOTS[i], buildTalentItem(talents.get(i), data));
            }
        }

        // Info-Item (oben rechts)
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(MM.deserialize("<gold>Talentpunkte"));
        List<Component> lore = new ArrayList<>();
        if (data != null) {
            lore.add(MM.deserialize("<yellow>Verfügbar: <white>" + data.getTalentPoints()));
            lore.add(MM.deserialize("<gray>Freigeschaltet: <white>" + data.getUnlockedTalents().size() + "/15"));
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<gray>Punkte verdienen:"));
            lore.add(MM.deserialize("<dark_gray>• Prestige machen (+1)"));
            lore.add(MM.deserialize("<dark_gray>• Tag-7-Bonus (+1)"));
        }
        im.lore(lore);
        info.setItemMeta(im);
        inv.setItem(4, info);

        // Schließen-Button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(MM.deserialize("<red>Schließen"));
        close.setItemMeta(cm);
        inv.setItem(49, close);

        player.openInventory(inv);
    }

    private ItemStack buildTalentItem(Talent talent, PlayerData data) {
        boolean unlocked = data.hasTalent(talent.id());
        boolean canUnlock = canUnlock(talent, data);
        boolean hasPoints = data.getTalentPoints() > 0;

        Material mat;
        if (talent.tier() == 1) mat = Material.EMERALD;
        else if (talent.tier() == 2) mat = Material.DIAMOND;
        else mat = Material.NETHER_STAR;

        ItemStack item = new ItemStack(unlocked ? mat : Material.COAL);
        ItemMeta meta = item.getItemMeta();

        String statusColor = unlocked ? "<green>" : (canUnlock && hasPoints ? "<yellow>" : "<dark_gray>");
        meta.displayName(MM.deserialize(statusColor + (unlocked ? "✔ " : "○ ") + talent.name()));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<dark_gray>" + talent.description()));
        lore.add(MM.deserialize(""));
        lore.add(MM.deserialize("<gray>Tier: <white>" + talent.tier()));
        if (talent.prestigeReq() > 0)
            lore.add(MM.deserialize("<gray>Benötigt Prestige: <white>" + talent.prestigeReq()));
        if (talent.requiredTalent() != null) {
            Talent req = TalentManager.ALL_TALENTS.get(talent.requiredTalent());
            if (req != null)
                lore.add(MM.deserialize("<gray>Benötigt: " + req.name()));
        }
        lore.add(MM.deserialize(""));
        if (unlocked) {
            lore.add(MM.deserialize("<green>✔ Freigeschaltet"));
        } else if (canUnlock && hasPoints) {
            lore.add(MM.deserialize("<yellow>▶ Klicken zum Freischalten (1 Punkt)"));
        } else if (!canUnlock) {
            lore.add(MM.deserialize("<red>✗ Voraussetzungen nicht erfüllt"));
        } else {
            lore.add(MM.deserialize("<red>✗ Keine Talentpunkte verfügbar"));
        }
        meta.lore(lore);

        if (unlocked) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean canUnlock(Talent talent, PlayerData data) {
        if (data.hasTalent(talent.id())) return false;
        if (data.getPrestige() < talent.prestigeReq()) return false;
        if (talent.requiredTalent() != null && !data.hasTalent(talent.requiredTalent())) return false;
        if (talent.tier() == 2) {
            long t1 = TalentManager.ALL_TALENTS.values().stream()
                    .filter(t -> t.tier() == 1 && data.hasTalent(t.id())).count();
            if (t1 < 3) return false;
        }
        if (talent.tier() == 3) {
            long t2 = TalentManager.ALL_TALENTS.values().stream()
                    .filter(t -> t.tier() == 2 && data.hasTalent(t.id())).count();
            if (t2 < 3) return false;
        }
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!MM.serialize(e.getView().title()).contains(TITLE)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot == 49) { player.closeInventory(); return; }

        // Talent-Slot prüfen
        for (int i = 0; i < TALENT_SLOTS.length; i++) {
            if (slot == TALENT_SLOTS[i]) {
                List<Talent> talents = new ArrayList<>(TalentManager.ALL_TALENTS.values());
                if (i >= talents.size()) return;
                Talent talent = talents.get(i);

                PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
                if (data == null) return;

                TalentManager.UnlockResult result = plugin.getTalentManager().unlock(player, data, talent.id());
                switch (result) {
                    case NO_POINTS -> player.sendMessage(MM.deserialize("<red>Du hast keine Talentpunkte!"));
                    case ALREADY_UNLOCKED -> player.sendMessage(MM.deserialize("<yellow>Dieses Talent ist bereits freigeschaltet."));
                    case REQUIREMENTS_NOT_MET -> player.sendMessage(MM.deserialize(
                            "<red>Voraussetzungen nicht erfüllt!\n<gray>Benötigt: Prestige " + talent.prestigeReq()
                            + (talent.requiredTalent() != null ? " + " + talent.requiredTalent() : "")));
                    default -> {}
                }

                // GUI aktualisieren
                open(player);
                return;
            }
        }
    }

    private ItemStack filler(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }
}
