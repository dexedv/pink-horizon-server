package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class HelpHoloCommand implements CommandExecutor, TabCompleter {

    private static final List<String> HELP_LINES = List.of(
        "<bold><gradient:#FF69B4:#9B59B6>✦ Pink Horizon – Survival ✦</gradient></bold>",
        " ",
        "<bold><gold>⚙ Allgemein</gold></bold>",
        "<gray>/spawn        <white>→ Zum Spawn teleportieren",
        "<gray>/rtp          <white>→ Zufällig in die Welt teleportieren",
        "<gray>/kit starter  <white>→ Starter-Kit erhalten (24h Cooldown)",
        "<gray>/stats        <white>→ Deine Statistiken anzeigen",
        " ",
        "<bold><gold>🏠 Homes & Teleport</gold></bold>",
        "<gray>/sethome [Name]  <white>→ Home setzen",
        "<gray>/home [Name]     <white>→ Zu Home teleportieren",
        "<gray>/homes           <white>→ Alle Homes anzeigen",
        "<gray>/delhome [Name]  <white>→ Home löschen",
        "<gray>/tpa <Spieler>   <white>→ Teleportanfrage senden",
        "<gray>/tpaccept        <white>→ Anfrage annehmen",
        " ",
        "<bold><gold>💰 Wirtschaft</gold></bold>",
        "<gray>/balance       <white>→ Kontostand anzeigen",
        "<gray>/pay <Sp.> <B> <white>→ Coins überweisen",
        "<gray>/baltop        <white>→ Reichste Spieler",
        "<gray>/jobs          <white>→ Job wählen & Geld verdienen",
        "<gray>/shop          <white>→ Upgrades kaufen (Fly, KI, Claims)",
        " ",
        "<bold><gold>🏗 Claims</gold></bold>",
        "<gray>/claim         <white>→ Chunk schützen",
        "<gray>/unclaim       <white>→ Schutz entfernen",
        "<gray>/claimlist     <white>→ Eigene Claims anzeigen",
        "<gray>/trust <Sp.>   <white>→ Spieler im Claim vertrauen",
        " ",
        "<bold><gold>🗺 Warps</gold></bold>",
        "<gray>/warps         <white>→ Alle Warps anzeigen",
        "<gray>/warp <Name>   <white>→ Zu Warp teleportieren",
        " ",
        "<bold><gold>💬 Sonstiges</gold></bold>",
        "<gray>/report <Sp.> <Grund>  <white>→ Spieler melden",
        "<gray>/hub                   <white>→ Zur Lobby"
    );

    private final PHSurvival plugin;

    public HelpHoloCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage("§cKeine Berechtigung!");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "place";

        switch (sub) {
            case "place" -> {
                plugin.getHologramManager().create("help", player.getLocation(), HELP_LINES, 0.9f);
                player.sendMessage("§aHilfe-Hologram gesetzt!");
            }
            case "remove" -> {
                boolean removed = plugin.getHologramManager().remove("help");
                player.sendMessage(removed ? "§aHilfe-Hologram entfernt!" : "§cKein Hilfe-Hologram gefunden.");
            }
            default -> player.sendMessage("§cVerwendung: /helpholo [place|remove]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("place", "remove");
        return List.of();
    }
}
