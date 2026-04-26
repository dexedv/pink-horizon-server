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
                chat(player,
                        "<gold>━━━━━━━━━ IdleForge – Tutorial ━━━━━━━━━\n"
                      + "<gray>Hallo <white>" + player.getName() + "<gray>!\n"
                      + "<gray>Generatoren verdienen automatisch <green>Geld<gray>, auch offline.\n"
                      + "<gray>Du erhältst jetzt deinen ersten <white>Cobblestone-Generator<gray>.\n"
                      + "<yellow>➤ <white>Schritt 1/5: <yellow>Platziere den Generator auf deiner Insel!\n"
                      + "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                // Starter-Generator geben
                player.getInventory().addItem(
                        plugin.getGeneratorManager().createGeneratorItem(GeneratorType.COBBLESTONE, 1));
            }

            case 2 -> {
                title(player, "<green><bold>Generator platziert!</bold>",
                        "<gray>Er verdient jetzt Geld für dich…");
                sound(player, Sound.ENTITY_PLAYER_LEVELUP);
                chat(player,
                        "<green>✔ Gut gemacht!\n"
                      + "<gray>Das <white>Hologramm<gray> über dem Block zeigt dir Einkommen & Level.\n"
                      + "<gray>Schau kurz zu, wie das Geld wächst…\n"
                      + "<yellow>➤ <white>Schritt 2/5: <yellow>Gleich geht's weiter…");
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
                chat(player,
                        "<aqua>⬆ Jetzt upgraden!\n"
                      + "<gray>Es gibt <white>zwei Wege<gray>, das Upgrade-Menü zu öffnen:\n"
                      + "<yellow>  1. <white>/gen upgrade <gray>– öffnet alle deine Generatoren\n"
                      + "<yellow>  2. <aqua>Sneak + Rechtsklick <gray>auf einen platzierten Generator\n"
                      + "<gray>     → öffnet direkt das Menü <white>nur für diesen Block<gray>!\n"
                      + "<yellow>➤ <white>Schritt 3/5: <yellow>Öffne das Upgrade-Menü (eine der beiden Methoden)");
            }

            case 4 -> {
                title(player, "<aqua>Upgrade-Menü offen!", "<gray>Führe ein Upgrade durch!");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                chat(player,
                        "<gray>Klicke auf deinen <white>Cobblestone-Generator<gray> im Menü.\n"
                      + "<gray>Grüner Preis = du kannst es dir leisten!\n"
                      + "<dark_gray>Tipp: <gray>Rechtsklick mit einem Upgrade-Token = <green>kostenloses Upgrade<gray>!\n"
                      + "<yellow>➤ <white>Schritt 4/5: <yellow>Führe ein Upgrade durch!");
            }

            case 5 -> {
                title(player, "<green>Upgrade gemacht!", "<gray>Nächster Tipp in 6s…");
                sound(player, Sound.ENTITY_PLAYER_LEVELUP);
                chat(player,
                        "<green>✔ Perfekt!\n"
                      + "<dark_gray>─── Tipp: Sneak-Rechtsklick ───\n"
                      + "<gray>Halte <white>Shift gedrückt<gray> und <white>Rechtsklicke<gray> direkt auf\n"
                      + "<gray>einen platzierten Generator-Block.\n"
                      + "<gray>→ Das öffnet sofort das Upgrade-Menü <aqua>nur für diesen Block<gray>.\n"
                      + "<gray>Praktisch, wenn du viele Generatoren hast!\n"
                      + "<dark_gray>(Geht weiter in 6 Sekunden…)");
                // 6s → Schritt 6
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 5) advanceTo(player, 6); },
                        120L);
                tasks.put(player.getUniqueId(), t);
            }

            case 6 -> {
                title(player, "<gold>Tier-Upgrades!", "<gray>Von Cobblestone bis Netherite");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                chat(player,
                        "<gold>⚙ Tier-Upgrades – so funktioniert's:\n"
                      + "<dark_gray>─────────────────────────────────\n"
                      + "<gray>Jeder Generator hat ein <white>Max-Level<gray> (abhängig von deinem Prestige).\n"
                      + "<gray>Wenn du <white>Max-Level<gray> erreichst, erscheint ein <aqua>Tier-Upgrade-Button<gray>.\n"
                      + "<gray>Damit steigst du vom aktuellen Material auf das nächste hoch:\n"
                      + "<white>  Cobblestone → Iron → Gold → Lapis → Diamond → Netherite\n"
                      + "<gray>Das Level wird auf <white>1 zurückgesetzt<gray>, aber der\n"
                      + "<gray>Basisertrag steigt <green>massiv<gray>!\n"
                      + "<dark_gray>─────────────────────────────────\n"
                      + "<dark_gray>(Geht weiter in 6 Sekunden…)");
                // 6s → Schritt 7
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 6) advanceTo(player, 7); },
                        120L);
                tasks.put(player.getUniqueId(), t);
            }

            case 7 -> {
                title(player, "<light_purple>Fusion!", "<gray>3 gleiche = 1 mächtiger");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                chat(player,
                        "<light_purple>✦ Fusions-System – das Geheimnis der Macht:\n"
                      + "<dark_gray>─────────────────────────────────\n"
                      + "<gray>Platziere <white>3 Generatoren desselben Typs <aqua>direkt nebeneinander<gray>.\n"
                      + "<gray>Dann <white>Sneak + Rechtsklick<gray> auf einen davon → im Menü\n"
                      + "<gray>erscheint ein <gold>Fusions-Button<gray>.\n"
                      + "<gray>Die Fusions-Kette:\n"
                      + "<white>  Normal → <yellow>Mega <gray>(×4) → <gold>Ultra <gray>(×16)\n"
                      + "<white>         → <red>God <gray>(×64) → <dark_red>Titan <gray>(×256 Ertrag!)\n"
                      + "<gray>Alternativ: <white>/gen fuse <gray>listet fusionierbare Generatoren auf.\n"
                      + "<gray>Alle 3 müssen auf <white>Max-Level<gray> sein!\n"
                      + "<dark_gray>─────────────────────────────────\n"
                      + "<dark_gray>(Geht weiter in 8 Sekunden…)");
                // 8s → Schritt 8 (Hologramme)
                BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> { if (getStep(player.getUniqueId()) == 7) advanceTo(player, 8); },
                        160L);
                tasks.put(player.getUniqueId(), t);
            }

            case 8 -> {
                title(player, "<aqua>Hologramme!", "<gray>Deine Insel, deine Infos");
                sound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                chat(player,
                        "<aqua>📊 Hologramme – dekoriere deine Insel:\n"
                      + "<dark_gray>─────────────────────────────────\n"
                      + "<gray>Du kannst <white>zwei Arten von Hologrammen<gray> auf deiner Insel setzen:\n"
                      + "\n"
                      + "<yellow>⚙ Stats-Hologramm\n"
                      + "<gray>Zeigt dein Geld, Prestige, Einkommen & mehr.\n"
                      + "<white>  /gen holo set     <gray>→ platziert es an deiner Position\n"
                      + "<white>  /gen holo remove  <gray>→ entfernt es\n"
                      + "\n"
                      + "<gold>🏆 Ranglisten-Hologramm\n"
                      + "<gray>Zeigt die <white>Top-10 Spieler<gray> des gesamten Servers – live!\n"
                      + "<white>  /gen holo lb        <gray>→ platziert es an deiner Position\n"
                      + "<white>  /gen holo lbremove  <gray>→ entfernt es\n"
                      + "<dark_gray>─────────────────────────────────\n"
                      + "<dark_gray>(Tutorial endet in 8 Sekunden…)");
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

                chat(player,
                        "<gold>━━━━━━━━━ Tutorial abgeschlossen! ━━━━━━━━━\n"
                      + "<green>Du hast <yellow>$500<green> Startbonus erhalten! 🎉\n"
                      + "<dark_gray>\n"
                      + "<yellow>/gen shop      <gray>- Neue Generatoren kaufen\n"
                      + "<yellow>/gen upgrade   <gray>- Alle Generatoren upgraden\n"
                      + "<yellow>/gen fuse       <gray>- 3× gleiche Gens = 1 Mega-Gen\n"
                      + "<yellow>/gen prestige   <gray>- Prestige für massive Boni\n"
                      + "<yellow>/gen holo set   <gray>- Persönliches Stats-Hologramm\n"
                      + "<yellow>/gen holo lb    <gray>- Ranglisten-Hologramm\n"
                      + "<yellow>/gen help       <gray>- Alle Befehle\n"
                      + "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

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

    private void chat(Player p, String msg) {
        p.sendMessage(MM.deserialize(msg));
    }

    private void sound(Player p, Sound s) {
        p.playSound(p.getLocation(), s, 1f, 1f);
    }
}
