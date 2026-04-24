package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class CosmeticsManager implements Listener {

    public enum Trail {
        FLAMME    ("§c🔥 Flammen-Spur",    Material.BLAZE_POWDER,    Particle.FLAME,         "Feurige Partikel an deinen Füßen"),
        SCHNEE    ("§f❄ Schnee-Spur",       Material.SNOWBALL,         Particle.SNOWFLAKE,      "Sanfte Schneeflocken"),
        VOID      ("§8🌑 Void-Spur",        Material.OBSIDIAN,         Particle.PORTAL,         "Dunkle Portal-Partikel"),
        NATUR     ("§a🌿 Natur-Spur",       Material.FERN,             Particle.HAPPY_VILLAGER, "Grüne Natur-Partikel"),
        REGENBOGEN("§d🌈 Regenbogen-Spur",  Material.FIREWORK_ROCKET,  Particle.DUST,           "Bunte Farb-Partikel");

        public final String displayName;
        public final Material icon;
        public final Particle particle;
        public final String desc;

        Trail(String displayName, Material icon, Particle particle, String desc) {
            this.displayName = displayName;
            this.icon        = icon;
            this.particle    = particle;
            this.desc        = desc;
        }
    }

    private static final int[] RAINBOW_RGB = {
        0xFF0000, 0xFF7700, 0xFFFF00, 0x00FF00, 0x0077FF, 0x8B00FF
    };

    private final PHLobby plugin;
    private final Map<UUID, Trail> activeTrails  = new HashMap<>();
    private final Map<UUID, Integer> rainbowTick = new HashMap<>();
    private BukkitTask particleTask;

    public CosmeticsManager(PHLobby plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startParticleTask();
    }

    private void startParticleTask() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Trail> e : new HashMap<>(activeTrails).entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null || !p.isOnline()) continue;

                Trail trail = e.getValue();
                var loc = p.getLocation().add(0, 0.15, 0);

                if (trail == Trail.REGENBOGEN) {
                    int tick = rainbowTick.merge(e.getKey(), 1, (a, b) -> (a + b) % RAINBOW_RGB.length);
                    int rgb  = RAINBOW_RGB[tick];
                    p.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.15, 0.1, 0.15,
                        new Particle.DustOptions(Color.fromRGB(rgb), 1.3f));
                } else {
                    p.getWorld().spawnParticle(trail.particle, loc, 5, 0.15, 0.1, 0.15, 0.02);
                }
            }
        }, 2L, 2L);
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(), 27,
            Component.text("§d§l★ Kosmetik"));

        ItemStack pane = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(0, makePane(Material.BLACK_STAINED_GLASS_PANE));
        for (int s = 1; s < 9; s++) inv.setItem(s, makePane(Material.BLACK_STAINED_GLASS_PANE));

        Trail active   = activeTrails.get(player.getUniqueId());
        Trail[] trails = Trail.values();
        int[]  slots   = {10, 11, 12, 13, 14};

        for (int i = 0; i < trails.length; i++) {
            inv.setItem(slots[i], buildTrailItem(trails[i], trails[i] == active));
        }

        inv.setItem(22, makeItem(Material.BARRIER, "§cSpur deaktivieren",
            List.of("§7Entfernt den aktiven Trail")));
        inv.setItem(26, makeItem(Material.DARK_OAK_DOOR, "§7Schließen", List.of()));

        player.openInventory(inv);
    }

    private ItemStack buildTrailItem(Trail trail, boolean active) {
        ItemStack item = new ItemStack(trail.icon);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text((active ? "§a§l✔ " : "§7") + trail.displayName));
        meta.lore(List.of(
            Component.text("§8─────────────────────"),
            Component.text("§7" + trail.desc),
            Component.text("§8─────────────────────"),
            Component.text(active ? "§a● Aktiv" : "§8○ Inaktiv"),
            Component.text("§7Klicken zum " + (active ? "Deaktivieren" : "Aktivieren"))
        ));
        if (active) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiHolder))  return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 26) { player.closeInventory(); return; }

        if (slot == 22) {
            activeTrails.remove(player.getUniqueId());
            rainbowTick.remove(player.getUniqueId());
            player.sendMessage("§7Spur deaktiviert.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
            openGui(player);
            return;
        }

        int[]  slots  = {10, 11, 12, 13, 14};
        Trail[] trails = Trail.values();
        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            Trail trail   = trails[i];
            Trail current = activeTrails.get(player.getUniqueId());
            if (current == trail) {
                activeTrails.remove(player.getUniqueId());
                rainbowTick.remove(player.getUniqueId());
                player.sendMessage("§7" + trail.displayName + " §7deaktiviert.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
            } else {
                activeTrails.put(player.getUniqueId(), trail);
                player.sendMessage("§a✔ " + trail.displayName + " §aaktiviert!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
            openGui(player);
            break;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void removePlayer(UUID uuid) {
        activeTrails.remove(uuid);
        rainbowTick.remove(uuid);
    }

    public void stop() {
        if (particleTask != null) particleTask.cancel();
        activeTrails.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack makePane(Material mat) {
        ItemStack p = new ItemStack(mat);
        ItemMeta  m = p.getItemMeta();
        m.displayName(Component.empty());
        p.setItemMeta(m);
        return p;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream().map(Component::text).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static class GuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
