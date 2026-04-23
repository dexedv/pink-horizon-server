package de.pinkhorizon.smash.arena;

import de.pinkhorizon.smash.boss.BossConfig;
import de.pinkhorizon.smash.managers.BossModifierManager;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ArenaInstance {

    private final UUID         playerUuid;
    private final String       worldName;
    private World              world;
    private Entity             bossEntity;
    private BossBar            bossBar;
    private BossConfig         config;
    private double             currentHp;
    private double             sessionDamage;
    private BukkitTask         targetTask;
    // Boss phase tracking
    private final Set<Double>  triggeredPhases = new HashSet<>();
    private boolean            rageActive      = false;
    private long               rageEndTime     = 0;
    // Boss modifiers (v3)
    private Set<BossModifierManager.BossModifier> modifiers = EnumSet.noneOf(BossModifierManager.BossModifier.class);
    private BukkitTask         regenTask         = null;
    private BukkitTask         explosivTask      = null;
    // Warte-Spawn (Spieler muss manuell starten)
    private boolean            bossReadyToSpawn  = false;
    private int                nextBossLevel     = 1;

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

    public void heal(double amount) {
        currentHp = Math.min(currentHp + amount, config.maxHp());
    }

    public boolean isDead() {
        return currentHp <= 0;
    }

    public double getHpPercent() {
        return Math.max(0, Math.min(1, currentHp / config.maxHp()));
    }

    /** Welt bleibt, nur Boss und Zustand werden für nächstes Level resettet */
    public void resetForNextBoss(int newLevel) {
        if (targetTask   != null) { targetTask.cancel();   targetTask   = null; }
        if (regenTask    != null) { regenTask.cancel();    regenTask    = null; }
        if (explosivTask != null) { explosivTask.cancel(); explosivTask = null; }
        config           = BossConfig.forLevel(newLevel);
        currentHp        = config.maxHp();
        sessionDamage    = 0;
        bossEntity       = null;
        triggeredPhases.clear();
        rageActive       = false;
        rageEndTime      = 0;
        modifiers        = EnumSet.noneOf(BossModifierManager.BossModifier.class);
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
    // Phase tracking
    public boolean    isPhaseTriggered(double t)   { return triggeredPhases.contains(t); }
    public void       triggerPhase(double t)       { triggeredPhases.add(t); }
    public boolean    isRageActive()               { return rageActive && System.currentTimeMillis() < rageEndTime; }
    public void       activateRage(long durationMs){ rageActive = true; rageEndTime = System.currentTimeMillis() + durationMs; }
    // Boss modifiers (v3)
    public Set<BossModifierManager.BossModifier> getModifiers()                                   { return modifiers; }
    public void setModifiers(Set<BossModifierManager.BossModifier> m)                             { modifiers = m; }
    public BukkitTask getRegenTask()                                                               { return regenTask; }
    public void       setRegenTask(BukkitTask t)                                                   { this.regenTask = t; }
    public BukkitTask getExplosivTask()                                                            { return explosivTask; }
    public void       setExplosivTask(BukkitTask t)                                                { this.explosivTask = t; }
    // Warte-Spawn
    public boolean    isBossReadyToSpawn()                                                         { return bossReadyToSpawn; }
    public void       setBossReadyToSpawn(boolean b)                                               { this.bossReadyToSpawn = b; }
    public int        getNextBossLevel()                                                            { return nextBossLevel; }
    public void       setNextBossLevel(int l)                                                       { this.nextBossLevel = l; }
}
