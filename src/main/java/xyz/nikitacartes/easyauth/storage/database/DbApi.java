package xyz.nikitacartes.easyauth.storage.database;

import xyz.nikitacartes.easyauth.EasyAuth;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;
import xyz.nikitacartes.easyauth.storage.deprecated.PlayerCacheV0;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;

import static xyz.nikitacartes.easyauth.EasyAuth.technicalConfig;

public interface DbApi {
    /**
     * Opens database connection.
     */
    void connect() throws DBApiException;

    /**
     * Closes database connection.
     */
    void close();

    /**
     * Tells whether DbApi connection is closed.
     *
     * @return false if connection is open, otherwise false
     */
    boolean isClosed();

    /**
     * Inserts the data for the player.
     *
     * @param data data to put inside database
     * @return true if operation was successful, otherwise false
     */
    boolean registerUser(PlayerEntryV1 data);

    /**
     * Gets data for the provided username.
     *
     * @param username username of the player to get data for
     * @return data if player is registered, otherwise empty PlayerEntryV1
     */
    @Nullable
    PlayerEntryV1 getUserData(String username);

    /**
     * Deletes data for the provided username.
     *
     * @param username username of player to delete data for
     */
    void deleteUserData(String username);

    /**
     * Updates player's data.
     *
     * @param data data to put inside database
     */
    void updateUserData(PlayerEntryV1 data);

    /**
     * Get all data from DbApi.
     * @return HashMap with all data.
     */
    HashMap<String, PlayerEntryV1> getAllData();

    /**
     * Migrate data from v1 to v2.
     * @param userCache HashMap of usernames and UUIDs.
     */
    void migrateFromV1(HashMap<String, String> userCache);

    default PlayerEntryV1 migrateFromV1(String data, String username) {
        String lowerCaseUsername = username.toLowerCase(Locale.ENGLISH);

        PlayerCacheV0 playerCache = PlayerCacheV0.fromJson(data);
        PlayerEntryV1 playerEntry = new PlayerEntryV1(username, lowerCaseUsername, data);

        playerEntry.lastAuthenticated = playerCache.validUntil - EasyAuth.config.sessionTimeout * 1000;
        playerEntry.registrationDate = -1;
        if (technicalConfig.forcedOfflinePlayers.contains(lowerCaseUsername) || technicalConfig.forcedOfflinePlayers.contains(username)) {
            playerEntry.onlineAccount = PlayerEntryV1.OnlineAccount.FALSE;
        } else if (technicalConfig.confirmedOnlinePlayers.contains(lowerCaseUsername) || technicalConfig.confirmedOnlinePlayers.contains(username)) {
            playerEntry.onlineAccount = PlayerEntryV1.OnlineAccount.TRUE;
        }

        return playerEntry;
    }
}
