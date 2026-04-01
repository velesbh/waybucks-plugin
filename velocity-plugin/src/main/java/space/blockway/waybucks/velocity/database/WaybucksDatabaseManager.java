package space.blockway.waybucks.velocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class WaybucksDatabaseManager {

    private final WaybucksVelocityConfig config;
    private final Logger logger;
    private HikariDataSource dataSource;

    public WaybucksDatabaseManager(WaybucksVelocityConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void initialize() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();

        boolean sqlite = isSqliteByConfig();

        if (sqlite) {
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            String sqlitePath = config.getSqlitePath();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionInitSql("PRAGMA journal_mode=WAL;");
            hikariConfig.addDataSourceProperty("journal_mode", "WAL");
            hikariConfig.setPoolName("Waybucks-SQLite-Pool");
        } else {
            // Force-load the driver so DriverManager can find it after shading.
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL driver not found on classpath", e);
            }
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            String jdbcUrl = String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                    config.getMysqlHost(),
                    config.getMysqlPort(),
                    config.getMysqlDatabase()
            );
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.getMysqlUsername());
            hikariConfig.setPassword(config.getMysqlPassword());
            hikariConfig.setMaximumPoolSize(config.getMysqlMaxPoolSize());
            hikariConfig.setMinimumIdle(config.getMysqlMinIdle());
            hikariConfig.setConnectionTimeout(config.getMysqlConnectionTimeout());
            hikariConfig.setMaxLifetime(config.getMysqlMaxLifetime());
            hikariConfig.setPoolName("Waybucks-MySQL-Pool");
        }

        dataSource = new HikariDataSource(hikariConfig);

        runSchema(sqlite);

        logger.info("Database initialized ({})", sqlite ? "SQLite" : "MySQL");
    }

    private void runSchema(boolean sqlite) throws SQLException {
        String schemaFile = sqlite ? "schema-sqlite.sql" : "schema-mysql.sql";

        String schemaSql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(schemaFile)) {
            if (in == null) {
                throw new IllegalStateException("Schema file not found in classpath: " + schemaFile);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                schemaSql = reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read schema file: " + schemaFile, e);
        }

        // Split by semicolon to execute each statement individually
        String[] statements = schemaSql.split(";");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }

        logger.info("Schema applied from {}", schemaFile);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    public boolean isSqlite() {
        return isSqliteByConfig();
    }

    private boolean isSqliteByConfig() {
        return "sqlite".equalsIgnoreCase(config.getDbType());
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
}
