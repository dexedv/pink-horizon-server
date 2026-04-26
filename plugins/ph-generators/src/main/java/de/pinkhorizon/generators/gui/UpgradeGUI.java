package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.GeneratorManager;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upgrade-GUI: Zeigt alle Generatoren des Spielers und erlaubt Upgrades.
 */
public class UpgradeGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "Generator-Upgrades";

    /** UUID → aktuell angezeigte Generatoren-Liste (für Click-Mapping) */
    private final ConcurrentHashMap<UUID, List<PlacedGenerator>> openInventories = new ConcurrentHashMap<>();

    public UpgradeGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        List<PlacedGenerator> gens = data.getGenerators();
        int rows = Math.max(3, (int) Math.ceil((gens.size() + 9) / 9.0) + 1);
        rows = Math.min(rows, 6);

        Inventory inv = Bukkit.createInventory(null, rows * 9, MM.deserialize("<aqua>" + TITLE));

        // Füller
        ItemStack glass = filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < rows * 9; i++) inv.setItem(i, glass);

        // Info-Zeile oben
        inv.setItem(4, buildPlayerInfoItem(data));

        // Generatoren ab Slot 9
        for (int i = 0; i < gens.size() && i < (rows - 1) * 9; i++) {
            inv.setItem(9 + i, buildGenUpgradeItem(gens.get(i), data));
        }

        openInventories.put(player.getUniqueId(), new ArrayList<>(gens));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<aqua>" + TITLE))) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 9) return; // Info-Zeile ignorieren

        List<PlacedGenerator> gens = openInventories.get(player.getUniqueId());
        if (gens == null) return;

        int genIndex = slot - 9;
        if (genIndex < 0 || genIndex >= gens.size()) return;

        PlacedGenerator gen = gens.get(genIndex);
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        boolean useToken = event.isRightClick() && plugin.getAfkRewardManager().hasUpgradeToken(player);

        if (useToken) {
            // Gratis-Upgrade mit Token
            int maxLevel = data.maxGeneratorLevel();
            if (gen.getLevel() >= maxLevel) {
                player.sendMessage(MM.deserialize("<red>Maximales Level erreicht!"));
                return;
            }
            plugin.getAfkRewardManager().consumeUpgradeToken(player);
            gen.setLevel(gen.getLevel() + 1);
            data.incrementUpgrades();
            plugin.getRepository().updateGeneratorLevel(gen);
            plugin.getHologramManager().updateHologram(gen, data);
            plugin.getAchievementManager().track(data, "upgrade_100", 1);
            player.sendMessage(MM.deserialize("<aqua>✦ Token verwendet! Generator ist jetzt Level " + gen.getLevel()));
            open(player);
            return;
        }

        // Normales Upgrade
        GeneratorManager.UpgradeResult result = plugin.getGeneratorManager().upgrade(player, gen);
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(MM.deserialize("<green>✔ " + gen.getType().getDisplayName()
                        + " <green>auf Level <white>" + gen.getLevel() + " <green>upgegraded!"));
                open(player);
            }
            case MAX_LEVEL -> player.sendMessage(MM.deserialize(
                    "<red>Maximales Level (" + data.maxGeneratorLevel() + ") erreicht! Mache mehr Prestige."));
            case NO_MONEY -> player.sendMessage(MM.deserialize(
                    "<red>Nicht genug Geld! Benötigt: $" + gen.upgradeCost()
                            + " | Du hast: $" + data.getMoney()));
            default -> {}
        }
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    private ItemStack buildGenUpgradeItem(PlacedGenerator gen, PlayerData data) {
        ItemStack item = new ItemStack(gen.getType().getBlock());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(gen.getType().getDisplayName()));

        int level = gen.getLevel();
        int maxLevel = data.maxGeneratorLevel();
        long upgCost = gen.upgradeCost();
        double income = gen.incomePerSecond();
        double nextIncome = gen.getType().incomeAt(level + 1);
        boolean canAfford = data.getMoney() >= upgCost;
        boolean isMax = level >= maxLevel;

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Level: <white>" + level + "<gray>/" + maxLevel));
        lore.add(MM.deserialize("<gray>Einkommen: <green>$" + String.format("%.1f", income) + "/s"));
        if (!isMax) {
            lore.add(MM.deserialize("<gray>Nach Upgrade: <aqua>$" + String.format("%.1f", nextIncome) + "/s"));
            lore.add(MM.deserialize((canAfford ? "<green>" : "<red>") + "Upgrade-Kosten: $" + upgCost));
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<yellow>Linksklick → Upgrade"));
            if (plugin.getAfkRewardManager().hasUpgradeToken(player(data))) {
                lore.add(MM.deserialize("<aqua>Rechtsklick → Token verwenden (kostenlos)"));
            }
        } else {
            lore.add(MM.deserialize("<green><bold>★ MAX LEVEL ★</bold></green>"));
            lore.add(MM.deserialize("<gray>Mache Prestige für mehr Level!"));
        }
        lore.add(MM.deserialize("<dark_gray>" + gen.getWorld() + " " + gen.getX() + "," + gen.getY() + "," + gen.getZ()));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Player player(PlayerData data) {
        return Bukkit.getPlayer(data.getUuid());
    }

    private ItemStack buildPlayerInfoItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold>Deine Stats"));
        meta.lore(List.of(
                MM.deserialize("<gray>Geld: <green>$" + data.getMoney()),
                MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige()),
                MM.deserialize("<gray>Max Level: <aqua>" + data.maxGeneratorLevel()),
                MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size())
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray> "));
        item.setItemMeta(meta);
        return item;
    }

    public void close(UUID uuid) {
        openInventories.remove(uuid);
    }
}
