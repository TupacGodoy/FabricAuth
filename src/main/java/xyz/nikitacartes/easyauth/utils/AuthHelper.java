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
        if (config.enableGlobalPassword && !config.singleUseGlobalPassword) {
            // We have global password enabled
            // Player must know global password if not registered
            char[] passwordCopy = password.clone();
            if (verifyPassword(passwordCopy, technicalConfig.globalPassword)) {
                return PasswordOptions.CORRECT;
            } else {
                if (playerEntry == null || playerEntry.password.isEmpty()) {
                    return PasswordOptions.WRONG;
                }
            }
        }
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
        // Verify password
        return verifyPassword(password, storedPassword) ? PasswordOptions.CORRECT : PasswordOptions.WRONG;
    }

    public static PasswordOptions checkPassword(String username, char[] password) {
        return checkPassword(DB.getUserData(username), password);
    }

    public static PasswordOptions checkPassword(PlayerAuth player, char[] password) {
        return checkPassword(player.easyAuth$getPlayerEntryV1(), password);
    }

    public static boolean checkGlobalPassword(char[] password) {
        return verifyPassword(password, technicalConfig.globalPassword);
    }

    public static String hashPassword(char[] password) {
        return HasherArgon2.hash(password);
    }

    private static boolean verifyPassword(char[] pass, String hashed) {
        boolean success = HasherArgon2.verify(pass, hashed);
        if (!success && extendedConfig.checkUnmigratedArgon2 && HasherBCrypt.verify(pass, hashed)) {
            return true;
        }
        return success;
    }

    public enum PasswordOptions {
        CORRECT,
        WRONG,
        NOT_REGISTERED
    }
}
