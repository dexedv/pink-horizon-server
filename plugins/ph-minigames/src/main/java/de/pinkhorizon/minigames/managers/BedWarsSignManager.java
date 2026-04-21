package de.pinkhorizon.minigames.managers;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.bedwars.BedWarsGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedWarsSignManager {

    private final PHMinigames plugin;
    private final List<SignEntry> signs = new ArrayList<>();
    private BukkitTask updateTask;
    private final File signsFile;

    public record SignEntry(String world, int x, int y, int z, String arenaName) {
        public boolean matches(Location loc) {
            return loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z
                    && loc.getWorld() != null && loc.getWorld().getName().equals(world);
        }
    }

    public BedWarsSignManager(PHMinigames plugin) {
        this.plugin = plugin;
        this.signsFile = new File(plugin.getDataFolder(), "signs.yml");
        load();
    }

    public void start() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 40L);
    }

    public void stop() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    }

    public void addSign(Location loc, String arenaName) {
        signs.removeIf(e -> e.matches(loc));
        signs.add(new SignEntry(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), arenaName));
        save();
        updateSign(loc, arenaName);
    }

    public boolean removeSign(Location loc) {
        boolean removed = signs.removeIf(e -> e.matches(loc));
        if (removed) save();
        return removed;
    }

    public String getArenaForSign(Location loc) {
        return signs.stream().filter(e -> e.matches(loc)).map(SignEntry::arenaName).findFirst().orElse(null);
    }

    public boolean isSign(Location loc) {
        return signs.stream().anyMatch(e -> e.matches(loc));
    }

    public void updateAll() {
        for (SignEntry entry : signs) {
            World world = Bukkit.getWorld(entry.world());
            if (world == null) continue;
            updateSign(new Location(world, entry.x(), entry.y(), entry.z()), entry.arenaName());
        }
    }

    private void updateSign(Location loc, String arenaName) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        // Bestes Spiel für diese Arena ermitteln: joinbares zuerst, dann laufendes
        BedWarsGame displayGame = null;
        for (BedWarsGame game : plugin.getArenaManager().getActiveGames()) {
            if (!game.getArena().name.equals(arenaName)) continue;
            if (displayGame == null) displayGame = game;
            if (game.isJoinable()) { displayGame = game; break; }
        }

        Component line0 = Component.text("[BedWars]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
        Component line1 = Component.text(arenaName.isEmpty() ? "Beliebig" : arenaName, NamedTextColor.WHITE);
        Component line2;
        Component line3;

        if (displayGame == null) {
            line2 = Component.text("● Wartend", NamedTextColor.GREEN);
            line3 = Component.text("0/? Spieler", NamedTextColor.GRAY);
        } else {
            int cur = displayGame.getAllPlayers().size();
            int max = displayGame.getArena().maxTeams * displayGame.getArena().teamSize;
            line3 = Component.text(cur + "/" + max + " Spieler", NamedTextColor.GRAY);
            line2 = switch (displayGame.getState()) {
                case WAITING  -> Component.text("● Wartend",   NamedTextColor.GREEN);
                case STARTING -> Component.text("● Startet...", NamedTextColor.YELLOW);
                case RUNNING  -> Component.text("● Läuft",     NamedTextColor.RED);
                case ENDED    -> Component.text("● Beendet",   NamedTextColor.GRAY);
            };
        }

        sign.getSide(Side.FRONT).line(0, line0);
        sign.getSide(Side.FRONT).line(1, line1);
        sign.getSide(Side.FRONT).line(2, line2);
        sign.getSide(Side.FRONT).line(3, line3);
        sign.update();
    }

    private void load() {
        signs.clear();
        if (!signsFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(signsFile);
        for (Map<?, ?> map : cfg.getMapList("signs")) {
            try {
                signs.add(new SignEntry(
                        (String)  map.get("world"),
                        ((Number) map.get("x")).intValue(),
                        ((Number) map.get("y")).intValue(),
                        ((Number) map.get("z")).intValue(),
                        (String)  map.get("arena")
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Ungültiger Schilder-Eintrag übersprungen: " + e.getMessage());
            }
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (SignEntry e : signs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", e.world());
            map.put("x", e.x());
            map.put("y", e.y());
            map.put("z", e.z());
            map.put("arena", e.arenaName());
            list.add(map);
        }
        cfg.set("signs", list);
        try { cfg.save(signsFile); }
        catch (IOException ex) { plugin.getLogger().warning("Fehler beim Speichern der Schilder: " + ex.getMessage()); }
    }
}
