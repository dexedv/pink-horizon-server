package de.pinkhorizon.lobby.listeners;

import de.pinkhorizon.lobby.PHLobby;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;

public class LobbyListener implements Listener {

    private final PHLobby plugin;

    public LobbyListener(PHLobby plugin) {
        this.plugin = plugin;
    }

    // ── Spielerschutz ───────────────────────────────────────────────────

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    // ── Void-Schutz ─────────────────────────────────────────────────────

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < 0) {
            teleportToSpawn(event.getPlayer());
        }
    }

    private void teleportToSpawn(Player player) {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) return;
        Location spawn = new Location(world,
            plugin.getConfig().getDouble("spawn.x", 0),
            plugin.getConfig().getDouble("spawn.y", 65),
            plugin.getConfig().getDouble("spawn.z", 0),
            (float) plugin.getConfig().getDouble("spawn.yaw", 0),
            (float) plugin.getConfig().getDouble("spawn.pitch", 0));
        player.teleport(spawn);
    }

    // ── Block-Schutz ────────────────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getBuildCommand().isBuildMode(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("lobby.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (!plugin.getBuildCommand().isBuildMode(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("lobby.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getBuildCommand().isBuildMode(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("lobby.admin")) {
            event.setCancelled(true);
        }
    }

    // ── Keine Mobs ──────────────────────────────────────────────────────

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        event.setCancelled(true);
    }

    // ── Explosionen / Feuer ─────────────────────────────────────────────

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == org.bukkit.Material.FIRE
                || event.getSource().getType() == org.bukkit.Material.SOUL_FIRE) {
            event.setCancelled(true);
        }
    }

    // ── Chat-Format ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player = event.getPlayer();
        de.pinkhorizon.lobby.managers.RankManager.Rank rank =
            plugin.getRankManager().getRank(player.getUniqueId());
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());

        Component formatted = Component.text("§8[§dLobby§8] " + rank.chatPrefix)
            .append(Component.text(player.getName(), rank.nameColor))
            .append(Component.text(" §8» §f" + text, NamedTextColor.WHITE));

        Bukkit.broadcast(formatted);
    }

    // ── Server-MOTD ─────────────────────────────────────────────────────

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        event.setMotd("§d§lPink Horizon §8| §7Community Server\n§7Lobby §8• §aSurvival §8• §6SkyBlock §8• §bMinigames");
    }
}
