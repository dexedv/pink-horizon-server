package de.pinkhorizon.smash.arena;

import de.pinkhorizon.smash.boss.BossConfig;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class ArenaInstance {

    private final UUID       playerUuid;
    private final String     worldName;
    private World            world;
    private Entity           bossEntity;
    private BossBar          bossBar;
    private BossConfig       config;
    private double           currentHp;
    private double           sessionDamage;
    private BukkitTask       targetTask;

    public ArenaInstance(UUID playerUuid, String worldName, int bossLevel) {
        this.playerUuid    = playerUuid;
        this.worldName     = worldName;
        this.config        = BossConfig.forLevel(bossLevel);
        this.currentHp     = config.maxHp();
        this.sessionDamage = 0;
    }

    public void applyDamage(double dmg) {
        currentHp     = Math.max(0, currentHp - dmg);
        sessionDamage += dmg;
    }

    public boolean isDead() {
        return currentHp <= 0;
    }

    public double getHpPercent() {
        return Math.max(0, Math.min(1, currentHp / config.maxHp()));
    }

    /** Welt bleibt, nur Boss und Zustand werden für nächstes Level resettet */
    public void resetForNextBoss(int newLevel) {
        if (targetTask != null) { targetTask.cancel(); targetTask = null; }
        config        = BossConfig.forLevel(newLevel);
        currentHp     = config.maxHp();
        sessionDamage = 0;
        bossEntity    = null;
    }

    // ── Getter / Setter ────────────────────────────────────────────────────

    public UUID       getPlayerUuid()              { return playerUuid; }
    public String     getWorldName()               { return worldName; }
    public World      getWorld()                   { return world; }
    public void       setWorld(World w)            { this.world = w; }
    public Entity     getBossEntity()              { return bossEntity; }
    public void       setBossEntity(Entity e)      { this.bossEntity = e; }
    public BossBar    getBossBar()                 { return bossBar; }
    public void       setBossBar(BossBar b)        { this.bossBar = b; }
    public BossConfig getConfig()                  { return config; }
    public double     getCurrentHp()               { return currentHp; }
    public double     getSessionDamage()           { return sessionDamage; }
    public int        getBossLevel()               { return config.level(); }
    public BukkitTask getTargetTask()              { return targetTask; }
    public void       setTargetTask(BukkitTask t)  { this.targetTask = t; }
}
