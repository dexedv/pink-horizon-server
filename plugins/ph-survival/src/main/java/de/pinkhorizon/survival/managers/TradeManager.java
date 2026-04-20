package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TradeManager {

    // ── Inventory layout (45 slots / 5 rows) ────────────────────────────
    // Row 1-2 (0-17):  own offer
    // Row 3 (18-26):   separator / slot 22 = confirm / slot 26 = cancel
    // Row 4-5 (27-44): other player's offer (read-only)

    public static final int CONFIRM_SLOT = 22;
    public static final int CANCEL_SLOT  = 26;
    public static final int INV_SIZE     = 45;

    public static class TradeHolder implements InventoryHolder {
        public final UUID owner;
        public final UUID partner;
        public boolean confirmed = false;
        private Inventory inventory;

        public TradeHolder(UUID owner, UUID partner) {
            this.owner   = owner;
            this.partner = partner;
        }

        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv)   { this.inventory = inv; }
    }

    private final PHSurvival plugin;
    // pending requests: sender → recipient
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    // active sessions: both UUIDs point to the SAME session
    private final Map<UUID, TradeHolder[]> sessions = new HashMap<>();

    public TradeManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Request API ──────────────────────────────────────────────────────

    public void sendRequest(UUID from, UUID to) {
        pendingRequests.put(from, to);
    }

    public boolean hasRequest(UUID from, UUID to) {
        return to.equals(pendingRequests.get(from));
    }

    public void cancelRequest(UUID from) {
        pendingRequests.remove(from);
    }

    public boolean isInSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    // ── Session API ──────────────────────────────────────────────────────

    /** Starts a trade session between two players and opens both GUIs. */
    public void startSession(Player playerA, Player playerB) {
        pendingRequests.remove(playerA.getUniqueId());
        pendingRequests.remove(playerB.getUniqueId());

        TradeHolder holderA = new TradeHolder(playerA.getUniqueId(), playerB.getUniqueId());
        TradeHolder holderB = new TradeHolder(playerB.getUniqueId(), playerA.getUniqueId());

        String titleA = "§6§lHandel §8| §7" + playerA.getName() + " §8↔ §7" + playerB.getName();
        String titleB = titleA;

        Inventory invA = Bukkit.createInventory(holderA, INV_SIZE, Component.text(titleA));
        Inventory invB = Bukkit.createInventory(holderB, INV_SIZE, Component.text(titleB));
        holderA.setInventory(invA);
        holderB.setInventory(invB);

        fillBorders(invA, playerB.getName(), false);
        fillBorders(invB, playerA.getName(), false);

        sessions.put(playerA.getUniqueId(), new TradeHolder[]{holderA, holderB});
        sessions.put(playerB.getUniqueId(), new TradeHolder[]{holderA, holderB});

        playerA.openInventory(invA);
        playerB.openInventory(invB);
    }

    /** Returns the two holders [ownHolder, partnerHolder] or null. */
    public TradeHolder[] getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /** Called when a player toggles confirm. Returns true if trade should execute. */
    public boolean toggleConfirm(UUID uuid) {
        TradeHolder[] pair = sessions.get(uuid);
        if (pair == null) return false;
        TradeHolder own     = pair[0].owner.equals(uuid) ? pair[0] : pair[1];
        TradeHolder partner = pair[0].owner.equals(uuid) ? pair[1] : pair[0];
        own.confirmed = !own.confirmed;
        updateConfirmButtons(own, partner);
        return own.confirmed && partner.confirmed;
    }

    /** Updates confirm buttons in both inventories after a change. */
    public void syncPartnerOffer(UUID uuid) {
        TradeHolder[] pair = sessions.get(uuid);
        if (pair == null) return;
        TradeHolder own     = pair[0].owner.equals(uuid) ? pair[0] : pair[1];
        TradeHolder partner = pair[0].owner.equals(uuid) ? pair[1] : pair[0];
        // Reset partner's confirmation since offer changed
        own.confirmed = false;
        // Copy own's offer (slots 0-17) into partner's read-only area (27-44)
        for (int i = 0; i < 18; i++) {
            ItemStack item = own.getInventory().getItem(i);
            partner.getInventory().setItem(27 + i, item != null ? item.clone() : null);
        }
        updateConfirmButtons(own, partner);
    }

    /** Executes the trade: swap items, close inventories, remove session. */
    public void executeTrade(UUID uuid) {
        TradeHolder[] pair = sessions.get(uuid);
        if (pair == null) return;
        TradeHolder holderA = pair[0];
        TradeHolder holderB = pair[1];

        Player pA = Bukkit.getPlayer(holderA.owner);
        Player pB = Bukkit.getPlayer(holderB.owner);

        // Collect offered items
        List<ItemStack> offerA = collectOffer(holderA.getInventory());
        List<ItemStack> offerB = collectOffer(holderB.getInventory());

        removeSession(holderA.owner);

        if (pA != null) { pA.closeInventory(); giveItems(pA, offerB); pA.sendMessage(Component.text("§aHandel abgeschlossen!")); }
        if (pB != null) { pB.closeInventory(); giveItems(pB, offerA); pB.sendMessage(Component.text("§aHandel abgeschlossen!")); }
    }

    /** Cancels a trade, returning items to the player who had them. */
    public void cancelSession(UUID uuid) {
        TradeHolder[] pair = sessions.get(uuid);
        if (pair == null) return;
        TradeHolder holderA = pair[0];
        TradeHolder holderB = pair[1];

        Player pA = Bukkit.getPlayer(holderA.owner);
        Player pB = Bukkit.getPlayer(holderB.owner);

        List<ItemStack> offerA = collectOffer(holderA.getInventory());
        List<ItemStack> offerB = collectOffer(holderB.getInventory());

        removeSession(holderA.owner);

        if (pA != null) { pA.closeInventory(); giveItems(pA, offerA); pA.sendMessage(Component.text("§cHandel abgebrochen.")); }
        if (pB != null) { pB.closeInventory(); giveItems(pB, offerB); pB.sendMessage(Component.text("§cHandel abgebrochen.")); }
    }

    private void removeSession(UUID uuid) {
        TradeHolder[] pair = sessions.remove(uuid);
        if (pair != null) {
            sessions.remove(pair[0].owner);
            sessions.remove(pair[1].owner);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<ItemStack> collectOffer(Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) items.add(item.clone());
            inv.setItem(i, null);
        }
        return items;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private void fillBorders(Inventory inv, String partnerName, boolean confirmedSelf) {
        ItemStack sep = makeSep(Material.GRAY_STAINED_GLASS_PANE, "§8 ");
        // Middle row: 18-26
        for (int s = 18; s <= 26; s++) inv.setItem(s, sep);

        // Confirm button
        inv.setItem(CONFIRM_SLOT, makeButton(confirmedSelf));
        // Cancel button
        inv.setItem(CANCEL_SLOT, makeSep(Material.RED_STAINED_GLASS_PANE, "§cAbbrechen"));

        // Partner offer area label (slot 27 header is just locked air, labeled via hologram)
        ItemStack info = makeSep(Material.PAPER, "§eAngebot von §f" + partnerName);
        ItemMeta m = info.getItemMeta();
        m.displayName(Component.text("§eAngebot von §f" + partnerName));
        info.setItemMeta(m);
        // No label item needed – partner items appear there directly
    }

    private void updateConfirmButtons(TradeHolder own, TradeHolder partner) {
        own.getInventory().setItem(CONFIRM_SLOT, makeButton(own.confirmed));
        partner.getInventory().setItem(CONFIRM_SLOT, makeButton(partner.confirmed));
    }

    private ItemStack makeButton(boolean confirmed) {
        ItemStack item = new ItemStack(confirmed ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(confirmed ? "§a§lBestätigt ✔" : "§aKlicken zum Bestätigen"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSep(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
