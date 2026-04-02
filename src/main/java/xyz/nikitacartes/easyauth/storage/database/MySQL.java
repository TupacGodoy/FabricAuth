package xyz.nikitacartes.easyauth.storage.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nikitacartes.easyauth.config.StorageConfigV1;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static xyz.nikitacartes.easyauth.EasyAuth.config;
import static xyz.nikitacartes.easyauth.EasyAuth.extendedConfig;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.*;

/**
 * Optimized MySQL implementation using HikariCP connection pooling.
 * Provides better performance through connection reuse and prepared statement caching.
 */
public class MySQL implements DbApi {
    private final StorageConfigV1 config;
    private HikariDataSource dataSource;

    // Prepared statement cache for common queries
    private static final String SELECT_USER_SQL = "SELECT username, username_lower, uuid, data FROM %s WHERE %s = ?;";
    private static final String INSERT_USER_SQL = "INSERT INTO %s (username, username_lower, uuid, data, last_ip) VALUES (?, ?, ?, ?, ?);";
    private static final String UPDATE_USER_SQL = "UPDATE %s SET uuid = ?, data = ?, last_ip = ? WHERE username = ?;";
    private static final String DELETE_USER_SQL = "DELETE FROM %s WHERE username = ?;";
    private static final String COUNT_ACCOUNTS_BY_IP_SQL = "SELECT COUNT(*) FROM %s WHERE last_ip = ?;";
    private static final String GET_USERNAMES_BY_IP_SQL = "SELECT username FROM %s WHERE last_ip = ?;";

    public MySQL(StorageConfigV1 config) {
        this.config = config;
    }

    /**
     * Initializes connection pool with optimized settings.
     */
    public void connect() throws DBApiException {
        try {
            LogInfo("Initializing MySQL connection pool...");

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s/%s?autoReconnect=true&useServerPrepStmts=true&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048",
                    config.mysql.mysqlHost, config.mysql.mysqlDatabase));
            hikariConfig.setUsername(config.mysql.mysqlUser);
            hikariConfig.setPassword(config.mysql.mysqlPassword);

            // Optimized pool settings for Minecraft server workloads
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(300000); // 5 minutes
            hikariConfig.setMaxLifetime(600000); // 10 minutes
            hikariConfig.setConnectionTimeout(10000); // 10 seconds
            hikariConfig.setLeakDetectionThreshold(60000); // 1 minute

            // Performance optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

            dataSource = new HikariDataSource(hikariConfig);

            // Initialize table if needed
            initializeTable();

            LogInfo("MySQL connection pool initialized successfully");
        } catch (Exception e) {
            throw new DBApiException("Failed to initialize MySQL connection pool", e);
        }
    }

    private void initializeTable() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(
                     "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?;")) {

            checkStmt.setString(1, config.mysql.mysqlTable);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                // Create table
                try (Statement createStmt = conn.createStatement()) {
                    createStmt.executeUpdate(String.format("""
                                    CREATE TABLE `%s`.`%s` (
                                        `id` INT NOT NULL AUTO_INCREMENT,
                                        `username` VARCHAR(255) NOT NULL,
                                        `username_lower` VARCHAR(255) NOT NULL,
                                        `uuid` VARCHAR(255) NULL,
                                        `data` JSON NOT NULL,
                                        `last_ip` VARCHAR(45) NULL,
                                        PRIMARY KEY (`id`), UNIQUE (`username`),
                                        INDEX idx_last_ip (`last_ip`),
                                        INDEX idx_username_lower (`username_lower`)
                                    ) ENGINE = InnoDB;""",
                            config.mysql.mysqlDatabase, config.mysql.mysqlTable));
                    LogInfo("Created MySQL table: " + config.mysql.mysqlTable);
                }
            } else {
                // Check and add columns/indexes if missing
                migrateTableSchema(conn);
            }
        }
    }

    private void migrateTableSchema(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();

        // Check for username column
        try (ResultSet columns = metaData.getColumns(null, null, config.mysql.mysqlTable, "username")) {
            if (!columns.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(String.format("ALTER TABLE `%s`.`%s` ADD COLUMN `username` VARCHAR(255) NULL;",
                            config.mysql.mysqlDatabase, config.mysql.mysqlTable));
                    stmt.executeUpdate(String.format("ALTER TABLE `%s`.`%s` ADD COLUMN `username_lower` VARCHAR(255) NULL;",
                            config.mysql.mysqlDatabase, config.mysql.mysqlTable));
                    stmt.executeUpdate(String.format("ALTER TABLE `%s`.`%s` DROP INDEX `uuid`;",
                            config.mysql.mysqlDatabase, config.mysql.mysqlTable));
                    stmt.executeUpdate(String.format("ALTER TABLE `%s`.`%s` MODIFY COLUMN `uuid` VARCHAR(255) NULL;",
                            config.mysql.mysqlDatabase, config.mysql.mysqlTable));
                    LogInfo("Migrated table schema: added username columns");
                }
            }
        }

        // Check for last_ip column
        try (ResultSet columns = metaData.getColumns(null, null, config.mysql.mysqlTable, "last_ip")) {
            if (!columns.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(String.format("ALTER TABLE `%s`.`%s` ADD COLUMN `last_ip` VARCHAR(45) NULL;",
                            config.mysql.mysqlDatabase, config.mysql.mysqlTable));
                    LogInfo("Migrated table schema: added last_ip column");
                }
            }
        }

        // Add indexes for performance
        try (Statement stmt = conn.createStatement()) {
            try {
                stmt.executeUpdate(String.format("CREATE INDEX idx_last_ip ON `%s`.`%s`(`last_ip`);",
                        config.mysql.mysqlDatabase, config.mysql.mysqlTable));
            } catch (SQLException ignored) {
                // Index may already exist
            }
            try {
                stmt.executeUpdate(String.format("CREATE INDEX idx_username_lower ON `%s`.`%s`(`username_lower`);",
                        config.mysql.mysqlDatabase, config.mysql.mysqlTable));
            } catch (SQLException ignored) {
                // Index may already exist
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LogInfo("MySQL connection pool closed");
        }
    }

    @Override
    public boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void registerUser(PlayerEntryV1 data) {
        if (xyz.nikitacartes.easyauth.EasyAuth.config.debug) {
            LogDebug("Registering new player " + data.username);
        }

        String sql = String.format(INSERT_USER_SQL, config.mysql.mysqlTable);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, data.username);
            stmt.setString(2, data.usernameLowerCase);
            stmt.setString(3, data.uuid == null ? null : data.uuid.toString());
            stmt.setString(4, data.toJson());
            stmt.setString(5, data.lastIp);

            if (stmt.executeUpdate() == 0) {
                LogError("Failed to register user: " + data.username);
            }
        } catch (SQLException e) {
            LogError("Register error for user: " + data.username, e);
        }
    }

    @Override
    public @Nullable PlayerEntryV1 getUserData(String username) {
        String column = extendedConfig.allowCaseInsensitiveUsername ? "username" : "username_lower";
        String lookupName = extendedConfig.allowCaseInsensitiveUsername ? username : username.toLowerCase(Locale.ENGLISH);

        String sql = String.format(SELECT_USER_SQL, config.mysql.mysqlTable, column);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, lookupName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerEntryV1(
                            rs.getString("username"),
                            rs.getString("username_lower"),
                            rs.getString("uuid"),
                            rs.getString("data")
                    );
                }
            }
        } catch (SQLException e) {
            LogError("Error retrieving user data: " + username, e);
        }
        return null;
    }

    @Override
    public @NotNull PlayerEntryV1 getUserDataOrCreate(String username) {
        PlayerEntryV1 playerEntry = getUserData(username);
        if (playerEntry == null) {
            playerEntry = new PlayerEntryV1(username);
            registerUser(playerEntry);
        }
        return playerEntry;
    }

    @Override
    public boolean deleteUserData(String username) {
        String sql = String.format(DELETE_USER_SQL, config.mysql.mysqlTable);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogError("Delete user error: " + username, e);
            return false;
        }
    }

    @Override
    public boolean updateUserData(PlayerEntryV1 data) {
        String sql = String.format(UPDATE_USER_SQL, config.mysql.mysqlTable);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, data.uuid == null ? null : data.uuid.toString());
            stmt.setString(2, data.toJson());
            stmt.setString(3, data.lastIp);
            stmt.setString(4, data.username);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogError("Update user error: " + data.username, e);
            return false;
        }
    }

    @Override
    public HashMap<String, PlayerEntryV1> getAllData() {
        HashMap<String, PlayerEntryV1> result = new HashMap<>();
        String sql = String.format("SELECT username, username_lower, uuid, data FROM %s;", config.mysql.mysqlTable);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String username = rs.getString("username");
                if (username != null) {
                    result.put(username, new PlayerEntryV1(
                            username,
                            rs.getString("username_lower"),
                            rs.getString("uuid"),
                            rs.getString("data")
                    ));
                }
            }
        } catch (SQLException e) {
            LogError("Error retrieving all data", e);
        }
        return result;
    }

    @Override
    public int countAccountsByIp(String ipAddress) {
        String sql = String.format(COUNT_ACCOUNTS_BY_IP_SQL, config.mysql.mysqlTable);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ipAddress);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LogError("Error counting accounts by IP", e);
        }
        return 0;
    }

    @Override
    public List<String> getUsernamesByIp(String ipAddress) {
        List<String> usernames = new ArrayList<>();
        String sql = String.format(GET_USERNAMES_BY_IP_SQL, config.mysql.mysqlTable);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ipAddress);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    usernames.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            LogError("Error getting usernames by IP", e);
        }
        return usernames;
    }

    @Override
    public void migrateFromV1(HashMap<String, String> userCache) {
        String selectSql = String.format("SELECT data FROM %s WHERE uuid = ?;", config.mysql.mysqlTable);
        String upsertSql = String.format(
                "INSERT INTO %s (username, username_lower, uuid, data) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE data = VALUES(data);", config.mysql.mysqlTable);

        try (Connection conn = getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {

            int batchSize = 0;
            for (Map.Entry<String, String> entry : userCache.entrySet()) {
                String username = entry.getKey();
                String uuid = entry.getValue();

                // Try to find existing data by UUID
                String data = null;
                selectStmt.setString(1, uuid);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        data = rs.getString("data");
                    }
                }

                // Try lowercase username UUID
                if (data == null) {
                    String lowerUuid = Uuids.getOfflinePlayerUuid(username.toLowerCase(Locale.ENGLISH)).toString();
                    selectStmt.setString(1, lowerUuid);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            data = rs.getString("data");
                        }
                    }
                }

                if (data != null) {
                    PlayerEntryV1 playerEntry = migrateFromV1(data, username);
                    upsertStmt.setString(1, playerEntry.username);
                    upsertStmt.setString(2, playerEntry.usernameLowerCase);
                    upsertStmt.setString(3, playerEntry.uuid == null ? null : playerEntry.uuid.toString());
                    upsertStmt.setString(4, playerEntry.toJson());
                    upsertStmt.addBatch();

                    if (++batchSize >= 1000) {
                        upsertStmt.executeBatch();
                        batchSize = 0;
                    }
                }
            }

            if (batchSize > 0) {
                upsertStmt.executeBatch();
            }
        } catch (SQLException e) {
            LogError("Migration error", e);
        }
    }

    @Override
    public void migrateFromV4() {
        // Try SQL-based migration first
        String sql = String.format(
                "UPDATE %s SET last_ip = JSON_UNQUOTE(JSON_EXTRACT(data, '$.last_ip'));",
                config.mysql.mysqlTable);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
            LogInfo("Migrated IPs successfully using SQL");
        } catch (SQLException e) {
            LogWarn("SQL migration failed, falling back to Java: " + e.getMessage());
            migrateFromV4JavaFallback();
        }
    }

    private void migrateFromV4JavaFallback() {
        String updateSql = String.format("UPDATE %s SET last_ip = ? WHERE username = ?;", config.mysql.mysqlTable);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {

            HashMap<String, PlayerEntryV1> allData = getAllData();
            int batchSize = 0;

            for (PlayerEntryV1 entry : allData.values()) {
                stmt.setString(1, entry.lastIp);
                stmt.setString(2, entry.username);
                stmt.addBatch();

                if (++batchSize >= 1000) {
                    stmt.executeBatch();
                    batchSize = 0;
                }
            }

            if (batchSize > 0) {
                stmt.executeBatch();
            }
            LogInfo("Migrated IPs successfully using Java fallback");
        } catch (SQLException e) {
            LogError("Java fallback migration failed", e);
        }
    }
}
