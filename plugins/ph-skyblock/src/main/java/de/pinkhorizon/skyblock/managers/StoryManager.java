package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Story/Lore-System: Void-Göttin Nyx schläft unter den Inseln.
 * 5 Story-Kapitel, die sich durch normale Spielprogression entfalten.
 */
public class StoryManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Kapitel-Voraussetzungen
    public static final int CHAPTER_2_ISLAND_LEVEL  = 50;
    public static final int CHAPTER_3_ISLAND_LEVEL  = 200;
    public static final int CHAPTER_4_PRESTIGE      = 1;
    // Kapitel 5: Community-Event (manuell ausgelöst)

    private final PHSkyBlock plugin;

    // player-uuid → aktuelles Story-Kapitel (0-5)
    private final Map<UUID, Integer> playerChapter = new ConcurrentHashMap<>();

    // Nyx-Erwachen Fortschritt (0-100%)
    private int nyxAwakeningProgress = 0;
    private boolean nyxEventActive   = false;

    // Story-Texte für jedes Kapitel
    private static final Map<Integer, String[]> CHAPTER_INTRO = new LinkedHashMap<>() {{
        put(1, new String[]{
            "<dark_purple>═══════════════════════════════════════",
            "<light_purple>  ✧ Kapitel I: Die Stimme aus der Leere ✧",
            "<dark_purple>═══════════════════════════════════════",
            "<gray>",
            "<white>Eine uralte Stimme flüstert aus den Tiefen unter deiner Insel...",
            "<gray>",
            "<italic><light_purple>„Ich bin Nyx. Die Leere ist mein Traum,",
            "<italic><light_purple>und eure Inseln sind meine Gedanken.\"",
            "<gray>",
            "<yellow>Suche den <white>Alten Seher <yellow>auf der Spawn-Insel.",
            "<dark_purple>═══════════════════════════════════════"
        });
        put(2, new String[]{
            "<dark_purple>═══════════════════════════════════════",
            "<light_purple>  ✧ Kapitel II: Das erste Ritual ✧",
            "<dark_purple>═══════════════════════════════════════",
            "<gray>",
            "<white>Der Alte Seher hat dir das Wissen der Rituale übergeben.",
            "<italic><light_purple>„Die Rituale sind Nyxs Sprache.",
            "<italic><light_purple>Führe sie durch – sie wird zuhören.\"",
            "<gray>",
            "<yellow>Führe dein erstes Ritual auf deiner Insel durch.",
            "<dark_purple>═══════════════════════════════════════"
        });
        put(3, new String[]{
            "<dark_purple>═══════════════════════════════════════",
            "<light_purple>  ✧ Kapitel III: Der Drachenpakt ✧",
            "<dark_purple>═══════════════════════════════════════",
            "<gray>",
            "<white>Die Leere zieht sich zusammen. Nyx träumt lebhafter.",
            "<italic><light_purple>„Ein Drache wacht über meinen tiefsten Traum.",
            "<italic><light_purple>Besiege ihn – und du wirst die Wahrheit kennen.\"",
            "<gray>",
            "<yellow>Aktiviere das <white>Drachenpakt-Ritual <yellow>auf deiner Insel.",
            "<dark_purple>═══════════════════════════════════════"
        });
        put(4, new String[]{
            "<dark_purple>═══════════════════════════════════════",
            "<light_purple>  ✧ Kapitel IV: Nyx' Botschaft ✧",
            "<dark_purple>═══════════════════════════════════════",
            "<gray>",
            "<white>Sternschnuppen fallen auf deine Insel – Nyxs Botschaften.",
            "<italic><light_purple>„Du hast die erste Prestige-Grenze überschritten.",
            "<italic><light_purple>Ich sehe dich nun. Bald erwache ich vollständig.\"",
            "<gray>",
            "<yellow>Sammle <white>10 Sternschnuppen <yellow>um die letzte Phase einzuleiten.",
            "<dark_purple>═══════════════════════════════════════"
        });
        put(5, new String[]{
            "<dark_purple>═════════════════════════════════════════════",
            "<light_purple>  ✧ Kapitel V: NYX ERWACHT ✧",
            "<dark_purple>═════════════════════════════════════════════",
            "<gray>",
            "<gold><bold>DIE LEERE ÖFFNET SICH.",
            "<white>Nyx, die Göttin der Leere, erwacht aus ihrem Schlaf.",
            "<white>Alle Spieler: Sammelt euch auf der Arena-Insel!",
            "<gray>",
            "<red><bold>SERVER-WEITER BOSS-KAMPF GESTARTET!",
            "<dark_purple>═════════════════════════════════════════════"
        });
    }};

    public StoryManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        createTable();
        loadChapters();
    }

    private void createTable() {
        try (Connection c = plugin.getDatabase().getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_story_progress (
                    player_uuid   VARCHAR(36) PRIMARY KEY,
                    chapter       INT         DEFAULT 0,
                    nyx_fragments INT         DEFAULT 0,
                    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_nyx_event (
                    id            INT         PRIMARY KEY DEFAULT 1,
                    progress      INT         DEFAULT 0,
                    active        TINYINT     DEFAULT 0,
                    completed_at  TIMESTAMP
                )
            """);
            // Sicherstellen dass der Nyx-Eintrag existiert
            s.executeUpdate("INSERT IGNORE INTO sky_nyx_event (id) VALUES(1)");
        } catch (SQLException e) {
            plugin.getLogger().warning("Story-Tabellen Fehler: " + e.getMessage());
        }
    }

    private void loadChapters() {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT player_uuid, chapter FROM sky_story_progress")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    playerChapter.put(UUID.fromString(rs.getString("player_uuid")), rs.getInt("chapter"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Story laden fehlgeschlagen: " + e.getMessage());
        }

        // Nyx-Fortschritt laden
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT progress, active FROM sky_nyx_event WHERE id=1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nyxAwakeningProgress = rs.getInt("progress");
                    nyxEventActive       = rs.getBoolean("active");
                }
            }
        } catch (SQLException ignored) {}
    }

    // ── Story-Kapitel aufrufen ────────────────────────────────────────────────

    /**
     * Prüft ob ein Spieler ein neues Kapitel freischalten kann.
     * Wird bei Login und wichtigen Ereignissen aufgerufen.
     */
    public void checkAndAdvanceChapter(Player player, int islandLevel, int prestige) {
        int current = playerChapter.getOrDefault(player.getUniqueId(), 0);

        if (current == 0) {
            // Kapitel 1 beim ersten Join
            unlockChapter(player, 1);
        } else if (current == 1 && islandLevel >= CHAPTER_2_ISLAND_LEVEL) {
            unlockChapter(player, 2);
        } else if (current == 2 && islandLevel >= CHAPTER_3_ISLAND_LEVEL) {
            unlockChapter(player, 3);
        } else if (current == 3 && prestige >= CHAPTER_4_PRESTIGE) {
            unlockChapter(player, 4);
        }
    }

    public void unlockChapter(Player player, int chapter) {
        int current = playerChapter.getOrDefault(player.getUniqueId(), 0);
        if (chapter <= current) return;

        playerChapter.put(player.getUniqueId(), chapter);
        saveChapter(player.getUniqueId(), chapter);

        // Kapitel-Intro mit Verzögerung und Typewriter-Effekt anzeigen
        String[] lines = CHAPTER_INTRO.getOrDefault(chapter, new String[0]);
        showChapterIntro(player, lines);
    }

    private void showChapterIntro(Player player, String[] lines) {
        // Fadeout Effekt
        player.sendTitle(" ", " ", 10, 60, 10);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.5f, 0.3f);

        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (!player.isOnline() || i >= lines.length) {
                    cancel();
                    return;
                }
                player.sendMessage(MM.deserialize(lines[i]));
                i++;
            }
        }.runTaskTimer(plugin, 20L, 15L); // eine Zeile alle 15 Ticks
    }

    // ── Nyx-Erwachen ──────────────────────────────────────────────────────────

    /**
     * Erhöht den Server-weiten Nyx-Fortschritt (Community-Ritual-Abschlüsse).
     */
    public void addNyxProgress(int amount) {
        nyxAwakeningProgress = Math.min(100, nyxAwakeningProgress + amount);
        saveNyxProgress();

        if (nyxAwakeningProgress >= 100 && !nyxEventActive) {
            triggerNyxAwakening();
        }

        // Server-Ankündigung bei Meilensteinen
        if (nyxAwakeningProgress == 25 || nyxAwakeningProgress == 50 || nyxAwakeningProgress == 75) {
            Bukkit.broadcast(MM.deserialize(
                "<dark_purple>✧ <light_purple>NYX-ERWACHEN: <white>" + nyxAwakeningProgress
                + "% <light_purple>– Die Leere pulsiert stärker..."));
        }
    }

    private void triggerNyxAwakening() {
        nyxEventActive = true;

        // Server-weite Ankündigung
        String[] lines = CHAPTER_INTRO.getOrDefault(5, new String[0]);
        for (String line : lines) {
            Bukkit.broadcast(MM.deserialize(line));
        }

        // Alle Spieler die Kapitel 5 freischalten
        for (Player online : Bukkit.getOnlinePlayers()) {
            unlockChapter(online, 5);
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
        }

        // Nyx-Boss spawnen (Platzhalter – muss auf Arena-Insel gesetzt werden)
        plugin.getLogger().info("[STORY] NYX ERWACHT – Starte Boss-Event!");

        // Nach 2 Stunden endet das Event und resettet sich für nächsten Zyklus
        new BukkitRunnable() {
            @Override public void run() {
                nyxEventActive       = false;
                nyxAwakeningProgress = 0;
                saveNyxProgress();
                Bukkit.broadcast(MM.deserialize(
                    "<dark_purple>✧ <light_purple>Nyx kehrt in ihren Schlaf zurück. "
                    + "<gray>Ein neuer Zyklus beginnt in 7 Tagen."));
            }
        }.runTaskLater(plugin, 144_000L); // 2h
    }

    // ── Status ───────────────────────────────────────────────────────────────

    public boolean isNyxActive() { return nyxEventActive; }

    public void showStoryStatus(Player player) {
        new de.pinkhorizon.skyblock.gui.StoryGui(plugin, player).open(player);
    }

    @Deprecated
    public void showStoryStatusChat(Player player) {
        int chapter = playerChapter.getOrDefault(player.getUniqueId(), 0);
        player.sendMessage(MM.deserialize("<dark_purple>══ Story: Void-Göttin Nyx ══"));
        player.sendMessage(MM.deserialize("<gray>Aktuelles Kapitel: <white>" + chapter + "/5"));
        player.sendMessage(MM.deserialize("<gray>Nyx-Erwachen: <light_purple>" + nyxAwakeningProgress + "%"));

        if (chapter < 5 && nyxAwakeningProgress < 100) {
            String hint = switch (chapter) {
                case 0, 1 -> "Baue deine Insel aus (Island Level " + CHAPTER_2_ISLAND_LEVEL + "+)";
                case 2 -> "Führe Rituale durch und erreiche Island Level " + CHAPTER_3_ISLAND_LEVEL;
                case 3 -> "Erreiche Prestige 1 und aktiviere das Drachenpakt-Ritual";
                case 4 -> "Sammle Sternschnuppen und helfe dem Nyx-Erwachen (Community)";
                default -> "Alle Kapitel abgeschlossen!";
            };
            player.sendMessage(MM.deserialize("<yellow>Nächster Schritt: <white>" + hint));
        }
    }

    public int getChapter(UUID uuid) {
        return playerChapter.getOrDefault(uuid, 0);
    }

    public int getNyxProgress() {
        return nyxAwakeningProgress;
    }

    // ── Persistenz ───────────────────────────────────────────────────────────

    private void saveChapter(UUID uuid, int chapter) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sky_story_progress (player_uuid, chapter) VALUES(?,?) "
                   + "ON DUPLICATE KEY UPDATE chapter=?")) {
                ps.setString(1, uuid.toString());
                ps.setInt   (2, chapter);
                ps.setInt   (3, chapter);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Story speichern fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    private void saveNyxProgress() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sky_nyx_event SET progress=?, active=? WHERE id=1")) {
                ps.setInt(1, nyxAwakeningProgress);
                ps.setInt(2, nyxEventActive ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }
}
