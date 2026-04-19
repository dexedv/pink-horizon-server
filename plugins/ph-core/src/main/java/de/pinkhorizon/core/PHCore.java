package de.pinkhorizon.core;

import de.pinkhorizon.core.commands.HubCommand;
import de.pinkhorizon.core.commands.MsgCommand;
import de.pinkhorizon.core.commands.ReportCommand;
import de.pinkhorizon.core.database.DatabaseManager;
import de.pinkhorizon.core.listeners.ChatListener;
import de.pinkhorizon.core.listeners.JoinQuitListener;
import org.bukkit.plugin.java.JavaPlugin;

public class PHCore extends JavaPlugin {

    private static PHCore instance;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // Datenbank initialisieren
        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        // Commands registrieren
        getCommand("hub").setExecutor(new HubCommand(this));
        getCommand("msg").setExecutor(new MsgCommand(this));
        getCommand("report").setExecutor(new ReportCommand(this));

        // Listener registrieren
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("PH-Core gestartet!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("PH-Core gestoppt.");
    }

    public static PHCore getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
