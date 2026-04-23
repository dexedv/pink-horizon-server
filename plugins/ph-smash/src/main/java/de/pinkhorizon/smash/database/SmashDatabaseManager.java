package de.pinkhorizon.smash.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.smash.PHSmash;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SmashDatabaseManager {

    private final HikariDataSource ds;

    public SmashDatabaseManager(PHSmash plugin) throws SQLException {
        String host = plugin.getConfig().getString("database.host", "ph-mysql");
        int    port = plugin.getConfig().getInt("database.port", 3306);
        String name = plugin.getConfig().getString("database.name", "ph_smash");
        String user = plugin.getConfig().getString("database.user", "ph_user");
        String pass = plugin.getConfig().getString("database.password", "");

        String envPass = System.getenv("MYSQL_PASSWORD");
        if (envPass != null && !envPass.isBlank()) pass = envPass;

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Berlin");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(8);
        cfg.setConnectionTimeout(10_000);
        cfg.setPoolName("SmashPool");
        ds = new HikariDataSource(cfg);
        createTables();
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    private void createTables() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS smash_state (
                  id         TINYINT PRIMARY KEY DEFAULT 1,
                  boss_level INT NOT NULL DEFAULT 1
                )""");
            st.executeUpdate("INSERT IGNORE INTO smash_state (id, boss_level) VALUES (1, 1)");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS smash_players (
                  uuid           CHAR(36)  PRIMARY KEY,
                  kills          INT       NOT NULL DEFAULT 0,
                  total_damage   BIGINT    NOT NULL DEFAULT 0,
                  best_level     INT       NOT NULL DEFAULT 0,
                  personal_level INT       NOT NULL DEFAULT 1,
                  last_seen      DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // Migration: personal_level Spalte zu bestehenden Tabellen hinzufügen
            try {
                st.executeUpdate(
                    "ALTER TABLE smash_players ADD COLUMN personal_level INT NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                if (e.getErrorCode() != 1060) throw e; // 1060 = Duplicate column name
            }

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS smash_inventory (
                  uuid      CHAR(36)    NOT NULL,
                  item_type VARCHAR(32) NOT NULL,
                  quantity  INT         NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, item_type)
                )""");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS smash_upgrades (
                  uuid       CHAR(36)    NOT NULL,
                  upgrade_id VARCHAR(32) NOT NULL,
                  level      INT         NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, upgrade_id)
                )""");
        }
    }
}
