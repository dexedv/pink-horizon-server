package de.pinkhorizon.skyblock.data;

import de.pinkhorizon.skyblock.enums.GeneratorTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Generator {

    public static final int MAX_BUFFER = 500;        // Max. Items im Puffer
    public static final int MAX_LEVEL  = 50;         // Max. Generator-Level

    private final int id;
    private final UUID ownerUuid;
    private final String world;
    private final int x, y, z;
    private int level;
    private boolean autoSell;
    private long totalProduced;
    private long lastTickMillis;

    /** Gepufferter Loot (bis MAX_BUFFER Items). */
    private final List<ItemStack> buffer = new ArrayList<>();

    public Generator(int id, UUID ownerUuid, String world, int x, int y, int z,
                     int level, boolean autoSell, long totalProduced) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.world = world;
        this.x = x; this.y = y; this.z = z;
        this.level = level;
        this.autoSell = autoSell;
        this.totalProduced = totalProduced;
        this.lastTickMillis = System.currentTimeMillis();
    }

    // ── Produzieren ────────────────────────────────────────────────────────────

    /**
     * Prüft ob es Zeit ist zu produzieren und gibt ein Item zurück.
     * Gibt null zurück wenn noch nicht genug Zeit vergangen ist oder Puffer voll.
     */
    public ItemStack tryProduce() {
        long now = System.currentTimeMillis();
        long intervalMs = (long) GeneratorTier.tickInterval(level) * 50L; // Ticks → ms
        if (now - lastTickMillis < intervalMs) return null;
        if (buffer.size() >= MAX_BUFFER) return null;

        lastTickMillis = now;
        GeneratorTier tier = GeneratorTier.rollLoot(level);
        ItemStack item = new ItemStack(tier.getMaterial(), 1);
        buffer.add(item);
        totalProduced++;
        return item;
    }

    /** Nimmt alle Buffer-Items heraus. */
    public List<ItemStack> collectAll() {
        List<ItemStack> copy = new ArrayList<>(buffer);
        buffer.clear();
        return copy;
    }

    /** Berechnet den Auto-Sell-Wert aller gepufferten Items. */
    public long calcAutoSellCoins() {
        long total = 0;
        for (ItemStack item : buffer) {
            GeneratorTier tier = tierFromMaterial(item.getType());
            if (tier != null) total += tier.getCoinValue() * item.getAmount();
        }
        return total;
    }

    private GeneratorTier tierFromMaterial(org.bukkit.Material mat) {
        for (GeneratorTier t : GeneratorTier.values()) {
            if (t.getMaterial() == mat) return t;
        }
        return null;
    }

    // ── Upgrade ────────────────────────────────────────────────────────────────

    public boolean canUpgrade() {
        return level < MAX_LEVEL;
    }

    public long getUpgradeCost() {
        return GeneratorTier.upgradeCost(level);
    }

    public void upgrade() {
        if (canUpgrade()) level++;
    }

    // ── Location ───────────────────────────────────────────────────────────────

    public Location getLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }

    public String getPosKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    // ── Hologramm-Text ─────────────────────────────────────────────────────────

    public String getHologramLine1() {
        return "§6⚙ §e§lGenerator §7(Stufe §6" + level + "§7)";
    }

    public String getHologramLine2() {
        GeneratorTier top = topTier();
        long ms = (long) GeneratorTier.tickInterval(level) * 50L;
        String rate = ms >= 1000 ? (ms / 1000) + "s" : ms + "ms";
        return top.getDisplayName() + " §7alle §f" + rate;
    }

    public String getHologramLine3() {
        return autoSell
            ? "§a[Auto-Sell: AN]"
            : "§7[Puffer: §f" + buffer.size() + "§7/" + MAX_BUFFER + "]";
    }

    private GeneratorTier topTier() {
        for (int i = GeneratorTier.values().length - 1; i >= 0; i--) {
            if (level >= GeneratorTier.values()[i].getUnlockLevel())
                return GeneratorTier.values()[i];
        }
        return GeneratorTier.COBBLESTONE;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public int getId()             { return id; }
    public UUID getOwnerUuid()     { return ownerUuid; }
    public String getWorld()       { return world; }
    public int getX()              { return x; }
    public int getY()              { return y; }
    public int getZ()              { return z; }
    public int getLevel()          { return level; }
    public boolean isAutoSell()    { return autoSell; }
    public long getTotalProduced() { return totalProduced; }
    public List<ItemStack> getBuffer() { return buffer; }
    public long getLastTickMillis(){ return lastTickMillis; }

    public void setLevel(int v)        { this.level = v; }
    public void setAutoSell(boolean v) { this.autoSell = v; }
    public void setTotalProduced(long v){ this.totalProduced = v; }
    public void setLastTickMillis(long v){ this.lastTickMillis = v; }
}
