package de.pinkhorizon.minigames.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.minigames.PHMinigames;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MinigamesDatabaseManager {

    private final HikariDataSource ds;

    public MinigamesDatabaseManager(PHMinigames plugin) throws SQLException {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int    port = plugin.getConfig().getInt   ("database.port", 3306);
        String user = plugin.getConfig().getString("database.user", "ph_user");
        String pass = plugin.getConfig().getString("database.password", "ph-db-2024");
        String db   = plugin.getConfig().getString("database.name", "ph_minigames");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("PH-Minigames-Pool");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");

        ds = new HikariDataSource(cfg);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mg_bedwars_stats (
                    uuid         CHAR(36)     NOT NULL,
                    wins         INT          NOT NULL DEFAULT 0,
                    losses       INT          NOT NULL DEFAULT 0,
                    kills        INT          NOT NULL DEFAULT 0,
                    deaths       INT          NOT NULL DEFAULT 0,
                    beds_broken  INT          NOT NULL DEFAULT 0,
                    finals       INT          NOT NULL DEFAULT 0,
                    games_played INT          NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mg_bedwars_arenas (
                    id           INT          NOT NULL AUTO_INCREMENT,
                    name         VARCHAR(64)  NOT NULL,
                    world        VARCHAR(64)  NOT NULL DEFAULT 'world',
                    max_teams    INT          NOT NULL DEFAULT 4,
                    team_size    INT          NOT NULL DEFAULT 2,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mg_bedwars_spawns (
                    arena_id     INT          NOT NULL,
                    team         VARCHAR(8)   NOT NULL,
                    x            DOUBLE       NOT NULL,
                    y            DOUBLE       NOT NULL,
                    z            DOUBLE       NOT NULL,
                    yaw          FLOAT        NOT NULL DEFAULT 0,
                    pitch        FLOAT        NOT NULL DEFAULT 0,
                    PRIMARY KEY (arena_id, team)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mg_bedwars_beds (
                    arena_id     INT          NOT NULL,
                    team         VARCHAR(8)   NOT NULL,
                    x            INT          NOT NULL,
                    y            INT          NOT NULL,
                    z            INT          NOT NULL,
                    world        VARCHAR(64)  NOT NULL DEFAULT 'world',
                    PRIMARY KEY (arena_id, team)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mg_bedwars_spawners (
                    id           INT          NOT NULL AUTO_INCREMENT,
                    arena_id     INT          NOT NULL,
                    type         VARCHAR(16)  NOT NULL,
                    x            DOUBLE       NOT NULL,
                    y            DOUBLE       NOT NULL,
                    z            DOUBLE       NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mg_holograms (
                    name         VARCHAR(64)  NOT NULL,
                    world        VARCHAR(64)  NOT NULL,
                    x            DOUBLE       NOT NULL,
                    y            DOUBLE       NOT NULL,
                    z            DOUBLE       NOT NULL,
                    `lines`      TEXT         NOT NULL,
                    PRIMARY KEY (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        }
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
