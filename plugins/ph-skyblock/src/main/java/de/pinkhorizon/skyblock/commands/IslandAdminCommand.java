package de.pinkhorizon.skyblock.commands;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class IslandAdminCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PRE = "<dark_gray>[<light_purple><bold>isadmin</bold></light_purple><dark_gray>] <white>";

    private final PHSkyBlock plugin;

    public IslandAdminCommand(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "tp"       -> cmdTp(sender, args);
            case "delete"   -> cmdDelete(sender, args);
            case "info"     -> cmdInfo(sender, args);
            case "setspawn" -> cmdSetSpawn(sender);
            case "reload"   -> cmdReload(sender);
            case "npc"      -> cmdNpc(sender, args);
            case "holo"     -> cmdHolo(sender, args);
            default         -> sendHelp(sender);
        }
        return true;
    }

    // ── NPC-Befehle ───────────────────────────────────────────────────────────

    /**
     * /isadmin npc set <id> [name...]  – NPC an deiner Position setzen
     * /isadmin npc del <id>            – NPC entfernen
     * /isadmin npc list               – Alle NPCs anzeigen
     * /isadmin npc reload             – NPCs neu laden
     */
    private void cmdNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur für Spieler."); return; }
        if (args.length < 2) { sendNpcHelp(player); return; }

        switch (args[1].toLowerCase()) {

            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin npc set <id> [name...]"));
                    return;
                }
                String id   = args[2];
                String name = args.length > 3
                    ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                    : "§eNPC";

                Location loc = player.getLocation();
                boolean ok = plugin.getNpcManager().addNpc(id, loc, name, "PLAINS");
                if (ok) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<green>NPC <yellow>'" + id + "'</yellow> gesetzt bei <white>"
                        + formatLoc(loc)));
                } else {
                    player.sendMessage(MM.deserialize(PRE + "<red>Fehler beim Spawnen des NPCs."));
                }
            }

            case "del", "delete", "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin npc del <id>"));
                    return;
                }
                String id = args[2];
                boolean ok = plugin.getNpcManager().removeNpc(id);
                player.sendMessage(MM.deserialize(PRE + (ok
                    ? "<green>NPC <yellow>'" + id + "'</yellow> entfernt."
                    : "<red>NPC <yellow>'" + id + "'</yellow> nicht gefunden.")));
            }

            case "list" -> {
                List<String> ids = plugin.getNpcManager().listNpcIds();
                if (ids.isEmpty()) {
                    player.sendMessage(MM.deserialize(PRE + "<gray>Keine NPCs gesetzt."));
                } else {
                    player.sendMessage(MM.deserialize(PRE
                        + "<gray>NPCs (" + ids.size() + "): <yellow>"
                        + String.join("<gray>, <yellow>", ids)));
                }
            }

            case "reload" -> {
                plugin.getNpcManager().reloadNpcs();
                player.sendMessage(MM.deserialize(PRE + "<green>NPCs neu geladen."));
            }

            default -> sendNpcHelp(player);
        }
    }

    // ── Hologramm-Befehle ─────────────────────────────────────────────────────

    /**
     * /isadmin holo add <id> <text...>          – Neues Hologramm erstellen
     * /isadmin holo del <id>                    – Hologramm löschen
     * /isadmin holo addline <id> <text...>      – Zeile hinzufügen
     * /isadmin holo delline <id> <zeilenNr>     – Zeile entfernen (1-basiert)
     * /isadmin holo list                        – Alle Hologramme anzeigen
     * /isadmin holo info <id>                   – Zeilen anzeigen
     * /isadmin holo reload                      – Alle neu spawnen
     */
    private void cmdHolo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur für Spieler."); return; }
        if (args.length < 2) { sendHoloHelp(player); return; }

        switch (args[1].toLowerCase()) {

            case "add" -> {
                if (args.length < 4) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin holo add <id> <text...>"));
                    return;
                }
                String id   = args[2];
                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                Location loc = player.getLocation();
                boolean ok = plugin.getInfoHologramManager().addHologram(id, loc, text);
                player.sendMessage(MM.deserialize(PRE + (ok
                    ? "<green>Hologramm <yellow>'" + id + "'</yellow> erstellt bei <white>"
                      + formatLoc(loc)
                    : "<red>Fehler beim Erstellen.")));
            }

            case "del", "delete", "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin holo del <id>"));
                    return;
                }
                boolean ok = plugin.getInfoHologramManager().removeHologram(args[2]);
                player.sendMessage(MM.deserialize(PRE + (ok
                    ? "<green>Hologramm <yellow>'" + args[2] + "'</yellow> gelöscht."
                    : "<red>Hologramm nicht gefunden.")));
            }

            case "addline" -> {
                if (args.length < 4) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin holo addline <id> <text...>"));
                    return;
                }
                String id   = args[2];
                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = plugin.getInfoHologramManager().addLine(id, text);
                player.sendMessage(MM.deserialize(PRE + (ok
                    ? "<green>Zeile zu <yellow>'" + id + "'</yellow> hinzugefügt."
                    : "<red>Hologramm <yellow>'" + id + "'</yellow> nicht gefunden.")));
            }

            case "delline" -> {
                if (args.length < 4) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin holo delline <id> <zeilenNr>"));
                    return;
                }
                String id = args[2];
                int lineNum;
                try { lineNum = Integer.parseInt(args[3]) - 1; }
                catch (NumberFormatException e) {
                    player.sendMessage(MM.deserialize(PRE + "<red>Ungültige Zeilennummer."));
                    return;
                }
                boolean ok = plugin.getInfoHologramManager().removeLine(id, lineNum);
                player.sendMessage(MM.deserialize(PRE + (ok
                    ? "<green>Zeile " + (lineNum + 1) + " aus <yellow>'" + id + "'</yellow> entfernt."
                    : "<red>Fehler – ID nicht gefunden oder Zeile außer Bereich.")));
            }

            case "list" -> {
                List<String> ids = plugin.getInfoHologramManager().listIds();
                if (ids.isEmpty()) {
                    player.sendMessage(MM.deserialize(PRE + "<gray>Keine Hologramme gesetzt."));
                } else {
                    player.sendMessage(MM.deserialize(PRE
                        + "<gray>Hologramme (" + ids.size() + "): <yellow>"
                        + String.join("<gray>, <yellow>", ids)));
                }
            }

            case "info" -> {
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize(PRE
                        + "<red>Syntax: <yellow>/isadmin holo info <id>"));
                    return;
                }
                String id = args[2];
                List<String> lines = plugin.getInfoHologramManager().getLines(id);
                if (lines.isEmpty()) {
                    player.sendMessage(MM.deserialize(PRE + "<red>Hologramm nicht gefunden."));
                    return;
                }
                player.sendMessage(MM.deserialize(PRE
                    + "<gray>Hologramm <yellow>'" + id + "'</yellow>:"));
                for (int i = 0; i < lines.size(); i++) {
                    player.sendMessage(MM.deserialize(
                        "  <gray>" + (i + 1) + ". <white>" + lines.get(i)));
                }
            }

            case "reload" -> {
                plugin.getInfoHologramManager().reloadAll();
                player.sendMessage(MM.deserialize(PRE + "<green>Hologramme neu geladen."));
            }

            default -> sendHoloHelp(player);
        }
    }

    // ── Bestehende Befehle ────────────────────────────────────────────────────

    private void cmdTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) { sender.sendMessage("Nur für Spieler."); return; }
        if (args.length < 2) { sendHelp(sender); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found", "name", args[1])); return; }
        Island island = plugin.getIslandManager().getIslandByOwner(target.getUniqueId());
        if (island == null) { sender.sendMessage(plugin.msg("island-no-island")); return; }
        admin.teleport(new Location(Bukkit.getWorld(island.getWorld()),
            island.getHomeX(), island.getHomeY(), island.getHomeZ()));
        admin.sendMessage(plugin.msg("island-home-tp"));
    }

    private void cmdDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { sendHelp(sender); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found", "name", args[1])); return; }
        Island island = plugin.getIslandManager().getIslandByOwner(target.getUniqueId());
        if (island == null) { sender.sendMessage(plugin.msg("island-no-island")); return; }
        plugin.getIslandManager().deleteIsland(island);
        sender.sendMessage(MM.deserialize(
            "<green>Insel von <light_purple>" + target.getName() + "</light_purple> gelöscht."));
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendHelp(sender); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found", "name", args[1])); return; }
        Island island = plugin.getIslandManager().getIslandOfPlayer(target.getUniqueId());
        if (island == null) { sender.sendMessage(plugin.msg("island-no-island")); return; }
        sender.sendMessage(MM.deserialize("<light_purple>━━━ Island Info ━━━"));
        sender.sendMessage(MM.deserialize("<gray>ID: <white>"       + island.getId()));
        sender.sendMessage(MM.deserialize("<gray>UUID: <white>"     + island.getIslandUuid()));
        sender.sendMessage(MM.deserialize("<gray>Owner: <white>"    + island.getOwnerName()));
        sender.sendMessage(MM.deserialize("<gray>Welt: <white>"     + island.getWorld()));
        sender.sendMessage(MM.deserialize("<gray>Zentrum: <white>"  + island.getCenterX()
            + ", " + island.getCenterY() + ", " + island.getCenterZ()));
        sender.sendMessage(MM.deserialize("<gray>Level/Score: <white>" + island.getLevel()
            + " / " + island.getScore()));
        sender.sendMessage(MM.deserialize("<gray>Mitglieder: <white>"
            + (island.getMembers().size() + 1) + "/" + island.getMaxMembers()));
    }

    private void cmdSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player admin)) { sender.sendMessage("Nur für Spieler."); return; }
        Location loc = admin.getLocation();
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", (double) loc.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
        admin.sendMessage(MM.deserialize(PRE + "<green>Spawn gesetzt!"));
    }

    private void cmdReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(MM.deserialize(PRE + "<green>Config neu geladen."));
    }

    // ── Hilfe ─────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender s) {
        s.sendMessage(MM.deserialize("<light_purple>━━━ /isadmin Befehle ━━━"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin tp <Spieler>        <gray>– TP zur Insel"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin delete <Spieler>    <gray>– Insel löschen"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin info <Spieler>      <gray>– Insel-Details"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin setspawn            <gray>– Spawn setzen"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin reload              <gray>– Config neu laden"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin npc ...             <gray>– NPC-Verwaltung"));
        s.sendMessage(MM.deserialize("<yellow>/isadmin holo ...            <gray>– Hologramm-Verwaltung"));
    }

    private void sendNpcHelp(Player p) {
        p.sendMessage(MM.deserialize("<light_purple>━━━ NPC-Befehle ━━━"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin npc set <id> [name] <gray>– NPC hier setzen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin npc del <id>        <gray>– NPC entfernen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin npc list            <gray>– Alle NPCs anzeigen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin npc reload          <gray>– NPCs neu laden"));
        p.sendMessage(MM.deserialize("<gray>NPC-IDs: <white>generator-shop, quest-master,"));
        p.sendMessage(MM.deserialize("<white>achievement-master, title-shop <gray>(oder eigene ID)"));
        p.sendMessage(MM.deserialize("<gray>Name mit §-Codes: z.B. <white>§6⚙ §eGenerator-Shop"));
    }

    private void sendHoloHelp(Player p) {
        p.sendMessage(MM.deserialize("<light_purple>━━━ Hologramm-Befehle ━━━"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo add <id> <text>     <gray>– Erstellen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo del <id>            <gray>– Löschen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo addline <id> <text> <gray>– Zeile hinzufügen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo delline <id> <nr>   <gray>– Zeile löschen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo info <id>           <gray>– Zeilen anzeigen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo list                <gray>– Alle anzeigen"));
        p.sendMessage(MM.deserialize("<yellow>/isadmin holo reload              <gray>– Neu laden"));
        p.sendMessage(MM.deserialize("<gray>Text mit §-Codes: <white>§6Hallo §aWelt"));
    }

    // ── Tab-Completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("tp","delete","info","setspawn","reload","npc","holo"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "npc"  -> filter(List.of("set","del","list","reload"), args[1]);
                case "holo" -> filter(List.of("add","del","addline","delline","info","list","reload"), args[1]);
                default     -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc")
                && (args[1].equalsIgnoreCase("del") || args[1].equalsIgnoreCase("delete"))) {
            return filter(plugin.getNpcManager().listNpcIds(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("holo")
                && !args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("list")
                && !args[1].equalsIgnoreCase("reload")) {
            return filter(plugin.getInfoHologramManager().listIds(), args[2]);
        }
        return List.of();
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    private String formatLoc(Location loc) {
        return String.format("%.1f / %.1f / %.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}
