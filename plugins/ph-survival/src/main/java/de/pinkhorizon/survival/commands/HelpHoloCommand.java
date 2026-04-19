package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class HelpHoloCommand implements CommandExecutor, TabCompleter {

    // Titel – zentriert über allen 3 Spalten
    private static final List<String> TITLE_LINES = List.of(
        "<bold><gradient:#FF69B4:#9B59B6>✦ Pink Horizon – Survival ✦</gradient></bold>"
    );

    // Spalte 1 (links): Allgemein + Claims
    private static final List<String> COL1_LINES = List.of(
        "<bold><gold>⚙ Allgemein</gold></bold>",
        "<gray>/spawn        <white>→ Zum Spawn tp.",
        "<gray>/rtp          <white>→ Zufällig teleportieren",
        "<gray>/kit starter  <white>→ Starter-Kit (24h)",
        "<gray>/stats        <white>→ Statistiken anzeigen",
        " ",
        "<bold><gold>🏗 Claims</gold></bold>",
        "<gray>/claim         <white>→ Chunk schützen",
        "<gray>/unclaim       <white>→ Schutz entfernen",
        "<gray>/claimlist     <white>→ Eigene Claims",
        "<gray>/trust <Sp.>   <white>→ Spieler vertrauen"
    );

    // Spalte 2 (mitte): Homes & Teleport + Warps
    private static final List<String> COL2_LINES = List.of(
        "<bold><gold>🏠 Homes & Teleport</gold></bold>",
        "<gray>/sethome [Name]  <white>→ Home setzen",
        "<gray>/home [Name]     <white>→ Home tp.",
        "<gray>/homes           <white>→ Alle Homes",
        "<gray>/delhome [Name]  <white>→ Home löschen",
        "<gray>/tpa <Spieler>   <white>→ TP-Anfrage senden",
        "<gray>/tpaccept        <white>→ Anfrage annehmen",
        " ",
        "<bold><gold>🗺 Warps</gold></bold>",
        "<gray>/warps         <white>→ Alle Warps",
        "<gray>/warp <Name>   <white>→ Zu Warp tp."
    );

    // Spalte 3 (rechts): Wirtschaft + Sonstiges
    private static final List<String> COL3_LINES = List.of(
        "<bold><gold>💰 Wirtschaft</gold></bold>",
        "<gray>/balance       <white>→ Kontostand",
        "<gray>/pay <Sp.> <B> <white>→ Coins überweisen",
        "<gray>/baltop        <white>→ Reichste Spieler",
        "<gray>/jobs          <white>→ Job & Geld verdienen",
        "<gray>/shop          <white>→ Upgrades kaufen",
        " ",
        "<bold><gold>💬 Sonstiges</gold></bold>",
        "<gray>/report <Sp.> <Gr.> <white>→ Spieler melden",
        "<gray>/hub               <white>→ Zur Lobby"
    );

    private static final double COL_OFFSET = 4.0; // Abstand zwischen Spalten (Blöcke)

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
                Location base = player.getLocation();
                var hm = plugin.getHologramManager();

                // Titel 1.5 Blöcke über der Basis
                hm.create("help-title", base.clone().add(0, 1.5, 0), TITLE_LINES, 1.1f);

                // 3 Spalten auf gleicher Höhe
                hm.create("help-col1", base.clone().add(-COL_OFFSET, 0, 0), COL1_LINES, 0.85f);
                hm.create("help-col2", base.clone(),                         COL2_LINES, 0.85f);
                hm.create("help-col3", base.clone().add( COL_OFFSET, 0, 0), COL3_LINES, 0.85f);

                player.sendMessage("§aHilfe-Hologram gesetzt!");
            }
            case "remove" -> {
                var hm = plugin.getHologramManager();
                boolean r1 = hm.remove("help-title");
                boolean r2 = hm.remove("help-col1");
                boolean r3 = hm.remove("help-col2");
                boolean r4 = hm.remove("help-col3");
                // Rückwärts-Kompatibilität: altes Single-Hologram
                hm.remove("help");
                boolean any = r1 || r2 || r3 || r4;
                player.sendMessage(any ? "§aHilfe-Hologram entfernt!" : "§cKein Hilfe-Hologram gefunden.");
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
