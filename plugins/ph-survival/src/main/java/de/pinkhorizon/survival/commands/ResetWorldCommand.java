package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ResetWorldCommand implements CommandExecutor {

    private final PHSurvival plugin;
    private final Set<UUID> pendingConfirm = new HashSet<>();

    public ResetWorldCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage(Component.text("§cKein Zugriff!"));
            return true;
        }

        UUID id = sender instanceof Player p ? p.getUniqueId() : UUID.nameUUIDFromBytes("console".getBytes());

        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            pendingConfirm.add(id);
            sender.sendMessage(Component.text("§c§l⚠ ACHTUNG: Die gesamte Survival-Welt wird unwiderruflich gelöscht!"));
            sender.sendMessage(Component.text("§7Overworld, Nether und End werden zurückgesetzt."));
            sender.sendMessage(Component.text("§7Spawn-Border-Punkte werden geleert."));
            sender.sendMessage(Component.text("§eZum Bestätigen: §c/resetworld confirm"));
            return true;
        }

        if (!pendingConfirm.remove(id)) {
            sender.sendMessage(Component.text("§cBitte zuerst §e/resetworld §cohne 'confirm' ausführen."));
            return true;
        }

        sender.sendMessage(Component.text("§6[ResetWorld] §eStarte Welt-Reset..."));

        // 1. Alle Spieler kicken
        Component kickMsg = Component.text("§6[PinkHorizon] §cDie Survival-Welt wird zurückgesetzt. Bitte in Kürze wieder verbinden!");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player sp && p.equals(sp)) continue;
            p.kick(kickMsg);
        }

        // 2. Welten entladen (Hauptthread)
        for (String name : new String[]{"world_the_end", "world_nether", "world"}) {
            World w = Bukkit.getWorld(name);
            if (w != null) {
                w.getPlayers().forEach(p -> p.kick(kickMsg));
                Bukkit.unloadWorld(w, false); // false = nicht speichern
                plugin.getLogger().info("[ResetWorld] Entladen: " + name);
            }
        }

        // 3. Verzeichnisse async löschen, dann Welt neu erstellen
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File container = plugin.getServer().getWorldContainer();
            for (String name : new String[]{"world", "world_nether", "world_the_end"}) {
                File dir = new File(container, name);
                if (dir.exists()) {
                    deleteDirectory(dir.toPath());
                    plugin.getLogger().info("[ResetWorld] Gelöscht: " + dir.getName());
                }
            }

            // Border-Punkte leeren
            plugin.getSpawnBorderManager().clearPoints();

            // 4. Neue Welt auf dem Hauptthread erstellen
            Bukkit.getScheduler().runTask(plugin, () -> {
                World newWorld = new WorldCreator("world").createWorld();
                if (newWorld != null) {
                    plugin.getLogger().info("[ResetWorld] Neue Welt erfolgreich erstellt.");
                    sender.sendMessage(Component.text("§a[ResetWorld] §aWelt erfolgreich zurückgesetzt!"));
                    sender.sendMessage(Component.text("§7Nächste Schritte:"));
                    sender.sendMessage(Component.text("§e1. §7Stell dich an den Spawn-Punkt → §e/setspawn"));
                    sender.sendMessage(Component.text("§e2. §7Schematic einfügen → §e/schem load <name> §7→ §e/schem paste"));
                    sender.sendMessage(Component.text("§e3. §7Border setzen → §e/spawnborder wand"));
                    if (sender instanceof Player sp && sp.isOnline()) {
                        sp.teleport(newWorld.getSpawnLocation());
                    }
                } else {
                    plugin.getLogger().severe("[ResetWorld] Welt konnte nicht erstellt werden!");
                    sender.sendMessage(Component.text("§c[ResetWorld] Fehler beim Erstellen der neuen Welt!"));
                }
            });
        });

        return true;
    }

    private void deleteDirectory(Path path) {
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { plugin.getLogger().warning("[ResetWorld] Konnte nicht löschen: " + p); }
                });
        } catch (IOException e) {
            plugin.getLogger().severe("[ResetWorld] Fehler beim Löschen von " + path + ": " + e.getMessage());
        }
    }
}
