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

    private static final List<String> LINES = List.of(
        "<bold><gradient:#FF69B4:#9B59B6>✦ Pink Horizon – Survival ✦</gradient></bold>",
        " ",
        "<bold><gold>⚙ Allgemein</gold></bold>",
        "<gray>/spawn          <white>→ Zum Spawn",
        "<gray>/rtp            <white>→ Zufällig teleportieren",
        "<gray>/back           <white>→ Letzten Ort zurück",
        "<gray>/kit starter    <white>→ Starter-Kit (24h)",
        "<gray>/stats          <white>→ Statistiken anzeigen",
        "<gray>/tutorial       <white>→ Tutorial öffnen",
        " ",
        "<bold><gold>🏗 Claims</gold></bold>",
        "<gray>/claim          <white>→ Chunk schützen (100 💰)",
        "<gray>/unclaim        <white>→ Schutz entfernen",
        "<gray>/claimlist      <white>→ Eigene Claims",
        "<gray>/claimmap       <white>→ Claim-Karte anzeigen",
        "<gray>/trust <Sp.>    <white>→ Spieler vertrauen",
        "<gray>/untrust <Sp.>  <white>→ Vertrauen entziehen",
        " ",
        "<bold><gold>🏠 Homes & Teleport</gold></bold>",
        "<gray>/sethome [Name]  <white>→ Home setzen",
        "<gray>/home [Name]     <white>→ Home tp.",
        "<gray>/homes           <white>→ Alle Homes (max. 3)",
        "<gray>/delhome [Name]  <white>→ Home löschen",
        "<gray>/tpa <Spieler>   <white>→ TP-Anfrage senden",
        "<gray>/tpaccept        <white>→ Anfrage annehmen",
        "<gray>/tpdeny          <white>→ Anfrage ablehnen",
        " ",
        "<bold><gold>🗺 Warps</gold></bold>",
        "<gray>/warps           <white>→ Alle Warps",
        "<gray>/warp <Name>     <white>→ Zu Warp tp.",
        " ",
        "<bold><gold>💰 Wirtschaft</gold></bold>",
        "<gray>/balance        <white>→ Kontostand",
        "<gray>/pay <Sp.> <B>  <white>→ Coins überweisen",
        "<gray>/baltop         <white>→ Reichste Spieler",
        "<gray>/sell           <white>→ Items verkaufen",
        " ",
        "<bold><gold>💼 Jobs & Shop</gold></bold>",
        "<gray>/jobs           <white>→ Job & Coins verdienen",
        "<gray>/shop           <white>→ Upgrades kaufen",
        " ",
        "<bold><gold>🏦 Bank & Auktion</gold></bold>",
        "<gray>/bank           <white>→ Bankkonto (Zinsen!)",
        "<gray>/ah             <white>→ Auktionshaus",
        " ",
        "<bold><gold>💬 Soziales</gold></bold>",
        "<gray>/friend          <white>→ Freunde verwalten",
        "<gray>/mail            <white>→ Nachrichten",
        "<gray>/trade <Spieler> <white>→ Handel starten",
        "<gray>/report <Sp.>    <white>→ Spieler melden",
        " ",
        "<bold><gold>⚒ Block-Upgrades</gold></bold>",
        "<gray>Shift+Rechtsklick <white>→ Ofen/Trichter upgraden",
        " ",
        "<bold><gold>🏆 Fortschritt</gold></bold>",
        "<gray>/achievements   <white>→ Erfolge",
        "<gray>/quests         <white>→ Quests"
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
                var hm = plugin.getHologramManager();
                hm.remove("help");
                hm.remove("help-title");
                hm.remove("help-col1");
                hm.remove("help-col2");
                hm.remove("help-col3");
                hm.create("help", player.getLocation(), LINES, 0.85f);
                player.sendMessage("§aHilfe-Hologram gesetzt!");
            }
            case "remove" -> {
                var hm = plugin.getHologramManager();
                boolean any = hm.remove("help") | hm.remove("help-title")
                            | hm.remove("help-col1") | hm.remove("help-col2")
                            | hm.remove("help-col3");
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
