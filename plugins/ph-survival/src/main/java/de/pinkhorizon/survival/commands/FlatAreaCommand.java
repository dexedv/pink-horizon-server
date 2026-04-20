package de.pinkhorizon.survival.commands;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockTypes;
import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlatAreaCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public FlatAreaCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur für Spieler!"); return true; }
        if (!player.hasPermission("survival.admin")) {
            player.sendMessage(Component.text("§cKein Zugriff!"));
            return true;
        }

        int radius = 150; // 300x300
        int y = player.getLocation().getBlockY();

        if (args.length >= 1) {
            try { radius = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
                player.sendMessage(Component.text("§cRadius muss eine Zahl sein!"));
                return true;
            }
        }
        if (args.length >= 2) {
            try { y = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                player.sendMessage(Component.text("§cY-Level muss eine Zahl sein!"));
                return true;
            }
        }

        int cx = player.getLocation().getBlockX();
        int cz = player.getLocation().getBlockZ();
        int minX = cx - radius, maxX = cx + radius;
        int minZ = cz - radius, maxZ = cz + radius;
        int minY = player.getWorld().getMinHeight();
        int maxY = player.getWorld().getMaxHeight() - 1;
        final int groundY = y;
        final int finalRadius = radius;

        player.sendMessage(Component.text("§6[FlatArea] §ePlaniere " + (radius * 2) + "×" + (radius * 2)
            + " Blöcke auf Y=" + groundY + "..."));

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {

                Region r;

                // Alles oberhalb des Bodens → Luft
                if (groundY + 1 <= maxY) {
                    r = new CuboidRegion(BlockVector3.at(minX, groundY + 1, minZ), BlockVector3.at(maxX, maxY, maxZ));
                    editSession.setBlocks(r, BlockTypes.AIR.getDefaultState());
                }

                // Gras-Schicht
                r = new CuboidRegion(BlockVector3.at(minX, groundY, minZ), BlockVector3.at(maxX, groundY, maxZ));
                editSession.setBlocks(r, BlockTypes.GRASS_BLOCK.getDefaultState());

                // Dirt-Schicht (3 Blöcke tief)
                if (groundY - 1 >= minY) {
                    r = new CuboidRegion(BlockVector3.at(minX, Math.max(minY, groundY - 3), minZ), BlockVector3.at(maxX, groundY - 1, maxZ));
                    editSession.setBlocks(r, BlockTypes.DIRT.getDefaultState());
                }

                // Stein darunter
                if (groundY - 4 >= minY) {
                    r = new CuboidRegion(BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, groundY - 4, maxZ));
                    editSession.setBlocks(r, BlockTypes.STONE.getDefaultState());
                }

                int changed = editSession.getBlockChangeCount();
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§a[FlatArea] §aFertig! §7" + changed
                        + " Blöcke gesetzt. (Radius=" + finalRadius + ", Y=" + groundY + ")")));

            } catch (Exception e) {
                plugin.getLogger().severe("[FlatArea] Fehler: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("§c[FlatArea] Fehler: " + e.getMessage())));
            }
        });

        return true;
    }
}
