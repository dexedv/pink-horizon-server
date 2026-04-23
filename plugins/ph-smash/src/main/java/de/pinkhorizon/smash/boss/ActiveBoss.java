package de.pinkhorizon.smash.boss;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActiveBoss {

    private final BossConfig config;
    private double currentHp;
    private final Entity entity;
    private final BossBar bossBar;
    private final Map<UUID, Double> damageContrib = new HashMap<>();

    public ActiveBoss(BossConfig config, Entity entity, BossBar bossBar) {
        this.config    = config;
        this.currentHp = config.maxHp();
        this.entity    = entity;
        this.bossBar   = bossBar;
    }

    public void applyDamage(UUID playerId, double amount) {
        currentHp = Math.max(0, currentHp - amount);
        damageContrib.merge(playerId, amount, Double::sum);
    }

    public boolean isDead() {
        return currentHp <= 0;
    }

    public double getHpPercent() {
        return currentHp / config.maxHp();
    }

    public BossConfig getConfig()                  { return config; }
    public double     getCurrentHp()               { return currentHp; }
    public Entity     getEntity()                  { return entity; }
    public BossBar    getBossBar()                 { return bossBar; }
    public Map<UUID, Double> getDamageContrib()    { return damageContrib; }
}
