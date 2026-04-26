package de.pinkhorizon.generators.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.generators.PHGenerators;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Eigene, vollständig unabhängige Datenbankverbindung für PH-Generators.
 * Kein Zugriff auf ph-core oder andere Plugin-Datenbanken.
 */
public class GenDatabaseManager {

    private final PHGenerators plugin;
    private HikariDataSource dataSource;
    private String dbType;

    public GenDatabaseManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void init() {
        dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        try {
            HikariConfig cfg = new HikariConfig();
            if (dbType.equals("mysql")) {
                String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                int    port = plugin.getConfig().getInt("database.mysql.port", 3306);
                String db   = plugin.getConfig().getString("database.mysql.database", "ph_generators");
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
                File dbFile = new File(plugin.getDataFolder(), "generators.db");
                cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                cfg.setDriverClassName("org.sqlite.JDBC");
                cfg.setMaximumPoolSize(1);
            }
            cfg.setPoolName("PH-Generators-Pool");
            dataSource = new HikariDataSource(cfg);
            createTables();
            plugin.getLogger().info("[Generators] Datenbank verbunden (" + dbType + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("[Generators] Datenbankfehler: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Connection con = getConnection()) {
            // Spielerdaten
            con.prepareStatement("""
                CREATE TABLE IF NOT EXISTS gen_players (
                    uuid              VARCHAR(36) PRIMARY KEY,
                    name              VARCHAR(16) NOT NULL,
                    money             BIGINT      DEFAULT 0,
                    prestige          INT         DEFAULT 0,
                    total_earned      BIGINT      DEFAULT 0,
                    total_upgrades    INT         DEFAULT 0,
                    afk_boxes_opened  INT         DEFAULT 0,
                    booster_expiry    BIGINT      DEFAULT 0,
                    booster_mult      DOUBLE      DEFAULT 1.0,
                    last_seen         BIGINT      DEFAULT 0
                )
            """).execute();

            // Platzierte Generatoren
            String genTable;
            if (dbType.equals("mysql")) {
                genTable = """
                    CREATE TABLE IF NOT EXISTS gen_generators (
                        id     INT AUTO_INCREMENT PRIMARY KEY,
                        uuid   VARCHAR(36) NOT NULL,
                        world  VARCHAR(64) NOT NULL,
                        x      INT         NOT NULL,
                        y      INT         NOT NULL,
                        z      INT         NOT NULL,
                        tier   VARCHAR(32) NOT NULL,
                        level  INT         DEFAULT 1,
                        UNIQUE KEY pos_key (world, x, y, z)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
            } else {
                genTable = """
                    CREATE TABLE IF NOT EXISTS gen_generators (
                        id     INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid   VARCHAR(36) NOT NULL,
                        world  VARCHAR(64) NOT NULL,
                        x      INT         NOT NULL,
                        y      INT         NOT NULL,
                        z      INT         NOT NULL,
                        tier   VARCHAR(32) NOT NULL,
                        level  INT         DEFAULT 1,
                        UNIQUE (world, x, y, z)
                    )
                """;
            }
            con.prepareStatement(genTable).execute();

            // Quests
            con.prepareStatement("""
                CREATE TABLE IF NOT EXISTS gen_quests (
                    uuid        VARCHAR(36) NOT NULL,
                    quest_id    VARCHAR(64) NOT NULL,
                    progress    BIGINT      DEFAULT 0,
                    completed   TINYINT     DEFAULT 0,
                    reset_date  VARCHAR(10) NOT NULL,
                    PRIMARY KEY (uuid, quest_id)
                )
            """).execute();

            // Achievements
            con.prepareStatement("""
                CREATE TABLE IF NOT EXISTS gen_achievements (
                    uuid           VARCHAR(36) NOT NULL,
                    achievement_id VARCHAR(64) NOT NULL,
                    progress       BIGINT      DEFAULT 0,
                    completed      TINYINT     DEFAULT 0,
                    PRIMARY KEY (uuid, achievement_id)
                )
            """).execute();

            // Gilden
            String guildTable;
            if (dbType.equals("mysql")) {
                guildTable = """
                    CREATE TABLE IF NOT EXISTS gen_guilds (
                        id           INT AUTO_INCREMENT PRIMARY KEY,
                        name         VARCHAR(32) NOT NULL UNIQUE,
                        leader_uuid  VARCHAR(36) NOT NULL,
                        created_at   BIGINT DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
            } else {
                guildTable = """
                    CREATE TABLE IF NOT EXISTS gen_guilds (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        name         VARCHAR(32) NOT NULL UNIQUE,
                        leader_uuid  VARCHAR(36) NOT NULL,
                        created_at   BIGINT DEFAULT 0
                    )
                """;
            }
            con.prepareStatement(guildTable).execute();

            // Gildenmitglieder
            con.prepareStatement("""
                CREATE TABLE IF NOT EXISTS gen_guild_members (
                    guild_id   INT         NOT NULL,
                    uuid       VARCHAR(36) NOT NULL,
                    PRIMARY KEY (uuid)
                )
            """).execute();

            // AFK-Tracking
            con.prepareStatement("""
                CREATE TABLE IF NOT EXISTS gen_afk (
                    uuid          VARCHAR(36) PRIMARY KEY,
                    afk_since     BIGINT DEFAULT 0,
                    last_box_time BIGINT DEFAULT 0
                )
            """).execute();

            // Migration: lb_holo position
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN lb_holo_world VARCHAR(64) NULL")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN lb_holo_x INT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN lb_holo_y INT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN lb_holo_z INT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}

            // Migration: tutorial_done
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN tutorial_done TINYINT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}

            // Migration: border_size
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN border_size INT DEFAULT 40")
                    .execute();
            } catch (java.sql.SQLException ignored) {}

            // Migration: stats-hologramm position
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN holo_world VARCHAR(64) NULL")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN holo_x INT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN holo_y INT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
            try { con.prepareStatement(
                    "ALTER TABLE gen_players ADD COLUMN holo_z INT DEFAULT 0")
                    .execute();
            } catch (java.sql.SQLException ignored) {}
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("[PH-Generators] Datenbankverbindung nicht verfügbar.");
        }
        return dataSource.getConnection();
    }

    public String getDbType() { return dbType; }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
