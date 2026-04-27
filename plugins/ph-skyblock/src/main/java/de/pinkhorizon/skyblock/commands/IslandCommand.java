package de.pinkhorizon.skyblock.commands;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class IslandCommand implements CommandExecutor {

    private final PHSkyBlock plugin;
    // Reset-Cooldown: UUID → Zeitstempel in ms
    private final Map<UUID, Long> resetCooldowns = new HashMap<>();

    public IslandCommand(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("only-players"));
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "create"   -> cmdCreate(player);
            case "home"     -> cmdHome(player);
            case "spawn"    -> cmdSpawn(player);
            case "sethome"  -> cmdSetHome(player);
            case "reset"    -> cmdReset(player, args);
            case "info"     -> cmdInfo(player);
            case "top"      -> cmdTop(player);
            case "invite"   -> cmdInvite(player, args);
            case "accept"   -> cmdAccept(player);
            case "kick"     -> cmdKick(player, args);
            case "ban"      -> cmdBan(player, args);
            case "unban"    -> cmdUnban(player, args);
            case "members"  -> cmdMembers(player);
            case "open"     -> cmdOpen(player);
            case "warp"     -> cmdWarp(player, args);
            case "setwarp"  -> cmdSetWarp(player, args);
            case "delwarp"  -> cmdDelWarp(player);
            case "chat"     -> cmdChat(player);
            case "level",
                 "score"    -> cmdScore(player);
            case "leave"    -> cmdLeave(player);
            case "visit"    -> cmdVisit(player, args);
            default         -> cmdHelp(player);
        }
        return true;
    }

    // ── /is create ───────────────────────────────────────────────────────────

    private void cmdCreate(Player player) {
        if (plugin.getIslandManager().hasIsland(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-already-exists"));
            return;
        }
        player.sendMessage(plugin.msg("island-created"));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Island island = plugin.getIslandManager().createIsland(player);
            if (island != null) {
                player.teleport(new Location(
                    Bukkit.getWorld(island.getWorld()),
                    island.getHomeX(), island.getHomeY(), island.getHomeZ(),
                    island.getHomeYaw(), island.getHomePitch()
                ));
                SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                if (sp != null) {
                    sp.setIslandId(island.getId());
                    plugin.getIslandRepository().setPlayerIslandId(player.getUniqueId(), island.getId());
                }
            }
        });
    }

    // ── /is home ─────────────────────────────────────────────────────────────

    private void cmdHome(Player player) {
        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }

        World w = Bukkit.getWorld(island.getWorld());
        if (w == null) { player.sendMessage(plugin.msg("island-no-island")); return; }

        player.teleport(new Location(w,
            island.getHomeX(), island.getHomeY(), island.getHomeZ(),
            island.getHomeYaw(), island.getHomePitch()));
        player.sendMessage(plugin.msg("island-home-tp"));
    }

    // ── /is spawn ────────────────────────────────────────────────────────────

    private void cmdSpawn(Player player) {
        plugin.getIslandManager().teleportToSpawn(player);
        player.sendMessage(plugin.msg("island-spawn-tp"));
    }

    // ── /is sethome ──────────────────────────────────────────────────────────

    private void cmdSetHome(Player player) {
        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }

        // Nur Owner oder Co-Owner dürfen
        if (!island.getOwnerUuid().equals(player.getUniqueId()) && !island.isCoOwner(player.getUniqueId())) {
            player.sendMessage(plugin.msg("no-permission")); return;
        }

        // Muss auf der eigenen Insel stehen
        if (!plugin.getIslandManager().isOnIsland(player, island)) {
            player.sendMessage(plugin.msg("island-outside-border")); return;
        }

        Location loc = player.getLocation();
        island.setHomeX(loc.getX());
        island.setHomeY(loc.getY());
        island.setHomeZ(loc.getZ());
        island.setHomeYaw(loc.getYaw());
        island.setHomePitch(loc.getPitch());
        plugin.getIslandManager().saveIsland(island);
        player.sendMessage(plugin.msg("island-setHome-done"));
    }

    // ── /is reset [bestätigen] ────────────────────────────────────────────────

    private void cmdReset(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-owner")); return;
        }

        // Cooldown prüfen
        long cd = plugin.getConfig().getLong("island.reset-cooldown", 300) * 1000L;
        long last = resetCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = cd - (System.currentTimeMillis() - last);
        if (remaining > 0) {
            player.sendMessage(plugin.msg("island-reset-cooldown", "time", remaining / 1000));
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("bestätigen")) {
            player.sendMessage(plugin.msg("island-reset-confirm"));
            return;
        }

        resetCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getIslandManager().resetIsland(island, player);
    }

    // ── /is info ──────────────────────────────────────────────────────────────

    private void cmdInfo(Player player) {
        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }

        player.sendMessage(plugin.msg("island-info-header"));
        player.sendMessage(plugin.msg("island-info-owner", "name", island.getOwnerName()));
        player.sendMessage(plugin.msg("island-info-level", "level", island.getLevel(), "score", island.getScore()));
        player.sendMessage(plugin.msg("island-info-size", "size", island.getSize()));
        player.sendMessage(plugin.msg("island-info-members",
            "count", island.getMembers().size() + 1, "max", island.getMaxMembers()));
        player.sendMessage(plugin.msg("island-info-footer"));
    }

    // ── /is top ───────────────────────────────────────────────────────────────

    private void cmdTop(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var top = plugin.getIslandManager().getTopIslands(10);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(plugin.msg("island-top-header"));
                if (top.isEmpty()) {
                    player.sendMessage(plugin.msg("island-top-empty"));
                } else {
                    int rank = 1;
                    for (Island i : top) {
                        player.sendMessage(plugin.msg("island-top-entry",
                            "rank", rank++,
                            "owner", i.getOwnerName(),
                            "level", i.getLevel(),
                            "score", i.getScore()));
                    }
                }
                player.sendMessage(plugin.msg("island-top-footer"));
            });
        });
    }

    // ── /is invite <Spieler> ─────────────────────────────────────────────────

    private void cmdInvite(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId()) && !island.isCoOwner(player.getUniqueId())) {
            player.sendMessage(plugin.msg("no-permission")); return;
        }
        if (args.length < 2) { player.sendMessage(plugin.msg("help-header")); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found", "name", args[1])); return;
        }
        if (plugin.getIslandManager().hasIsland(target.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-member")); return; // target hat schon Insel
        }

        if (!plugin.getIslandManager().invitePlayer(island, target)) {
            player.sendMessage(plugin.msg("island-invite-full")); return;
        }

        player.sendMessage(plugin.msg("island-invite-sent", "name", target.getName()));
        target.sendMessage(plugin.msg("island-invite-received", "inviter", player.getName()));
    }

    // ── /is accept ────────────────────────────────────────────────────────────

    private void cmdAccept(Player player) {
        if (!plugin.getIslandManager().hasPendingInvite(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-invite-no-invite")); return;
        }

        Integer islandId = plugin.getIslandManager().getPendingInviteIslandId(player.getUniqueId());
        Island island = islandId != null ? plugin.getIslandManager().getIslandById(islandId) : null;
        String ownerName = island != null ? island.getOwnerName() : "?";

        if (plugin.getIslandManager().acceptInvite(player)) {
            player.sendMessage(plugin.msg("island-join-success", "name", ownerName));
            if (island != null) {
                Player owner = Bukkit.getPlayer(island.getOwnerUuid());
                if (owner != null) owner.sendMessage(plugin.msg("island-join-notify", "name", player.getName()));
                // Mitglieder benachrichtigen
                for (Island.IslandMember m : island.getMembers()) {
                    Player mp = Bukkit.getPlayer(m.uuid());
                    if (mp != null && !mp.equals(player)) {
                        mp.sendMessage(plugin.msg("island-join-notify", "name", player.getName()));
                    }
                }
            }
        }
    }

    // ── /is kick <Spieler> ────────────────────────────────────────────────────

    private void cmdKick(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-owner")); return;
        }
        if (args.length < 2) { player.sendMessage(plugin.msg("help-header")); return; }

        String targetName = args[1];
        Island.IslandMember member = island.getMembers().stream()
            .filter(m -> m.name().equalsIgnoreCase(targetName)).findFirst().orElse(null);
        if (member == null) {
            player.sendMessage(plugin.msg("player-not-found", "name", targetName)); return;
        }

        plugin.getIslandManager().kickMember(island, member.uuid());
        player.sendMessage(plugin.msg("island-kick-done", "name", member.name()));

        Player kicked = Bukkit.getPlayer(member.uuid());
        if (kicked != null) kicked.sendMessage(plugin.msg("island-kick-notify", "owner", player.getName()));
    }

    // ── /is ban <Spieler> ─────────────────────────────────────────────────────

    private void cmdBan(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-owner")); return;
        }
        if (args.length < 2) { player.sendMessage(plugin.msg("help-header")); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found", "name", args[1])); return;
        }
        plugin.getIslandManager().banPlayer(island, target.getUniqueId(), target.getName());
        player.sendMessage(plugin.msg("island-ban-done", "name", target.getName()));
        target.sendMessage(plugin.msg("island-ban-notify", "owner", player.getName()));
    }

    // ── /is unban <Spieler> ───────────────────────────────────────────────────

    private void cmdUnban(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-owner")); return;
        }
        if (args.length < 2) { player.sendMessage(plugin.msg("help-header")); return; }

        // Offline-Spieler: UUID aus DB nicht verfügbar ohne Lookup → Spieler muss online sein
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found", "name", args[1])); return;
        }
        plugin.getIslandManager().unbanPlayer(island, target.getUniqueId());
        player.sendMessage(plugin.msg("island-unban-done", "name", target.getName()));
    }

    // ── /is members ───────────────────────────────────────────────────────────

    private void cmdMembers(Player player) {
        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }

        player.sendMessage(plugin.msg("island-info-header"));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "  <yellow>Owner: <white>" + island.getOwnerName()));
        for (Island.IslandMember m : island.getMembers()) {
            String roleLabel = switch (m.role()) {
                case COOP    -> "<aqua>[Co-Owner]</aqua> ";
                case MEMBER  -> "<green>[Mitglied]</green> ";
                case VISITOR -> "<gray>[Besucher]</gray> ";
            };
            player.sendMessage(MiniMessage.miniMessage().deserialize("  " + roleLabel + "<white>" + m.name()));
        }
        player.sendMessage(plugin.msg("island-info-footer"));
    }

    // ── /is open ──────────────────────────────────────────────────────────────

    private void cmdOpen(Player player) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-owner")); return;
        }

        island.setOpen(!island.isOpen());
        plugin.getIslandManager().saveIsland(island);
        player.sendMessage(island.isOpen()
            ? plugin.msg("island-open-on")
            : plugin.msg("island-open-off"));
    }

    // ── /is warp [Spieler] ────────────────────────────────────────────────────

    private void cmdWarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.msg("help-header")); return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found", "name", args[1])); return;
        }
        Island island = plugin.getIslandManager().getIslandByOwner(target.getUniqueId());
        if (island == null || !island.isWarpEnabled()) {
            player.sendMessage(plugin.msg("island-warp-none")); return;
        }
        if (plugin.getIslandManager().isBanned(island, player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-banned")); return;
        }

        World w = Bukkit.getWorld(island.getWorld());
        if (w == null) return;
        player.teleport(new Location(w,
            island.getHomeX(), island.getHomeY(), island.getHomeZ(),
            island.getHomeYaw(), island.getHomePitch()));
        player.sendMessage(plugin.msg("island-warp-visiting", "name", target.getName()));
    }

    // ── /is setwarp [name] ────────────────────────────────────────────────────

    private void cmdSetWarp(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-owner")); return;
        }

        String name = args.length > 1 ? args[1] : player.getName();
        island.setWarpEnabled(true);
        island.setWarpName(name);
        plugin.getIslandManager().saveIsland(island);
        player.sendMessage(plugin.msg("island-warp-on"));
    }

    // ── /is delwarp ───────────────────────────────────────────────────────────

    private void cmdDelWarp(Player player) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        island.setWarpEnabled(false);
        plugin.getIslandManager().saveIsland(island);
        player.sendMessage(plugin.msg("island-warp-off"));
    }

    // ── /is chat ──────────────────────────────────────────────────────────────

    private void cmdChat(Player player) {
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) return;
        if (!plugin.getIslandManager().hasIsland(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-no-island")); return;
        }

        sp.setIslandChat(!sp.isIslandChat());
        player.sendMessage(sp.isIslandChat()
            ? plugin.msg("island-chat-on")
            : plugin.msg("island-chat-off"));
    }

    // ── /is level ────────────────────────────────────────────────────────────

    private void cmdScore(Player player) {
        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }

        player.sendMessage(plugin.msg("island-score-calc"));
        plugin.getScoreManager().calculateScore(island, () ->
            player.sendMessage(plugin.msg("island-score-result",
                "score", island.getScore(), "level", island.getLevel())));
    }

    // ── /is leave ─────────────────────────────────────────────────────────────

    private void cmdLeave(Player player) {
        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (island.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-not-member")); // Besitzer kann nicht "leaven"
            return;
        }
        plugin.getIslandManager().kickMember(island, player.getUniqueId());
        player.sendMessage(plugin.msg("island-spawn-tp"));
        plugin.getIslandManager().teleportToSpawn(player);
    }

    // ── /is visit <Spieler> ───────────────────────────────────────────────────

    private void cmdVisit(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(plugin.msg("help-header")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found", "name", args[1])); return;
        }

        Island island = plugin.getIslandManager().getIslandByOwner(target.getUniqueId());
        if (island == null) { player.sendMessage(plugin.msg("island-no-island")); return; }
        if (!island.isOpen() && !island.isWarpEnabled()) {
            player.sendMessage(plugin.msg("island-protected")); return;
        }
        if (plugin.getIslandManager().isBanned(island, player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-banned")); return;
        }

        World w = Bukkit.getWorld(island.getWorld());
        if (w == null) return;
        player.teleport(new Location(w,
            island.getHomeX(), island.getHomeY(), island.getHomeZ(),
            island.getHomeYaw(), island.getHomePitch()));
        player.sendMessage(plugin.msg("island-warp-visiting", "name", target.getName()));
    }

    // ── /is help ─────────────────────────────────────────────────────────────

    private void cmdHelp(Player player) {
        player.sendMessage(plugin.msg("help-header"));
        String[] cmds = {
            "/is create          – Erstelle deine Insel",
            "/is home            – Teleportiere zu deiner Insel",
            "/is spawn           – Teleportiere zum Spawn",
            "/is sethome         – Setze dein Insel-Home",
            "/is info            – Insel-Informationen",
            "/is members         – Mitglieder anzeigen",
            "/is invite <Spieler>– Spieler einladen",
            "/is accept          – Einladung annehmen",
            "/is kick <Spieler>  – Mitglied entfernen",
            "/is ban/unban       – Spieler sperren/freigeben",
            "/is open            – Insel öffnen/schließen",
            "/is setwarp [name]  – Insel-Warp setzen",
            "/is warp <Spieler>  – Zum Warp eines Spielers",
            "/is visit <Spieler> – Offene Insel besuchen",
            "/is reset           – Insel zurücksetzen",
            "/is level           – Insel-Score berechnen",
            "/is top             – Top 10 Inseln",
            "/is chat            – Insel-Chat umschalten",
            "/is leave           – Insel verlassen"
        };
        for (String c : cmds) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "  <gray>" + c));
        }
        player.sendMessage(plugin.msg("help-footer"));
    }
}
