package de.pinkhorizon.core.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Statische Utility-Klasse für die LuckPerms-Integration.
 * Synchronisiert PH-Ränge mit LuckPerms-Gruppen.
 */
public final class LuckPermsHook {

    private static LuckPerms api;
    private static final Logger LOG = Logger.getLogger("PH-Core");

    private LuckPermsHook() {}

    /**
     * Initialisierung – muss in PHCore.onEnable() aufgerufen werden.
     * Setzt die API-Referenz falls LuckPerms verfügbar ist.
     */
    public static void init() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            LOG.info("[PH] LuckPerms nicht gefunden – Rang-Sync deaktiviert.");
            return;
        }
        try {
            api = LuckPermsProvider.get();
            LOG.info("[PH] LuckPerms-Integration aktiviert.");
        } catch (IllegalStateException e) {
            LOG.warning("[PH] LuckPerms-Initialisierung fehlgeschlagen: " + e.getMessage());
        }
    }

    /** @return true wenn LuckPerms verfügbar und initialisiert ist */
    public static boolean isAvailable() {
        return api != null;
    }

    /**
     * Setzt den Spieler in die zur PH-Rang-ID passende LuckPerms-Gruppe.
     * Alle bisherigen Gruppen-Zuordnungen werden vorher entfernt.
     * Läuft asynchron.
     *
     * <p>Rang-Mapping:
     * <ul>
     *   <li>owner   → LP-Gruppe "owner"</li>
     *   <li>admin   → LP-Gruppe "admin"</li>
     *   <li>mod     → LP-Gruppe "mod"</li>
     *   <li>mvp     → LP-Gruppe "mvp"</li>
     *   <li>vip     → LP-Gruppe "vip"</li>
     *   <li>spieler → LP-Gruppe "default"</li>
     * </ul>
     *
     * @param uuid   UUID des Spielers
     * @param rankId PH-Rang-ID
     */
    public static void setGroup(UUID uuid, String rankId) {
        if (api == null) return;

        String groupName = rankId.equals("spieler") ? "default" : rankId;
        UserManager um = api.getUserManager();

        um.loadUser(uuid).thenAcceptAsync(user -> {
            if (user == null) return;

            // Alle bestehenden Gruppen-Zuordnungen entfernen
            user.data().clear(NodeType.INHERITANCE::matches);

            // Neue Gruppe setzen ("default" verwaltet LP selbst)
            if (!groupName.equals("default")) {
                user.data().add(InheritanceNode.builder(groupName).build());
            }

            um.saveUser(user);
        });
    }
}
