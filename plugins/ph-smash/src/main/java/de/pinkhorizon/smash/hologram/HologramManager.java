package de.pinkhorizon.smash.hologram;

import de.pinkhorizon.smash.PHSmash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.time.DayOfWeek;
import java.util.*;

public class HologramManager {

    // ── Hologramm-Typen ────────────────────────────────────────────────────

    public enum HologramType {
        KILLS    ("kills",    "§c§l⚔ Top Kills"),
        LEVEL    ("level",    "§a§l★ Top Boss-Level"),
        DAMAGE   ("damage",   "§e§l✦ Top Schaden"),
        COINS    ("coins",    "§6§l✦ Top Münzen"),
        PRESTIGE ("prestige", "§d§l★ Top Prestige"),
        WEEKLY   ("weekly",   "§b§l⏳ Wochenturnier – Top Kills"),
        COMMANDS ("commands", "§b§l✦ Befehle");

        public final String key;
        public final String title;

        HologramType(String key, String title) {
            this.key = key; this.title = title;
        }

        public static HologramType fromKey(String key) {
            for (HologramType t : values())
                if (t.key.equalsIgnoreCase(key)) return t;
            return null;
        }
    }

    private record LeaderEntry(String name, long value, long extra) {
        LeaderEntry(String name, long value) { this(name, value, 0); }
    }

    // ── Felder ─────────────────────────────────────────────────────────────

    private final PHSmash                          plugin;
    private final Map<HologramType, TextDisplay>   displays = new EnumMap<>(HologramType.class);
    private BukkitTask refreshTask;

    private static final LegacyComponentSerializer LEGACY   = LegacyComponentSerializer.legacySection();
    private static final int                       REFRESH_TICKS = 20 * 60; // 60 Sekunden

    public HologramManager(PHSmash plugin) {
        this.plugin = plugin;
        // Entitäten erst spawnen wenn Server-Ticks laufen
        Bukkit.getScheduler().runTask(plugin, this::spawnAll);
        startRefreshTask();
    }

    // ── Öffentliche API ────────────────────────────────────────────────────

    /** Admin-Befehl: Hologramm an aktueller Position setzen */
    public void setHologram(Player admin, HologramType type) {
        Location loc = admin.getLocation().add(0, 0.3, 0);
        saveLocation(type, loc);
        // Entity spawnen (main thread), dann Daten async laden
        spawnEntity(type, loc);
        admin.sendMessage("§a✔ §7Hologramm §e" + type.title + " §7gesetzt. Lädt Daten...");
    }

    /** Beim Plugin-Disable alle Holograms entfernen */
    public void removeAll() {
        if (refreshTask != null) refreshTask.cancel();
        for (TextDisplay td : displays.values())
            if (td != null && td.isValid()) td.remove();
        displays.clear();
    }

    // ── Spawn & Refresh ────────────────────────────────────────────────────

    private void spawnAll() {
        for (HologramType type : HologramType.values()) {
            Location loc = loadLocation(type);
            if (loc != null) spawnEntity(type, loc);
        }
    }

    /** Spawnt die TextDisplay-Entität (main thread) und lädt dann Daten (async). */
    private void spawnEntity(HologramType type, Location loc) {
        // Alte entfernen
        TextDisplay old = displays.remove(type);
        if (old != null && old.isValid()) old.remove();

        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.setPersistent(false);
        td.setDefaultBackground(true);
        td.setSeeThrough(false);
        td.setBillboard(Display.Billboard.CENTER);
        td.setLineWidth(200);
        td.setViewRange(1.5f);
        displays.put(type, td);

        if (type == HologramType.COMMANDS) {
            td.text(buildText(type, List.of()));
        } else {
            td.text(LEGACY.deserialize("§7Lade Rangliste..."));
            // Async: Daten aus DB holen, dann Anzeige aktualisieren
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<LeaderEntry> data = fetchData(type);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (td.isValid()) td.text(buildText(type, data));
                });
            });
        }
    }

    private void startRefreshTask() {
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Für jeden aktiven Typ Daten holen (COMMANDS ist statisch – kein Refresh nötig)
            Map<HologramType, List<LeaderEntry>> allData = new EnumMap<>(HologramType.class);
            for (HologramType type : displays.keySet()) {
                if (type != HologramType.COMMANDS) allData.put(type, fetchData(type));
            }
            // Text-Update zurück auf Main-Thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Map.Entry<HologramType, TextDisplay> e : displays.entrySet()) {
                    TextDisplay td = e.getValue();
                    if (td == null || !td.isValid()) continue;
                    List<LeaderEntry> data = allData.get(e.getKey());
                    if (data != null) td.text(buildText(e.getKey(), data));
                }
            });
        }, REFRESH_TICKS, REFRESH_TICKS);
    }

    // ── Daten aus Datenbank ────────────────────────────────────────────────

    private List<LeaderEntry> fetchData(HologramType type) {
        if (type == HologramType.COMMANDS) return List.of();

        List<LeaderEntry> result = new ArrayList<>();

        // COINS and PRESTIGE use their own tables
        if (type == HologramType.COINS) {
            String sql = "SELECT uuid, coins FROM smash_coins WHERE coins > 0 ORDER BY coins DESC LIMIT 10";
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement(sql);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    UUID   uuid  = UUID.fromString(rs.getString("uuid"));
                    long   value = rs.getLong("coins");
                    String name  = Bukkit.getOfflinePlayer(uuid).getName();
                    result.add(new LeaderEntry(name != null ? name : "???", value));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("HologramManager.fetchData(COINS): " + e.getMessage());
            }
            return result;
        }

        if (type == HologramType.PRESTIGE) {
            String sql = "SELECT uuid, prestige FROM smash_prestige WHERE prestige > 0 ORDER BY prestige DESC LIMIT 10";
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement(sql);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    UUID   uuid  = UUID.fromString(rs.getString("uuid"));
                    long   value = rs.getLong("prestige");
                    String name  = Bukkit.getOfflinePlayer(uuid).getName();
                    result.add(new LeaderEntry(name != null ? name : "???", value));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("HologramManager.fetchData(PRESTIGE): " + e.getMessage());
            }
            return result;
        }

        if (type == HologramType.WEEKLY) {
            java.time.LocalDate weekStart = java.time.LocalDate.now()
                .with(java.time.DayOfWeek.MONDAY);
            String sql = "SELECT uuid, kills, best_level FROM smash_weekly WHERE week_start = ? AND kills > 0 ORDER BY kills DESC LIMIT 10";
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement(sql)) {
                st.setDate(1, java.sql.Date.valueOf(weekStart));
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        UUID   uuid  = UUID.fromString(rs.getString("uuid"));
                        long   kills = rs.getLong("kills");
                        long   best  = rs.getLong("best_level");
                        String name  = Bukkit.getOfflinePlayer(uuid).getName();
                        result.add(new LeaderEntry(name != null ? name : "???", kills, best));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("HologramManager.fetchData(WEEKLY): " + e.getMessage());
            }
            return result;
        }

        String col = switch (type) {
            case KILLS  -> "kills";
            case LEVEL  -> "personal_level";
            case DAMAGE -> "total_damage";
            default     -> "kills";
        };
        String sql = "SELECT uuid, " + col + " FROM smash_players"
                   + " WHERE " + col + " > 0 ORDER BY " + col + " DESC LIMIT 10";
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                UUID   uuid  = UUID.fromString(rs.getString("uuid"));
                long   value = rs.getLong(col);
                String name  = Bukkit.getOfflinePlayer(uuid).getName();
                result.add(new LeaderEntry(name != null ? name : "???", value));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("HologramManager.fetchData: " + e.getMessage());
        }
        return result;
    }

    // ── Text-Builder ───────────────────────────────────────────────────────

    private Component buildText(HologramType type, List<LeaderEntry> data) {
        if (type == HologramType.COMMANDS) return buildCommandsText();

        StringBuilder sb = new StringBuilder();

        // Wochendatum im Header nur beim Weekly-Hologramm
        if (type == HologramType.WEEKLY) {
            java.time.LocalDate monday = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY);
            java.time.LocalDate sunday = monday.plusDays(6);
            String weekRange = String.format("%02d.%02d. – %02d.%02d.",
                monday.getDayOfMonth(), monday.getMonthValue(),
                sunday.getDayOfMonth(), sunday.getMonthValue());
            sb.append(type.title).append("\n");
            sb.append("§8§m──────────────────\n");
            sb.append("§7Woche: §f").append(weekRange).append("\n");
            sb.append("§8§m──────────────────\n");
        } else {
            sb.append(type.title).append("\n");
            sb.append("§8§m──────────────────\n");
        }

        String[] medals = {"§6✦ ", "§7✦ ", "§8✦ ", "  ", "  ", "  ", "  ", "  ", "  ", "  "};
        String[] rankColors = {"§6", "§7", "§8", "§7", "§7", "§7", "§7", "§7", "§7", "§7"};

        for (int i = 0; i < data.size(); i++) {
            LeaderEntry e    = data.get(i);
            String medal     = i < medals.length ? medals[i] : "  ";
            String rankColor = i < rankColors.length ? rankColors[i] : "§7";
            String valueStr  = switch (type) {
                case KILLS    -> "§c" + e.value() + " §7Kills";
                case LEVEL    -> "§aLv. §2" + e.value();
                case DAMAGE   -> "§e" + formatDmg(e.value()) + " §7Dmg";
                case COINS    -> "§6" + e.value() + " §7Münzen";
                case PRESTIGE -> "§d✦ §f" + e.value() + " §7Prestige";
                case WEEKLY   -> "§b" + e.value() + " §7Kills  §8(§7Lv. §c" + e.extra() + "§8)";
                case COMMANDS -> "";
            };
            sb.append(medal).append(rankColor).append("#").append(i + 1)
              .append(" §f").append(e.name())
              .append(" §8– ").append(valueStr);
            if (i < data.size() - 1) sb.append("\n");
        }

        if (data.isEmpty()) {
            sb.append("§7Noch keine Einträge vorhanden.\n§8Besiege deinen ersten Boss!");
        }

        sb.append("\n§8§m──────────────────");
        sb.append("\n§8Aktualisiert alle 60s");

        return LEGACY.deserialize(sb.toString());
    }

    // ── Config-Persistenz ─────────────────────────────────────────────────

    private void saveLocation(HologramType type, Location loc) {
        String path = "holograms." + type.key;
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x",     loc.getX());
        plugin.getConfig().set(path + ".y",     loc.getY());
        plugin.getConfig().set(path + ".z",     loc.getZ());
        plugin.saveConfig();
    }

    private Location loadLocation(HologramType type) {
        String path      = "holograms." + type.key;
        String worldName = plugin.getConfig().getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
            plugin.getConfig().getDouble(path + ".x"),
            plugin.getConfig().getDouble(path + ".y"),
            plugin.getConfig().getDouble(path + ".z"));
    }

    // ── Befehls-Hologramm (statisch) ──────────────────────────────────────

    private Component buildCommandsText() {
        String nl  = "\n";
        String div = "§8§m─────────────────────" + nl;
        return LEGACY.deserialize(
            "§b§l≡ §r§c§lSmash the Boss §b§l≡" + nl +
            div +
            "§e§l✦ Allgemein" + nl +
            " §7NPC §8»  §aArena betreten §8oder §cArena verlassen" + nl +
            " §7Kompass §8» §7Navigator öffnen" + nl +
            " §f/stb join     §8» §7Arena betreten" + nl +
            " §f/stb leave    §8» §7Arena verlassen" + nl +
            " §f/stb stats    §8» §7Deine Statistiken" + nl +
            " §f/stb coins    §8» §7Münzstand anzeigen" + nl +
            div +
            "§6§l⬆ Upgrades & Fähigkeiten" + nl +
            " §f/stb upgrades  §8» §7Angriff, Verteidigung, HP, Tempo, Lebensraub" + nl +
            " §f/stb abilities §8» §7Fähigkeiten: Allgemein, §c⚔ Schwert§7, §e🏹 Bogen§7," + nl +
            "                   §b🪓 Axt §8(Blutung/Klaf. Wunde/Tiefe Hiebe)§7, §6🔥 Feuerball§7, §dIMMUN" + nl +
            div +
            "§6§l⚒ Schmiede & Bestiarium" + nl +
            " §f/stb forge     §8» §7Waffen-Verzauberungen schmieden" + nl +
            " §f/stb bestiary  §8» §7Boss-Kills nach Typ ansehen" + nl +
            " §7Im Navigator §8» §6Schmiede §8/ §2Bestiarium" + nl +
            div +
            "§5§l✦ Talente, Runen & Tägliche Quests" + nl +
            " §f/stb prestige  §8» §7Prestige (ab Boss-Level §c999§7)" + nl +
            " §7Im Navigator §8» §5Talente §8/ §cRunen §8/ §eTägl. Quests" + nl +
            div +
            "§e§l⏳ Turnier & Kopfgeld" + nl +
            " §f/stb weekly   §8» §7Wöchentl. Turnier-Rangliste" + nl +
            " §f/stb bounty   §8» §7Tages-Kopfgeld ansehen" + nl +
            " §7Tages-Kopfgeld: §6+1000 Coins §8+ §d3x Boss-Kern" + nl +
            div +
            "§a§l☆ Shop" + nl +
            " §7Im Navigator §8» §aShop §8– Tränke & Items" + nl +
            div +
            "§c§l⚔ Kampf-Tipps" + nl +
            " §7Schwert §8& §7Bogen schaden skalieren mit Upgrades" + nl +
            " §7Boss-Phasen: §e75% §8/ §c50% Rage §8/ §425% Heilung" + nl +
            " §7Combo-System: §e+2% §7Schaden pro Treffer §8(max §e+50%§8)" + nl +
            " §7Stirbst du? §cStreak verloren!" + nl +
            " §aNach Boss-Sieg: §fKristall Rechtsklick §8» §anächsten Boss!" + nl +
            div +
            "§8play.pinkhorizon.fun"
        );
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private static String formatDmg(long dmg) {
        if (dmg >= 1_000_000_000) return String.format("%.1fB", dmg / 1_000_000_000.0);
        if (dmg >= 1_000_000)     return String.format("%.1fM", dmg / 1_000_000.0);
        if (dmg >= 1_000)         return String.format("%.1fK", dmg / 1_000.0);
        return String.valueOf(dmg);
    }
}
