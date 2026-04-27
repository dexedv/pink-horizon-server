package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.managers.VoidFishingManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Ersetzt normale Fisch-Drops durch Void-Loot wenn unter Y=0 geangelt wird.
 */
public class VoidFishingListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    public VoidFishingListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        Player player = event.getPlayer();
        // Nur aktiv wenn der Angelhaken unter Y=0 war
        var hook = event.getHook();
        if (hook.getLocation().getY() >= 0) return;

        // Normalen Drop abbrechen und durch Void-Loot ersetzen
        event.setCancelled(true);

        ItemStack loot = plugin.getVoidFishingManager().rollLoot(player);

        // Partikel + Sound
        player.getWorld().spawnParticle(Particle.PORTAL,
            caughtItem.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);

        // Item direkt ins Inventar
        var leftover = player.getInventory().addItem(loot);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(i ->
                player.getWorld().dropItemNaturally(player.getLocation(), i));
        }

        // Meldung
        var itemName = loot.getItemMeta().displayName();
        player.sendMessage(MM.deserialize(
            "<dark_aqua>🎣 <gray>Void-Fund: ").append(itemName));

        // Bei Legendary: Server-Ankündigung
        var voidMgr = plugin.getVoidFishingManager();
        if (isLegendary(loot)) {
            String name = loot.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(loot.getItemMeta().displayName())
                : loot.getType().name();
            voidMgr.announceLegendary(player, name);
        }

        // Quest-Update: Fisch fangen zählt auch für Void
        plugin.getQuestManager().onFishCatch(player.getUniqueId());
    }

    private boolean isLegendary(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        var lore = item.getItemMeta().lore();
        if (lore == null || lore.size() < 2) return false;
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(lore.get(1));
        return plain.contains("LEGENDARY");
    }
}
