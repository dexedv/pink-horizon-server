package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet TextDisplay-Hologramme über Generator-Blöcken.
 * Kein externes Plugin nötig – nutzt Paper's eingebaute TextDisplay-Entities.
 */
public class HologramManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** locationKey → TextDisplay */
    private final Map<String, TextDisplay> holograms = new HashMap<>();
    private BukkitTask updateTask;

    public HologramManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void startUpdateTask() {
        int ticks = plugin.getConfig().getInt("hologram-update-ticks", 40);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, ticks, ticks);
    }

    public void stopUpdateTask() {
        if (updateTask != null) updateTask.cancel();
        removeAll();
    }

    // ── Hologramm-Verwaltung ─────────────────────────────────────────────────

    private void spawnHologramWithData(PlacedGenerator gen, PlayerData data) {
        removeHologram(gen);
        World world = Bukkit.getWorld(gen.getWorld());
        if (world == null) return;

        Location loc = new Location(world, gen.getX() + 0.5, gen.getY() + 1.6, gen.getZ() + 0.5);
        String text = buildHologramText(gen, data);

        TextDisplay display = world.spawn(loc, TextDisplay.class, entity -> {
            entity.text(MM.deserialize(text));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f(0, 0, 0, 1)));
        });

        holograms.put(gen.locationKey(), display);
    }

    /** Hilfsmethode für updateAll */

    public void updateHologram(PlacedGenerator gen, PlayerData data) {
        TextDisplay display = holograms.get(gen.locationKey());
        if (display == null || display.isDead()) {
            PlayerData d = data != null ? data : plugin.getPlayerDataMap().get(gen.getOwnerUUID());
            spawnHologramWithData(gen, d);
            return;
        }
        display.text(MM.deserialize(buildHologramText(gen, data)));
    }

    public void spawnHologram(PlacedGenerator gen) {
        PlayerData data = plugin.getPlayerDataMap().get(gen.getOwnerUUID());
        spawnHologramWithData(gen, data);
    }

    public void removeHologram(PlacedGenerator gen) {
        TextDisplay display = holograms.remove(gen.locationKey());
        if (display != null && !display.isDead()) display.remove();
    }

    public void removeAll() {
        holograms.values().forEach(d -> { if (d != null && !d.isDead()) d.remove(); });
        holograms.clear();
    }

    private void updateAll() {
        for (Map.Entry<UUID, PlayerData> entry : plugin.getPlayerDataMap().entrySet()) {
            PlayerData data = entry.getValue();
            for (PlacedGenerator gen : data.getGenerators()) {
                TextDisplay display = holograms.get(gen.locationKey());
                if (display == null || display.isDead()) {
                    spawnHologramWithData(gen, data);
                } else {
                    display.text(MM.deserialize(buildHologramText(gen, data)));
                }
            }
        }
    }

    // ── Text-Generierung ─────────────────────────────────────────────────────

    private String buildHologramText(PlacedGenerator gen, PlayerData data) {
        String megaPrefix = gen.getType().isMega() ? "<bold>" : "";
        String megaSuffix = gen.getType().isMega() ? "</bold>" : "";

        double rawIncome = gen.incomePerSecond();
        double effectiveIncome = rawIncome;
        if (data != null) {
            effectiveIncome *= data.prestigeMultiplier()
                    * data.effectiveBoosterMultiplier()
                    * plugin.getMoneyManager().getServerBoosterMultiplier()
                    * plugin.getSynergyManager().getTotalSynergyMultiplier(data);
        }

        String nextUpgrade = "";
        if (data != null && gen.getLevel() < data.maxGeneratorLevel()) {
            nextUpgrade = "\n<gray>⬆ Upgrade: <yellow>$" + MoneyManager.formatMoney(gen.upgradeCost());
        } else if (data != null) {
            nextUpgrade = "\n<green><bold>MAX LEVEL</bold></green>";
        }

        return megaPrefix + gen.getType().getDisplayName() + megaSuffix + "\n"
                + "<gray>Level: <white>" + gen.getLevel()
                + nextUpgrade + "\n"
                + "<green>$" + MoneyManager.formatMoney(Math.round(effectiveIncome)) + "/s";
    }
}
