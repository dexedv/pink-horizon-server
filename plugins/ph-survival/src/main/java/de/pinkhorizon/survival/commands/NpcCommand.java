package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NpcCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public NpcCommand(PHSurvival plugin) {
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

        var nm = plugin.getNpcManager();

        switch (args[0].toLowerCase()) {

            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("§cNutzung: /npc create <Name> [Profession]"));
                    return true;
                }
                // Name: Unterstriche als Leerzeichen, MiniMessage-Tags erlaubt
                String name = args[1].replace("_", " ");
                Villager.Profession prof = Villager.Profession.NONE;
                if (args.length >= 3) {
                    try { prof = Villager.Profession.valueOf(args[2].toUpperCase()); }
                    catch (Exception e) {
                        player.sendMessage(Component.text("§cUnbekannte Profession: §e" + args[2]));
                        return true;
                    }
                }
                int id = nm.createNpc(name, player.getLocation(), prof);
                player.sendMessage(Component.text("§aNPC §e#" + id + " §aerstellt!"));
                player.sendMessage(Component.text("§7Befehl hinzufügen: §e/npc addcmd " + id + " <Befehl>"));
            }

            case "delete" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /npc delete <ID>")); return true; }
                try {
                    int id = Integer.parseInt(args[1]);
                    if (nm.deleteNpc(id)) player.sendMessage(Component.text("§7NPC §e#" + id + " §7gelöscht."));
                    else player.sendMessage(Component.text("§cNPC §e#" + id + " §cnicht gefunden."));
                } catch (NumberFormatException e) { player.sendMessage(Component.text("§cUngültige ID!")); }
            }

            case "addcmd" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("§cNutzung: /npc addcmd <ID> <Befehl>"));
                    player.sendMessage(Component.text("§8Präfix §e[console] §8für Server-Befehle · §e{player} §8als Spieler-Platzhalter"));
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    String cmd = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    if (nm.addCommand(id, cmd)) player.sendMessage(Component.text("§aBefehl hinzugefügt: §e/" + cmd));
                    else player.sendMessage(Component.text("§cNPC §e#" + id + " §cnicht gefunden."));
                } catch (NumberFormatException e) { player.sendMessage(Component.text("§cUngültige ID!")); }
            }

            case "removecmd" -> {
                if (args.length < 3) { player.sendMessage(Component.text("§cNutzung: /npc removecmd <ID> <Index>")); return true; }
                try {
                    int id  = Integer.parseInt(args[1]);
                    int idx = Integer.parseInt(args[2]);
                    if (nm.removeCommand(id, idx)) player.sendMessage(Component.text("§7Befehl §e#" + idx + " §7entfernt."));
                    else player.sendMessage(Component.text("§cNPC nicht gefunden oder Index ungültig."));
                } catch (NumberFormatException e) { player.sendMessage(Component.text("§cUngültige Zahl!")); }
            }

            case "info" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /npc info <ID>")); return true; }
                try {
                    int id = Integer.parseInt(args[1]);
                    var info = nm.getNpcInfo(id);
                    if (info == null) { player.sendMessage(Component.text("§cNPC §e#" + id + " §cnicht gefunden.")); return true; }
                    player.sendMessage(Component.text("§6§l── NPC #" + id + " ──"));
                    player.sendMessage(Component.text("§7Name:       §f" + info.name()));
                    player.sendMessage(Component.text("§7Profession: §f" + info.profession()));
                    player.sendMessage(Component.text("§7Welt:       §f" + info.world()));
                    var cmds = nm.getCommands(id);
                    if (cmds.isEmpty()) {
                        player.sendMessage(Component.text("§7Befehle: §ckeine"));
                    } else {
                        player.sendMessage(Component.text("§7Befehle:"));
                        for (int i = 0; i < cmds.size(); i++)
                            player.sendMessage(Component.text("  §e#" + (i + 1) + " §7" + cmds.get(i)));
                    }
                } catch (NumberFormatException e) { player.sendMessage(Component.text("§cUngültige ID!")); }
            }

            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("§cNutzung: /npc rename <ID> <Name>"));
                    player.sendMessage(Component.text("§8MiniMessage-Tags erlaubt, z.B. §e<gold>Händler</gold>"));
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    if (nm.renameNpc(id, name)) player.sendMessage(Component.text("§aNPC §e#" + id + " §aumbenannt zu: §f" + name));
                    else player.sendMessage(Component.text("§cNPC §e#" + id + " §cnicht gefunden."));
                } catch (NumberFormatException e) { player.sendMessage(Component.text("§cUngültige ID!")); }
            }

            case "list" -> {
                var infos = nm.getAllInfo();
                if (infos.isEmpty()) { player.sendMessage(Component.text("§7Keine NPCs vorhanden.")); return true; }
                player.sendMessage(Component.text("§6§l── NPCs (" + infos.size() + ") ──"));
                for (var info : infos) {
                    int cmdCount = nm.getCommands(info.id()).size();
                    player.sendMessage(Component.text("§e#" + info.id() + " §f" + info.name()
                        + " §8(" + info.world() + ") §7" + cmdCount + " Befehle"));
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6§l── /npc Befehle ──"));
        player.sendMessage(Component.text("§e/npc create <Name> [Profession]  §7- NPC an deiner Position erstellen"));
        player.sendMessage(Component.text("§e/npc delete <ID>                 §7- NPC löschen"));
        player.sendMessage(Component.text("§e/npc addcmd <ID> <Befehl>        §7- Befehl bei Rechtsklick hinzufügen"));
        player.sendMessage(Component.text("§e/npc removecmd <ID> <Index>      §7- Befehl entfernen"));
        player.sendMessage(Component.text("§e/npc info <ID>                   §7- Details anzeigen"));
        player.sendMessage(Component.text("§e/npc rename <ID> <Name>          §7- NPC umbenennen (MiniMessage)"));
        player.sendMessage(Component.text("§e/npc list                        §7- Alle NPCs auflisten"));
        player.sendMessage(Component.text("§8Präfix §e[console] §8für Server-Befehle, §e{player} §8als Spieler-Name"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1)
            return List.of("create", "delete", "addcmd", "removecmd", "rename", "info", "list");
        if (args.length == 3 && args[0].equalsIgnoreCase("create"))
            return Arrays.stream(Villager.Profession.values()).map(p -> p.name()).collect(Collectors.toList());
        if (args.length == 2 && List.of("delete", "addcmd", "removecmd", "rename", "info").contains(args[0].toLowerCase()))
            return new java.util.ArrayList<>(plugin.getNpcManager().getAllIds());
        return List.of();
    }
}
