package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.JobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobsCommand implements CommandExecutor {

    public static final String JOB_KEY = "job_id";

    private final PHSurvival plugin;

    public JobsCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        openGui(player);
        return true;
    }

    public void openGui(Player player) {
        UUID uuid = player.getUniqueId();
        JobManager jm = plugin.getJobManager();
        JobManager.Job current = jm.getJob(uuid);

        Inventory inv = plugin.getServer().createInventory(null, 54,
                Component.text("§6§lJobs §8| §ePink Horizon"));

        // Rahmen
        ItemStack border = makeGlass(Material.GRAY_STAINED_GLASS_PANE, "§8 ");
        ItemStack gold   = makeGlass(Material.GOLD_BLOCK, "§6 ");
        for (int i = 0; i < 9; i++)  inv.setItem(i, gold);
        for (int i = 45; i < 54; i++) inv.setItem(i, gold);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
        }

        // Job-Items (5 Jobs in Zeile 2 und 3, zentriert)
        int[] slots = { 11, 13, 15, 20, 24 };
        JobManager.Job[] jobs = JobManager.Job.values();
        for (int i = 0; i < jobs.length; i++) {
            inv.setItem(slots[i], makeJobItem(jobs[i], current, uuid, jm));
        }

        // Info-Item (Slot 31)
        inv.setItem(31, makeInfoItem(current, uuid, jm));

        // Separator
        for (int s : new int[]{18, 19, 21, 22, 23, 25, 26}) {
            inv.setItem(s, border);
        }

        player.openInventory(inv);
    }

    private ItemStack makeJobItem(JobManager.Job job, JobManager.Job current,
                                  UUID uuid, JobManager jm) {
        boolean isActive = job == current;
        ItemStack item = new ItemStack(job.icon);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(
                (isActive ? "§a§l" : "§e§l") + job.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(job.description, NamedTextColor.GRAY));
        lore.add(Component.text(""));

        if (isActive) {
            int level = jm.getLevel(uuid);
            int xp    = jm.getXp(uuid);
            int next  = JobManager.xpForNextLevel(level);
            lore.add(Component.text("§aAktiver Job §6✔"));
            lore.add(Component.text("§7Level: §e" + level + " §8/ §e" + JobManager.getMaxLevel()));
            if (next > 0) {
                lore.add(Component.text("§7XP: §b" + xp + " §8/ §b" + next));
                lore.add(Component.text("§7Bonus: §6+" + (int)((JobManager.getMultiplier(level) - 1.0) * 100) + "%"));
            } else {
                lore.add(Component.text("§6§lMAX LEVEL"));
            }
            lore.add(Component.text(""));
            lore.add(Component.text("§cKlicken zum Verlassen", NamedTextColor.RED));
        } else {
            lore.add(Component.text("§7Klicken zum Beitreten", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, JOB_KEY),
                PersistentDataType.STRING,
                job.name()
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeInfoItem(JobManager.Job current, UUID uuid, JobManager jm) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§e§lMein Job-Status"));

        List<Component> lore = new ArrayList<>();
        if (current != null) {
            lore.add(Component.text("§7Job: §f" + current.displayName));
            lore.add(Component.text("§7Level: §e" + jm.getLevel(uuid)));
            lore.add(Component.text("§7XP: §b" + jm.getXp(uuid)));
        } else {
            lore.add(Component.text("§cKein aktiver Job.", NamedTextColor.RED));
            lore.add(Component.text("§7Wähle einen Job oben aus."));
        }
        lore.add(Component.text(""));
        lore.add(Component.text("§8Du kannst immer nur", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("§8einen Job gleichzeitig haben.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
