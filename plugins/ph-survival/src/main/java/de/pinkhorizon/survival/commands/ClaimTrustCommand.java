package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class ClaimTrustCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public ClaimTrustCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();

        switch (label.toLowerCase()) {
            case "trust" -> {
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /trust <Spieler>");
                    return true;
                }
                if (!plugin.getClaimManager().isClaimed(chunk)) {
                    player.sendMessage("\u00a7cDieser Chunk ist nicht geclaimed!");
                    return true;
                }
                if (!plugin.getClaimManager().isOwner(chunk, player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu bist nicht der Besitzer dieses Chunks!");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("\u00a7cSpieler nicht gefunden oder nicht online!");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("\u00a7cDu kannst dir selbst nicht vertrauen.");
                    return true;
                }
                plugin.getClaimManager().trustPlayer(chunk, target.getUniqueId());
                player.sendMessage("\u00a7a" + target.getName() + "\u00a7a wurde vertraut!");
                target.sendMessage("\u00a7a" + player.getName() + "\u00a7a vertraut dir in seinem Chunk!");
            }
            case "untrust" -> {
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /untrust <Spieler>");
                    return true;
                }
                if (!plugin.getClaimManager().isClaimed(chunk)) {
                    player.sendMessage("\u00a7cDieser Chunk ist nicht geclaimed!");
                    return true;
                }
                if (!plugin.getClaimManager().isOwner(chunk, player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu bist nicht der Besitzer dieses Chunks!");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                UUID targetUuid = null;
                if (target != null) {
                    targetUuid = target.getUniqueId();
                } else {
                    // try to find from trusted list by name
                    Set<UUID> trusted = plugin.getClaimManager().getTrusted(chunk);
                    for (UUID uuid : trusted) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.getName().equalsIgnoreCase(args[0])) {
                            targetUuid = uuid;
                            break;
                        }
                    }
                }
                if (targetUuid == null) {
                    player.sendMessage("\u00a7cSpieler nicht gefunden!");
                    return true;
                }
                plugin.getClaimManager().untrustPlayer(chunk, targetUuid);
                player.sendMessage("\u00a7aVertrauen entfernt.");
            }
            case "trustall" -> {
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /trustall <Spieler>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("\u00a7cSpieler nicht gefunden oder nicht online!");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("\u00a7cDu kannst dir selbst nicht vertrauen.");
                    return true;
                }
                int count = plugin.getClaimManager().trustAll(player.getUniqueId(), target.getUniqueId());
                if (count == 0) {
                    player.sendMessage("\u00a7cDu hast keine Claims oder " + target.getName() + " ist bereits überall vertraut.");
                } else {
                    player.sendMessage("\u00a7a" + target.getName() + "\u00a7a wurde auf \u00a7f" + count + "\u00a7a Claims vertraut!");
                    target.sendMessage("\u00a7a" + player.getName() + "\u00a7a vertraut dir jetzt auf all seinen Claims!");
                }
            }
            case "trustlist" -> {
                if (!plugin.getClaimManager().isClaimed(chunk)) {
                    player.sendMessage("\u00a7cDieser Chunk ist nicht geclaimed!");
                    return true;
                }
                if (!plugin.getClaimManager().isOwner(chunk, player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu bist nicht der Besitzer dieses Chunks!");
                    return true;
                }
                Set<UUID> trusted = plugin.getClaimManager().getTrusted(chunk);
                if (trusted.isEmpty()) {
                    player.sendMessage("\u00a77Keine vertrauten Spieler in diesem Chunk.");
                } else {
                    player.sendMessage("\u00a7dVertraute Spieler:");
                    for (UUID uuid : trusted) {
                        Player p = Bukkit.getPlayer(uuid);
                        String name = p != null ? p.getName() : uuid.toString();
                        player.sendMessage("\u00a77- \u00a7f" + name);
                    }
                }
            }
        }
        return true;
    }
}
