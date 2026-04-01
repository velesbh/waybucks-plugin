package space.blockway.waybucks.velocity.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class WaybucksVelocityConfig {

    private final Path dataDirectory;
    private final Logger logger;
    private Map<String, Object> config;

    public WaybucksVelocityConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void load() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.yml");

            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in == null) {
                        throw new IllegalStateException("Default config.yml not found in classpath resources");
                    }
                    try (OutputStream out = Files.newOutputStream(configFile)) {
                        in.transferTo(out);
                    }
                }
                logger.info("Created default config.yml");
            }

            loadFromDisk(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public void reload() {
        Path configFile = dataDirectory.resolve("config.yml");
        try {
            loadFromDisk(configFile);
            logger.info("Configuration reloaded");
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload configuration", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk(Path configFile) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configFile)) {
            config = yaml.load(in);
        }
        if (config == null) {
            config = new java.util.HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String path, T defaultValue) {
        String[] parts = path.split("\\.");
        Object current = config;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return defaultValue;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return defaultValue;
            }
        }
        try {
            return (T) current;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    private String getString(String path, String defaultValue) {
        Object val = get(path, null);
        return val != null ? val.toString() : defaultValue;
    }

    private int getInt(String path, int defaultValue) {
        Object val = get(path, null);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private long getLong(String path, long defaultValue) {
        Object val = get(path, null);
        if (val instanceof Number n) return n.longValue();
        if (val != null) {
            try { return Long.parseLong(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private boolean getBoolean(String path, boolean defaultValue) {
        Object val = get(path, null);
        if (val instanceof Boolean b) return b;
        if (val != null) return Boolean.parseBoolean(val.toString());
        return defaultValue;
    }

    // Database
    public String getDbType() {
        return getString("database.type", "sqlite");
    }

    public String getSqlitePath() {
        return getString("database.sqlite.path", "waybucks.db");
    }

    public String getMysqlHost() {
        return getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return getString("database.mysql.database", "waybucks");
    }

    public String getMysqlUsername() {
        return getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return getString("database.mysql.password", "");
    }

    public int getMysqlMaxPoolSize() {
        return getInt("database.mysql.pool.max-pool-size", 10);
    }

    public int getMysqlMinIdle() {
        return getInt("database.mysql.pool.min-idle", 2);
    }

    public long getMysqlConnectionTimeout() {
        return getLong("database.mysql.pool.connection-timeout", 30000L);
    }

    public long getMysqlMaxLifetime() {
        return getLong("database.mysql.pool.max-lifetime", 1800000L);
    }

    // API
    public boolean isApiEnabled() {
        return getBoolean("api.enabled", false);
    }

    public int getApiPort() {
        return getInt("api.port", 8080);
    }

    public String getApiBind() {
        return getString("api.bind", "0.0.0.0");
    }

    public String getMasterKey() {
        return getString("api.master-key", "changeme");
    }

    // Currency
    public String getCurrencyName() {
        return getString("currency.name", "Waybuck");
    }

    public String getCurrencyNamePlural() {
        return getString("currency.name-plural", "Waybucks");
    }

    public String getCurrencySymbol() {
        return getString("currency.symbol", "WB");
    }

    public long getStartingBalance() {
        return getLong("currency.starting-balance", 0L);
    }

    public long getMaxBalance() {
        return getLong("currency.max-balance", Long.MAX_VALUE);
    }

    // Daily
    public long getDailyBase() {
        return getLong("daily.base", 100L);
    }

    public long getDailyStreakBonus() {
        return getLong("daily.streak-bonus", 10L);
    }

    public int getDailyStreakCap() {
        return getInt("daily.streak-cap", 30);
    }

    public int getDailyCooldownHours() {
        return getInt("daily.cooldown-hours", 24);
    }

    // Item
    public boolean isItemEnabled() {
        return getBoolean("item.enabled", false);
    }

    // Share database (Bungeecord/Velocity multi-instance)
    public boolean isShareDatabase() {
        return getBoolean("share-database", false);
    }
}
