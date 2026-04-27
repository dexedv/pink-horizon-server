package de.pinkhorizon.skyblock.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.skyblock.PHSkyBlock;

import java.sql.Connection;
import java.sql.SQLException;

public class SkyDatabase {

    private final HikariDataSource ds;

    public SkyDatabase(PHSkyBlock plugin) {
        var cfg = plugin.getConfig().getConfigurationSection("database");
        String host     = cfg.getString("host", "ph-mysql");
        int    port     = cfg.getInt("port", 3306);
        String name     = cfg.getString("name", "ph_skyblock");
        String user     = cfg.getString("user", "ph_user");
        String password = cfg.getString("password", "");
        int    poolSize = cfg.getInt("pool-size", 8);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
        hc.setUsername(user);
        hc.setPassword(password);
        hc.setMaximumPoolSize(poolSize);
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);
        hc.setPoolName("PHSkyBlock-Pool");
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        ds = new HikariDataSource(hc);
        plugin.getLogger().info("SkyBlock-Datenbankverbindung hergestellt (" + host + ":" + port + "/" + name + ")");
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
