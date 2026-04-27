package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.Material;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Island Chronicles: Automatisches Tagebuch für jede Insel.
 * Zeichnet wichtige Ereignisse auf und macht sie lesbar.
 */
public class ChronicleManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final PHSkyBlock plugin;

    public ChronicleManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        createTables();
    }

    private void createTables() {
        try (Connection c = plugin.getDatabase().getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_chronicles (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    island_uuid VARCHAR(36)  NOT NULL,
                    type        VARCHAR(64)  NOT NULL,
                    message     VARCHAR(512) NOT NULL,
                    created_at  BIGINT       NOT NULL,
                    INDEX idx_island (island_uuid)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Chronicle-Tabelle Fehler: " + e.getMessage());
        }
    }

    /**
     * Fügt einen Eintrag zur Insel-Chronik des Spielers hinzu.
     */
    public void addEntry(UUID playerUuid, String type, String message) {
        BentoBoxHook.getIsland(playerUuid).ifPresent(island ->
            insertEntry(playerUuid, type, message));
    }

    public void insertEntry(UUID islandUuid, String type, String message) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sky_chronicles (island_uuid, type, message, created_at) VALUES(?,?,?,?)")) {
                ps.setString(1, islandUuid.toString());
                ps.setString(2, type);
                ps.setString(3, message);
                ps.setLong  (4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Chronicle-Eintrag fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    /**
     * Gibt dem Spieler das Chronik-Buch.
     */
    public void giveChronicle(Player player) {
        var islandOpt = BentoBoxHook.getIsland(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Du hast keine Insel."));
            return;
        }
        UUID islandUuid = player.getUniqueId();

        List<String[]> entries = loadEntries(islandUuid, 20);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Insel-Chronik");
        meta.setAuthor(player.getName());

        List<net.kyori.adventure.text.Component> pages = new ArrayList<>();

        // Titelseite
        pages.add(MM.deserialize(
            "<gold><bold>Insel-Chronik\n\n"
            + "<dark_gray>von <white>" + player.getName() + "\n\n"
            + "<gray>" + DATE_FMT.format(new java.util.Date())));

        // Einträge (max 10 pro Seite, max 2 Seiten)
        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        for (String[] entry : entries) {
            String line = "[" + formatDate(Long.parseLong(entry[2])) + "]\n" + entry[1] + "\n";
            sb.append(line);
            lineCount++;
            if (lineCount >= 8) {
                pages.add(net.kyori.adventure.text.Component.text(sb.toString()));
                sb = new StringBuilder();
                lineCount = 0;
            }
        }
        if (!sb.isEmpty()) {
            pages.add(net.kyori.adventure.text.Component.text(sb.toString()));
        }
        if (pages.size() == 1) {
            pages.add(net.kyori.adventure.text.Component.text("Noch keine Einträge.\n\nSpiele und schreibe Geschichte!"));
        }

        meta.pages(pages);
        book.setItemMeta(meta);

        // Buch direkt geben oder altes ersetzen
        player.getInventory().addItem(book);
        player.sendMessage(MM.deserialize("<gold>📖 Insel-Chronik erhalten!"));
    }

    private List<String[]> loadEntries(UUID islandUuid, int limit) {
        List<String[]> result = new ArrayList<>();
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT type, message, created_at FROM sky_chronicles WHERE island_uuid=? " +
                 "ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, islandUuid.toString());
            ps.setInt   (2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new String[]{
                        rs.getString("type"),
                        rs.getString("message"),
                        String.valueOf(rs.getLong("created_at"))
                    });
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Chronicle laden fehlgeschlagen: " + e.getMessage());
        }
        Collections.reverse(result); // älteste zuerst
        return result;
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date(millis));
    }

    // ── Standard-Ereignisse ───────────────────────────────────────────────────

    public void onIslandCreated(UUID playerUuid) {
        addEntry(playerUuid, "island_created", "Insel gegründet – der Anfang einer Legende.");
    }

    public void onFirstDiamond(UUID playerUuid) {
        addEntry(playerUuid, "first_diamond", "Ersten Diamanten abgebaut!");
    }

    public void onPrestige(UUID playerUuid, int prestige) {
        addEntry(playerUuid, "prestige", "Prestige " + prestige + " erreicht!");
    }

    public void onAchievement(UUID playerUuid, String name) {
        addEntry(playerUuid, "achievement", "Achievement freigeschaltet: " + name);
    }
}
