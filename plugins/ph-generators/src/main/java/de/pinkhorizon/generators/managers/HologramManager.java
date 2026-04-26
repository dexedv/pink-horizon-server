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

    /** locationKey → TextDisplay (Generator-Holograms) */
    private final Map<String, TextDisplay> holograms = new HashMap<>();
    /** uuid → TextDisplay (Stats-Holograms) */
    private final Map<UUID, TextDisplay> statsHolograms = new HashMap<>();
    /** uuid → TextDisplay (Leaderboard-Holograms) */
    private final Map<UUID, TextDisplay> lbHolograms = new HashMap<>();
    /** uuid → TextDisplay (Spawn-Info-Hologramme – auto-platziert neben dem Insel-Spawnpunkt) */
    private final Map<UUID, TextDisplay> spawnHolos = new HashMap<>();
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
        removeAllStatsHolos();
        removeAllLbHolos();
        removeAllSpawnHolos();
    }

    // ── Hologramm-Verwaltung ─────────────────────────────────────────────────

    private void spawnHologramWithData(PlacedGenerator gen, PlayerData data) {
        removeHologram(gen);
        World world = Bukkit.getWorld(gen.getWorld());
        if (world == null) return;

        // Chunk laden damit der TextDisplay gespawnt werden kann
        int cx = gen.getX() >> 4;
        int cz = gen.getZ() >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            world.loadChunk(cx, cz, false);
        }

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
            // Stats-Hologramm aktualisieren
            if (data.hasStatsHolo()) {
                updateStatsHolo(entry.getKey(), data);
            }
            // Ranglisten-Hologramm aktualisieren
            if (data.hasLbHolo()) {
                updateLbHolo(entry.getKey(), data);
            }
            // Spawn-Info-Hologramm aktualisieren (falls gesetzt)
            if (spawnHolos.containsKey(entry.getKey())) {
                updateSpawnHolo(entry.getKey(), data);
            }
        }
    }

    // ── Stats-Hologramm ──────────────────────────────────────────────────────

    public void setStatsHolo(UUID uuid, org.bukkit.Location loc) {
        removeStatsHolo(uuid);
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f(0, 0, 0, 1)));
        });
        statsHolograms.put(uuid, display);
        PlayerData data = plugin.getPlayerDataMap().get(uuid);
        if (data != null) display.text(MM.deserialize(buildStatsText(data)));
    }

    public void removeStatsHolo(UUID uuid) {
        TextDisplay d = statsHolograms.remove(uuid);
        if (d != null && !d.isDead()) d.remove();
    }

    public void removeAllStatsHolos() {
        statsHolograms.values().forEach(d -> { if (d != null && !d.isDead()) d.remove(); });
        statsHolograms.clear();
    }

    // ── Ranglisten-Hologramm ─────────────────────────────────────────────────

    public void setLbHolo(UUID uuid, Location loc) {
        removeLbHolo(uuid);
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f(0, 0, 0, 1)));
        });
        display.text(MM.deserialize(buildLbText()));
        lbHolograms.put(uuid, display);
    }

    public void removeLbHolo(UUID uuid) {
        TextDisplay d = lbHolograms.remove(uuid);
        if (d != null && !d.isDead()) d.remove();
    }

    public void removeAllLbHolos() {
        lbHolograms.values().forEach(d -> { if (d != null && !d.isDead()) d.remove(); });
        lbHolograms.clear();
    }

    private void updateLbHolo(UUID uuid, PlayerData data) {
        TextDisplay display = lbHolograms.get(uuid);
        if (display == null || display.isDead()) {
            World world = org.bukkit.Bukkit.getWorld(data.getLbHoloWorld());
            if (world == null) return;
            Location loc = new Location(world,
                    data.getLbHoloX() + 0.5, data.getLbHoloY(), data.getLbHoloZ() + 0.5);
            setLbHolo(uuid, loc);
            return;
        }
        display.text(MM.deserialize(buildLbText()));
    }

    private String buildLbText() {
        java.util.List<de.pinkhorizon.generators.data.PlayerData> top =
                plugin.getLeaderboardManager().getCachedTop();

        StringBuilder sb = new StringBuilder();
        sb.append("<gold><bold>⚙ Top Generatoren-Spieler</bold></gold>\n");
        sb.append("<dark_gray>──────────────────────</dark_gray>\n");

        if (top.isEmpty()) {
            sb.append("<gray>Noch keine Daten…");
            return sb.toString();
        }

        String[] medals = {"<gold>① ", "<gray>② ", "<dark_red>③ ",
                           "<yellow>#4 ", "<yellow>#5 ", "<yellow>#6 ",
                           "<yellow>#7 ", "<yellow>#8 ", "<yellow>#9 ", "<yellow>#10 "};

        int shown = Math.min(top.size(), 10);
        for (int i = 0; i < shown; i++) {
            de.pinkhorizon.generators.data.PlayerData d = top.get(i);
            boolean online = org.bukkit.Bukkit.getPlayer(d.getUuid()) != null;
            String dot = online ? "<green>●</green> " : "<red>●</red> ";
            String prestige = d.getPrestige() > 0 ? " <dark_purple>[P" + d.getPrestige() + "]" : "";
            sb.append(medals[i]).append(dot)
              .append("<white>").append(d.getName()).append(prestige)
              .append(" <gold>$").append(MoneyManager.formatMoney(d.getMoney())).append("\n");
        }
        sb.append("<dark_gray>──────────────────────</dark_gray>\n");
        sb.append("<dark_gray>Aktualisiert alle 60s");
        return sb.toString();
    }

    private void updateStatsHolo(UUID uuid, PlayerData data) {
        TextDisplay display = statsHolograms.get(uuid);
        if (display == null || display.isDead()) {
            World world = Bukkit.getWorld(data.getHoloWorld());
            if (world == null) return;
            Location loc = new Location(world, data.getHoloX() + 0.5, data.getHoloY(), data.getHoloZ() + 0.5);
            setStatsHolo(uuid, loc);
            return;
        }
        display.text(MM.deserialize(buildStatsText(data)));
    }

    private String buildStatsText(PlayerData data) {
        // Gesamteinkommen berechnen
        double totalIncome = 0;
        for (PlacedGenerator gen : data.getGenerators()) {
            totalIncome += gen.incomePerSecond()
                    * data.prestigeMultiplier()
                    * data.effectiveBoosterMultiplier()
                    * plugin.getMoneyManager().getServerBoosterMultiplier()
                    * plugin.getSynergyManager().getTotalSynergyMultiplier(data);
        }
        int maxSlots = data.maxGeneratorSlots(
                plugin.getConfig().getInt("max-generators", 10),
                plugin.getConfig().getInt("generator-slot-per-prestige", 2));

        StringBuilder sb = new StringBuilder();
        sb.append("<gold><bold>⚙ ").append(data.getName()).append("</bold></gold>\n");
        sb.append("<dark_gray>──────────────────</dark_gray>\n");
        sb.append("<gray>💰 Geld: <green>$").append(MoneyManager.formatMoney(data.getMoney())).append("\n");
        sb.append("<gray>✦ Prestige: <light_purple>").append(data.getPrestige())
          .append(" <gray>(+").append((int)((data.prestigeMultiplier()-1)*100)).append("%)\n");
        sb.append("<gray>⚙ Generatoren: <white>").append(data.getGenerators().size()).append("<gray>/").append(maxSlots).append("\n");
        sb.append("<gray>📈 Einkommen: <aqua>$").append(MoneyManager.formatMoney(Math.round(totalIncome))).append("/s\n");
        sb.append("<gray>🏆 Gesamt: <yellow>$").append(MoneyManager.formatMoney(data.getTotalEarned())).append("\n");
        sb.append("<gray>🌍 Border: <white>").append(data.getBorderSize()).append("×").append(data.getBorderSize()).append("\n");
        sb.append("<dark_gray>──────────────────</dark_gray>\n");
        if (data.hasActiveBooster()) {
            long remaining = (data.getBoosterExpiry() - System.currentTimeMillis() / 1000) / 60;
            sb.append("<yellow>⚡ x").append(data.getBoosterMultiplier())
              .append(" Booster <gray>(").append(remaining).append(" Min)");
        } else {
            sb.append("<dark_gray>Kein Booster aktiv");
        }
        return sb.toString();
    }

    // ── Spawn-Info-Hologramm ─────────────────────────────────────────────────

    /**
     * Setzt das automatische Spawn-Info-Hologramm 4 Blöcke neben dem Insel-Spawnpunkt.
     * Wird beim Teleportieren auf die Insel automatisch aufgerufen.
     *
     * @param uuid UUID des Spielers
     * @param spawnLoc Spawn-Position der Insel (aus config)
     */
    public void setSpawnHolo(UUID uuid, Location spawnLoc) {
        removeSpawnHolo(uuid);

        // 4 Blöcke in +X-Richtung (Ost), auf Augenhöhe
        double offsetX = plugin.getConfig().getDouble("island.spawn-holo-offset-x", 4.0);
        double offsetZ = plugin.getConfig().getDouble("island.spawn-holo-offset-z", 0.0);
        Location holoLoc = spawnLoc.clone().add(offsetX, 1.6, offsetZ);

        World world = holoLoc.getWorld();
        if (world == null) return;

        PlayerData data = plugin.getPlayerDataMap().get(uuid);

        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f(0, 0, 0, 1)));
            if (data != null) entity.text(MM.deserialize(buildSpawnHoloText(data)));
        });

        spawnHolos.put(uuid, display);
    }

    public void removeSpawnHolo(UUID uuid) {
        TextDisplay d = spawnHolos.remove(uuid);
        if (d != null && !d.isDead()) d.remove();
    }

    public void removeAllSpawnHolos() {
        spawnHolos.values().forEach(d -> { if (d != null && !d.isDead()) d.remove(); });
        spawnHolos.clear();
    }

    private void updateSpawnHolo(UUID uuid, PlayerData data) {
        TextDisplay display = spawnHolos.get(uuid);
        if (display == null || display.isDead()) {
            // Hologramm ist weg (z.B. Chunk-Reload) → neu spawnen
            World world = Bukkit.getWorld(plugin.getIslandWorldManager().getWorldName(uuid));
            if (world == null) return;
            double spawnX = plugin.getConfig().getDouble("island.spawn-x", 0.5);
            double spawnY = plugin.getConfig().getDouble("island.spawn-y", 64.0);
            double spawnZ = plugin.getConfig().getDouble("island.spawn-z", 0.5);
            setSpawnHolo(uuid, new Location(world, spawnX, spawnY, spawnZ));
            return;
        }
        display.text(MM.deserialize(buildSpawnHoloText(data)));
    }

    private String buildSpawnHoloText(PlayerData data) {
        boolean hasGen      = !data.getGenerators().isEmpty();
        boolean hasUpgraded = data.getTotalUpgrades() > 0;
        boolean hasThreeGen = data.getGenerators().size() >= 3;
        boolean hasPrestige = data.getPrestige() > 0;

        double totalIncome = 0;
        for (de.pinkhorizon.generators.data.PlacedGenerator gen : data.getGenerators()) {
            totalIncome += gen.incomePerSecond()
                    * data.prestigeMultiplier()
                    * data.effectiveBoosterMultiplier()
                    * plugin.getMoneyManager().getServerBoosterMultiplier()
                    * plugin.getSynergyManager().getTotalSynergyMultiplier(data)
                    * data.getRankMultiplier();
        }

        StringBuilder sb = new StringBuilder();

        sb.append("<light_purple><bold>  ✦ IdleForge – Guide ✦  </bold></light_purple>\n");
        sb.append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━\n");

        // Schritt 1
        if (!hasGen) {
            sb.append("<yellow><bold>➤ Schritt 1: Generator platzieren</bold></yellow>\n");
            sb.append("<gray>  Du hast einen <white>Cobblestone-Generator\n");
            sb.append("<gray>  im Inventar. Lege ihn auf der\n");
            sb.append("<gray>  Insel ab um zu starten!\n");
            sb.append("<dark_gray>  ↳ Er generiert sofort Geld/s\n");
        } else {
            sb.append("<green>✔ <white>Schritt 1: <green>Generator platziert!\n");
        }
        sb.append("<dark_gray>───────────────────────\n");

        // Schritt 2
        if (hasGen && !hasUpgraded) {
            sb.append("<yellow><bold>➤ Schritt 2: Generator upgraden</bold></yellow>\n");
            sb.append("<gray>  <white>Schleichen + Rechtsklick\n");
            sb.append("<gray>  auf deinen Generator\n");
            sb.append("<gray>  oder <yellow>/gen upgrade\n");
            sb.append("<dark_gray>  ↳ Jedes Level = +10% Einkommen\n");
        } else if (!hasGen) {
            sb.append("<dark_gray>○ Schritt 2: Generator upgraden\n");
        } else {
            sb.append("<green>✔ <white>Schritt 2: <green>Upgrade durchgeführt!\n");
        }
        sb.append("<dark_gray>───────────────────────\n");

        // Schritt 3
        if (hasUpgraded && !hasThreeGen) {
            sb.append("<yellow><bold>➤ Schritt 3: Mehr Generatoren</bold></yellow>\n");
            sb.append("<gray>  Kaufe weitere Typen im Shop:\n");
            sb.append("<gray>  <yellow>/gen shop\n");
            sb.append("<dark_gray>  ↳ Iron, Gold, Lapis … bis Netherite\n");
        } else if (!hasUpgraded) {
            sb.append("<dark_gray>○ Schritt 3: Mehr Generatoren kaufen\n");
        } else {
            sb.append("<green>✔ <white>Schritt 3: <green>3+ Generatoren aktiv!\n");
        }
        sb.append("<dark_gray>───────────────────────\n");

        // Schritt 4
        if (hasThreeGen && !hasPrestige) {
            sb.append("<yellow><bold>➤ Schritt 4: Prestige machen</bold></yellow>\n");
            sb.append("<gray>  Kosten: <gold>$").append(MoneyManager.formatMoney(data.nextPrestigeCost())).append("\n");
            sb.append("<gray>  Befehl: <yellow>/gen prestige\n");
            sb.append("<dark_gray>  ↳ Höheres Max-Level + +5% Bonus\n");
        } else if (!hasThreeGen) {
            sb.append("<dark_gray>○ Schritt 4: Prestige (später)\n");
        } else {
            sb.append("<green>✔ <white>Schritt 4: <green>Prestige ").append(data.getPrestige()).append(" erreicht!\n");
        }

        sb.append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━\n");

        sb.append("<gray>💰 <green>$").append(MoneyManager.formatMoney(data.getMoney()));
        if (totalIncome > 0) {
            sb.append("  <gray>📈 <aqua>$")
              .append(MoneyManager.formatMoney(Math.round(totalIncome))).append("/s");
        }
        sb.append("\n");

        if (data.hasActiveBooster()) {
            long rem = data.getBoosterExpiry() - System.currentTimeMillis() / 1000;
            sb.append("<yellow>⚡ x").append(data.getBoosterMultiplier())
              .append(" <dark_gray>(").append(rem / 60).append("m ").append(rem % 60).append("s)\n");
        }
        if (plugin.getMoneyManager().isServerBoosterActive()) {
            long rem = plugin.getMoneyManager().getServerBoosterExpiry() - System.currentTimeMillis() / 1000;
            sb.append("<gold>⚡ Server x").append(plugin.getMoneyManager().getServerBoosterMultiplier())
              .append(" <dark_gray>(").append(rem / 60).append("m)\n");
        }

        sb.append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("<dark_gray>💡 <gray>/gen <dark_gray>→ Hauptmenü öffnen");

        return sb.toString();
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

        boolean isMax = data != null && gen.getLevel() >= data.maxGeneratorLevel();
        boolean hasTierUp = isMax && gen.getType().getNextTier() != null;

        String levelText = isMax
                ? "<gold><bold>" + gen.getLevel() + "</bold></gold>"
                : "<white>" + gen.getLevel();

        String statusLine;
        String incomeLine;
        if (!isMax) {
            statusLine = "\n<gray>⬆ Upgrade: <yellow>$" + MoneyManager.formatMoney(gen.upgradeCost());
            incomeLine = "\n<green>$" + MoneyManager.formatMoney(Math.round(effectiveIncome)) + "/s";
        } else if (hasTierUp) {
            statusLine = "\n<gold><bold>★ MAX LEVEL ★</bold></gold>"
                    + "\n<aqua>⬆ Tier: <yellow>$" + MoneyManager.formatMoney(gen.getType().getTierUpgradeCost());
            incomeLine = "\n<green><bold>$" + MoneyManager.formatMoney(Math.round(effectiveIncome)) + "/s</bold></green>";
        } else {
            statusLine = "\n<gold><bold>✦ ABSOLUTES MAXIMUM ✦</bold></gold>";
            incomeLine = "\n<green><bold>$" + MoneyManager.formatMoney(Math.round(effectiveIncome)) + "/s</bold></green>";
        }

        return megaPrefix + gen.getType().getDisplayName() + megaSuffix
                + "\n<gray>Level: " + levelText
                + statusLine
                + incomeLine;
    }
}
