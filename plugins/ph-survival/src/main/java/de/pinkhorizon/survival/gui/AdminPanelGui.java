package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;

public class AdminPanelGui implements Listener {

    // ── Holder ────────────────────────────────────────────────────────────

    public enum View { MAIN, PLAYERS, PLAYER_ACTIONS, ECONOMY, SERVER, RANK_SELECT }

    public static class PanelHolder implements InventoryHolder {
        public final View view;
        public final UUID targetUuid;  // null wenn nicht relevant
        public final int  page;
        PanelHolder(View view, UUID targetUuid, int page) {
            this.view = view; this.targetUuid = targetUuid; this.page = page;
        }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Farben ────────────────────────────────────────────────────────────

    private static final TextColor PINK   = TextColor.color(0xFF69B4);
    private static final TextColor GOLD   = TextColor.color(0xFFD700);
    private static final TextColor DARK   = TextColor.color(0x2D2D2D);

    // ── Felder ────────────────────────────────────────────────────────────

    private final PHSurvival plugin;
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.GERMAN);

    // Chat-Input-System: adminUUID → callback
    private final Map<UUID, Consumer<String>> chatCallbacks = new HashMap<>();

    public AdminPanelGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ────────────────────────────────────────────────────────────

    public void openMain(Player admin) {
        Inventory inv = create(new PanelHolder(View.MAIN, null, 0), 54,
            "§d§l⚙ Admin-Panel §8» §7Übersicht");

        border(inv, Material.PURPLE_STAINED_GLASS_PANE);

        // Hauptmenü-Buttons
        inv.setItem(20, btn(Material.PLAYER_HEAD,      "§e§l👥 Spieler-Verwaltung",
            List.of("§7Alle Online-Spieler verwalten", "§7Kick • Ban • TP • Coins • Rang")));
        inv.setItem(22, btn(Material.GOLD_INGOT,       "§6§l💰 Wirtschaft",
            List.of("§7Coins verwalten", "§7Geben • Nehmen • Setzen • Prüfen")));
        inv.setItem(24, btn(Material.COMMAND_BLOCK,    "§c§l🔧 Server-Tools",
            List.of("§7Broadcast • Tag/Nacht", "§7ItemClear • Restart")));
        inv.setItem(31, btn(Material.NETHER_STAR,      "§b§l🏆 Ränge verwalten",
            List.of("§7Rang eines Spielers ändern")));

        inv.setItem(49, closeBtn());
        admin.openInventory(inv);
    }

    // ── Spielerliste ──────────────────────────────────────────────────────

    public void openPlayers(Player admin, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int perPage = 36;
        int totalPages = Math.max(1, (int) Math.ceil(online.size() / (double) perPage));
        page = Math.clamp(page, 0, totalPages - 1);

        Inventory inv = create(new PanelHolder(View.PLAYERS, null, page), 54,
            "§d§l👥 Spieler §8[" + (page+1) + "/" + totalPages + "]");
        border(inv, Material.CYAN_STAINED_GLASS_PANE);

        List<Player> pageList = online.subList(page * perPage, Math.min((page + 1) * perPage, online.size()));
        int[] slots = innerSlots(perPage);
        for (int i = 0; i < pageList.size(); i++) {
            Player target = pageList.get(i);
            inv.setItem(slots[i], playerHead(target, admin));
        }

        if (page > 0)            inv.setItem(45, navBtn(Material.ARROW, "§e◀ Zurück"));
        if (page < totalPages-1) inv.setItem(53, navBtn(Material.ARROW, "§eWeiter ▶"));
        inv.setItem(49, backBtn());
        admin.openInventory(inv);
    }

    private ItemStack playerHead(Player target, Player admin) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) head.getItemMeta();
        m.setOwningPlayer(target);
        SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(target.getUniqueId());
        long coins = plugin.getEconomyManager().getBalance(target.getUniqueId());
        m.displayName(comp(target.getName(), PINK, true));
        m.lore(List.of(
            comp("  Rang:   " + rank.id, NamedTextColor.GRAY, false),
            comp("  Coins:  " + FMT.format(coins), GOLD, false),
            comp("  Ping:   " + target.getPing() + " ms", NamedTextColor.GRAY, false),
            Component.empty(),
            comp("  ▶ Klicken zum Verwalten", NamedTextColor.YELLOW, false)
        ));
        head.setItemMeta(m);
        return head;
    }

    // ── Spieler-Aktionen ──────────────────────────────────────────────────

    public void openPlayerActions(Player admin, UUID targetUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String name = target.getName() != null ? target.getName() : targetUuid.toString().substring(0, 8);
        boolean online = target.isOnline();
        long coins = plugin.getEconomyManager().getBalance(targetUuid);
        SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(targetUuid);

        Inventory inv = create(new PanelHolder(View.PLAYER_ACTIONS, targetUuid, 0), 54,
            "§d§l⚙ Spieler: §f" + name);
        border(inv, Material.MAGENTA_STAINED_GLASS_PANE);

        // Info-Banner Slot 4
        inv.setItem(4, playerInfoItem(name, rank, coins, online));

        // Aktionen
        inv.setItem(10, btn(Material.BOOK,               "§e📋 Rang ändern",
            List.of("§7Aktuell: §f" + rank.id, "§eKlicken zum Ändern")));
        inv.setItem(12, btn(Material.GOLD_INGOT,         "§6➕ Coins geben",
            List.of("§7Aktuell: §f" + FMT.format(coins), "§eKlicken und Betrag eingeben")));
        inv.setItem(14, btn(Material.BARRIER,            "§c➖ Coins nehmen",
            List.of("§7Aktuell: §f" + FMT.format(coins), "§eKlicken und Betrag eingeben")));
        inv.setItem(16, btn(Material.COMPARATOR,         "§b⚙ Coins setzen",
            List.of("§7Aktuell: §f" + FMT.format(coins), "§eKlicken und Betrag eingeben")));

        if (online) {
            inv.setItem(28, btn(Material.ENDER_PEARL,    "§a🔗 Zu Spieler TP",
                List.of("§7Teleportiere dich zu §f" + name)));
            inv.setItem(30, btn(Material.LEAD,           "§a⬇ Spieler TP zu dir",
                List.of("§7Teleportiere §f" + name + " §7zu dir")));
            inv.setItem(32, btn(Material.LEATHER_BOOTS,  "§e🎮 Gamemode ändern",
                List.of("§7Survival / Creative / Spectator")));
            inv.setItem(34, btn(Material.PLAYER_HEAD,    "§c👢 Kicken",
                List.of("§7Spieler vom Server werfen", "§eKlicken und Grund eingeben")));
        }
        inv.setItem(40, btn(Material.TNT,                "§4🔨 Bannen",
            List.of("§7Spieler dauerhaft bannen", "§eKlicken und Grund eingeben")));

        inv.setItem(49, backBtn());
        admin.openInventory(inv);
    }

    private ItemStack playerInfoItem(String name, SurvivalRankManager.Rank rank, long coins, boolean online) {
        ItemStack item = new ItemStack(online ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta m = item.getItemMeta();
        m.displayName(comp("Spieler: " + name, PINK, true));
        m.lore(List.of(
            comp("  Status: " + (online ? "§aOnline" : "§7Offline"), NamedTextColor.GRAY, false),
            comp("  Rang:   " + rank.id, NamedTextColor.GRAY, false),
            comp("  Coins:  " + FMT.format(coins), GOLD, false)
        ));
        item.setItemMeta(m);
        return item;
    }

    // ── Wirtschaft ────────────────────────────────────────────────────────

    public void openEconomy(Player admin) {
        Inventory inv = create(new PanelHolder(View.ECONOMY, null, 0), 54,
            "§6§l💰 Wirtschaft");
        border(inv, Material.YELLOW_STAINED_GLASS_PANE);

        inv.setItem(20, btn(Material.GOLD_INGOT,  "§a➕ Coins geben",    List.of("§7Spieler auswählen + Betrag")));
        inv.setItem(22, btn(Material.IRON_INGOT,  "§c➖ Coins nehmen",   List.of("§7Spieler auswählen + Betrag")));
        inv.setItem(24, btn(Material.COMPARATOR,  "§b⚙ Coins setzen",   List.of("§7Spieler auswählen + Betrag")));
        inv.setItem(31, btn(Material.PAPER,        "§e📋 Balance prüfen", List.of("§7Spieler auswählen")));

        inv.setItem(49, backBtn());
        admin.openInventory(inv);
    }

    // ── Server-Tools ──────────────────────────────────────────────────────

    public void openServer(Player admin) {
        Inventory inv = create(new PanelHolder(View.SERVER, null, 0), 54,
            "§c§l🔧 Server-Tools");
        border(inv, Material.RED_STAINED_GLASS_PANE);

        World world = admin.getWorld();
        boolean isDay   = world.getTime() < 12300;
        boolean isRain  = world.hasStorm();

        inv.setItem(10, btn(Material.SUNFLOWER,       "§e☀ Tag setzen",
            List.of("§7Aktuell: §f" + (isDay ? "Tag" : "Nacht"))));
        inv.setItem(12, btn(Material.INK_SAC,         "§9🌙 Nacht setzen",
            List.of("§7Setzt Zeit auf Mitternacht")));
        inv.setItem(14, btn(isRain ? Material.WATER_BUCKET : Material.BUCKET,
            isRain ? "§b🌧 Regen stoppen" : "§9🌧 Regen starten",
            List.of("§7Aktuell: §f" + (isRain ? "Regen" : "Trocken"))));
        inv.setItem(16, btn(Material.TNT,             "§c💥 Item-Clear",
            List.of("§7Alle Boden-Items löschen")));

        inv.setItem(28, btn(Material.PAPER,           "§e📢 Broadcast",
            List.of("§7Netzweite Nachricht senden", "§eKlicken und Text eingeben")));
        inv.setItem(30, btn(Material.NETHER_STAR,     "§a⚡ Alle heilen",
            List.of("§7Alle Spieler vollständig heilen")));
        inv.setItem(32, btn(Material.TOTEM_OF_UNDYING,"§d🍗 Alle sättigen",
            List.of("§7Alle Spieler sättigen")));
        inv.setItem(34, btn(Material.REDSTONE,        "§4🔄 Server-Neustart",
            List.of("§7Startet einen 5-Minuten-Countdown", "§c⚠ Nicht rückgängig machbar!")));

        inv.setItem(49, backBtn());
        admin.openInventory(inv);
    }

    // ── Rang-Auswahl ─────────────────────────────────────────────────────

    public void openRankSelect(Player admin, UUID targetUuid) {
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(targetUuid).getName())
            .orElse("Unbekannt");
        SurvivalRankManager.Rank current = plugin.getRankManager().getRank(targetUuid);

        Inventory inv = create(new PanelHolder(View.RANK_SELECT, targetUuid, 0), 54,
            "§d§l🏆 Rang: §f" + name);
        border(inv, Material.PURPLE_STAINED_GLASS_PANE);

        // Alle Ränge als Buttons
        Material[] icons = {
            Material.NETHER_STAR,       // owner
            Material.DIAMOND,           // admin
            Material.EMERALD,           // dev
            Material.LAPIS_LAZULI,      // moderator
            Material.AMETHYST_SHARD,    // supporter
            Material.GOLD_INGOT,        // vip
            Material.IRON_INGOT         // spieler
        };
        int[] slots = {10, 12, 14, 16, 28, 30, 32};
        String[] rankIds = {"owner", "admin", "dev", "moderator", "supporter", "vip", "spieler"};
        String[] labels  = {"§4§lOwner", "§c§lAdmin", "§b§lDEV", "§9§lModerator",
                             "§3§lSupporter", "§6§lVIP", "§f§lSpieler"};

        for (int i = 0; i < rankIds.length; i++) {
            boolean active = rankIds[i].equals(current.id);
            ItemStack item = new ItemStack(icons[i]);
            ItemMeta m = item.getItemMeta();
            m.displayName(comp(labels[i] + (active ? " §a[AKTUELL]" : ""), NamedTextColor.WHITE, false)
                .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(
                active ? comp("  ◀ Aktueller Rang", NamedTextColor.GREEN, false)
                       : comp("  ▶ Klicken zum Setzen", NamedTextColor.YELLOW, false)
            ));
            if (active) m.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(m);
            inv.setItem(slots[i], item);
        }

        inv.setItem(49, backBtn());
        admin.openInventory(inv);
    }

    // ── Click-Handler ────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (!(event.getInventory().getHolder() instanceof PanelHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        switch (holder.view) {
            case MAIN           -> handleMain(admin, slot);
            case PLAYERS        -> handlePlayers(admin, slot, holder.page);
            case PLAYER_ACTIONS -> handlePlayerActions(admin, slot, holder.targetUuid);
            case ECONOMY        -> handleEconomy(admin, slot);
            case SERVER         -> handleServer(admin, slot);
            case RANK_SELECT    -> handleRankSelect(admin, slot, holder.targetUuid);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PanelHolder) event.setCancelled(true);
    }

    // ── Handler: Main ─────────────────────────────────────────────────────

    private void handleMain(Player admin, int slot) {
        switch (slot) {
            case 20 -> openPlayers(admin, 0);
            case 22 -> openEconomy(admin);
            case 24 -> openServer(admin);
            case 31 -> { chatInput(admin, "§e» Für Rang-Änderung: Spielernamen eingeben", input -> {
                if (input.equalsIgnoreCase("cancel")) { openMain(admin); return; }
                Player target = Bukkit.getPlayerExact(input);
                if (target == null) { admin.sendMessage(comp("Spieler nicht online.", NamedTextColor.RED, false)); openMain(admin); return; }
                openRankSelect(admin, target.getUniqueId());
            }); }
            case 49 -> admin.closeInventory();
        }
    }

    // ── Handler: Players ──────────────────────────────────────────────────

    private void handlePlayers(Player admin, int slot, int page) {
        if (slot == 49) { openMain(admin); return; }
        if (slot == 45) { openPlayers(admin, page - 1); return; }
        if (slot == 53) { openPlayers(admin, page + 1); return; }

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int perPage = 36;
        int[] slots = innerSlots(perPage);
        List<Player> pageList = online.subList(page * perPage, Math.min((page + 1) * perPage, online.size()));
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i] && i < pageList.size()) {
                openPlayerActions(admin, pageList.get(i).getUniqueId());
                return;
            }
        }
    }

    // ── Handler: Player Actions ───────────────────────────────────────────

    private void handlePlayerActions(Player admin, int slot, UUID targetUuid) {
        if (slot == 49) { openPlayers(admin, 0); return; }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetUuid);
        Player target = offline.getPlayer();
        String name = offline.getName() != null ? offline.getName() : "Unbekannt";

        switch (slot) {
            // Rang ändern
            case 10 -> openRankSelect(admin, targetUuid);

            // Coins geben
            case 12 -> chatInput(admin, "§e» Coins geben an §f" + name + "§e – Betrag eingeben:", input -> {
                if (input.equalsIgnoreCase("cancel")) { openPlayerActions(admin, targetUuid); return; }
                try {
                    long amount = Long.parseLong(input.replace(".", "").replace(",", ""));
                    plugin.getEconomyManager().deposit(targetUuid, amount);
                    admin.sendMessage(comp("✔ " + FMT.format(amount) + " Coins an " + name + " gegeben.", NamedTextColor.GREEN, false));
                    if (target != null) target.sendMessage(comp("Du hast " + FMT.format(amount) + " Coins erhalten.", GOLD, false));
                } catch (NumberFormatException e) {
                    admin.sendMessage(comp("Ungültiger Betrag.", NamedTextColor.RED, false));
                }
                openPlayerActions(admin, targetUuid);
            });

            // Coins nehmen
            case 14 -> chatInput(admin, "§e» Coins nehmen von §f" + name + "§e – Betrag eingeben:", input -> {
                if (input.equalsIgnoreCase("cancel")) { openPlayerActions(admin, targetUuid); return; }
                try {
                    long amount = Long.parseLong(input.replace(".", "").replace(",", ""));
                    boolean ok = plugin.getEconomyManager().withdraw(targetUuid, amount);
                    if (ok) {
                        admin.sendMessage(comp("✔ " + FMT.format(amount) + " Coins von " + name + " abgezogen.", NamedTextColor.GREEN, false));
                    } else {
                        admin.sendMessage(comp("Spieler hat nicht genug Coins.", NamedTextColor.RED, false));
                    }
                } catch (NumberFormatException e) {
                    admin.sendMessage(comp("Ungültiger Betrag.", NamedTextColor.RED, false));
                }
                openPlayerActions(admin, targetUuid);
            });

            // Coins setzen
            case 16 -> chatInput(admin, "§e» Coins setzen für §f" + name + "§e – Betrag eingeben:", input -> {
                if (input.equalsIgnoreCase("cancel")) { openPlayerActions(admin, targetUuid); return; }
                try {
                    long amount = Long.parseLong(input.replace(".", "").replace(",", ""));
                    plugin.getEconomyManager().setBalance(targetUuid, amount);
                    admin.sendMessage(comp("✔ Coins von " + name + " auf " + FMT.format(amount) + " gesetzt.", NamedTextColor.GREEN, false));
                } catch (NumberFormatException e) {
                    admin.sendMessage(comp("Ungültiger Betrag.", NamedTextColor.RED, false));
                }
                openPlayerActions(admin, targetUuid);
            });

            // TP zu Spieler
            case 28 -> {
                if (target == null) { admin.sendMessage(comp("Spieler ist offline.", NamedTextColor.RED, false)); return; }
                admin.teleport(target.getLocation());
                admin.sendMessage(comp("✔ Zu " + name + " teleportiert.", NamedTextColor.GREEN, false));
                openPlayerActions(admin, targetUuid);
            }

            // Spieler zu Admin TP
            case 30 -> {
                if (target == null) { admin.sendMessage(comp("Spieler ist offline.", NamedTextColor.RED, false)); return; }
                target.teleport(admin.getLocation());
                admin.sendMessage(comp("✔ " + name + " zu dir teleportiert.", NamedTextColor.GREEN, false));
                target.sendMessage(comp("Du wurdest von " + admin.getName() + " teleportiert.", NamedTextColor.YELLOW, false));
                openPlayerActions(admin, targetUuid);
            }

            // Gamemode
            case 32 -> {
                if (target == null) { admin.sendMessage(comp("Spieler ist offline.", NamedTextColor.RED, false)); return; }
                GameMode next = switch (target.getGameMode()) {
                    case SURVIVAL   -> GameMode.CREATIVE;
                    case CREATIVE   -> GameMode.SPECTATOR;
                    default         -> GameMode.SURVIVAL;
                };
                target.setGameMode(next);
                admin.sendMessage(comp("✔ Gamemode von " + name + " auf " + next.name() + " gesetzt.", NamedTextColor.GREEN, false));
                target.sendMessage(comp("Dein Gamemode wurde auf " + next.name() + " gesetzt.", NamedTextColor.YELLOW, false));
                openPlayerActions(admin, targetUuid);
            }

            // Kicken
            case 34 -> {
                if (target == null) { admin.sendMessage(comp("Spieler ist offline.", NamedTextColor.RED, false)); return; }
                chatInput(admin, "§e» Kick-Grund für §f" + name + "§e eingeben (oder §ccancel§e):", input -> {
                    if (input.equalsIgnoreCase("cancel")) { openPlayerActions(admin, targetUuid); return; }
                    Player fresh = Bukkit.getPlayer(targetUuid);
                    if (fresh != null) {
                        fresh.kick(Component.text("Du wurdest gekickt: " + input, NamedTextColor.RED));
                        admin.sendMessage(comp("✔ " + name + " wurde gekickt: " + input, NamedTextColor.GREEN, false));
                        Bukkit.broadcast(comp("[Admin] " + name + " wurde gekickt.", NamedTextColor.GRAY, false));
                    } else {
                        admin.sendMessage(comp("Spieler ist nicht mehr online.", NamedTextColor.RED, false));
                    }
                    openMain(admin);
                });
            }

            // Bannen
            case 40 -> chatInput(admin, "§e» Ban-Grund für §f" + name + "§e eingeben (oder §ccancel§e):", input -> {
                if (input.equalsIgnoreCase("cancel")) { openPlayerActions(admin, targetUuid); return; }
                offline.banPlayer(input, admin.getName());
                Player fresh = Bukkit.getPlayer(targetUuid);
                if (fresh != null) fresh.kick(Component.text("Du wurdest gebannt: " + input, NamedTextColor.DARK_RED));
                admin.sendMessage(comp("✔ " + name + " wurde gebannt: " + input, NamedTextColor.GREEN, false));
                Bukkit.broadcast(comp("[Admin] " + name + " wurde gebannt.", NamedTextColor.GRAY, false));
                openMain(admin);
            });
        }
    }

    // ── Handler: Economy ──────────────────────────────────────────────────

    private void handleEconomy(Player admin, int slot) {
        if (slot == 49) { openMain(admin); return; }

        String[] prompts = {
            "§e» Coins geben – §fSpieler:Betrag eingeben (z.B. §fSteve:5000§e):",
            "§e» Coins nehmen – §fSpieler:Betrag eingeben:",
            "§e» Coins setzen – §fSpieler:Betrag eingeben:",
            "§e» Balance prüfen – §fSpielernamen eingeben:"
        };
        int[] slotMap = {20, 22, 24, 31};
        String[] actions = {"give", "take", "set", "check"};
        for (int i = 0; i < slotMap.length; i++) {
            if (slot == slotMap[i]) {
                final String action = actions[i];
                chatInput(admin, prompts[i], input -> {
                    if (input.equalsIgnoreCase("cancel")) { openEconomy(admin); return; }
                    if (action.equals("check")) {
                        Player t = Bukkit.getPlayerExact(input);
                        UUID uid = t != null ? t.getUniqueId() : null;
                        if (uid == null) { admin.sendMessage(comp("Spieler nicht online.", NamedTextColor.RED, false)); openEconomy(admin); return; }
                        long bal = plugin.getEconomyManager().getBalance(uid);
                        admin.sendMessage(comp("💰 " + input + ": " + FMT.format(bal) + " Coins", GOLD, false));
                        openEconomy(admin);
                        return;
                    }
                    String[] parts = input.split(":", 2);
                    if (parts.length < 2) { admin.sendMessage(comp("Format: Spieler:Betrag", NamedTextColor.RED, false)); openEconomy(admin); return; }
                    Player t = Bukkit.getPlayerExact(parts[0].trim());
                    if (t == null) { admin.sendMessage(comp("Spieler nicht online.", NamedTextColor.RED, false)); openEconomy(admin); return; }
                    try {
                        long amount = Long.parseLong(parts[1].trim().replace(".", "").replace(",", ""));
                        switch (action) {
                            case "give" -> { plugin.getEconomyManager().deposit(t.getUniqueId(), amount); admin.sendMessage(comp("✔ " + FMT.format(amount) + " an " + t.getName() + " gegeben.", NamedTextColor.GREEN, false)); }
                            case "take" -> { boolean ok = plugin.getEconomyManager().withdraw(t.getUniqueId(), amount); admin.sendMessage(ok ? comp("✔ " + FMT.format(amount) + " von " + t.getName() + " abgezogen.", NamedTextColor.GREEN, false) : comp("Zu wenig Coins.", NamedTextColor.RED, false)); }
                            case "set"  -> { plugin.getEconomyManager().setBalance(t.getUniqueId(), amount); admin.sendMessage(comp("✔ Coins von " + t.getName() + " auf " + FMT.format(amount) + " gesetzt.", NamedTextColor.GREEN, false)); }
                        }
                    } catch (NumberFormatException e) {
                        admin.sendMessage(comp("Ungültiger Betrag.", NamedTextColor.RED, false));
                    }
                    openEconomy(admin);
                });
                return;
            }
        }
    }

    // ── Handler: Server ───────────────────────────────────────────────────

    private void handleServer(Player admin, int slot) {
        if (slot == 49) { openMain(admin); return; }
        World world = admin.getWorld();
        switch (slot) {
            case 10 -> { world.setTime(1000);  admin.sendMessage(comp("☀ Tag gesetzt.", NamedTextColor.YELLOW, false)); openServer(admin); }
            case 12 -> { world.setTime(14000); admin.sendMessage(comp("🌙 Nacht gesetzt.", NamedTextColor.AQUA, false)); openServer(admin); }
            case 14 -> {
                boolean rain = world.hasStorm();
                world.setStorm(!rain);
                world.setThundering(false);
                admin.sendMessage(comp(rain ? "🌤 Regen gestoppt." : "🌧 Regen gestartet.", NamedTextColor.AQUA, false));
                openServer(admin);
            }
            case 16 -> {
                admin.closeInventory();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "itemclear");
            }
            case 28 -> chatInput(admin, "§e» Broadcast-Text eingeben (oder §ccancel§e):", input -> {
                if (input.equalsIgnoreCase("cancel")) { openServer(admin); return; }
                Component msg = comp("[Broadcast] " + input, PINK, true);
                Bukkit.broadcast(msg);
                admin.sendMessage(comp("✔ Broadcast gesendet.", NamedTextColor.GREEN, false));
                openServer(admin);
            });
            case 30 -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setHealth(p.getMaxHealth());
                    p.setFoodLevel(20);
                    p.setSaturation(20f);
                }
                admin.sendMessage(comp("✔ Alle Spieler wurden geheilt.", NamedTextColor.GREEN, false));
                openServer(admin);
            }
            case 32 -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setFoodLevel(20);
                    p.setSaturation(20f);
                }
                admin.sendMessage(comp("✔ Alle Spieler wurden gesättigt.", NamedTextColor.GREEN, false));
                openServer(admin);
            }
            case 34 -> {
                admin.closeInventory();
                plugin.getServer().broadcast(comp("[Admin] Server-Neustart in 5 Minuten!", NamedTextColor.RED, false));
                // ph-core NetworkRestartManager auslösen
                de.pinkhorizon.core.PHCore.getInstance().getNetworkRestartManager().triggerManual(300);
            }
        }
    }

    // ── Handler: Rang ─────────────────────────────────────────────────────

    private void handleRankSelect(Player admin, int slot, UUID targetUuid) {
        if (slot == 49) { openPlayerActions(admin, targetUuid); return; }

        String[] rankIds = {"owner", "admin", "dev", "moderator", "supporter", "vip", "spieler"};
        int[] slots = {10, 12, 14, 16, 28, 30, 32};
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(targetUuid).getName()).orElse("Unbekannt");

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                String rankId = rankIds[i];
                plugin.getRankManager().setRank(targetUuid, name, rankId);
                admin.sendMessage(comp("✔ Rang von " + name + " auf " + rankId + " gesetzt.", NamedTextColor.GREEN, false));
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    target.sendMessage(comp("Dein Rang wurde auf " + rankId + " geändert.", PINK, false));
                    plugin.getRankManager().applyTabName(target);
                    plugin.getScoreboardManager().giveScoreboard(target);
                }
                openPlayerActions(admin, targetUuid);
                return;
            }
        }
    }

    // ── Chat-Input-System ────────────────────────────────────────────────

    private void chatInput(Player admin, String prompt, Consumer<String> callback) {
        admin.closeInventory();
        admin.sendMessage(Component.text("─────────────────────────", PINK));
        admin.sendMessage(Component.text(prompt.replace("&", "§")));
        admin.sendMessage(Component.text("  §8(§ccancel§8 zum Abbrechen)", NamedTextColor.GRAY));
        admin.sendMessage(Component.text("─────────────────────────", PINK));
        chatCallbacks.put(admin.getUniqueId(), callback);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> cb = chatCallbacks.remove(event.getPlayer().getUniqueId());
        if (cb == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(input));
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────

    private Inventory create(InventoryHolder holder, int size, String title) {
        return Bukkit.createInventory(holder, size,
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(title));
    }

    private void border(Inventory inv, Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.empty());
        pane.setItemMeta(m);
        for (int i = 0; i < 9; i++)     inv.setItem(i, pane);
        for (int i = 45; i < 54; i++)   inv.setItem(i, pane);
        for (int r = 1; r < 5; r++) {
            inv.setItem(r * 9, pane);
            inv.setItem(r * 9 + 8, pane);
        }
    }

    private ItemStack btn(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.displayName(legacy(name));
        m.lore(lore.stream().map(this::legacy).toList());
        item.setItemMeta(m);
        return item;
    }

    private ItemStack navBtn(Material mat, String name) {
        return btn(mat, name, List.of());
    }

    private ItemStack backBtn() {
        return btn(Material.ARROW, "§7◀ Zurück", List.of("§8Zum vorherigen Menü"));
    }

    private ItemStack closeBtn() {
        return btn(Material.BARRIER, "§c✖ Schließen", List.of());
    }

    private int[] innerSlots(int max) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots.stream().mapToInt(Integer::intValue).limit(max).toArray();
    }

    private Component comp(String text, TextColor color, boolean bold) {
        Component c = Component.text(text, color).decoration(TextDecoration.ITALIC, false);
        return bold ? c.decoration(TextDecoration.BOLD, true) : c;
    }

    private Component legacy(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
