package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.managers.ContractManager;
import de.pinkhorizon.skyblock.managers.ContractManager.Contract;
import de.pinkhorizon.skyblock.managers.ContractManager.ContractType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * GUI: Auftrags-Brett – zeigt aktive Kontrakte, erlaubt Annahme.
 * 6 Reihen.
 */
public class ContractGui extends GuiBase {

    private static final int SLOT_CLOSE  = 49;
    private static final int SLOT_INFO   = 4;
    // Kontrakt-Slots: 10–16, 19–25, 28–34 (3 Reihen × 7 Slots)
    private static final int[] CONTRACT_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34
    };

    private static final Material[] TYPE_MATS = {
        Material.CHEST,         // DELIVERY
        Material.WHEAT,         // FARMING
        Material.BEACON,        // COMMUNITY
        Material.IRON_SWORD     // PVE
    };

    private final PHSkyBlock plugin;
    private final Player player;
    private List<Contract> contracts;

    public ContractGui(PHSkyBlock plugin, Player player) {
        super("<gold>✦ <bold>Auftrags-Brett</bold> ✦", 6);
        this.plugin = plugin;
        this.player = player;
        this.contracts = new ArrayList<>(plugin.getContractManager().loadActive());
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.YELLOW_STAINED_GLASS_PANE);

        inventory.setItem(SLOT_INFO, item(Material.PAPER,
            "<gold><bold>Auftrags-Brett",
            "<gray>Erfülle Aufträge für Belohnungen.",
            "<gray>Aktive Aufträge: <yellow>" + contracts.size(),
            "",
            "<dark_gray>Klicke auf einen Auftrag um ihn anzunehmen."
        ));

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");

        for (int i = 0; i < Math.min(contracts.size(), CONTRACT_SLOTS.length); i++) {
            Contract ct = contracts.get(i);
            Material mat = TYPE_MATS[ct.type().ordinal() % TYPE_MATS.length];
            String progress = ct.type() == ContractType.COMMUNITY
                ? "<gray>Fortschritt: <yellow>" + ct.progress() + "/" + ct.goal()
                : "<gray>Ziel: <white>" + ct.requirement();
            String deadline = "<gray>Deadline: <red>" + sdf.format(new Date(ct.deadline()));
            String reward   = "<yellow>Belohnung: <gold>" + String.format("%,d", ct.rewardCoins()) + " Coins";

            inventory.setItem(CONTRACT_SLOTS[i], item(mat,
                "<yellow>#" + ct.id() + " <white>" + ct.type().name(),
                progress,
                reward,
                deadline,
                "",
                "<green>► Klicken um anzunehmen"
            ));
        }

        if (contracts.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER,
                "<gray>Keine aktiven Aufträge",
                "<dark_gray>Komm später wieder."
            ));
        }

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            p.closeInventory();
            return;
        }

        for (int i = 0; i < CONTRACT_SLOTS.length; i++) {
            if (slot == CONTRACT_SLOTS[i] && i < contracts.size()) {
                Contract ct = contracts.get(i);
                p.closeInventory();
                plugin.getContractManager().acceptContract(p, ct.id());
                return;
            }
        }
    }
}
