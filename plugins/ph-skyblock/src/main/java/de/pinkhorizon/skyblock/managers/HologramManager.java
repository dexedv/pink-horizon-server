package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet TextDisplay-Hologramme über Generatoren.
 * Drei Zeilen pro Generator, positioniert über dem Block.
 */
public class HologramManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // posKey → [Zeile1, Zeile2, Zeile3] EntityUUIDs
    private final Map<String, UUID[]> hologramMap = new HashMap<>();

    private final PHSkyBlock plugin;

    public HologramManager(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    /** Spawnt drei TextDisplay-Zeilen über dem Generator-Block. */
    public void spawnHologram(Generator gen) {
        removeHologram(gen.getPosKey()); // Erst alte entfernen

        Location base = gen.getLocation().clone().add(0.5, 1.15, 0.5);

        UUID[] ids = new UUID[3];
        ids[0] = spawnLine(base.clone().add(0, 0.55, 0), gen.getHologramLine1());
        ids[1] = spawnLine(base.clone().add(0, 0.25, 0), gen.getHologramLine2());
        ids[2] = spawnLine(base.clone().add(0, 0.00, 0), gen.getHologramLine3());

        hologramMap.put(gen.getPosKey(), ids);
    }

    /** Aktualisiert die Texte ohne neue Entities zu spawnen. */
    public void updateHologram(Generator gen) {
        UUID[] ids = hologramMap.get(gen.getPosKey());
        if (ids == null) return;

        String[] lines = { gen.getHologramLine1(), gen.getHologramLine2(), gen.getHologramLine3() };
        for (int i = 0; i < 3; i++) {
            if (ids[i] == null) continue;
            var entity = plugin.getServer().getEntity(ids[i]);
            if (entity instanceof TextDisplay td) {
                final String text = lines[i];
                if (plugin.getServer().isPrimaryThread()) {
                    td.text(MM.deserialize(legacyToMini(text)));
                } else {
                    final var tdFinal = td;
                    plugin.getServer().getScheduler().runTask(plugin,
                        () -> tdFinal.text(MM.deserialize(legacyToMini(text))));
                }
            }
        }
    }

    /** Entfernt das Hologramm für den gegebenen posKey. */
    public void removeHologram(String posKey) {
        UUID[] ids = hologramMap.remove(posKey);
        if (ids == null) return;
        for (UUID id : ids) {
            if (id == null) continue;
            var entity = plugin.getServer().getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    public void removeHologram(Generator gen) {
        removeHologram(gen.getPosKey());
    }

    /** Entfernt alle Hologramme (beim Plugin-Stop). */
    public void removeAll() {
        hologramMap.keySet().forEach(key -> {
            UUID[] ids = hologramMap.get(key);
            if (ids == null) return;
            for (UUID id : ids) {
                if (id == null) continue;
                var entity = plugin.getServer().getEntity(id);
                if (entity != null) entity.remove();
            }
        });
        hologramMap.clear();
    }

    // ── Interne Hilfsmethoden ─────────────────────────────────────────────────

    private UUID spawnLine(Location loc, String legacyText) {
        if (loc.getWorld() == null) return null;
        String miniText = legacyToMini(legacyText);
        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(MM.deserialize(miniText));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setDefaultBackground(false);
            d.setShadowed(true);
        });
        return td.getUniqueId();
    }

    /**
     * Konvertiert §-Farbcodes in MiniMessage-Format.
     * Für einfache Farben und Bold/Italic.
     */
    private String legacyToMini(String s) {
        if (s == null) return "";
        return s
            .replace("§0", "<black>").replace("§1", "<dark_blue>").replace("§2", "<dark_green>")
            .replace("§3", "<dark_aqua>").replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
            .replace("§6", "<gold>").replace("§7", "<gray>").replace("§8", "<dark_gray>")
            .replace("§9", "<blue>").replace("§a", "<green>").replace("§b", "<aqua>")
            .replace("§c", "<red>").replace("§d", "<light_purple>").replace("§e", "<yellow>")
            .replace("§f", "<white>").replace("§l", "<bold>").replace("§o", "<italic>")
            .replace("§n", "<underlined>").replace("§m", "<strikethrough>").replace("§r", "<reset>");
    }
}
