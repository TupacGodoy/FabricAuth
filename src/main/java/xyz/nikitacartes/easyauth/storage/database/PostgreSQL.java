package xyz.nikitacartes.easyauth.storage.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import static xyz.nikitacartes.easyauth.EasyAuth.config;
import static xyz.nikitacartes.easyauth.EasyAuth.extendedConfig;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.*;

/**
 * Optimized PostgreSQL implementation using HikariCP connection pooling.
 * Provides better performance through connection reuse and prepared statement caching.
 */
public class PostgreSQL implements DbApi {
    private final StorageConfigV1 config;
    private HikariDataSource dataSource;

    // Prepared statement templates
    private static final String SELECT_USER_SQL = "SELECT username, username_lower, uuid, data FROM %s WHERE %s = ?;";
    private static final String INSERT_USER_SQL = "INSERT INTO %s (username, username_lower, uuid, data, last_ip) VALUES (?, ?, ?, ?::jsonb, ?);";
    private static final String UPDATE_USER_SQL = "UPDATE %s SET uuid = ?, data = ?::jsonb, last_ip = ? WHERE username = ?;";
    private static final String DELETE_USER_SQL = "DELETE FROM %s WHERE username = ?;";
    private static final String COUNT_ACCOUNTS_BY_IP_SQL = "SELECT COUNT(*) FROM %s WHERE last_ip = ?;";
    private static final String GET_USERNAMES_BY_IP_SQL = "SELECT username FROM %s WHERE last_ip = ?;";

    public PostgreSQL(StorageConfigV1 config) {
        this.config = config;
    }

    @Override
    public void connect() throws DBApiException {
        try {
            LogInfo("Initializing PostgreSQL connection pool...");

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s/%s",
                    config.postgresql.pgHost, config.postgresql.pgDatabase));
            hikariConfig.setUsername(config.postgresql.pgUser);
            hikariConfig.setPassword(config.postgresql.pgPassword);

            // Optimized pool settings
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setMaxLifetime(600000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setLeakDetectionThreshold(60000);

            // PostgreSQL-specific optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true");

            dataSource = new HikariDataSource(hikariConfig);

            // Initialize table
            initializeTable();

            LogInfo("PostgreSQL connection pool initialized successfully");
        } catch (Exception e) {
            throw new DBApiException("Failed to initialize PostgreSQL connection pool", e);
        }
    }

    private void initializeTable() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create table if not exists
            stmt.executeUpdate(String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id SERIAL PRIMARY KEY,
                        username VARCHAR(255) UNIQUE NOT NULL,
                        username_lower VARCHAR(255) NOT NULL,
                        uuid VARCHAR(255),
                        last_ip VARCHAR(45),
                        data JSONB NOT NULL
                    );
                    """, config.postgresql.pgTable));

            // Create indexes
            try {
                stmt.executeUpdate(String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_last_ip ON %s(last_ip);",
                        config.postgresql.pgTable, config.postgresql.pgTable));
                stmt.executeUpdate(String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_username_lower ON %s(username_lower);",
                        config.postgresql.pgTable, config.postgresql.pgTable));
            } catch (SQLException ignored) {
                // Indexes may already exist
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LogInfo("PostgreSQL connection pool closed");
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

        String sql = String.format(INSERT_USER_SQL, config.postgresql.pgTable);
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
            LogError("Register error: " + data.username, e);
        }
    }

    @Override
    public @Nullable PlayerEntryV1 getUserData(String username) {
        String column = extendedConfig.allowCaseInsensitiveUsername ? "username" : "username_lower";
        String lookupName = extendedConfig.allowCaseInsensitiveUsername ? username : username.toLowerCase(Locale.ENGLISH);

        String sql = String.format(SELECT_USER_SQL, config.postgresql.pgTable, column);

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
            LogError("Error retrieving user: " + username, e);
        }
        return null;
    }

    @Override
    public @NotNull PlayerEntryV1 getUserDataOrCreate(String username) {
        PlayerEntryV1 data = getUserData(username);
        if (data == null) {
            data = new PlayerEntryV1(username);
            registerUser(data);
        }
        return data;
    }

    @Override
    public boolean deleteUserData(String username) {
        String sql = String.format(DELETE_USER_SQL, config.postgresql.pgTable);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogError("Delete error: " + username, e);
            return false;
        }
    }

    @Override
    public boolean updateUserData(PlayerEntryV1 data) {
        String sql = String.format(UPDATE_USER_SQL, config.postgresql.pgTable);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, data.uuid == null ? null : data.uuid.toString());
            stmt.setString(2, data.toJson());
            stmt.setString(3, data.lastIp);
            stmt.setString(4, data.username);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LogError("Update error: " + data.username, e);
            return false;
        }
    }

    @Override
    public HashMap<String, PlayerEntryV1> getAllData() {
        HashMap<String, PlayerEntryV1> players = new HashMap<>();
        String sql = String.format("SELECT username, username_lower, uuid, data FROM %s;", config.postgresql.pgTable);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String username = rs.getString("username");
                if (username != null) {
                    players.put(username, new PlayerEntryV1(
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
        return players;
    }

    @Override
    public int countAccountsByIp(String ipAddress) {
        String sql = String.format(COUNT_ACCOUNTS_BY_IP_SQL, config.postgresql.pgTable);
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
        String sql = String.format(GET_USERNAMES_BY_IP_SQL, config.postgresql.pgTable);

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
        throw new UnsupportedOperationException("PostgreSQL does not support migrateFromV1");
    }

    @Override
    public void migrateFromV4() {
        throw new UnsupportedOperationException("PostgreSQL does not support migrateFromV4");
    }
}
