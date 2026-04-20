package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpawnBorderManager {

    public record BorderPoint(double x, double z) {}

    private final PHSurvival plugin;
    private final File dataFile;
    private YamlConfiguration data;
    private final List<BorderPoint> points = new ArrayList<>();

    public SpawnBorderManager(PHSurvival plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "spawn_border.yml");
        load();
    }

    // ── API ──────────────────────────────────────────────────────────────

    public List<BorderPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public int addPoint(double x, double z) {
        points.add(new BorderPoint(x, z));
        save();
        return points.size(); // 1-basierter Index des neuen Punktes
    }

    public boolean removePoint(int index) { // 1-basiert
        if (index < 1 || index > points.size()) return false;
        points.remove(index - 1);
        save();
        return true;
    }

    public void clearPoints() {
        points.clear();
        save();
    }

    public boolean hasPolygon() {
        return points.size() >= 3;
    }

    // ── Point-in-Polygon (Ray-Casting) ────────────────────────────────────

    public boolean isInside(double x, double z) {
        if (!hasPolygon()) return false;
        int n = points.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = points.get(i).x(), zi = points.get(i).z();
            double xj = points.get(j).x(), zj = points.get(j).z();
            if (((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (zj - zi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Gibt den nächstgelegenen Punkt AUF dem Polygon-Rand zurück.
     * Wird genutzt um den Spieler sanft an die Grenze zu drücken.
     */
    public double[] nearestBoundaryPoint(double x, double z) {
        int n = points.size();
        double bestDist = Double.MAX_VALUE;
        double[] best = {x, z};
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = points.get(j).x(), az = points.get(j).z();
            double bx = points.get(i).x(), bz = points.get(i).z();
            double dx = bx - ax, dz = bz - az;
            double len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1,
                ((x - ax) * dx + (z - az) * dz) / len2));
            double px = ax + t * dx, pz = az + t * dz;
            double dist = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (dist < bestDist) { bestDist = dist; best = new double[]{px, pz}; }
        }
        return best;
    }

    /** Schwerpunkt des Polygons – genutzt um den Push-Vektor zu berechnen. */
    public double[] centroid() {
        double sx = 0, sz = 0;
        for (BorderPoint p : points) { sx += p.x(); sz += p.z(); }
        return new double[]{sx / points.size(), sz / points.size()};
    }

    // ── Persistenz ────────────────────────────────────────────────────────

    private void load() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        points.clear();
        List<?> raw = data.getList("points");
        if (raw == null) return;
        for (Object obj : raw) {
            if (!(obj instanceof java.util.Map<?, ?> map)) continue;
            try {
                double x = ((Number) map.get("x")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                points.add(new BorderPoint(x, z));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        for (BorderPoint p : points) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("x", p.x());
            map.put("z", p.z());
            list.add(map);
        }
        data.set("points", list);
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("spawn_border.yml konnte nicht gespeichert werden: " + e.getMessage()); }
    }
}
