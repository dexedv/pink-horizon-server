package de.pinkhorizon.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.core.PHCore;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final PHCore plugin;
    private HikariDataSource dataSource;
    private String dbType;

    public DatabaseManager(PHCore plugin) {
        this.plugin = plugin;
    }

    public void init() {
        dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        try {
            HikariConfig cfg = new HikariConfig();
            if (dbType.equals("mysql")) {
                String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                int    port = plugin.getConfig().getInt("database.mysql.port", 3306);
                String db   = plugin.getConfig().getString("database.mysql.database", "pinkhorizon");
                String user = plugin.getConfig().getString("database.mysql.username", "ph_user");
                String pass = plugin.getConfig().getString("database.mysql.password", "");
                cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
                cfg.setUsername(user);
                cfg.setPassword(pass);
                cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
                cfg.setMaximumPoolSize(10);
                cfg.setMinimumIdle(2);
                cfg.setConnectionTimeout(30_000);
                cfg.setIdleTimeout(600_000);
                cfg.setMaxLifetime(1_800_000);
            } else {
                File dbFile = new File(plugin.getDataFolder(), "data.db");
                cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                cfg.setDriverClassName("org.sqlite.JDBC");
                cfg.setMaximumPoolSize(1);
            }
            cfg.setPoolName("PH-Core-Pool");
            dataSource = new HikariDataSource(cfg);
            createTables();
            plugin.getLogger().info("Datenbank verbunden (" + dbType + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Datenbankfehler: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String playersTable;
        if (dbType.equals("mysql")) {
            playersTable = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid       VARCHAR(36)  NOT NULL PRIMARY KEY,
                    name       VARCHAR(16)  NOT NULL,
                    coins      BIGINT       NOT NULL DEFAULT 0,
                    rank       VARCHAR(32)  NOT NULL DEFAULT 'spieler',
                    first_join TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_join  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        } else {
            playersTable = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid       VARCHAR(36) PRIMARY KEY,
                    name       VARCHAR(16) NOT NULL,
                    coins      BIGINT      DEFAULT 0,
                    rank       VARCHAR(32) DEFAULT 'spieler',
                    first_join TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                    last_join  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
                );
                """;
        }
        try (Connection con = getConnection();
             PreparedStatement stmt = con.prepareStatement(playersTable)) {
            stmt.execute();
        }
    }

    /** Liefert den DB-Typ ("mysql" oder "sqlite"). */
    public String getDbType() { return dbType; }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
