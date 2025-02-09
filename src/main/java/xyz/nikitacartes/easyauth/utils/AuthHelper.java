package xyz.nikitacartes.easyauth.utils;

import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;
import xyz.nikitacartes.easyauth.utils.hashing.HasherArgon2;
import xyz.nikitacartes.easyauth.utils.hashing.HasherBCrypt;

import static xyz.nikitacartes.easyauth.EasyAuth.*;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogDebug;

public class AuthHelper {
    /**
     * Check password using PlayerEntryV1 object
     *
     * @param playerEntry PlayerEntryV1 object
     * @param password    password that needs to be checked
     * @return PasswordOptions enum
     */
    public static PasswordOptions checkPassword(PlayerEntryV1 playerEntry, char[] password) {
        if (playerEntry == null || playerEntry.password.isEmpty()) {
            return PasswordOptions.NOT_REGISTERED;
        }
        String storedPassword = playerEntry.password;
        if (config.debug) {
            LogDebug("Checking password for " + playerEntry.username);
            LogDebug("Stored password's hash: " + storedPassword);
            LogDebug("Hashed password: " + HasherArgon2.hash(password));
            if (extendedConfig.checkUnmigratedArgon2) {
                LogDebug("Hashed password (BCrypt): " + HasherBCrypt.hash(password));
            }
        }
        if (config.enableGlobalPassword) {
            // We have global password enabled
            // Player must know global password or password set by auth register
            char[] passwordCopy = password.clone();
            return verifyPassword(password, technicalConfig.globalPassword) || verifyPassword(passwordCopy, storedPassword) ? PasswordOptions.CORRECT : PasswordOptions.WRONG;
        } else {
            // Verify password
            return verifyPassword(password, storedPassword) ? PasswordOptions.CORRECT : PasswordOptions.WRONG;
        }
    }

    public static PasswordOptions checkPassword(String username, char[] password) {
        return checkPassword(DB.getUserData(username), password);
    }

    public static PasswordOptions checkPassword(PlayerAuth player, char[] password) {
        return checkPassword(player.easyAuth$getPlayerEntryV1(), password);
    }

    public static String hashPassword(char[] password) {
        return HasherArgon2.hash(password);
    }

    private static boolean verifyPassword(char[] pass, String hashed) {
        if (extendedConfig.checkUnmigratedArgon2 && HasherBCrypt.verify(pass, hashed)) {
            return true;
        }
        return HasherArgon2.verify(pass, hashed);
    }

    public enum PasswordOptions {
        CORRECT,
        WRONG,
        NOT_REGISTERED
    }
}
