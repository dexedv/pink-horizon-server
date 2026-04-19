package de.pinkhorizon.core.database;

import de.pinkhorizon.core.PHCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final PHCore plugin;
    private Connection connection;

    public DatabaseManager(PHCore plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String type = plugin.getConfig().getString("database.type", "sqlite");
        try {
            if (type.equalsIgnoreCase("mysql")) {
                initMySQL();
            } else {
                initSQLite();
            }
            createTables();
            plugin.getLogger().info("Datenbank verbunden (" + type + ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("Datenbankfehler: " + e.getMessage());
        }
    }

    private void initSQLite() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private void initMySQL() throws SQLException {
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String db = plugin.getConfig().getString("database.mysql.database");
        String user = plugin.getConfig().getString("database.mysql.username");
        String pass = plugin.getConfig().getString("database.mysql.password");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&autoReconnect=true";
        connection = DriverManager.getConnection(url, user, pass);
    }

    private void createTables() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    coins BIGINT DEFAULT 0,
                    rank VARCHAR(32) DEFAULT 'Spieler',
                    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Schließen der DB: " + e.getMessage());
        }
    }
}
