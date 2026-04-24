package de.pinkhorizon.smash.commands;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.hologram.HologramManager.HologramType;
import de.pinkhorizon.smash.managers.AbilityManager.AbilityType;
import de.pinkhorizon.smash.managers.ForgeManager.ForgeEnchant;
import de.pinkhorizon.smash.managers.TalentManager.TalentType;
import de.pinkhorizon.smash.managers.UpgradeManager.UpgradeType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class SmashCommand implements CommandExecutor, TabCompleter {

    private final PHSmash plugin;

    public SmashCommand(PHSmash plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c§lSmash the Boss §8– §7/stb <join|leave|upgrades|stats|setarena|forceboss>");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "join" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                if (plugin.getArenaManager().hasArena(p.getUniqueId())) {
                    p.sendMessage("§eDu bist bereits in einer Arena! §7(/stb leave zum Verlassen)");
                    return true;
                }
                plugin.getArenaManager().createArena(p);
            }

            case "leave" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                if (!plugin.getArenaManager().hasArena(p.getUniqueId())) {
                    p.sendMessage("§eDu bist in keiner Arena!");
                    return true;
                }
                plugin.getArenaManager().destroyArena(p.getUniqueId());
            }

            case "upgrades", "upgrade" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                plugin.getUpgradeGui().open(p);
            }

            case "abilities", "ability" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                plugin.getAbilityGui().open(p);
            }

            case "coins" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                long coins = plugin.getCoinManager().getCoins(p.getUniqueId());
                p.sendMessage("§e✦ §7Deine Münzen: §6§l" + coins);
            }

            case "prestige" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                plugin.getPrestigeManager().doPrestige(p);
            }

            case "stats" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                int  kills        = plugin.getPlayerDataManager().getKills(p.getUniqueId());
                long damage       = plugin.getPlayerDataManager().getTotalDamage(p.getUniqueId());
                int  personalLvl  = plugin.getPlayerDataManager().getPersonalBossLevel(p.getUniqueId());
                boolean inArena   = plugin.getArenaManager().hasArena(p.getUniqueId());
                p.sendMessage("§c§l⚔ Deine Stats:");
                p.sendMessage("§7  Boss-Level:     §c" + personalLvl + (inArena ? " §8(aktiv)" : ""));
                p.sendMessage("§7  Boss-Kills:     §a" + kills);
                p.sendMessage("§7  Gesamt-Schaden: §e" + damage);
            }

            case "setarena" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                if (args.length < 2) { p.sendMessage("§c/stb setarena <spawn|boss>"); return true; }

                var loc = p.getLocation();
                String key = args[1].equalsIgnoreCase("boss") ? "arena.boss" : "arena.spawn";
                plugin.getConfig().set(key + ".world", loc.getWorld().getName());
                plugin.getConfig().set(key + ".x",     loc.getX());
                plugin.getConfig().set(key + ".y",     loc.getY());
                plugin.getConfig().set(key + ".z",     loc.getZ());
                plugin.getConfig().set(key + ".yaw",   (double) loc.getYaw());
                plugin.getConfig().set(key + ".pitch", (double) loc.getPitch());
                plugin.saveConfig();
                p.sendMessage("§a✔ §f" + key + " §7→ §f"
                    + String.format("%.1f / %.1f / %.1f", loc.getX(), loc.getY(), loc.getZ()));
            }

            case "sethub" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                var loc = p.getLocation();
                plugin.getConfig().set("hub.spawn.x",     loc.getX());
                plugin.getConfig().set("hub.spawn.y",     loc.getY());
                plugin.getConfig().set("hub.spawn.z",     loc.getZ());
                plugin.getConfig().set("hub.spawn.yaw",   (double) loc.getYaw());
                plugin.getConfig().set("hub.spawn.pitch", (double) loc.getPitch());
                plugin.saveConfig();
                p.sendMessage("§a✔ §7Hub-Spawn gesetzt: §f"
                    + String.format("%.1f / %.1f / %.1f", loc.getX(), loc.getY(), loc.getZ()));
            }

            case "forceboss" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                int level = 1;
                if (args.length >= 2) {
                    try { level = Math.min(999, Math.max(1, Integer.parseInt(args[1]))); }
                    catch (NumberFormatException ignored) {}
                }
                plugin.getArenaManager().forceBoss(p, level);
            }

            case "setlevel" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                if (args.length < 2) { p.sendMessage("§c/stb setlevel <1-999>"); return true; }
                int lvl;
                try { lvl = Math.min(999, Math.max(1, Integer.parseInt(args[1]))); }
                catch (NumberFormatException e) { p.sendMessage("§cUngültige Zahl!"); return true; }
                plugin.getPlayerDataManager().setPersonalBossLevel(p.getUniqueId(), lvl);
                p.sendMessage("§a✔ §7Dein Boss-Level wurde auf §c" + lvl + " §7gesetzt.");
            }

            case "setnpc" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                java.util.Set<String> npcKeys = java.util.Set.of("leveldown", "upgrade", "join", "leave");
                if (args.length < 2 || !npcKeys.contains(args[1].toLowerCase())) {
                    p.sendMessage("§c/stb setnpc <leveldown|upgrade|join|leave>"); return true;
                }
                plugin.getNpcManager().setNpc(p, args[1].toLowerCase());
            }

            case "sethologram", "setholo" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                if (args.length < 2) { p.sendMessage("§c/stb sethologram <kills|level|damage|coins|prestige|weekly|commands>"); return true; }
                HologramType type = HologramType.fromKey(args[1]);
                if (type == null) { p.sendMessage("§cUnbekannter Typ. Wähle: kills, level, damage, coins, prestige, weekly, commands"); return true; }
                plugin.getHologramManager().setHologram(p, type);
            }

            case "forge" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                plugin.getForgeGui().open(p);
            }

            case "bestiary" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                plugin.getBestiaryGui().open(p);
            }

            case "weekly" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                var lb = plugin.getWeeklyManager().getTopKills(10);
                p.sendMessage("§e§l⏳ §6Wöchentliches Turnier – §7Woche vom §e" + plugin.getWeeklyManager().getWeekStart());
                if (lb.isEmpty()) { p.sendMessage("§7Noch keine Einträge diese Woche."); return true; }
                String[] medals = {"§6✦", "§7✦", "§8✦"};
                for (int i = 0; i < lb.size(); i++) {
                    var entry = lb.get(i);
                    String medal = i < medals.length ? medals[i] : "  ";
                    p.sendMessage(medal + " §f#" + (i+1) + " " + entry.getKey() + " §8– §c" + entry.getValue() + " §7Kills");
                }
            }

            case "bounty" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                int bountyLevel = plugin.getBountyManager().getTodaysBountyLevel();
                boolean claimed = plugin.getBountyManager().hasClaimed(p.getUniqueId());
                p.sendMessage("§e§l✦ §6Tages-Kopfgeld §8– §7Boss Level §c" + bountyLevel);
                p.sendMessage(claimed
                    ? "§a✔ §7Bereits eingelöst heute."
                    : "§7Besiege Boss Level §c" + bountyLevel + " §7für §6+1000 Münzen §7& §d3x Boss-Kern§7!");
            }

            case "setupholos" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                double lateralDist = 3.0;
                if (args.length >= 2) {
                    try { lateralDist = Double.parseDouble(args[1]); }
                    catch (NumberFormatException e) { p.sendMessage("§cUngültiger Abstand. Beispiel: /stb setupholos 3"); return true; }
                }
                plugin.getHologramManager().setupHolosAroundCenter(p, lateralDist);
            }

            case "debugspeed" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                var speedAttr = p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                if (speedAttr == null) { sender.sendMessage("Kein Speed-Attribut gefunden!"); return true; }
                int speedLvl = plugin.getUpgradeManager().getLevel(p.getUniqueId(),
                    de.pinkhorizon.smash.managers.UpgradeManager.UpgradeType.SPEED);
                StringBuilder sb = new StringBuilder();
                sb.append("\n[debugspeed] Player=").append(p.getName())
                  .append(" base=").append(speedAttr.getBaseValue())
                  .append(" default=").append(speedAttr.getDefaultValue())
                  .append(" final=").append(speedAttr.getValue())
                  .append(" dbLevel=").append(speedLvl)
                  .append(" modifiers(").append(speedAttr.getModifiers().size()).append("):");
                for (var m : speedAttr.getModifiers()) {
                    sb.append("\n  key=").append(m.getKey())
                      .append(" op=").append(m.getOperation())
                      .append(" val=").append(m.getAmount());
                }
                plugin.getLogger().info(sb.toString());
                sender.sendMessage("§aDebug-Info in den Server-Log geschrieben (docker compose logs smash).");
            }

            case "maxout" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) { sender.sendMessage("§cSpieler §f" + args[1] + " §cnicht online!"); return true; }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("§c/stb maxout [Spieler]"); return true;
                }
                maxOutPlayer(target);
                sender.sendMessage("§a✔ §f" + target.getName() + " §7wurde auf §6MAX §7gesetzt.");
                if (!target.equals(sender))
                    target.sendMessage("§a✔ §7Admin hat alle deine Stats auf §6MAX §7gesetzt!");
            }

            default -> sender.sendMessage("§c§lSmash the Boss §8– §7/stb <join|leave|upgrades|stats|setarena|setnpc|sethologram|setupholos|forceboss|maxout>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new java.util.ArrayList<>(List.of("join", "leave", "upgrades", "abilities", "coins", "prestige", "stats", "forge", "bestiary", "weekly", "bounty"));
            if (sender.hasPermission("smash.admin")) { base.add("setarena"); base.add("sethub"); base.add("setnpc"); base.add("sethologram"); base.add("setupholos"); base.add("forceboss"); base.add("setlevel"); base.add("maxout"); base.add("debugspeed"); }
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setarena"))
            return List.of("spawn", "boss").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("setnpc"))
            return List.of("leveldown", "upgrade", "join", "leave").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("sethologram") || args[0].equalsIgnoreCase("setholo")))
            return List.of("kills", "level", "damage", "coins", "prestige", "weekly", "commands").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("maxout"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }

    // ── Hilfsmethode: alles auf Maximum setzen ────────────────────────────────

    private void maxOutPlayer(Player target) {
        java.util.UUID uuid = target.getUniqueId();
        String uuidStr = uuid.toString();

        try (Connection c = plugin.getDb().getConnection()) {

            // ── Upgrades ──────────────────────────────────────────────────────
            for (UpgradeType u : UpgradeType.values()) {
                try (PreparedStatement st = c.prepareStatement(
                        "INSERT INTO smash_upgrades (uuid, upgrade_id, level) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE level = VALUES(level)")) {
                    st.setString(1, uuidStr);
                    st.setString(2, u.id);
                    st.setInt(3, u.maxLevel);
                    st.executeUpdate();
                }
            }

            // ── Abilities ─────────────────────────────────────────────────────
            for (AbilityType a : AbilityType.values()) {
                try (PreparedStatement st = c.prepareStatement(
                        "INSERT INTO smash_abilities (uuid, ability_id, level) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE level = VALUES(level)")) {
                    st.setString(1, uuidStr);
                    st.setString(2, a.name());
                    st.setInt(3, a.maxLevel);
                    st.executeUpdate();
                }
            }

            // ── Talente ───────────────────────────────────────────────────────
            for (TalentType t : TalentType.values()) {
                try (PreparedStatement st = c.prepareStatement(
                        "INSERT INTO smash_talents (uuid, talent_id, level) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE level = VALUES(level)")) {
                    st.setString(1, uuidStr);
                    st.setString(2, t.id);
                    st.setInt(3, t.maxLevel);
                    st.executeUpdate();
                }
            }

            // ── Forge-Ladungen (999 pro Verzauberung) ─────────────────────────
            for (ForgeEnchant f : ForgeEnchant.values()) {
                try (PreparedStatement st = c.prepareStatement(
                        "INSERT INTO smash_forge (uuid, forge_id, charges) VALUES (?, ?, 999) " +
                        "ON DUPLICATE KEY UPDATE charges = 999")) {
                    st.setString(1, uuidStr);
                    st.setString(2, f.id);
                    st.executeUpdate();
                }
            }

            // ── Coins: auf 10 Mio setzen ──────────────────────────────────────
            try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO smash_coins (uuid, coins) VALUES (?, 10000000) " +
                    "ON DUPLICATE KEY UPDATE coins = GREATEST(coins, 10000000)")) {
                st.setString(1, uuidStr);
                st.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("maxOutPlayer: " + e.getMessage());
        }

        // Boss-Level auf 999
        plugin.getPlayerDataManager().setPersonalBossLevel(uuid, 999);

        // Upgrade-Stats (HP, Speed) sofort auf den Spieler anwenden
        plugin.getUpgradeManager().applyStats(target);
    }
}
