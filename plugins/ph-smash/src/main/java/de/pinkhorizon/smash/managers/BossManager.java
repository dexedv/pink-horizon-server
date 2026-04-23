package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.boss.ActiveBoss;
import de.pinkhorizon.smash.boss.BossConfig;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

public class BossManager {

    private final PHSmash   plugin;
    private ActiveBoss      activeBoss;
    private BukkitTask      bossBarTask;

    public BossManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    public void spawnBoss(int level) {
        if (activeBoss != null) removeBoss();

        BossConfig cfg  = BossConfig.forLevel(level);
        Location   loc  = getBossSpawn();

        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.IRON_GOLEM);
        entity.customName(net.kyori.adventure.text.Component.text(cfg.displayName()));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setGlowing(true);
        entity.setSilent(false);

        // Hohe HP damit das Entity nie durch echten Schaden stirbt
        var hpAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(99999);
            entity.setHealth(99999);
        }

        BossBar bar = Bukkit.createBossBar(
            buildBarTitle(cfg, cfg.maxHp()), BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);

        activeBoss = new ActiveBoss(cfg, entity, bar);

        Bukkit.broadcastMessage("§c§l⚡ Boss §8[Lv. §c" + level + "§8] §7ist erschienen! HP: §c"
            + cfg.formatHp(cfg.maxHp()));

        startBossBarUpdater();
        plugin.getScoreboardManager().updateAll();
    }

    public void applyDamage(Player player, double rawDamage) {
        if (activeBoss == null) return;

        double multiplier = plugin.getUpgradeManager().getAttackMultiplier(player.getUniqueId());
        double real       = rawDamage * multiplier;
        activeBoss.applyDamage(player.getUniqueId(), real);

        // Lifesteal
        double lifesteal = plugin.getUpgradeManager().getLifestealPercent(player.getUniqueId());
        if (lifesteal > 0) {
            double heal = real * lifesteal;
            double newHp = Math.min(player.getHealth() + heal, player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setHealth(newHp);
        }

        if (activeBoss.isDead()) {
            onBossDefeated();
        } else {
            updateBossBar();
            plugin.getScoreboardManager().updateAll();
        }
    }

    private void onBossDefeated() {
        if (activeBoss == null) return;
        int level = activeBoss.getConfig().level();
        Map<UUID, Double> contrib = activeBoss.getDamageContrib();

        // Loot verteilen
        plugin.getLootManager().distributeLoot(activeBoss);

        // Stats updaten
        for (Map.Entry<UUID, Double> e : contrib.entrySet()) {
            plugin.getPlayerDataManager().addKillAndDamage(e.getKey(), e.getValue().longValue(), level);
        }

        // Boss-Level erhöhen
        int nextLevel = Math.min(level + 1, 999);
        plugin.getPlayerDataManager().setGlobalBossLevel(nextLevel);

        // Broadcast + Fireworks
        Bukkit.broadcastMessage("§a§l✔ §7Boss §8[Lv. §c" + level + "§8] §7wurde besiegt!");
        spawnFireworks(getBossSpawn());

        removeBoss();

        // Nächsten Boss nach 3 Sekunden spawnen
        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnBoss(nextLevel), 60L);
    }

    public void removeBoss() {
        if (activeBoss == null) return;
        if (bossBarTask != null) { bossBarTask.cancel(); bossBarTask = null; }
        activeBoss.getBossBar().removeAll();
        if (activeBoss.getEntity().isValid()) activeBoss.getEntity().remove();
        activeBoss = null;
    }

    public void addPlayerToBar(Player player) {
        if (activeBoss != null) activeBoss.getBossBar().addPlayer(player);
    }

    public ActiveBoss getActiveBoss() { return activeBoss; }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private void startBossBarUpdater() {
        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBossBar, 4L, 4L);
    }

    private void updateBossBar() {
        if (activeBoss == null) return;
        BossConfig cfg = activeBoss.getConfig();
        double pct     = activeBoss.getHpPercent();
        activeBoss.getBossBar().setProgress(Math.max(0.0, Math.min(1.0, pct)));
        activeBoss.getBossBar().setTitle(buildBarTitle(cfg, activeBoss.getCurrentHp()));
        activeBoss.getBossBar().setColor(pct < 0.25 ? BarColor.RED : pct < 0.5 ? BarColor.YELLOW : BarColor.GREEN);
    }

    private String buildBarTitle(BossConfig cfg, double hp) {
        return cfg.displayName() + " §8– §f" + cfg.formatHp(hp) + " §8/ §f" + cfg.formatHp(cfg.maxHp());
    }

    private Location getBossSpawn() {
        String world = plugin.getConfig().getString("arena.boss.world", "world");
        double x     = plugin.getConfig().getDouble("arena.boss.x", 0.5);
        double y     = plugin.getConfig().getDouble("arena.boss.y", 64);
        double z     = plugin.getConfig().getDouble("arena.boss.z", 10.5);
        World  w     = Bukkit.getWorld(world);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, x, y, z);
    }

    private void spawnFireworks(Location loc) {
        for (int i = 0; i < 3; i++) {
            final int delay = i * 10;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                var fw = (org.bukkit.entity.Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                var meta = fw.getFireworkMeta();
                meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .withColor(Color.RED, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .with(org.bukkit.FireworkEffect.Type.STAR)
                    .trail(true).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            }, delay);
        }
    }
}
