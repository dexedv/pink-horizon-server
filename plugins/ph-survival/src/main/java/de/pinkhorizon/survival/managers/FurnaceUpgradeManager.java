package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Verwaltet Ofen-Upgrades per PersistentDataContainer (PDC).
 *
 * Das Level wird direkt im Block-State (TileEntity) und im Item gespeichert,
 * sodass ein abgebauter Ofen sein Upgrade behält und woanders wieder platziert
 * werden kann.
 */
public class FurnaceUpgradeManager {

    public static final int MAX_LEVEL = 5;

    // Schmelz-Ticks pro Level (200 = Standard = 10 Sekunden)
    private static final int[] COOK_TICKS = { 0, 200, 150, 100, 60, 30 };
    // Upgrade-Kosten in Coins (Index = Ziel-Level)
    public  static final long[] COSTS     = { 0, 0, 500, 1_500, 4_000, 10_000 };
    // Anzeigenamen
    public  static final String[] NAMES   = { "", "Normal", "Verbessert", "Schnell", "Blitz", "Quantumfusion" };
    // Prozentuale Beschleunigung gegenüber Level 1
    public  static final int[] SPEED_PCT  = { 0, 0, 25, 50, 70, 85 };

    private final PHSurvival plugin;
    private final NamespacedKey levelKey;

    public FurnaceUpgradeManager(PHSurvival plugin) {
        this.plugin   = plugin;
        this.levelKey = new NamespacedKey(plugin, "furnace_level");
    }

    public NamespacedKey getLevelKey() { return levelKey; }

    // ── Block-State PDC ───────────────────────────────────────────────────

    /** Gibt das Upgrade-Level des Ofens zurück (1 = kein Upgrade). */
    public int getLevel(Block block) {
        if (!(block.getState() instanceof Furnace furnace)) return 1;
        return furnace.getPersistentDataContainer()
                      .getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    /** Gibt die Schmelz-Ticks für den Ofen zurück. */
    public int getCookTicks(Block block) {
        return COOK_TICKS[Math.min(getLevel(block), MAX_LEVEL)];
    }

    /** Setzt das Level direkt in den Block-State. */
    public void setLevel(Block block, int level) {
        if (!(block.getState() instanceof Furnace furnace)) return;
        if (level <= 1) {
            furnace.getPersistentDataContainer().remove(levelKey);
        } else {
            furnace.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        }
        furnace.update();
    }

    // ── Item PDC ─────────────────────────────────────────────────────────

    /** Liest das Level aus einem Ofen-Item (z.B. frisch aufgehoben). */
    public int getLevelFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        return item.getItemMeta()
                   .getPersistentDataContainer()
                   .getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * Schreibt das Level in das PDC eines Items und setzt eine
     * visuelle Anzeige (Name + Lore), damit Spieler den Upgrade-Stand sehen.
     */
    public void applyLevelToItem(ItemStack item, int level) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (level <= 1) {
            meta.getPersistentDataContainer().remove(levelKey);
            meta.displayName(null);
            meta.lore(null);
        } else {
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
            meta.displayName(Component.text(
                "⚒ " + friendlyName(item.getType()) + " §7(Lv. " + level + " – " + NAMES[level] + ")",
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Schmelzgeschwindigkeit: +" + SPEED_PCT[level] + "%", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Shift + Rechtsklick zum Verwalten", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        }
        item.setItemMeta(meta);
    }

    // ── Upgrade ───────────────────────────────────────────────────────────

    /** Zieht Coins ab und erhöht das Level. Gibt true bei Erfolg zurück. */
    public boolean tryUpgrade(Block block, org.bukkit.entity.Player player) {
        int current = getLevel(block);
        if (current >= MAX_LEVEL) return false;
        int next = current + 1;
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), COSTS[next])) return false;
        setLevel(block, next);
        return true;
    }

    // ── Hilfsmethode ─────────────────────────────────────────────────────

    private static String friendlyName(Material mat) {
        return switch (mat) {
            case FURNACE       -> "Ofen";
            case BLAST_FURNACE -> "Hochofen";
            case SMOKER        -> "Räucherofen";
            default            -> mat.name();
        };
    }
}
