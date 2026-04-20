package de.pinkhorizon.survival.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.survival.PHSurvival;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Datenbankverbindung für die survival-exklusive Datenbank (ph_survival).
 * Keine anderen Server haben Zugriff auf diese Daten.
 */
public class SurvivalDatabaseManager {

    private final PHSurvival plugin;
    private HikariDataSource dataSource;

    public SurvivalDatabaseManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String host = plugin.getConfig().getString("database.host", "ph-mysql");
        int    port = plugin.getConfig().getInt("database.port", 3306);
        String db   = plugin.getConfig().getString("database.database", "ph_survival");
        String user = plugin.getConfig().getString("database.username", "ph_user");
        String pass = plugin.getConfig().getString("database.password", "");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
            + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setMaximumPoolSize(15);
        cfg.setMinimumIdle(3);
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("PH-Survival-Pool");

        try {
            dataSource = new HikariDataSource(cfg);
            createTables();
            plugin.getLogger().info("Survival-Datenbank verbunden (ph_survival@" + host + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Survival-DB Fehler: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {

            // Wirtschaft
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_economy (
                    uuid   VARCHAR(36) PRIMARY KEY,
                    coins  BIGINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Homes
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_homes (
                    uuid  VARCHAR(36) NOT NULL,
                    name  VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x     DOUBLE NOT NULL,
                    y     DOUBLE NOT NULL,
                    z     DOUBLE NOT NULL,
                    yaw   FLOAT  NOT NULL DEFAULT 0,
                    pitch FLOAT  NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Claims
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_claims (
                    world      VARCHAR(64) NOT NULL,
                    chunk_x    INT NOT NULL,
                    chunk_z    INT NOT NULL,
                    owner_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (world, chunk_x, chunk_z),
                    INDEX idx_owner (owner_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_claim_trusts (
                    world        VARCHAR(64) NOT NULL,
                    chunk_x      INT NOT NULL,
                    chunk_z      INT NOT NULL,
                    trusted_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (world, chunk_x, chunk_z, trusted_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Warps
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_warps (
                    name  VARCHAR(32) PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x     DOUBLE NOT NULL,
                    y     DOUBLE NOT NULL,
                    z     DOUBLE NOT NULL,
                    yaw   FLOAT  NOT NULL DEFAULT 0,
                    pitch FLOAT  NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Jobs
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_jobs (
                    uuid   VARCHAR(36) NOT NULL,
                    job_id VARCHAR(32) NOT NULL,
                    level  INT     NOT NULL DEFAULT 1,
                    xp     INT     NOT NULL DEFAULT 0,
                    active BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (uuid, job_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Statistiken
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_stats (
                    uuid          VARCHAR(36) PRIMARY KEY,
                    deaths        INT    NOT NULL DEFAULT 0,
                    mob_kills     INT    NOT NULL DEFAULT 0,
                    player_kills  INT    NOT NULL DEFAULT 0,
                    blocks_broken INT    NOT NULL DEFAULT 0,
                    playtime      BIGINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Achievements
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_achievements (
                    uuid        VARCHAR(36) NOT NULL,
                    achievement VARCHAR(64) NOT NULL,
                    PRIMARY KEY (uuid, achievement)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Quests
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_quests (
                    uuid       VARCHAR(36) NOT NULL,
                    quest_date DATE        NOT NULL,
                    quest_id   VARCHAR(32) NOT NULL,
                    progress   INT     NOT NULL DEFAULT 0,
                    completed  BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (uuid, quest_date, quest_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Freunde
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_friends (
                    uuid1 VARCHAR(36) NOT NULL,
                    uuid2 VARCHAR(36) NOT NULL,
                    PRIMARY KEY (uuid1, uuid2)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_friend_requests (
                    from_uuid VARCHAR(36) NOT NULL,
                    to_uuid   VARCHAR(36) NOT NULL,
                    PRIMARY KEY (from_uuid, to_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Bank
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_bank (
                    uuid          VARCHAR(36) PRIMARY KEY,
                    balance       BIGINT NOT NULL DEFAULT 0,
                    last_interest DATE   NOT NULL DEFAULT (CURRENT_DATE)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Mails
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_mails (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    to_uuid     VARCHAR(36)  NOT NULL,
                    sender_name VARCHAR(32)  NOT NULL,
                    message     TEXT         NOT NULL,
                    sent_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
                    INDEX idx_recipient (to_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Auktionshaus
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_auction (
                    id          VARCHAR(36) PRIMARY KEY,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(32) NOT NULL,
                    item_data   MEDIUMTEXT  NOT NULL,
                    price       BIGINT      NOT NULL,
                    listed_at   BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Upgrades
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_upgrades (
                    uuid             VARCHAR(36) PRIMARY KEY,
                    keep_inventory   BOOLEAN NOT NULL DEFAULT FALSE,
                    fly_perm         BOOLEAN NOT NULL DEFAULT FALSE,
                    ki_expiry        BIGINT  NOT NULL DEFAULT 0,
                    fly_expiry       BIGINT  NOT NULL DEFAULT 0,
                    extra_claims     INT     NOT NULL DEFAULT 0,
                    claim_purchases  INT     NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // Holograms
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_holograms (
                    name  VARCHAR(128) NOT NULL PRIMARY KEY,
                    world VARCHAR(64)  NOT NULL,
                    x     DOUBLE       NOT NULL,
                    y     DOUBLE       NOT NULL,
                    z     DOUBLE       NOT NULL,
                    scale FLOAT        NOT NULL DEFAULT 1.0,
                    `lines` TEXT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // NPCs
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_npcs (
                    id         INT AUTO_INCREMENT PRIMARY KEY,
                    name       VARCHAR(64)  NOT NULL,
                    world      VARCHAR(64)  NOT NULL,
                    x          DOUBLE       NOT NULL,
                    y          DOUBLE       NOT NULL,
                    z          DOUBLE       NOT NULL,
                    yaw        FLOAT        NOT NULL DEFAULT 0,
                    profession VARCHAR(32)  NOT NULL DEFAULT 'FARMER'
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_npc_commands (
                    npc_id  INT          NOT NULL,
                    idx     INT          NOT NULL,
                    command VARCHAR(256) NOT NULL,
                    PRIMARY KEY (npc_id, idx)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            // ChestShops
            st.execute("""
                CREATE TABLE IF NOT EXISTS sv_chestshops (
                    world        VARCHAR(64) NOT NULL,
                    x            INT         NOT NULL,
                    y            INT         NOT NULL,
                    z            INT         NOT NULL,
                    owner_uuid   VARCHAR(36) NOT NULL,
                    item         VARCHAR(64) NOT NULL,
                    amount       INT         NOT NULL DEFAULT 1,
                    buy_price    BIGINT      NOT NULL DEFAULT 10,
                    sell_price   BIGINT      NOT NULL DEFAULT 5,
                    buy_on       BOOLEAN     NOT NULL DEFAULT FALSE,
                    sell_on      BOOLEAN     NOT NULL DEFAULT FALSE,
                    balance      BIGINT      NOT NULL DEFAULT 0,
                    use_shop_bal BOOLEAN     NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (world, x, y, z)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
