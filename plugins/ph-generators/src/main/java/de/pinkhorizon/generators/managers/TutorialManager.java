package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Führt neue Spieler Schritt für Schritt durch IdleForge.
 *
 * Schritte:
 *  1 → Willkommen + Generator-Item geben → wartet auf Platzierung
 *  2 → Generator platziert → 8s Pause → Schritt 3
 *  3 → Upgrade-Menü öffnen (/gen upgrade oder Sneak+Rechtsklick)
 *  4 → GUI offen → Spieler soll upgraden
 *  5 → Erstes Upgrade gemacht → Tipp: Sneak+Rechtsklick (6s auto)
 *  6 → Tipp: Tier-Upgrades erklärt (6s auto)
 *  7 → Tipp: Fusion erklärt (8s auto)
 *  8 → Abschluss + $500 Bonus
 * -1 → Tutorial abgeschlossen (persistent)
 */
public class TutorialManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Aktiver Schritt pro Spieler (0 = inaktiv, -1 = abgeschlossen) */
    private final Map<UUID, Integer> steps = new HashMap<>();
    /** Laufende Delayed-Tasks */
    private final Map<UUID, BukkitTask> tasks  = new HashMap<>();

    public TutorialManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /** Startet das Tutorial (auch zum Neustart nutzbar). */
    public void startTutorial(Player player) {
        cancelTask(player.getUniqueId());
        steps.put(player.getUniqueId(), 1);
        showStep(player, 1);
        // Spawn-Hologramm auf Schritt 1 zurücksetzen (30s Preview)
        plugin.getHologramManager().previewTutorial(player.getUniqueId());
    }

    public boolean isActive(UUID uuid) {
        Integer s = steps.get(uuid);
        return s != null && s > 0;
    }

    /** Wird beim Platzieren eines Generators aufgerufen. */
    public void onGeneratorPlaced(Player player) {
        if (getStep(player.getUniqueId()) == 1) advanceTo(player, 2);
    }

    /** Wird beim Öffnen des Upgrade-GUIs aufgerufen. */
    public void onUpgradeGuiOpened(Player player) {
        if (getStep(player.getUniqueId()) == 3) advanceTo(player, 4);
    }

    /** Wird nach einem erfolgreichen Upgrade aufgerufen. */
    public void onUpgradeDone(Player player) {
        if (getStep(player.getUniqueId()) == 4) advanceTo(player, 5);
    }

    /** Aufräumen beim Quit. */
    public void onQuit(UUID uuid) {
        cancelTask(uuid);
        steps.remove(uuid);
    }

    // ── Schritte ─────────────────────────────────────────────────────────────

    private void showStep(Player player, int step) {
        switch (step) {

            case 1 -> {
                title(player,
                        "<gold><bold>Willkommen bei IdleForge!</bold>",
                        "<yellow>Starte dein Generator-Imperium!");
                sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                // Starter-Generator geben
                player.getInventory().addItem(
                        plugin.getGeneratorManager().createGeneratorItem(GeneratorType.COBBLESTONE, 1));
            }

            case 2 -> {
                title(player, "<green><bold>Generator platziert!</bold>",
                        "<gray>Er verdient jetzt Geld für dich…");
                sound(player, Sound.ENTITY_PLAYER_LEVELUP);
                // 8 Sekunden warten, dann Schritt 3
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 2) advanceTo(player, 3); },
                        160L);
                tasks.put(player.getUniqueId(), t);
            }

            case 3 -> {
                title(player, "<aqua>Zeit zum Upgraden!",
                        "<gray>Öffne das Upgrade-Menü");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
            }

            case 4 -> {
                title(player, "<aqua>Upgrade-Menü offen!", "<gray>Führe ein Upgrade durch!");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
            }

            case 5 -> {
                title(player, "<green>Upgrade gemacht!", "<gray>Nächster Tipp in 6s…");
                sound(player, Sound.ENTITY_PLAYER_LEVELUP);
                // 6s → Schritt 6
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 5) advanceTo(player, 6); },
                        120L);
                tasks.put(player.getUniqueId(), t);
            }

            case 6 -> {
                title(player, "<gold>Tier-Upgrades!", "<gray>Von Cobblestone bis Netherite");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                // 6s → Schritt 7
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 6) advanceTo(player, 7); },
                        120L);
                tasks.put(player.getUniqueId(), t);
            }

            case 7 -> {
                title(player, "<light_purple>Fusion!", "<gray>3 gleiche = 1 mächtiger");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                // 8s → Schritt 8 (Hologramme)
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 7) advanceTo(player, 8); },
                        160L);
                tasks.put(player.getUniqueId(), t);
            }

            case 8 -> {
                title(player, "<aqua>Hologramme!", "<gray>Deine Insel, deine Infos");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                // 8s → Schritt 9 (Abschluss)
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 8) advanceTo(player, 9); },
                        160L);
                tasks.put(player.getUniqueId(), t);
            }

            case 9 -> {
                title(player, "<gold><bold>Tutorial abgeschlossen!</bold>",
                        "<green>+$500 Startbonus erhalten!");
                sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);

                // Belohnung
                PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
                if (data != null) {
                    data.addMoney(500L);
                    data.setTutorialDone(true);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin,
                            () -> plugin.getRepository().saveTutorialDone(player.getUniqueId()));
                }

                steps.put(player.getUniqueId(), -1);
            }
        }
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private void advanceTo(Player player, int step) {
        cancelTask(player.getUniqueId());
        steps.put(player.getUniqueId(), step);
        showStep(player, step);
    }

    private int getStep(UUID uuid) {
        return steps.getOrDefault(uuid, 0);
    }

    private void cancelTask(UUID uuid) {
        BukkitTask t = tasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private void title(Player p, String title, String sub) {
        p.showTitle(Title.title(
                MM.deserialize(title),
                MM.deserialize(sub),
                Title.Times.times(
                        Duration.ofMillis(400),
                        Duration.ofMillis(3500),
                        Duration.ofMillis(600))));
    }

    private void sound(Player p, Sound s) {
        p.playSound(p.getLocation(), s, 1f, 1f);
    }
}
