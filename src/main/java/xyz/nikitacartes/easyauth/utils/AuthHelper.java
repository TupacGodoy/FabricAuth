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
            if (checkGlobalPassword(passwordCopy)) {
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
            LogDebug("Hashed password (BCrypt): " + HasherBCrypt.hash(password));
        }
        // Verify password
        if (!verifyPassword(password, storedPassword)) {
            return PasswordOptions.WRONG;
        }
        // Rehash password if it's using Argon2
        if (storedPassword.startsWith("$argon2")) {
            playerEntry.password = HasherBCrypt.hash(password);
            playerEntry.update();
        }
        return PasswordOptions.CORRECT;
    }

    public static PasswordOptions checkPassword(String username, char[] password) {
        return checkPassword(DB.getUserData(username), password);
    }

    public static PasswordOptions checkPassword(PlayerAuth player, char[] password) {
        return checkPassword(player.easyAuth$getPlayerEntryV1(), password);
    }

    public static boolean checkGlobalPassword(char[] password) {
        if (!verifyPassword(password, technicalConfig.globalPassword)) return false;

        // Rehash password if it's using Argon2
        if (technicalConfig.globalPassword.startsWith("$argon2")) {
            technicalConfig.globalPassword = HasherBCrypt.hash(password);
            technicalConfig.save();
        }
        return true;
    }

    public static String hashPassword(char[] password) {
        return HasherArgon2.hash(password);
    }

    private static boolean verifyPassword(char[] pass, String hashed) {
        if (hashed.startsWith("$argon2")) {
            return HasherArgon2.verify(pass, hashed);
        }

        return HasherBCrypt.verify(pass, hashed);
    }

    public enum PasswordOptions {
        CORRECT,
        WRONG,
        NOT_REGISTERED
    }
}
