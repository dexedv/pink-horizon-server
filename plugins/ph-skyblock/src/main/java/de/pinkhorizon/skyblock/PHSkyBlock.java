package de.pinkhorizon.skyblock;

import de.pinkhorizon.skyblock.commands.IslandAdminCommand;
import de.pinkhorizon.skyblock.commands.IslandCommand;
import de.pinkhorizon.skyblock.database.IslandRepository;
import de.pinkhorizon.skyblock.database.SkyDatabase;
import de.pinkhorizon.skyblock.listeners.IslandChatListener;
import de.pinkhorizon.skyblock.listeners.IslandProtectionListener;
import de.pinkhorizon.skyblock.listeners.PlayerListener;
import de.pinkhorizon.skyblock.managers.IslandManager;
import de.pinkhorizon.skyblock.managers.IslandScoreManager;
import de.pinkhorizon.skyblock.managers.PlayerManager;
import de.pinkhorizon.skyblock.managers.SkyScoreboardManager;
import de.pinkhorizon.skyblock.managers.WorldManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

public class PHSkyBlock extends JavaPlugin {

    private static PHSkyBlock instance;
    private SkyDatabase database;
    private IslandRepository islandRepository;
    private WorldManager worldManager;
    private PlayerManager playerManager;
    private IslandManager islandManager;
    private IslandScoreManager scoreManager;
    private SkyScoreboardManager scoreboardManager;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Präfix für alle Nachrichten ───────────────────────────────────────────
    private static final String PREFIX = "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] <white>";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Datenbank
        database         = new SkyDatabase(this);
        islandRepository = new IslandRepository(this, database);

        // Manager
        worldManager  = new WorldManager(this);
        playerManager = new PlayerManager(this, islandRepository);
        islandManager = new IslandManager(this, islandRepository, worldManager);
        scoreManager       = new IslandScoreManager(this);
        scoreboardManager  = new SkyScoreboardManager(this);

        // Kommandos
        getCommand("island").setExecutor(new IslandCommand(this));
        getCommand("isadmin").setExecutor(new IslandAdminCommand(this));

        // Listener
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new IslandProtectionListener(this), this);
        pm.registerEvents(new IslandChatListener(this), this);

        getLogger().info("PH-SkyBlock v2.0.0 gestartet!");
    }

    @Override
    public void onDisable() {
        // Alle Online-Spieler speichern
        getServer().getOnlinePlayers().forEach(p ->
            playerManager.saveAndUnload(p.getUniqueId()));
        if (database != null) database.close();
        getLogger().info("PH-SkyBlock gestoppt.");
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    /** Gibt einen farbigen MiniMessage-Component mit Präfix zurück. */
    public Component msg(String key, Object... args) {
        String raw = MESSAGES.getOrDefault(key, "<red>Unbekannte Nachricht: " + key);
        for (int i = 0; i < args.length - 1; i += 2) {
            raw = raw.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return MM.deserialize(PREFIX + raw);
    }

    public Component msgNoPrefix(String key, Object... args) {
        String raw = MESSAGES.getOrDefault(key, "<red>Unbekannte Nachricht: " + key);
        for (int i = 0; i < args.length - 1; i += 2) {
            raw = raw.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return MM.deserialize(raw);
    }

    // ── Statische Nachrichten (Deutsch) ───────────────────────────────────────

    private static final java.util.Map<String, String> MESSAGES = java.util.Map.ofEntries(
        java.util.Map.entry("no-permission",         "<red>Du hast keine Berechtigung dafür."),
        java.util.Map.entry("only-players",          "<red>Nur Spieler können diesen Befehl nutzen."),
        java.util.Map.entry("island-already-exists", "<red>Du hast bereits eine Insel! Nutze <yellow>/is home</yellow>."),
        java.util.Map.entry("island-created",        "<green>Deine Insel wurde erstellt! Viel Spaß beim Bauen!"),
        java.util.Map.entry("island-no-island",      "<red>Du hast noch keine Insel! Erstelle eine mit <yellow>/is create</yellow>."),
        java.util.Map.entry("island-home-tp",        "<green>Du wurdest zu deiner Insel teleportiert."),
        java.util.Map.entry("island-spawn-tp",       "<green>Du wurdest zum Spawn teleportiert."),
        java.util.Map.entry("island-setHome-done",   "<green>Dein Insel-Home wurde gesetzt."),
        java.util.Map.entry("island-reset-confirm",  "<red><bold>ACHTUNG!</bold> <white>Tippe <yellow>/is reset bestätigen</yellow> um deine Insel zurückzusetzen. <gray>(Alle Blöcke werden gelöscht!)"),
        java.util.Map.entry("island-reset-cooldown", "<red>Du musst noch <yellow>{time}s</yellow> warten bevor du deine Insel zurücksetzen kannst."),
        java.util.Map.entry("island-reset-start",    "<yellow>Deine Insel wird zurückgesetzt..."),
        java.util.Map.entry("island-reset-done",     "<green>Deine Insel wurde zurückgesetzt!"),
        java.util.Map.entry("island-info-header",    "<light_purple>━━━ <bold>Deine Insel</bold> ━━━"),
        java.util.Map.entry("island-info-owner",     "<gray>Besitzer: <white>{name}"),
        java.util.Map.entry("island-info-level",     "<gray>Level: <yellow>{level} <gray>| Score: <yellow>{score}"),
        java.util.Map.entry("island-info-size",      "<gray>Größe: <white>{size}×{size}"),
        java.util.Map.entry("island-info-members",   "<gray>Mitglieder: <white>{count}/{max}"),
        java.util.Map.entry("island-info-footer",    "<light_purple>━━━━━━━━━━━━━━━━━━━━━"),
        java.util.Map.entry("island-open-on",        "<green>Deine Insel ist jetzt <bold>öffentlich</bold>. Jeder kann besuchen."),
        java.util.Map.entry("island-open-off",       "<yellow>Deine Insel ist jetzt <bold>privat</bold>."),
        java.util.Map.entry("island-warp-on",        "<green>Dein Insel-Warp ist jetzt <bold>aktiv</bold>."),
        java.util.Map.entry("island-warp-off",       "<yellow>Dein Insel-Warp wurde <bold>deaktiviert</bold>."),
        java.util.Map.entry("island-warp-visiting",  "<green>Du teleportierst dich zum Warp von <light_purple>{name}</light_purple>."),
        java.util.Map.entry("island-warp-none",      "<red>Dieser Spieler hat keinen Warp aktiviert."),
        java.util.Map.entry("island-invite-sent",    "<green>Einladung an <light_purple>{name}</light_purple> gesendet. (<gray>60 Sekunden gültig<green>)"),
        java.util.Map.entry("island-invite-full",    "<red>Deine Insel hat bereits die maximale Anzahl an Mitgliedern."),
        java.util.Map.entry("island-invite-received","<light_purple>{inviter}</light_purple> <green>hat dich zu ihrer Insel eingeladen! Tippe <yellow>/is accept</yellow> um beizutreten."),
        java.util.Map.entry("island-invite-no-invite","<red>Du hast keine ausstehende Einladung."),
        java.util.Map.entry("island-join-success",   "<green>Du bist der Insel von <light_purple>{name}</light_purple> beigetreten!"),
        java.util.Map.entry("island-join-notify",    "<light_purple>{name}</light_purple> <green>ist deiner Insel beigetreten!"),
        java.util.Map.entry("island-kick-done",      "<green>Spieler <light_purple>{name}</light_purple> wurde von der Insel entfernt."),
        java.util.Map.entry("island-kick-notify",    "<red>Du wurdest von der Insel von <light_purple>{owner}</light_purple> entfernt."),
        java.util.Map.entry("island-ban-done",       "<green>Spieler <light_purple>{name}</light_purple> wurde gebannt."),
        java.util.Map.entry("island-ban-notify",     "<red>Du wurdest von der Insel von <light_purple>{owner}</light_purple> gebannt."),
        java.util.Map.entry("island-unban-done",     "<green>Spieler <light_purple>{name}</light_purple> wurde entbannt."),
        java.util.Map.entry("island-banned",         "<red>Du bist von dieser Insel gebannt."),
        java.util.Map.entry("island-not-member",     "<red>Du bist kein Mitglied dieser Insel."),
        java.util.Map.entry("island-not-owner",      "<red>Nur der Besitzer kann das."),
        java.util.Map.entry("island-protected",      "<red>Diese Insel ist geschützt!"),
        java.util.Map.entry("island-outside-border", "<red>Du kannst nur auf deiner eigenen Insel bauen!"),
        java.util.Map.entry("island-chat-on",        "<green>Insel-Chat <bold>aktiviert</bold>. Nur Mitglieder sehen deine Nachrichten."),
        java.util.Map.entry("island-chat-off",       "<yellow>Insel-Chat <bold>deaktiviert</bold>."),
        java.util.Map.entry("island-chat-format",    "<dark_aqua>[Insel] <aqua>{name}<white>: {msg}"),
        java.util.Map.entry("island-top-header",     "<light_purple>━━━ <bold>Top 10 Inseln</bold> ━━━"),
        java.util.Map.entry("island-top-entry",      "<gray>#{rank} <light_purple>{owner} <gray>– Level <yellow>{level} <gray>(<white>{score} Punkte<gray>)"),
        java.util.Map.entry("island-top-footer",     "<light_purple>━━━━━━━━━━━━━━━━━━━━━"),
        java.util.Map.entry("island-top-empty",      "<gray>Noch keine Inseln vorhanden."),
        java.util.Map.entry("island-score-calc",     "<green>Insel-Score wird berechnet..."),
        java.util.Map.entry("island-score-result",   "<green>Score: <yellow>{score} <gray>| Level: <yellow>{level}"),
        java.util.Map.entry("island-upgrade-done",   "<green>Upgrade <light_purple>{upgrade}</light_purple> auf Level <yellow>{level}</yellow> erworben!"),
        java.util.Map.entry("island-upgrade-no-score","<red>Nicht genug Score! Benötigt: <yellow>{need}</yellow>, vorhanden: <yellow>{have}</yellow>."),
        java.util.Map.entry("island-upgrade-maxed",  "<yellow>Dieses Upgrade ist bereits auf dem maximalen Level!"),
        java.util.Map.entry("player-not-found",      "<red>Spieler <yellow>{name}</yellow> nicht gefunden."),
        java.util.Map.entry("help-header",           "<light_purple>━━━ <bold>SkyBlock Hilfe</bold> ━━━"),
        java.util.Map.entry("help-footer",           "<light_purple>━━━━━━━━━━━━━━━━━━━━━")
    );

    // ── Getters ───────────────────────────────────────────────────────────────

    public static PHSkyBlock getInstance()         { return instance; }
    public IslandManager getIslandManager()        { return islandManager; }
    public PlayerManager getPlayerManager()        { return playerManager; }
    public WorldManager getWorldManager()          { return worldManager; }
    public IslandScoreManager getScoreManager()    { return scoreManager; }
    public IslandRepository getIslandRepository()  { return islandRepository; }
    public SkyScoreboardManager getScoreboardManager() { return scoreboardManager; }
}
