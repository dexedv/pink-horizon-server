package de.pinkhorizon.survival.commands;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SchematicCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public SchematicCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }
        if (!player.hasPermission("survival.admin")) {
            player.sendMessage(Component.text("§cKein Zugriff!"));
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "list" -> listSchematics(player);
            case "load" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /schem load <Name>")); return true; }
                loadSchematic(player, args[1]);
            }
            case "paste" -> {
                boolean noAir = args.length > 1 && args[1].equalsIgnoreCase("noair");
                pasteSchematic(player, noAir);
            }
            case "save" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /schem save <Name>")); return true; }
                saveSchematic(player, args[1]);
            }
            default -> sendHelp(player);
        }
        return true;
    }

    // ── list ─────────────────────────────────────────────────────────────

    private void listSchematics(Player player) {
        File dir = getSchematicsDir();
        if (!dir.exists()) { player.sendMessage(Component.text("§7Keine Schematics gefunden.")); return; }
        File[] files = dir.listFiles((f) -> f.isFile() && (f.getName().endsWith(".schem") || f.getName().endsWith(".schematic")));
        if (files == null || files.length == 0) {
            player.sendMessage(Component.text("§7Keine Schematics gefunden. Lege Dateien in §e" + dir.getPath() + " §7ab."));
            return;
        }
        player.sendMessage(Component.text("§6§l── Schematics (" + files.length + ") ──"));
        for (File f : files) {
            player.sendMessage(Component.text("§e  " + stripExtension(f.getName())));
        }
    }

    // ── load ─────────────────────────────────────────────────────────────

    private void loadSchematic(Player player, String name) {
        File file = findFile(name);
        if (file == null) {
            player.sendMessage(Component.text("§cSchematic §f" + name + " §cnicht gefunden! §7(/schem list)"));
            return;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            player.sendMessage(Component.text("§cUnbekanntes Schematic-Format!"));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                Clipboard clipboard = reader.read();
                var wePlayer = BukkitAdapter.adapt(player);
                var session  = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                session.setClipboard(new ClipboardHolder(clipboard));
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§aSchematic §f" + name + " §ageladen! §7Nutze §e/schem paste §7zum Einfügen.")));
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§cFehler beim Laden: " + e.getMessage())));
                plugin.getLogger().warning("Schematic-Ladefehler: " + e.getMessage());
            }
        });
    }

    // ── paste ────────────────────────────────────────────────────────────

    private void pasteSchematic(Player player, boolean noAir) {
        var wePlayer = BukkitAdapter.adapt(player);
        var session  = WorldEdit.getInstance().getSessionManager().get(wePlayer);
        if (session.getClipboard() == null) {
            player.sendMessage(Component.text("§cKein Schematic geladen! §7Nutze §e/schem load <Name>§7."));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(player.getWorld()))) {
                ClipboardHolder holder = session.getClipboard();
                BlockVector3 to = BlockVector3.at(
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ()
                );
                Operation operation = holder.createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(noAir)
                    .build();
                Operations.complete(operation);
                int changed = editSession.getBlockChangeCount();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§aSchematic eingefügt! §7(" + changed + " Blöcke" + (noAir ? ", Luft ignoriert" : "") + ")")));
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§cFehler beim Einfügen: " + e.getMessage())));
                plugin.getLogger().warning("Schematic-Paste-Fehler: " + e.getMessage());
            }
        });
    }

    // ── save ─────────────────────────────────────────────────────────────

    private void saveSchematic(Player player, String name) {
        var wePlayer = BukkitAdapter.adapt(player);
        var session  = WorldEdit.getInstance().getSessionManager().get(wePlayer);
        if (session.getClipboard() == null) {
            player.sendMessage(Component.text("§cKein Clipboard vorhanden! Nutze §e//copy §cin WorldEdit."));
            return;
        }
        File dir = getSchematicsDir();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, sanitize(name) + ".schem");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (ClipboardWriter writer = ClipboardFormats.findByAlias("schem").getWriter(new FileOutputStream(file))) {
                writer.write(session.getClipboard().getClipboard());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§aSchematic gespeichert als §f" + file.getName() + "§a.")));
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§cFehler beim Speichern: " + e.getMessage())));
                plugin.getLogger().warning("Schematic-Speicherfehler: " + e.getMessage());
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private File getSchematicsDir() {
        // Use FAWE/WorldEdit schematics folder
        File weDir = new File(plugin.getServer().getPluginsFolder(), "FastAsyncWorldEdit" + File.separator + "schematics");
        if (!weDir.exists()) weDir = new File(plugin.getServer().getPluginsFolder(), "WorldEdit" + File.separator + "schematics");
        return weDir;
    }

    private File findFile(String name) {
        File dir = getSchematicsDir();
        for (String ext : new String[]{".schem", ".schematic"}) {
            File f = new File(dir, name + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    private String stripExtension(String name) {
        if (name.endsWith(".schem")) return name.substring(0, name.length() - 6);
        if (name.endsWith(".schematic")) return name.substring(0, name.length() - 10);
        return name;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6§l── Schematics ──"));
        player.sendMessage(Component.text("§e/schem list §7- Schematics auflisten"));
        player.sendMessage(Component.text("§e/schem load <Name> §7- Schematic ins Clipboard laden"));
        player.sendMessage(Component.text("§e/schem paste [noair] §7- Clipboard einfügen"));
        player.sendMessage(Component.text("§e/schem save <Name> §7- Clipboard speichern"));
        player.sendMessage(Component.text("§8Tipp: §7Schematics in §eFastAsyncWorldEdit/schematics/§7 ablegen"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("list", "load", "paste", "save");
        if (args.length == 2 && args[0].equalsIgnoreCase("load")) {
            File dir = getSchematicsDir();
            if (!dir.exists()) return List.of();
            File[] files = dir.listFiles(f -> f.isFile() && (f.getName().endsWith(".schem") || f.getName().endsWith(".schematic")));
            if (files == null) return List.of();
            return Arrays.stream(files).map(f -> stripExtension(f.getName())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("paste")) return List.of("noair");
        return List.of();
    }
}
