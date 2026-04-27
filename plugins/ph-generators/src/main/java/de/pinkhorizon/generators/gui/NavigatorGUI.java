package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigator (Spielerkopf, Slot 8) + Generator-Aufheber (Goldspitzhacke, Slot 7).
 * Navigator-GUI: Übersicht aller IdleForge-Menüs.
 *
 * Layout (54 Slots):
 *   Row 0: [fill][fill][fill][fill][STATS][fill][fill][fill][fill]
 *   Row 1: [fill][SHOP][fill][UPGR][fill][BLKS][fill][PRST][fill]
 *   Row 2: [fill][QUST][fill][ACHV][fill][LDRB][fill][GILD][fill]
 *   Row 3: [fill][BLST][fill][TLNT][fill][MRKT][fill][MILS][fill]
 *   Row 4: [fill][SASN][fill][SYNG][fill][BRDR][fill][fill][fill]
 *   Row 5: all filler
 */
public class NavigatorGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "✦ IdleForge – Menüs";
    private static final int NAVIGATOR_SLOT   = 8;
    private static final int PICKER_SLOT      = 7;
    private static final int MINING_PICK_SLOT = 6;

    private final NamespacedKey miningPickaxeKey;

    public NavigatorGUI(PHGenerators plugin) {
        this.plugin = plugin;
        this.miningPickaxeKey = new NamespacedKey(plugin, "mining_pickaxe");
    }

    // ── Items geben ──────────────────────────────────────────────────────────

    public void giveCompass(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        player.getInventory().setItem(NAVIGATOR_SLOT,   buildNavigator(player));
        player.getInventory().setItem(PICKER_SLOT,      buildPicker());
        player.getInventory().setItem(MINING_PICK_SLOT, buildMiningPickaxe(data));
    }

    private ItemStack buildNavigator(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<light_purple><bold>✦ Menü-Navigator</bold>"));
        meta.lore(List.of(MM.deserialize("<gray>Rechtsklick → Alle Menüs")));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildMiningPickaxe(PlayerData data) {
        int lvl = data != null ? data.getMiningPickaxeLevel() : 1;
        double mult = 1.0 + (lvl - 1) * 0.15;
        double shardBonus = lvl * 1.0;
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<aqua><bold>⛏ Mining-Spitzhacke</bold> <dark_gray>[Lvl " + lvl + "]"));
        meta.lore(List.of(
                MM.deserialize("<gray>Für den Mining-Block benutzen"),
                MM.deserialize(""),
                MM.deserialize("<gray>Geld-Multiplikator: <green>x" + String.format("%.2f", mult)),
                MM.deserialize("<gray>Shard-Bonus: <light_purple>+" + (int) shardBonus + "%"),
                MM.deserialize(""),
                MM.deserialize("<dark_gray>/gen mining pickaxe upgrade")
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(miningPickaxeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPicker() {
        ItemStack item = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<yellow><bold>⛏ Generator-Aufheber</bold>"));
        meta.lore(List.of(
                MM.deserialize("<gray>Rechtsklick auf Generator → Aufheben"),
                MM.deserialize("<dark_gray>Level-Fortschritt bleibt erhalten!")
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    // ── Interaktion ──────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isNavigator(item)) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            event.setCancelled(true);
            open(player);
            return;
        }

        if (isMiningPickaxe(item)) {
            if ((event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                    && player.isSneaking()) {
                event.setCancelled(true);
                plugin.getMiningUpgradeGUI().open(player);
            }
            return;
        }

        if (isPicker(item)) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;

            PlacedGenerator gen = plugin.getGeneratorManager()
                    .getAt(event.getClickedBlock().getLocation());
            if (gen == null) return;

            event.setCancelled(true);

            if (!gen.getOwnerUUID().equals(player.getUniqueId())
                    && !player.hasPermission("ph.generators.admin")) {
                player.sendMessage(MM.deserialize("<red>Das ist nicht dein Generator!"));
                return;
            }

            ItemStack drop = plugin.getGeneratorManager().removeGeneratorWithDrop(
                    event.getClickedBlock().getLocation(), player.getUniqueId());
            if (drop == null) return;

            event.getClickedBlock().setType(Material.AIR);

            var leftover = player.getInventory().addItem(drop);
            if (!leftover.isEmpty()) {
                event.getClickedBlock().getWorld().dropItemNaturally(
                        player.getLocation(), leftover.values().iterator().next());
            }
            player.sendMessage(MM.deserialize(
                    "<yellow>⛏ Generator aufgehoben. <gray>Level-Fortschritt bleibt erhalten!"));
        }
    }

    // ── Schutz: nicht wegwerfen ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isSpecialItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isSpecialItem(event.getMainHandItem()) || isSpecialItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (isSpecialItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (isSpecialItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != null
                && event.getClickedInventory() == player.getInventory()) {
            int slot = event.getSlot();
            if (slot == NAVIGATOR_SLOT || slot == PICKER_SLOT || slot == MINING_PICK_SLOT) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveCompass(player), 1L);
    }

    // ── Navigator-GUI ────────────────────────────────────────────────────────

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<light_purple>" + TITLE));

        ItemStack fill = filler();
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        // Stats-Item
        inv.setItem(4, buildStatsItem(player, data));

        // ── Row 1: Haupt-Menüs ────────────────────────────────────────────
        inv.setItem(10, btn(Material.GOLD_BLOCK,       "<gold>Generator-Shop",
                "<gray>Generatoren & Booster kaufen",          "/gen shop"));
        inv.setItem(12, btn(Material.ANVIL,            "<aqua>Upgrades",
                "<gray>Generatoren upgraden",                  "/gen upgrade"));
        inv.setItem(14, btn(Material.CHEST,            "<yellow>Block-Shop",
                "<gray>Inselblöcke kaufen",                    "/gen blockshop"));
        inv.setItem(16, btn(Material.NETHER_STAR,      "<light_purple>Prestige",
                "<gray>Prestige durchführen",                  "/gen prestige"));

        // ── Row 2: Fortschritt & Social ──────────────────────────────────
        inv.setItem(19, btn(Material.BOOK,             "<green>Quests",
                "<gray>Tägliche & wöchentliche Aufgaben",      "/gen quests"));
        inv.setItem(21, btn(Material.TOTEM_OF_UNDYING, "<yellow>Achievements",
                "<gray>Errungenschaften ansehen",              "/gen achievements"));
        inv.setItem(23, btn(Material.DIAMOND,          "<aqua>Leaderboard",
                "<gray>Top-10 nach Geld",                      "/gen top"));
        inv.setItem(25, btn(Material.SHIELD,           "<blue>Gilde",
                "<gray>Gilde verwalten / beitreten",           "/gen guild"));

        // ── Row 3: Extras ────────────────────────────────────────────────
        inv.setItem(28, btn(Material.BLAZE_POWDER,     "<gold>Booster",
                "<gray>Gespeicherte Booster aktivieren",       "/gen booster"));
        inv.setItem(30, btn(Material.NETHER_STAR,      "<light_purple>Talente",
                "<gray>Talent-Baum öffnen",                    "/gen talents"));
        inv.setItem(32, btn(Material.EMERALD,           "<aqua>Token-Shop",
                "<gray>Tokens gegen Geld oder Booster tauschen", "/gen tokenshop"));
        inv.setItem(34, btn(Material.BEACON,           "<aqua>Meilensteine",
                "<gray>Meilensteine & Belohnungen",            "/gen milestones"));

        // ── Row 4: Verwaltung ────────────────────────────────────────────
        inv.setItem(37, btn(Material.CLOCK,            "<white>Saison-LB",
                "<gray>Saison-Rangliste anzeigen",             "/gen season"));
        inv.setItem(39, btn(Material.COMPARATOR,       "<gray>Synergien",
                "<gray>Aktive Generator-Synergien",            "/gen synergy"));
        inv.setItem(41, btn(Material.CYAN_CONCRETE,    "<cyan>Insel-Grenze",
                "<gray>Spielfeld erweitern",                   "/gen border"));
        inv.setItem(43, btn(Material.PLAYER_HEAD,      "<white>Spieler besuchen",
                "<gray>Insel eines Spielers besuchen",         "/gen visit "));
        inv.setItem(46, btn(Material.AMETHYST_BLOCK,   "<light_purple>Mining-Block",
                "<gray>Schlag & verdiene Geld + Shards",       "/gen mining"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<light_purple>" + TITLE))) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return;

        var lore = item.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) return;

        String cmd = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(lore.get(lore.size() - 1));

        if (!cmd.startsWith("/")) return;

        // Besuchen braucht noch einen Spielernamen → Inventar schließen und Chat öffnen
        if (cmd.trim().equals("/gen visit")) {
            player.closeInventory();
            player.sendMessage(MM.deserialize(
                    "<yellow>Gib den Spielernamen ein: <white>/gen visit <spielername>"));
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(cmd.substring(1).trim()));
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    /** Erstellt einen Menü-Button. Die letzte Lore-Zeile enthält den Befehl (versteckt). */
    private ItemStack btn(Material mat, String name, String desc, String command) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(
                MM.deserialize(desc),
                MM.deserialize(""),
                MM.deserialize("<yellow>▶ Klick zum Öffnen"),
                MM.deserialize("<dark_gray>" + command)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStatsItem(Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            item.setItemMeta(skull);
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold><bold>" + player.getName() + "</bold>"));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (data != null) {
            // Geld & Prestige
            lore.add(MM.deserialize("<gray>Guthaben: <green>$" + MoneyManager.formatMoney(data.getMoney())));
            lore.add(MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige()
                    + " <dark_gray>/ " + plugin.getConfig().getInt("max-prestige", 1000)));
            lore.add(MM.deserialize("<gray>Max Level: <aqua>" + data.maxGeneratorLevel()));
            int talentSlots = plugin.getTalentManager() != null
                    ? plugin.getTalentManager().getExtraGeneratorSlots(data) : 0;
            lore.add(MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size()
                    + " <dark_gray>/ " + data.maxGeneratorSlots(
                        plugin.getConfig().getInt("max-generators", 10),
                        plugin.getConfig().getInt("generator-slot-per-prestige", 2),
                        talentSlots)));

            // Aktiver Booster
            if (data.hasActiveBooster()) {
                long rem = data.getBoosterExpiry() - System.currentTimeMillis() / 1000;
                lore.add(MM.deserialize("<gold>⚡ Booster: <yellow>x" + data.getBoosterMultiplier()
                        + " <gray>(" + rem / 60 + "m " + rem % 60 + "s)"));
            } else if (!data.getStoredBoosters().isEmpty()) {
                lore.add(MM.deserialize("<gray>⚡ Booster bereit: <aqua>" + data.getStoredBoosters().size()
                        + " <dark_gray>→ /gen booster"));
            }

            // Server-Booster
            if (plugin.getMoneyManager().isServerBoosterActive()) {
                long rem = plugin.getMoneyManager().getServerBoosterExpiry() - System.currentTimeMillis() / 1000;
                lore.add(MM.deserialize("<gold>⚡ Server-Booster: <yellow>x"
                        + plugin.getMoneyManager().getServerBoosterMultiplier()
                        + " <gray>(" + rem / 60 + "m)"));
            }

            // Rang
            String group = data.getLpGroup();
            String rankDisp = switch (group) {
                case "nexus"    -> "<gold>[Nexus]";
                case "catalyst" -> "<light_purple>[Catalyst]";
                case "rune"     -> "<dark_purple>[Rune]";
                default         -> "<gray>Spieler";
            };
            lore.add(MM.deserialize("<gray>Rang: " + rankDisp));

            // Tokens
            if (data.getUpgradeTokens() > 0) {
                lore.add(MM.deserialize("<gray>Upgrade-Tokens: <aqua>" + data.getUpgradeTokens()));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray> "));
        item.setItemMeta(meta);
        return item;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private boolean isNavigator(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.contains("Navigator");
    }

    private boolean isPicker(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_PICKAXE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.contains("Aufheber");
    }

    public boolean isMiningPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(miningPickaxeKey, PersistentDataType.BYTE);
    }

    private boolean isSpecialItem(ItemStack item) {
        return isNavigator(item) || isPicker(item) || isMiningPickaxe(item);
    }
}
