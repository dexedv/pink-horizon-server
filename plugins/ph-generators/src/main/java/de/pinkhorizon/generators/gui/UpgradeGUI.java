package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.GeneratorManager;
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

    /** UUIDs die gerade im Single-View (Sneak-Rechtsklick) sind */
    private final java.util.Set<UUID> singleView = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public UpgradeGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    /** Öffnet das Upgrade-Menü direkt für einen einzelnen Generator (Sneak-Rechtsklick). */
    public void openSingle(Player player, PlacedGenerator gen) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, MM.deserialize("<aqua>" + TITLE));

        ItemStack glass = filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(4, buildPlayerInfoItem(data));
        inv.setItem(9, buildGenUpgradeItem(gen, data));

        // Fusions-Button wenn 2 passende Nachbarn auf Max-Level vorhanden
        List<PlacedGenerator> partners = findFusionPartners(gen, data);
        if (partners.size() >= 2) {
            inv.setItem(15, buildFusionButton(gen, partners));
        }

        List<PlacedGenerator> list = new ArrayList<>();
        list.add(gen);
        openInventories.put(player.getUniqueId(), list);
        singleView.add(player.getUniqueId());
        player.openInventory(inv);
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
        singleView.remove(player.getUniqueId());
        player.openInventory(inv);
        plugin.getTutorialManager().onUpgradeGuiOpened(player);
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

        // Fusions-Button (Slot 15, nur im Single-View)
        if (slot == 15 && singleView.contains(player.getUniqueId()) && !gens.isEmpty()) {
            PlacedGenerator mainGen = gens.get(0);
            PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
            if (data == null) return;

            List<PlacedGenerator> partners = findFusionPartners(mainGen, data);
            if (partners.size() < 2) {
                player.sendMessage(MM.deserialize("<red>Keine passenden Nachbar-Generatoren mehr!"));
                return;
            }

            List<PlacedGenerator> toFuse = new ArrayList<>();
            toFuse.add(mainGen);
            toFuse.add(partners.get(0));
            toFuse.add(partners.get(1));

            GeneratorManager.FuseResult result = plugin.getGeneratorManager().fuse(player, toFuse);
            switch (result) {
                case SUCCESS -> {
                    player.sendMessage(MM.deserialize(
                            "<gold>✦ Fusion erfolgreich! <yellow>Mega-Generator erschaffen!"));
                    player.closeInventory();
                }
                case NOT_MAX_LEVEL -> player.sendMessage(MM.deserialize(
                        "<red>Alle 3 Generatoren müssen auf Max-Level sein!"));
                case DIFFERENT_TYPE -> player.sendMessage(MM.deserialize(
                        "<red>Alle 3 Generatoren müssen denselben Typ haben!"));
                case ALREADY_MEGA -> player.sendMessage(MM.deserialize(
                        "<red>Mega-Generatoren können nicht fusioniert werden!"));
                case NO_MEGA_TIER -> player.sendMessage(MM.deserialize(
                        "<red>Dieser Typ hat kein Mega-Tier!"));
                default -> player.sendMessage(MM.deserialize("<red>Fusion fehlgeschlagen."));
            }
            return;
        }

        int genIndex = slot - 9;
        if (genIndex < 0 || genIndex >= gens.size()) return;

        PlacedGenerator gen = gens.get(genIndex);
        if (gen == null) return;
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
            refresh(player, gen);
            return;
        }

        // Normales Upgrade
        GeneratorManager.UpgradeResult result = plugin.getGeneratorManager().upgrade(player, gen);
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(MM.deserialize("<green>✔ " + gen.getType().getDisplayName()
                        + " <green>auf Level <white>" + gen.getLevel() + " <green>upgegraded!"));
                plugin.getTutorialManager().onUpgradeDone(player);
                refresh(player, gen);
            }
            case MAX_LEVEL -> {
                // Max-Level erreicht → Tier-Upgrade versuchen
                GeneratorManager.TierUpgradeResult tierResult = plugin.getGeneratorManager().tierUpgrade(player, gen);
                switch (tierResult) {
                    case SUCCESS -> {
                        player.sendMessage(MM.deserialize(
                                "<green>✔ Generator aufgewertet zu " + gen.getType().getDisplayName() + "! <gray>(Level zurück auf 1)"));
                        refresh(player, gen);
                    }
                    case NO_NEXT_TIER -> player.sendMessage(MM.deserialize(
                            "<red>Maximales Tier erreicht! Kein weiteres Upgrade möglich."));
                    case NO_MONEY -> player.sendMessage(MM.deserialize(
                            "<red>Nicht genug Geld für Tier-Upgrade! Benötigt: $"
                                    + gen.getType().getTierUpgradeCost()
                                    + " | Du hast: $" + data.getMoney()));
                    default -> {}
                }
            }
            case NO_MONEY -> player.sendMessage(MM.deserialize(
                    "<red>Nicht genug Geld! Benötigt: $" + gen.upgradeCost()
                            + " | Du hast: $" + data.getMoney()));
            default -> {}
        }
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    private ItemStack buildGenUpgradeItem(PlacedGenerator gen, PlayerData data) {
        int level = gen.getLevel();
        int maxLevel = data.maxGeneratorLevel();
        boolean isMax = level >= maxLevel;
        de.pinkhorizon.generators.GeneratorType nextTier = gen.getType().getNextTier();
        boolean isTrulyMaxTier = isMax && nextTier == null;

        // Max-Level → BEACON-Icon als Highlight; Max-Tier → NETHER_STAR
        Material displayMat = isTrulyMaxTier
                ? Material.NETHER_STAR
                : (isMax ? Material.BEACON : gen.getType().getBlock());

        ItemStack item = new ItemStack(displayMat);
        ItemMeta meta = item.getItemMeta();

        // Enchantment-Glow bei Max-Level
        if (isMax) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        String namePrefix = isMax ? "<bold>" : "";
        String nameSuffix = isMax ? "</bold>" : "";
        meta.displayName(MM.deserialize(namePrefix + gen.getType().getDisplayName() + nameSuffix));

        long upgCost = gen.upgradeCost();
        double income = gen.incomePerSecond();
        double nextIncome = gen.getType().incomeAt(level + 1);
        boolean canAfford = data.getMoney() >= upgCost;

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize((isMax ? "<gold>" : "<gray>") + "Level: "
                + (isMax ? "<bold>" : "<white>") + level + (isMax ? "</bold></gold>" : "")
                + (isMax ? "" : "<gray>/" + maxLevel)));
        lore.add(MM.deserialize("<gray>Einkommen: <green>$" + String.format("%.1f", income) + "/s"));

        if (!isMax) {
            lore.add(MM.deserialize("<gray>Nach Upgrade: <aqua>$" + String.format("%.1f", nextIncome) + "/s"));
            lore.add(MM.deserialize((canAfford ? "<green>" : "<red>") + "Upgrade-Kosten: $" + upgCost));
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<yellow>Linksklick → Upgrade"));
            if (plugin.getAfkRewardManager().hasUpgradeToken(player(data))) {
                lore.add(MM.deserialize("<aqua>Rechtsklick → Token verwenden (kostenlos)"));
            }
        } else if (nextTier != null) {
            long tierCost = gen.getType().getTierUpgradeCost();
            boolean canAffordTier = data.getMoney() >= tierCost;
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<gold><bold>⬆ MAX LEVEL – Tier-Upgrade möglich!</bold></gold>"));
            lore.add(MM.deserialize("<gray>Nächstes Tier: " + nextTier.getDisplayName()));
            lore.add(MM.deserialize((canAffordTier ? "<green>" : "<red>") + "Kosten: $" + tierCost));
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<yellow>Linksklick → Tier-Upgrade"));
        } else {
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<gold><bold>✦ ABSOLUTES MAXIMUM ✦</bold></gold>"));
            lore.add(MM.deserialize("<gray>Höchstes Tier & Level erreicht!"));
            lore.add(MM.deserialize("<dark_gray>Mache Prestige für mehr Level."));
        }
        lore.add(MM.deserialize(""));
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

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Geld: <green>$" + MoneyManager.formatMoney(data.getMoney())));
        lore.add(MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige()));
        lore.add(MM.deserialize("<gray>Max Level: <aqua>" + data.maxGeneratorLevel()));
        lore.add(MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size()));

        // Rang-Info
        String group = data.getLpGroup();
        if (!group.equals("default")) {
            String rankColor = switch (group) {
                case "nexus"    -> "<gold>";
                case "catalyst" -> "<light_purple>";
                case "rune"     -> "<dark_purple>";
                default         -> "<gray>";
            };
            lore.add(MM.deserialize("<gray>Rang: " + rankColor + group.substring(0, 1).toUpperCase() + group.substring(1)
                    + " <dark_gray>(+" + (int)((data.getRankMultiplier()-1)*100) + "%)"));
        }

        // Booster-Status
        if (data.hasActiveBooster()) {
            long rem = data.getBoosterExpiry() - System.currentTimeMillis() / 1000;
            lore.add(MM.deserialize("<gold>⚡ Booster: <yellow>x" + data.getBoosterMultiplier()
                    + " <gray>(" + rem / 60 + "m " + rem % 60 + "s)"));
        } else if (!data.getStoredBoosters().isEmpty()) {
            lore.add(MM.deserialize("<gray>Booster bereit: <aqua>" + data.getStoredBoosters().size()
                    + " <dark_gray>(→ /gen booster)"));
        }

        // Tokens
        if (data.getUpgradeTokens() > 0) {
            lore.add(MM.deserialize("<aqua>✦ Upgrade-Tokens: <white>" + data.getUpgradeTokens()
                    + " <dark_gray>(Rechtsklick)"));
        }

        meta.lore(lore);
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

    /** Sucht benachbarte Generatoren desselben Typs auf Max-Level (für Fusion). */
    private List<PlacedGenerator> findFusionPartners(PlacedGenerator gen, PlayerData data) {
        List<PlacedGenerator> partners = new ArrayList<>();
        // Fusion nur möglich wenn es ein nächstes Fusions-Tier gibt
        if (gen.getType().getNextFusionTier() == null) return partners;
        if (gen.getLevel() < data.maxGeneratorLevel()) return partners;

        org.bukkit.World world = Bukkit.getWorld(gen.getWorld());
        if (world == null) return partners;

        int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] off : offsets) {
            org.bukkit.Location loc = new org.bukkit.Location(
                    world, gen.getX() + off[0], gen.getY() + off[1], gen.getZ() + off[2]);
            PlacedGenerator neighbor = plugin.getGeneratorManager().getAt(loc);
            if (neighbor != null
                    && neighbor.getType() == gen.getType()
                    && neighbor.getLevel() >= data.maxGeneratorLevel()
                    && neighbor.getType().getNextFusionTier() != null) {
                partners.add(neighbor);
            }
        }
        return partners;
    }

    private ItemStack buildFusionButton(PlacedGenerator gen, List<PlacedGenerator> partners) {
        de.pinkhorizon.generators.GeneratorType result = gen.getType().getNextFusionTier();
        boolean isUltraFusion = gen.getType().isMega(); // Mega→Ultra Fusion
        ItemStack item = new ItemStack(isUltraFusion ? Material.NETHER_STAR : Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(isUltraFusion
                ? "<light_purple><bold>◆ ULTRA-FUSION verfügbar!</bold>"
                : "<gold><bold>✦ FUSION verfügbar!</bold>"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Fusioniere 3 gleiche Max-Level-Generatoren"));
        lore.add(MM.deserialize(isUltraFusion
                ? "<gray>zu einem gewaltigen <light_purple>Ultra-Generator<gray>!"
                : "<gray>zu einem mächtigen <gold>Mega-Generator<gray>!"));
        lore.add(MM.deserialize(""));
        if (result != null) {
            lore.add(MM.deserialize("<gray>Ergebnis: " + result.getDisplayName()));
        }
        lore.add(MM.deserialize("<gray>Partner gefunden: <white>" + Math.min(partners.size(), 2)));
        lore.add(MM.deserialize(""));
        lore.add(MM.deserialize("<yellow>Klick → Jetzt fusionieren!"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Aktualisiert das Menü nach einem Upgrade – bleibt im Single-View wenn dort geöffnet. */
    private void refresh(Player player, PlacedGenerator gen) {
        if (singleView.contains(player.getUniqueId())) {
            openSingle(player, gen);
        } else {
            open(player);
        }
    }

    public void close(UUID uuid) {
        openInventories.remove(uuid);
        singleView.remove(uuid);
    }
}
